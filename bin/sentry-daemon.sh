#!/bin/sh
usage="Usage: sentry-daemon.sh \
 (start|stop|status)  \
 "

PATH_BIN=`dirname "${BASH_SOURCE-$0}"`

SENTRY_PATH=$PATH_BIN/..
PATH_LOG=$SENTRY_PATH/logs
if [ $# -lt 1 ]; then
    echo $usage
    exit 1
fi

COMMAND=$1

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
     $PATH_BIN/sentry --command service -c #FIXME: (osayankin) Add configuration file here
     echo " `date +%d-%H.%M.%S` INFO : Process started for sentry " >> $PATH_LOG/daemons.txt
esac
