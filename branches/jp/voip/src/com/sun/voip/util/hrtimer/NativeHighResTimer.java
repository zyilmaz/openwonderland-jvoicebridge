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

package com.sun.voip.util.hrtimer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A high-resoltuion timer for use in Solaris 10. This uses the system's
 * underlying high-resolution timer, timer_create(TIMER_HIGHRES, ...).  Note
 * that by default this timer is only available to root.  In the case of
 * a non-root user starting this timer, it will throw a HighResTimerException
 * in init.
 * @author jkaplan
 */
public class NativeHighResTimer extends AbstractHighResTimer {
    /** a logger */
    private static final Logger logger = 
            Logger.getLogger(NativeHighResTimer.class.getName());
    
    /**
     * Initialize the timer
     */
    protected void init() throws HighResTimerException {
        String osName = System.getProperty("os.name");
        String osVers = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        
        // first try most-qualified name
        if (!tryLoad("hrtimer_" + osName + "_" + osVers + "_" + osArch)) {
            // try just os and architecture
            if (!tryLoad("hrtimer_" + osName + "_" + osArch)) {
                // try just os name
                if (!tryLoad("hrtimer_" + osName)) {
                    // try just the library name
                    if (!tryLoad("hrtimer")) {
                        throw new HighResTimerException("Unable to find " +
                                "library for " + osName + " " + osVers + " " +
                                osArch);
                    }
                }
            }
        }
        
        if (nInit() == -1) {
            throw new HighResTimerException("Unable to create timer");
        }
    }
    
    /**
     * Try to load a native library, return whether it was loaded or not
     * @param libName the name of the library to load
     * @return true if the library was loaded, or false if not
     */
    private boolean tryLoad(String libName) {
        // get rid of any punctuation
        libName = libName.replaceAll("\\p{Punct}", "_");
        
        logger.fine("Trying to load: " + libName);
        
        try {
            System.loadLibrary(libName);
        } catch (UnsatisfiedLinkError ule) {
            logger.log(Level.FINE, "Error loading " + libName + ": " + ule, 
                       ule);
            return false;
        }
        
        return true;
    }
    
    /**
     * Sleep for the given number of milliseconds.  This method will interfere
     * with an armed timer, so this method throws an exception if the timer
     * is armed.
     * 
     * @param millis the number of milliseconds to sleep for
     * @throws IllegalStateException if the timer is armed
     */
    public void sleep(long millis) {
        if (isArmed()) {
            throw new IllegalStateException("Already armed");
        }
        
        nSleep(millis);
    }

    /**
     * Arm the timer to tick with the given period and initial delay.  After
     * arming the timer, calling tick() periodically should average the
     * given number of milliseconds.  If the timer is already armed, this
     * will disarm the timer and re-arm it with the new values.
     * 
     * @param delay the initial delay before the first tick, in milliseconds
     * @param period the tick frequency in milliseconds
     */
    public void arm(long delay, long period) {
        setArmed(true);
        nArm(delay, period);
    }

    /**
     * Disarm the timer.  Calling tick() after the timer is disarmed will
     * result in an exception.
     */
    public void disarm() {
        setArmed(false);
        nDisarm();
    }

    /**
     * Tick periodically.  Once the timer is armed, calling this method 
     * should cause a sleep for, on average, the period given in the call
     * to arm.
     * 
     * @throws IllegalStateException if the timer has not been armed
     */
    public void tick() {
        if (!isArmed()) {
            throw new IllegalStateException("Not armed");
        }
        
        nTick();
    }
    
    /**
     * Called by the factory when a Timer is no longer in use.
     */
    protected void cleanup() {
        nCleanup();
    }

    // native methods
    private native int nInit();
    private native void nSleep(long millis);
    private native void nArm(long delay, long period);
    private native void nDisarm();
    private native void nTick();
    private native void nCleanup();
}
