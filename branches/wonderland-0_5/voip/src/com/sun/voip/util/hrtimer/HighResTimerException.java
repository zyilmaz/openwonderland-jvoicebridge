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
 * An exception thrown by a timer
 * @author jkaplan
 */
public class HighResTimerException extends Exception {
    
    /**
     * Creates a new instance of <code>TimerException</code> 
     * without detail message.
     */
    public HighResTimerException() {
    }
    
    /**
     * Constructs an instance of <code>TimerException</code> with the 
     * specified detail message.
     * @param msg the detail message.
     */
    public HighResTimerException(String msg) {
        super(msg);
    }
    
    
    /**
     * Constructs an instance of <code>TimerException</code> with the 
     * specified detail message and cause.
     * @param msg the detail message.
     * @param cause the underlying cause of this exception
     */
    public HighResTimerException(String msg, Throwable cause) {
        super(msg, cause);
    }
    
    /**
     * Constructs an instance of <code>TimerException</code> with the 
     * specified detail cause.
     * @param cause the underlying cause of this exception
     */
    public HighResTimerException(Throwable cause) {
        super(cause);
    }
}
