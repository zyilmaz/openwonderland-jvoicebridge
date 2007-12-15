#!/bin/sh

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

hostname=`hostname`

#
# Make a new log directory
#
date=`date +%m%d%y.%H:%M:%S`
mkdir log.$date
chmod 777 log.$date
touch log.$date/$hostname.out
chmod 777 log.$date/$hostname.out
touch log.$date/bridge.log
chmod 777 log.$date/bridge.log
touch log.$date/sipMessages.log
chmod 777 log.$date/sipMessages.log

#
# relink log to new log directory
#
rm -rf log
ln -s log.$date log

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

arch=`/usr/bin/arch`

###if [ "$arch" = "i86pc" ]; then
###    JAVA_HOME=/usr/jdk/latest
###    ANT=/usr/sfw/bin/ant
###else
###    JAVA_HOME=/lab/tools/src/jdk/1.5.0
###    ANT=/lab/tools/bin/ant
###fi

###export JAVA_HOME

###PATH=$JAVA_HOME/bin:$PATH
###export PATH

$priocntl ant run-bridge > log/$hostname.out 2>&1 &

echo $! > log/bridge.pid
