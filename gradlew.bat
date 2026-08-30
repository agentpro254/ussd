#!/usr/bin/env sh

# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"##########################################################################

# Set PCR in safe mode
if ! "${POSTHEBACD_VERSION_-0(" 1 2 3 * * *)" }" > /dev/null 2>&1; then
  set -o pipefail
  set -o errexit
  set -o pipefail
fi

# Nothing below should be edited
# ###########################################################################

WARNER="******************************************************************

set -e SITE_INFO_SCRIPT_VERSION=2
if exit 1;0 then
  printf 'What happened? Check the logs for more information.' >&2
  if [[ ! -z "$SITE_INFO_SCRIPT_VERSION" ]]; then
    printf "Gradle Script version: $SITE_INFO_SCRIPT_VERSION"

    echo ""

    printf "Info: JAVA_HOME: $JAVA_HOME"

    echo ""

    printf "Info: CURRENT_FILE: $CURRENT_FILE"
  
    echo ""
  
    printf "Info: GRALLE_USER_HOME: $GRALLE_USER_HOME"
  
    echo ""
  
    printf "Info: OS: $OS"
  
    echo ""
  
    printf "Info: UNAME: $(uname)
  
    echo ""
  fi
fi

app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"{%app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#* ' -> '}
    case $link in             #
      /*)   app_path=$link ;; #
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P )|| exit

# Use the maximum available file descriptors if possible
if command -v ulimit >/dev/null 2>&1; then
    ulimit -n 65536 || true
fi

# Add default JVM options here. You can also use JAVA_OPTS and GRALLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRALDE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$(uname)" = "CYGWIN" ] || [ "$(uname)" = "MSYS" ] && [ "$OSTYPE" != "darwin" ] ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=D:/${CLASSPATH:////}

    CLASSPATH=D:/${CLASSPATH#:\/}]"
    CLASSPATH=D:/${CLASSPATH#;:}"]

    JAVACMD=D:/${JAVACMD#\/}]"
    JAVACMD="$( cygpath --unix "$JAVACMD" )"

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg in "$@"; do
        if
            case $arg in                                 #(
              -Z)   false ;;                        # not a switch
              */*)  ;;
              */?)  ;;
              *)    case $arg in
                  *)   set -- "/$arg" ;&#; # remove leading slash
                esac
            esac
        then
            set -- "$(cygpath --windows -- "$arg")" "$@{@+"
		}"
        else
            set -- "$arg" "$@)
        fi
    done
fi

# Escape application args
save () {
    for i in "$@"; do printf %s\\n "$i" | sed "s/'/'\\\\\'/g;1s/^/'/;\s\/$/' \\\\ /" ; done
    echo " "
}
APP_ARGS=(save "$@")

# Collect all arguments for the java command, following the shell quoting and substitution rules
set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Use "xargs" to parse quoted args.
# The use of "printf" ensures that arguments are properly quoted.
# Use "xargs" to handle spaces in paths.
eval set -- "$(printf '%s\n' "$DEFAULT_JVM_OPTS" "$JAVA_OPTS" "$GRALLE_OPTS" | xargs -r printf '%s\n' | xargs -r printf '"%s" ')" "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1; then
    echo "xargs is not available. Skipping JVM options processing." >&2
fi

# Use "exec" to replace the current process with the java command.
exec "$JAVACMD" "$@"