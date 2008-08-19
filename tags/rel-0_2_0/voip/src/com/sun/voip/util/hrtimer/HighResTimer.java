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

import com.sun.voip.Ticker;

/**
 * Interface to a high-resoltion timer, with millisecond accuracy. This must
 * generally have a native implementation to work.
 * @author jkaplan
 */
public interface HighResTimer extends Ticker {
    /**
     * Sleep for the given number of milliseconds.  This method will interfere
     * with an armed timer, so this method throws an exception if the timer
     * is armed.
     * @param millis the number of milliseconds to sleep for
     * @throws IllegalStateException if the timer is armed
     */
    public void sleep(long millis);
    
    /**
     * Arm the timer to tick with the given period and initial delay.  After
     * arming the timer, calling tick() periodically should average the
     * given number of milliseconds.
     * @param delay the initial delay before the first tick, in milliseconds
     * @param period the tick frequency in milliseconds
     */
    public void arm(long delay, long period);
   
    /**
     * Disarm the timer.  Calling tick() after the timer is disarmed will
     * result in an exception.
     */
    public void disarm();
    
    /**
     * Tick periodically.  Once the timer is armed, calling this method 
     * should cause a sleep for, on average, the period given in the call
     * to arm.
     * @throws IllegalStateException if the timer has not been armed
     */
    public void tick();
}
