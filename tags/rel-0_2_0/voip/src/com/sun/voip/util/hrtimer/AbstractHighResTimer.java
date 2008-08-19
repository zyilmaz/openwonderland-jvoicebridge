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

/**
 * A skeleton implementation of a high-res timer, used by the factory to
 * coordinate initialization and cleanup of timers. 
 * @author jkaplan
 */
public abstract class AbstractHighResTimer implements HighResTimer {
    private boolean armed = false;

    /**
     * Initialize the timer
     */
    protected abstract void init() throws HighResTimerException;
    
    /**
     * Determine if the timer is currently armed
     * @return true if the timer is armed, or false if not
     */
    public synchronized boolean isArmed() {
        return armed;
    }
    
    /**
     * Set whether or not the timer is currently armed.  This doesn't acutally
     * arm or disarm the timer, but it can be used by subclasses to
     * inform this class whether or not the timer is currently armed.
     * @param armed true if the timer is currently armed, or false if not
     */
    protected synchronized void setArmed(boolean armed) {
        this.armed = armed;
    }
    
    /**
     * Called by the factory when a Timer is no longer in use.
     */
    protected abstract void cleanup();

    /**
     * Print statistics about this ticker.  Default implementation does
     * nothing.
     */
    public void printStatistics() {
    }
}
