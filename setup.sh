set -x

echo Creating .venv ...
python3 -m venv .venv
source ./.venv/bin/activate

if [ -z "$JAVA_HOME" ];then 
    Setting default JAVA_HOME="$JAVA_HOME" ...
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
fi

echo Installing jep ...
pip install jep

echo Intializing gradle ...
./gradlew 
