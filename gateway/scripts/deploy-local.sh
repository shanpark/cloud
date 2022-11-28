#!/bin/bash

PROCESS=gateway
TARGET_DIR=~/shan

cp ../build/libs/${PROCESS}-*.jar ${TARGET_DIR}/${PROCESS}/${PROCESS}.jar
echo "'${PROCESS}-*.jar' copied to '${TARGET_DIR}/${PROCESS}/'."

cd ${TARGET_DIR}/${PROCESS}
./install.sh
echo "'${PROCESS}.jar' restarted."
