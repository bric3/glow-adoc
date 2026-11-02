#!/bin/sh
# SPDX-License-Identifier: MPL-2.0

cache_generation=@CACHE_GENERATION@

# Manager execution must never install a missing runtime.
export MISE_AUTO_INSTALL=0 MISE_EXEC_AUTO_INSTALL=0 MISE_OFFLINE=1

launch_timings=no
use_cache=yes
for option do
    case "$option" in
        --) break ;;
        --timings) launch_timings=yes ;;
        --help|--version) use_cache=no ;;
    esac
done

launch_stage() {
    if [ "$launch_timings" = yes ]; then
        printf 'glow-adoc launcher: %s\n' "$1" >&2
    fi
}

launch_stage entered

hash_stdin() {
    if command -v sha256sum >/dev/null 2>&1; then
        hash_result=$(sha256sum) || return 1
    elif command -v shasum >/dev/null 2>&1; then
        hash_result=$(shasum -a 256) || return 1
    else
        return 1
    fi
    printf '%s\n' "${hash_result%% *}"
}

exec_selected() {
    case "$java_manager" in
        mise) exec mise exec java@27 -- "$@" ;;
        asdf|jenv) exec "$java_manager" exec "$@" ;;
        *) exec "$@" ;;
    esac
}

launch_java() {
    launch_mode=$1
    shift
    if [ -n "$artifact_hash" ]; then
        set -- "-Dglowadoc.launcher.artifact=$launcher_location" \
            "-Dglowadoc.launcher.sha256=$artifact_hash" "$@"
    fi
    launch_stage jvm-exec
    # This JDK property also lets Java 8 reach the bootstrap's version guard.
    # JAVA_OPTS intentionally supplies JVM arguments; application arguments stay quoted.
    # shellcheck disable=SC2086
    if [ "$launch_mode" = cached ]; then
        exec_selected "${java_executable:-java}" -Dsun.misc.unsafe.memory.access=allow \
            -Xlog:aot=off:stdout -Xlog:aot=warning:stderr ${JAVA_OPTS:-} "$@"
    fi
    # shellcheck disable=SC2086
    exec_selected "${java_executable:-java}" -Dsun.misc.unsafe.memory.access=allow ${JAVA_OPTS:-} "$@"
}

java_manager=PATH
sdkman_java=${SDKMAN_CANDIDATES_DIR:-${SDKMAN_DIR:-"$HOME/.sdkman"}/candidates}/java/current
in_java_environment=no
if [ "${GLOW_ADOC_JAVA_CONTEXT:-}" = "$cache_generation" ]; then
    in_java_environment=yes
else
    launch_stage manager-query
    if command -v mise >/dev/null 2>&1; then
        java_manager=mise
    elif [ -x "$sdkman_java/bin/java" ] || [ -x "$sdkman_java/Contents/Home/bin/java" ]; then
        # SDKMAN! is a shell function; use its selected candidate without sourcing a profile.
        java_manager=SDKMAN
        JAVA_HOME=$sdkman_java
        [ -x "$JAVA_HOME/bin/java" ] || JAVA_HOME=$JAVA_HOME/Contents/Home
        export JAVA_HOME
        PATH=$JAVA_HOME/bin:$PATH
        export PATH
    elif command -v asdf >/dev/null 2>&1; then
        java_manager=asdf
    elif command -v jenv >/dev/null 2>&1; then
        java_manager=jenv
    elif command -v jabba >/dev/null 2>&1; then
        java_manager=jabba
        JAVA_HOME=$(jabba which --home) || exit "$?"
        [ -n "$JAVA_HOME" ] || { printf 'jabba has no selected JDK.\n' >&2; exit 1; }
        export JAVA_HOME
        PATH=$JAVA_HOME/bin:$PATH
        export PATH
    fi
    launch_stage manager-selected
fi
unset GLOW_ADOC_JAVA_CONTEXT

if [ "$launch_timings" = yes ]; then
    printf 'glow-adoc timing java-manager: %s\n' "$java_manager" >&2
fi

aot_root=${XDG_CACHE_HOME:-"$HOME/.cache"}/glow-adoc/aot
aot_cache=
artifact_hash=
java_executable=
aot_options="${JAVA_OPTS:-} ${JAVA_TOOL_OPTIONS:-} ${JDK_JAVA_OPTIONS:-} ${_JAVA_OPTIONS:-}"
case "$aot_options" in *AOT*|*-agent*|*-javaagent*|*StartFlightRecording*) use_cache=no ;; esac
matching_generation=no
if [ "$use_cache" = yes ]; then
    for stored_cache in "$aot_root/$cache_generation-"*.aot; do
        if [ -f "$stored_cache" ]; then
            matching_generation=yes
            break
        fi
    done
