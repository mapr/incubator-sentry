#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

usage="Usage: sentry-daemon.sh \
 (start <configuration file>|stop|status)"

PATH_BIN=`dirname "${BASH_SOURCE-$0}"`

SENTRY_PATH=$PATH_BIN/..
PATH_LOG=$SENTRY_PATH/logs
if [ $# -lt 1 ]; then
    echo $usage
    exit 1
fi

COMMAND=$1
CONF_FILE=$2

if [[ $COMMAND == 'start' && -z $CONF_FILE ]]; then
    echo 'Error: No configuration file is set!'
    echo $usage
    exit 1
fi


case $COMMAND in
 status)
     PROCESS_ID=`jps | grep sentry | awk -F " " '{print $1}' | grep -v '^$'`
     if [ -n "$PROCESS_ID" ];then
         echo Sentry is running as process $PROCESS_ID.
         echo " `date +%m.%d-%H:%M:%S` INFO : Process checked for sentry : TRUE" >> $PATH_LOG/daemons.txt
         exit 0;
     else
         echo Sentry is not running.
         echo " `date +%m.%d-%H:%M:%S` INFO : Process checked for sentry : FALSE" >> $PATH_LOG/daemons.txt
         exit 1;
     fi
 ;;
 stop)
     PROCES_ID=`jps | grep sentry | awk -F " " '{print $1}'`
     if [ -n "$PROCESS_ID" ];then
         kill -9 $PROCES_ID
         exit 0
     else
         echo Sentry is not running.
     fi
 ;;
 start)
     echo " `date +%m.%d-%H:%M:%S` INFO : Process starting for sentry " >> $PATH_LOG/daemons.txt
     $PATH_BIN/sentry --command service -c $CONF_FILE
     echo " `date +%d-%H.%M.%S` INFO : Process started for sentry " >> $PATH_LOG/daemons.txt
esac
