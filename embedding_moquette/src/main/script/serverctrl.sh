#!/bin/sh

FINDNAME=$0
while [ -h $FINDNAME ] ; do FINDNAME=`ls -ld $FINDNAME | awk '{print $NF}'` ; done
SERVER_HOME=`echo $FINDNAME | sed -e 's@/[^/]*$@@'`
unset FINDNAME

if [ "$SERVER_HOME" = '.' ]; then
   SERVER_HOME=$(echo `pwd` | sed 's/\/bin//')
else
   SERVER_HOME=$(echo $SERVER_HOME | sed 's/\/bin//')
fi


NEW_MEMORY=1024m
HEAP_MEMORY=2048m
PERM_MEMORY=256m
CMS_CONFIG=''
SPRING_CONFIG='/usr/local/hikvision/ys7/ypush-api/appconfig/'
XDEBUG=''
    
case $1 in
start)

    JAVA_OPTS="-server -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.EPollSelectorProvider -XX:+HeapDumpOnOutOfMemoryError -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
    JAVA_OPTS="${JAVA_OPTS} -Djava.awt.headless=true -XX:-UseGCOverheadLimit -XX:+DisableExplicitGC -XX:LargePageSizeInBytes=128m -XX:+UseFastAccessorMethods"
	
    shift
    ARGS=($*)
    for ((i=0; i<${#ARGS[@]}; i++)); do
        case "${ARGS[$i]}" in
        -D*)    JAVA_OPTS="${JAVA_OPTS} ${ARGS[$i]}" ;;
        -Module*) MODULE="${ARGS[$i+1]}" ;;
		-New*) NEW_MEMORY="${ARGS[$i+1]}" ;;
        -Heap*) HEAP_MEMORY="${ARGS[$i+1]}" ;;
        -Perm*) PERM_MEMORY="${ARGS[$i+1]}" ;;
		-CMS*) CMS_CONFIG="-XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSCompactAtFullCollection -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=${ARGS[$i+1]}" ;;
        -JmxPort*)  JMX_PORT="${ARGS[$i+1]}" ;;
        -HttpPort*)  HTTP_PORT="${ARGS[$i+1]}" ;;
        -SPRINGCONF*)  SPRING_CONFIG="${ARGS[$i+1]}" ;;
        -XDEBUG*)  XDEBUG="${ARGS[$i+1]}" ;;
          *) parameters="${parameters} ${ARGS[$i]}" ;;
        esac
    done
    
    JAVA_OPTS="${JAVA_OPTS} -XX:NewSize=$NEW_MEMORY -XX:MaxNewSize=$NEW_MEMORY -Xms$HEAP_MEMORY -Xmx$HEAP_MEMORY -XX:PermSize=$PERM_MEMORY -XX:MaxPermSize=$PERM_MEMORY -Duser.dir=$SERVER_HOME $CMS_CONFIG"
    
    if [ "$JMX_PORT" != '' ]; then
        JAVA_OPTS="${JAVA_OPTS} -Dcom.sun.management.jmxremote.port=${JMX_PORT}"
    fi
    
    if [ "$XDEBUG" != '' ]; then
        JAVA_OPTS="${JAVA_OPTS} -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,address=9999,suspend=n"
        JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCTimeStamps -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc_`date '+%Y-%m-%d'`.log -XX:+PrintHeapAtGC"
        echo  "Starting ${MODULE} DEBUG MODLE ... "
    fi
    
    echo  "Starting ${MODULE} ... "
    nohup java $JAVA_OPTS -jar ${SERVER_HOME}/bin/${MODULE}.jar --spring.config.location=${SPRING_CONFIG} &
    echo STARTED
    ;;

stop)

    shift
    ARGS=($*)
    for ((i=0; i<${#ARGS[@]}; i++)); do
        case "${ARGS[$i]}" in
        -Module*) MODULE="${ARGS[$i+1]}" ;;
          *) parameters="${parameters} ${ARGS[$i]}" ;;
        esac
    done
    
    echo "Stopping ${MODULE} ... "
    PROID=`ps -ef|grep ${MODULE}.jar|grep -v grep|awk '{print $2}'`
	if [ -n "$PROID" ]; then
  		echo "Kill process id - ${PROID}"
  		kill -9 ${PROID}
  		echo STOPPED
	else
  		echo "No process running."
	fi
    ;;

    
esac

exit 0
