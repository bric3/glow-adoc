#!/bin/sh
# SPDX-License-Identifier: MPL-2.0

minimum_java_version=@MINIMUM_JAVA_VERSION@

# Discovery and shim execution must never install a missing runtime.
export MISE_AUTO_INSTALL=0 MISE_OFFLINE=1

hash_stdin() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum | cut -d ' ' -f 1
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 | cut -d ' ' -f 1
    else
        return 1
    fi
}

try_java() {
    java_executable=$1
    shift
    [ -x "$java_executable" ] || return 1

    java_version=$("$java_executable" -version 2>&1) || return 1
    java_identity=$java_version
    java_version=$(printf '%s\n' "$java_version" |
        sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)
    case "$java_version" in
        1.*) java_version=${java_version#1.} ;;
    esac
    java_major=${java_version%%[!0-9]*}
    case "$java_major" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ "$java_major" -ge "$minimum_java_version" ] || return 1

    for option do
        if [ "$option" = --timings ]; then
            printf 'glow-adoc timing java: %s\n' "$java_executable" >&2
        fi
    done

    case "$java_executable" in
        */bin/java)
            JAVA_HOME=${java_executable%/bin/java}
            export JAVA_HOME
            ;;
    esac
    aot_root=${XDG_CACHE_HOME:-"$HOME/.cache"}/glow-adoc/aot
    aot_cache=
    use_cache=yes
    case "${1:-}" in --help|--version) use_cache=no ;; esac
    case "${JAVA_OPTS:-}" in *AOT*|*-agent*|*StartFlightRecording*) use_cache=no ;; esac
    if [ "${1:-}" = --train-aot ] || { [ "$use_cache" = yes ] && [ -d "$aot_root" ]; }; then
        artifact_hash=$(hash_stdin < "$0")
        if [ -n "$artifact_hash" ]; then
            launcher_dir=${0%/*}
            [ "$launcher_dir" != "$0" ] || launcher_dir=.
            launcher_location=$(CDPATH= cd "$launcher_dir" && printf '%s/%s' "$(pwd -P)" "${0##*/}")
            aot_key=$(printf '%s\n' "$artifact_hash" "$launcher_location" "$java_executable" "$java_identity" "${JAVA_OPTS:-}" "$(uname -sm)" | hash_stdin)
            [ -z "$aot_key" ] || aot_cache=$aot_root/$aot_key.aot
        fi
    fi
    if [ "${1:-}" = --train-aot ]; then
        if [ "$#" -ne 2 ] || [ -z "$aot_cache" ]; then
            printf 'Usage: glow-adoc --train-aot FILE (requires sha256sum or shasum)\n' >&2
            exit 2
        fi
        umask 077
        mkdir -p "$aot_root" || exit 1
        training_dir=$(mktemp -d "$aot_root/.training.XXXXXX") || exit 1
        trap 'rm -rf "$training_dir"' EXIT
        trap 'exit 130' INT
        trap 'exit 143' HUP TERM
        # JAVA_OPTS supplies JVM arguments, matching ordinary launcher execution.
        # shellcheck disable=SC2086
        "$java_executable" --sun-misc-unsafe-memory-access=allow ${JAVA_OPTS:-} \
            -XX:+UnlockDiagnosticVMOptions -XX:+AOTCompatibleOopCompression \
            -XX:AOTCacheOutput="$training_dir/cache.aot" -jar "$0" \
            --no-pager --color=always "$2" > /dev/null || exit "$?"
        mv "$training_dir/cache.aot" "$aot_cache" || exit 1
        printf 'AOT cache: %s\n' "$aot_cache"
        exit 0
    fi
    case "${JAVA_OPTS:-}" in
        *AOT*|*-agent*|*StartFlightRecording*) aot_cache= ;;
    esac
    if [ -n "$aot_cache" ] && [ -r "$aot_cache" ]; then
        for option do
            if [ "$option" = --timings ]; then
                printf 'glow-adoc timing aot-cache: %s\n' "$aot_cache" >&2
            fi
        done
        # Auto mode falls back to normal startup when the VM cannot use the cache.
        # shellcheck disable=SC2086
        exec "$java_executable" --sun-misc-unsafe-memory-access=allow \
            -Xlog:aot=off:stdout -Xlog:aot=warning:stderr ${JAVA_OPTS:-} \
            -XX:+UnlockDiagnosticVMOptions -XX:+AOTCompatibleOopCompression \
            -XX:AOTMode=auto -XX:AOTCache="$aot_cache" -jar "$0" "$@"
    fi
    # JAVA_OPTS intentionally supplies separate JVM arguments; application arguments stay quoted.
    # shellcheck disable=SC2086
    exec "$java_executable" --sun-misc-unsafe-memory-access=allow ${JAVA_OPTS:-} -jar "$0" "$@"
}

try_java_home() {
    candidate_home=$1
    shift
    [ -n "$candidate_home" ] || return 1
    try_java "$candidate_home/bin/java" "$@"
    try_java "$candidate_home/Contents/Home/bin/java" "$@"
}

if command -v mise >/dev/null 2>&1; then
    try_java_home "$(mise where java 2>/dev/null)" "$@"
    try_java_home "$(mise where "java@$minimum_java_version" 2>/dev/null)" "$@"
    # Only installed versions are listed; resolve each path through mise.
    for installed_version in $(mise ls --installed --no-header java 2>/dev/null |
        sed -n 's/^java[[:space:]][[:space:]]*\([^[:space:]]*\).*/\1/p'); do
        try_java_home "$(mise where "java@$installed_version" 2>/dev/null)" "$@"
    done
fi

# SDKMAN! is a shell function; inspect its installed candidates without sourcing shell profiles.
sdkman_java=${SDKMAN_CANDIDATES_DIR:-${SDKMAN_DIR:-"$HOME/.sdkman"}/candidates}/java
try_java_home "$sdkman_java/current" "$@"
for candidate in "$sdkman_java"/*; do
    try_java_home "$candidate" "$@"
done

if command -v asdf >/dev/null 2>&1; then
    try_java_home "$(asdf where java 2>/dev/null)" "$@"
fi
for candidate in "${ASDF_DATA_DIR:-"$HOME/.asdf"}/installs/java"/*; do
    try_java_home "$candidate" "$@"
done

if command -v jenv >/dev/null 2>&1; then
    try_java_home "$(jenv prefix 2>/dev/null)" "$@"
fi
for candidate in "${JENV_ROOT:-"$HOME/.jenv"}/versions"/*; do
    try_java_home "$candidate" "$@"
done

for candidate in "${JABBA_HOME:-"$HOME/.jabba"}/jdk"/*; do
    try_java_home "$candidate" "$@"
done

try_java_home "${JAVA_HOME:-}" "$@"
try_java "$(command -v java 2>/dev/null)" "$@"

printf 'No Java %s+ installation found. Checked mise, SDKMAN!, asdf-vm, jenv, jabba, JAVA_HOME and PATH.\n' \
    "$minimum_java_version" >&2
exit 1
