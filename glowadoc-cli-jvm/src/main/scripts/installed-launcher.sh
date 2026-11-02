#!/bin/sh
# SPDX-License-Identifier: MPL-2.0
if [ -x /bin/zsh ]; then
    exec /bin/zsh --emulate sh @JAR@ "$@"
fi
exec /bin/sh @JAR@ "$@"
