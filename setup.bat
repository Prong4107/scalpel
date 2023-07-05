@echo off

git submodule update --init --recursive

rmdir /F /S /Q .venv .gradle

pip cache remove jep

echo Creating .venv ...
python -m venv .venv
call .venv\Scripts\activate

if not defined JAVA_HOME (
    for /D %%X in ("C:\Program Files\Java\jdk*") do (
        
        if exist "%%X\include" (
            set JAVA_HOME=%%X
            echo Setting default JAVA_HOME="%JAVA_HOME%" ...
            goto :break
        )
    )
)

:break
if not defined JAVA_HOME (
    echo WARNING: Could not find JDK and JAVA_HOME is not set
    echo   If install fails, set JAVA_HOME to a valid JDK and retry
)

echo Installing jep ...
pip install jep

echo Initializing gradle ...
call gradlew
