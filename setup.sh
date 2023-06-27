#!/bin/sh
set -e

echo Creating .venv ...
python3 -m venv .venv
source ./.venv/bin/activate

if [ -z "$JAVA_HOME" ];then 
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    echo Setting default JAVA_HOME="$JAVA_HOME" ...
fi

echo Installing jep ...
pip install jep

echo Intializing gradle ...
./gradlew 
