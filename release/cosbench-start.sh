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

# query_osgi: send a command to the OSGi console and return the output.
# Works with both GNU and OpenBSD netcat (Ubuntu 24.04 ships OpenBSD).
# Falls back to bash /dev/tcp if nc is unavailable.
query_osgi() {
    local cmd="$1"
    local host="$2"
    local port="$3"
    if command -v nc >/dev/null 2>&1; then
        # -w 1: 1-second idle/connection timeout (portable across GNU & OpenBSD nc)
        # timeout 2: hard kill safety net in case nc still hangs
        timeout 2 bash -c "echo '$cmd' | nc -w 1 $host $port" 2>/dev/null
    elif [ -e /dev/tcp ]; then
        timeout 2 bash -c "exec 3<>/dev/tcp/$host/$port; echo '$cmd' >&3; cat <&3; exec 3>&-" 2>/dev/null
    else
        return 1
    fi
}

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

succ=1

for module in $OSGI_BUNDLES
do
        ready=0
        attempts=60
        while [ $ready -ne 1 ];
        do
                query_osgi "ss -s ACTIVE cosbench" 0.0.0.0 $OSGI_CONSOLE_PORT | grep $module >> /dev/null
                if [ $? -ne 0 ];
                then
                        attempts=`expr $attempts - 1`
                        if [ $attempts -eq 0 ];
                        then
                                if [ $attempts -ne 60 ]; then echo ""; fi
                                echo "Starting    $module    [ERROR]"
                                succ=0
                                break
                        else
                                echo -n "."
                                sleep 1
                        fi
                else
                        if [ $attempts -ne 60 ]; then echo ""; fi
                        echo "Starting    $module    [OK]"
                        ready=1
                fi
        done
done

if [ $succ -eq 0 ];
then
        echo "Error in booting cosbench $SERVICE_NAME!"
        exit 1
elif [ $succ -eq 1 ]; then
	echo "Successfully started cosbench $SERVICE_NAME!"
fi

cat $BOOT_LOG

exit 0