fi
if [ "${1:-}" = --train-aot ] || [ "$matching_generation" = yes ]; then
    if [ "$in_java_environment" = no ] && [ "$java_manager" != PATH ]; then
        # Resolve AOT identity inside the manager's environment, including its JVM option variables.
        export GLOW_ADOC_JAVA_CONTEXT=$cache_generation
        # zsh's sh emulation avoids the large-file startup cost of /bin/sh on macOS.
        if [ -x /bin/zsh ]; then
            set -- /bin/zsh --emulate sh "$0" "$@"
        else
            set -- /bin/sh "$0" "$@"
        fi
        if [ "$java_manager" = asdf ]; then
            # asdf exec accepts managed shims; env runs a utility in the Java shim's environment.
            exec asdf env java "$@"
        fi
        exec_selected "$@"
    fi
    launch_stage runtime-identity
    java_executable=$(command -v java 2>/dev/null)
    # JAVA_HOME resolves manager shims and gives aliases of the same JDK a stable identity.
    if [ "$in_java_environment" = yes ] && [ -n "${JAVA_HOME:-}" ] \
        && [ -x "$JAVA_HOME/bin/java" ] && [ -r "$JAVA_HOME/release" ] \
        && { [ ! -r "${java_executable%/*}/../release" ] \
            || [ "$java_executable" -ef "$JAVA_HOME/bin/java" ]; }; then
        java_executable=$JAVA_HOME/bin/java
    fi
    java_identity=
    if [ -n "$java_executable" ] && [ -r "${java_executable%/*}/../release" ]; then
        while IFS= read -r release_line || [ -n "$release_line" ]; do
            java_identity="$java_identity$release_line
"
        done < "${java_executable%/*}/../release"
    fi
    if [ -n "$java_identity" ]; then
        [ "$launch_timings" != yes ] || printf 'glow-adoc timing java: %s\n' "$java_executable" >&2
        launch_stage artifact-hash-start
        artifact_hash=$(hash_stdin < "$0")
        launch_stage artifact-hash-ready
        if [ -n "$artifact_hash" ]; then
            launcher_dir=${0%/*}
            [ "$launcher_dir" != "$0" ] || launcher_dir=.
            launcher_location=$(CDPATH= cd "$launcher_dir" && printf '%s/%s' "$(pwd -P)" "${0##*/}")
            aot_key=$(printf '%s\n' "$artifact_hash" "${0##*/}" "$java_executable" "$java_identity" "${JAVA_OPTS:-}" "${JAVA_TOOL_OPTIONS:-}" "${JDK_JAVA_OPTIONS:-}" "${_JAVA_OPTIONS:-}" "$(uname -sm)" | hash_stdin)
            [ -z "$aot_key" ] || aot_cache=$aot_root/$cache_generation-$aot_key.aot
            launch_stage cache-key-ready
        fi
    fi
fi
launch_stage cache-lookup-ready
if [ "${1:-}" = --train-aot ]; then
    if [ "$#" -gt 2 ] || [ -z "$aot_cache" ]; then
        printf 'Usage: glow-adoc --train-aot [FILE] (requires JDK release metadata and sha256sum or shasum)\n' >&2
        exit 2
    fi
    if [ "$#" -eq 2 ]; then
        set -- "$2"
    else
        set -- --aot-workload
    fi
    umask 077
    mkdir -p "$aot_root" || exit 1
    training_dir=$(mktemp -d "$aot_root/.training.XXXXXX") || exit 1
    trap 'rm -rf "$training_dir"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' HUP TERM
    # Run in a child so the parent can publish the cache and remove temporary files.
    # shellcheck disable=SC2086
    (exec_selected "$java_executable" -Dsun.misc.unsafe.memory.access=allow ${JAVA_OPTS:-} \
        -XX:+UnlockDiagnosticVMOptions -XX:+AOTCompatibleOopCompression \
        -XX:AOTCacheOutput="$training_dir/cache.aot" -jar "$0" \
        --no-cache --no-pager --color=always "$@") > /dev/null || exit "$?"
    mv "$training_dir/cache.aot" "$aot_cache" || exit 1
    printf 'AOT cache: %s\n' "$aot_cache"
    exit 0
fi
if [ -n "$aot_cache" ] && [ -r "$aot_cache" ]; then
    [ "$launch_timings" != yes ] || printf 'glow-adoc timing aot-cache: %s\n' "$aot_cache" >&2
    # Auto mode falls back to normal startup when the VM cannot use the cache.
    launch_java cached \
        -XX:+UnlockDiagnosticVMOptions -XX:+AOTCompatibleOopCompression \
        -XX:AOTMode=auto -XX:AOTCache="$aot_cache" -jar "$0" "$@"
fi
launch_java normal -jar "$0" "$@"
