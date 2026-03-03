#!/bin/bash
#
#Copyright 2013 Intel Corporation, All Rights Reserved.
#
#Licensed under the Apache License, Version 2.0 (the "License");
#you may not use this file except in compliance with the License.
#You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing, software
#distributed under the License is distributed on an "AS IS" BASIS,
#WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#See the License for the specific language governing permissions and
#limitations under the License.
#

#-------------------------------
# COSBENCH STARTUP SCRIPT
#-------------------------------

SERVICE_NAME=$1

BOOT_LOG=log/$SERVICE_NAME-boot.log

OSGI_BUNDLES="$2"

OSGI_CONSOLE_PORT=$3

OSGI_CONFIG=conf/.$SERVICE_NAME

TOMCAT_CONFIG=conf/$SERVICE_NAME-tomcat-server.xml

#-------------------------------
# MAIN
#-------------------------------

rm -f $BOOT_LOG
mkdir -p log

echo "Launching osgi framwork ... "

/usr/bin/nohup java -Dcosbench.tomcat.config=$TOMCAT_CONFIG -server -cp main/* org.eclipse.equinox.launcher.Main -configuration $OSGI_CONFIG -console $OSGI_CONSOLE_PORT 1> $BOOT_LOG 2>&1 &

if [ $? -ne 0 ];
then
        echo "Error in launching osgi framework!"
        cat $BOOT_LOG
        exit 1
fi

sleep 1

echo "Successfully launched osgi framework!"

echo "Booting cosbench $SERVICE_NAME ... "

TOMCAT_PORT=$(grep 'port=' $TOMCAT_CONFIG | grep -o 'port="[0-9]*"' | head -1 | grep -o '[0-9]*')

attempts=120
started=0
while [ $attempts -gt 0 ]; do
        if grep -q "Service will listen on web port" $BOOT_LOG 2>/dev/null; then
                started=1
                break
        fi
        attempts=`expr $attempts - 1`
        echo -n "."
        sleep 1
done
echo ""

if [ $started -eq 0 ]; then
        echo "Error in booting cosbench $SERVICE_NAME!"
        cat $BOOT_LOG
        exit 1
fi

echo "Successfully started cosbench $SERVICE_NAME!"
if [ -n "$TOMCAT_PORT" ]; then
        echo "Web UI: http://0.0.0.0:${TOMCAT_PORT}/${SERVICE_NAME}/index.html"
fi

cat $BOOT_LOG

exit 0
