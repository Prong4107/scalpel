#!/bin/bash

JEP_PATH="$(echo .venv/lib/python3.*/site-packages/jep/jep-*.jar)"
export CLASSPATH=$JEP_PATH:./scalpel:./

javac scalpel/*.java
java scalpel.Main