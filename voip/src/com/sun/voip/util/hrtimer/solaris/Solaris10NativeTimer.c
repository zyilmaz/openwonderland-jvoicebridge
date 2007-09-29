/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <signal.h>
#include <port.h>
#include <time.h>
#include <sys/siginfo.h>

#include "com_sun_voip_util_hrtimer_NativeHighResTimer.h"

/* the port identifier */
int port;

/* the timer */
timer_t timerid;

/*
 * Class:     com_sun_voip_util_hrtimer_NativeHighResTimer
 * Method:    nInit
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_sun_voip_util_hrtimer_NativeHighResTimer_nInit
  (JNIEnv *env, jobject obj)
{
    struct sigevent sigev;
    port_notify_t   pnotif;
    int             ret;

    // create the port to listen on
    port = port_create();
    if (port == -1) {
        perror("Cannot create event port [port_create(3C)]");
	return (-1);
    }
	
    printf("\nThe port identifier is %d\n",port);

    /* Setup the port notification structure */
    pnotif.portnfy_port = port;
    pnotif.portnfy_user = NULL;

    /* Setting up the signal event structure which
       is returned when the timer fires. Note the
       port notification structure is pointed to 
       by the signal event value.
     */
    sigev.sigev_notify = SIGEV_PORT;
    sigev.sigev_value.sival_ptr = &pnotif;

    /* Create a timer using the realtime clock */
    ret = timer_create(CLOCK_HIGHRES, &sigev, &timerid);
    if (ret == -1) {
        perror("Could not create a timer [timer_create(3RT)]");
	return (-1);
    }

    return ret;
}

/*
 * Class:     com_sun_voip_util_hrtimer_NativeHighResTimer
 * Method:    nSleep
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_voip_util_hrtimer_NativeHighResTimer_nSleep
  (JNIEnv *env, jobject obj, jlong millis) 
{
    itimerspec_t    itimeout;
    port_event_t    pev;
    int             ret;
    
    itimeout.it_value.tv_sec = 0;
    itimeout.it_value.tv_nsec = millis * 1000000;
    itimeout.it_interval.tv_sec = 0;
    itimeout.it_interval.tv_nsec = 0;

    ret = timer_settime(timerid, 0, &itimeout, NULL);	
    if (ret == -1) {
        perror("Could not arm the timer [timer_settime(3RT)]");
        return;
    }

    ret = port_get(port, &pev, NULL);
    if (ret == -1) {
        perror("\nCould not reap timer events from the port [port_get(3C)]");
    }
    return;
}

/*
 * Class:     com_sun_voip_util_hrtimer_NativeHighResTimer
 * Method:    nArm
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_com_sun_voip_util_hrtimer_NativeHighResTimer_nArm
  (JNIEnv *env, jobject obj, jlong delay, jlong period)
{
    itimerspec_t    itimeout;
    int             ret;

    itimeout.it_value.tv_sec = 0;
    itimeout.it_value.tv_nsec = delay * 1000000;
    itimeout.it_interval.tv_sec = 0;
    itimeout.it_interval.tv_nsec = period * 1000000;

    ret = timer_settime(timerid, 0, &itimeout, NULL);	
    if (ret == -1) {
        perror("Could not arm the timer [timer_settime(3RT)]");
        return;
    }
}

/*
 * Class:     com_sun_voip_util_hrtimer_NativeHighResTimer
 * Method:    nDisarm
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_sun_voip_util_hrtimer_NativeHighResTimer_nDisarm
  (JNIEnv *env, jobject obj)
{
    itimerspec_t    itimeout;
    int             ret;

    itimeout.it_value.tv_sec = 0;
    itimeout.it_value.tv_nsec = 0;
    itimeout.it_interval.tv_sec = 0;
    itimeout.it_interval.tv_nsec = 0;

    ret = timer_settime(timerid, 0, &itimeout, NULL);	
    if (ret == -1) {
        perror("Could not arm the timer [timer_settime(3RT)]");
        return;
    }
}

/*
 * Class:     com_sun_voip_util_hrtimer_NativeHighResTimer
 * Method:    nTick
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_sun_voip_util_hrtimer_NativeHighResTimer_nTick
  (JNIEnv *env, jobject obj)
{
    port_event_t    pev;
    int             ret;

    ret = port_get(port, &pev, NULL);
    if (ret == -1) {
        perror("\nCould not reap timer events from the port [port_get(3C)]");
    }
    return;
}

/*
 * Class:     com_sun_voip_util_hrtimer_NativeHighResTimer
 * Method:    nCleanup
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_sun_voip_util_hrtimer_NativeHighResTimer_nCleanup
  (JNIEnv *env, jobject obj)
{
    int ret;

    ret = timer_delete(timerid);
    if(ret == -1){
        perror("Could not delete the timer [timer_delete(3RT)]");
    }
}
