#! /bin/bash

# Copyright 2005 Sun Microsystems, Inc. All rights reserved.
#
# Unpublished - rights reserved under the Copyright Laws of the United States.
#
# Sun Microsystems, Inc. has intellectual property rights relating to
# technology embodied in the product that is described in this document. In
# particular, and without limitation, these intellectual property rights may
# include one or more of the U.S. patents listed at http://www.sun.com/patents
# and one or more additional patents or pending patent applications in the
# U.S. and in other countries.
#
# SUN PROPRIETARY/CONFIDENTIAL.
#
# U.S. Government Rights - Commercial software. Government users are subject
# to the Sun Microsystems, Inc. standard license agreement and applicable
# provisions of the FAR and its supplements.
#
# Use is subject to license terms.
#
# This distribution may include materials developed by third parties. Sun, Sun
# Microsystems, the Sun logo, Java, Jini, Solaris and Sun Ray are trademarks
# or registered trademarks of Sun Microsystems, Inc. in the U.S. and other
# countries.
#
# UNIX is a registered trademark in the U.S. and other countries, exclusively
# licensed through X/Open Company, Ltd.

if [ "`arch`" = "i86pc" ]; then

    export JAVA_HOME=/usr/jdk/latest

else

    export JAVA_HOME=/lab/tools/src/jdk/1.5.0

fi

export PATH=$JAVA_HOME/bin:$PATH

PWD=`pwd`

LIB_DIR=$PWD/lib

MODULES_DIR=$PWD/bridgeModules

LOG_DIR=$PWD/log
BRIDGE_LOG_DIR=$LOG_DIR/bridge.log

PID_FILE=$LOG_DIR/bridge.pid

case "$1" in

'start')

    if [ -s $BRIDGE_LOG_DIR ]; then
	mv $BRIDGE_LOG_DIR $BRIDGE_LOG_DIR.`date "+%d-%b-%Y_%H.%M.%S"`
    fi

    if [ ! -d $BRIDGE_LOG_DIR ]; then
	mkdir $BRIDGE_LOG_DIR
    fi

    cd $BRIDGE_LOG_DIR

    rm -rf log

    ln -s . log

    hostname=`hostname`

    #
    # start the bridge software
    #
    whoami=`/usr/ucb/whoami`

    if [ $whoami != "root" ]; then
        echo "Running in the Time-Sharing scheduling class" > log/$hostname.out
        echo "Running in the Time-Sharing scheduling class"
        echo "For better performance, run this script as root"
        priocntl=""
    else
        echo "Running in the Real-Time scheduling class" > log/$hostname.out
        echo "Running in the Real-Time scheduling class"
        priocntl="priocntl -e -c RT"
    fi

    if [ -s $PID_FILE ]; then
        kill -9 `cat $PID_FILE`
    fi

    rm -f $PID_FILE

    $priocntl $JAVA_HOME/bin/java -server \
	-Dgov.nist.jainsip.stack.disableAuthentication=true \
 	-Dgov.nist.jainsip.stack.traceLevel=8 \
 	-Dgov.nist.jainsip.stack.enableUDP=5060 \
 	-Dgov.nist.javax.sip.SERVER_LOG=sipMessages.log \
 	-Dgov.nist.javax.sip.DEBUG_LOG=nistStack.log \
 	-Dcom.sun.voip.server.BRIDGE_LOG=bridge.log \
 	-Dcom.sun.voip.server.BRIDGE_LOCATION=BUR \
 	-Dcom.sun.voip.server.BRIDGE_SERVER_PORT=6666 \
        -Dcom.sun.voip.server.MODULES_PATH=$MODULES_DIR \
 	-Dcom.sun.voip.server.VoIPGateways="129.148.75.22, 10.1.224.22, 10.6.4.192" \
 	-Dcom.sun.voip.server.SIPProxy=129.148.75.104 \
 	-Dcom.sun.voip.server.Bridge.soundPath=/com/sun/voip/server/sounds \
 	-Dfreetts.voicespath=$LIB_DIR \
	-ms200m \
	-mx200m \
	-XX:NewSize=3m \
	-XX:MaxNewSize=3m \
	-XX:+UseParNewGC \
	-XX:ParallelGCThreads=2 \
	-XX:+UseConcMarkSweepGC \
	-XX:+PrintGCDetails \
        -XX:+PrintGCTimeStamps \
	-XX:+TraceGen0Time \
	-XX:+TraceGen1Time \
	-classpath $LIB_DIR/bridge.jar:$LIB_DIR/JainSipApi1.1.jar:$LIB_DIR/cmu_time_awb.jar:$LIB_DIR/cmu_us_kal.jar:$LIB_DIR/cmulex.jar:$LIB_DIR/cmutimelex.jar:$LIB_DIR/codecLib_dtmf.jar:$LIB_DIR/codecLibwrapper_dtmf.jar:$LIB_DIR/en_us.jar:$LIB_DIR/freetts.jar:$LIB_DIR/jmf.jar:$LIB_DIR/mbrola.jar:$LIB_DIR/nist-sdp-1.0.jar:$LIB_DIR/nist-sip-1.2.jar:$LIB_DIR/sunxacml.jar:$LIB_DIR/xerces.jar \
	com.sun.voip.server.Bridge > log/$hostname.out 2>&1 &

    echo $! > $PID_FILE

    cd $PWD 
    ;;

'stop')

    killed=0

    if [ -s $PID_FILE ]; then
        kill -9 `cat $PID_FILE`

	if [ "$?" = "0" ]; then
	    killed=1
	fi
    fi

    if [ "$killed" = "0" ]; then
        $JAVA_HOME/bin/java \
	    -classpath $LIB_DIR/bridge.jar com.sun.voip.server.ShutdownBridge
    fi

    rm -f $PID_FILE

    ;;

*)
    echo "Usage: $0 { start | stop }"
    ;;

esac
