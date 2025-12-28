#!/usr/bin/env bash
set -e

SRC_DIR="src"
MAIN_CLASS="com.example.App"

mvn compile
mvn exec:java -Dexec.mainClass="$MAIN_CLASS"