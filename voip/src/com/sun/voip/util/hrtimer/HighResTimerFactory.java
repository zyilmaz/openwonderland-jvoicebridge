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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.logging.Logger;

/**
 * A factory for creating high-resolution timers.
 */
public class HighResTimerFactory {
    /** the class of timer to load */
    private static String timerClass;
    
    /* a count & reference queue of timers in use */
    private static int refCount = 0;
    private static ReferenceQueue refs = new ReferenceQueue();
    
    /** a logger */
    private static final Logger logger =
            Logger.getLogger(HighResTimerFactory.class.getName());
    
    /**
     * Create a new timer.
     * @throws HighResTimerException if the timer cannot be created
     */
    public static HighResTimer getHighResTimer() 
        throws HighResTimerException 
    {
        AbstractHighResTimer ahrt;
        
        // instantiate the timer class
        try {
            Class timerClass = getTimerClass();
            ahrt = (AbstractHighResTimer) timerClass.newInstance();
        } catch (Exception ex) {
            throw new HighResTimerException("Error instantiating timer: " +
                    ex, ex);
        }
        
        // initialize the timer
        ahrt.init();
        
        // remember a reference to it
        new WeakReference(ahrt, refs);
        
        // if there were no references, start a cleanup thread
        if (refCount++ == 1) {
            Thread t = new Thread() {
                public void run() {
                    while (refCount > 0) {
                        try {
                            Reference ref = refs.remove();
                            AbstractHighResTimer timer = 
                                    (AbstractHighResTimer) ref.get();
                            timer.cleanup();
                    
                            refCount--;
                        } catch (InterruptedException ie) {
                            refCount = 0;
                        }
                    }
                }
            };
            t.start();
        } 
        
        return ahrt;
    }
    
    /**
     * Set the class of timer to instantiate
     * @param timerClass the fully-qualified class name of the timer
     * class to load
     */
    public static void setTimerClass(String timerClass) {
        HighResTimerFactory.timerClass = timerClass;
    }
    
    /**
     * Get the class of timer to instantiate.  This method determines the
     * class of timer in the following manner:
     * <ol><li>If setTimerClass() has been called, use the class that was set.
     *     <li>If the system property "com.sun.voip.hrtimer.util.TimerClass" is
     *         set, use the class name given at the command line.
     *     <li>Use a native high res timer
     * @return a Class that can be instantiated to create the timer 
     * @throws ClassNotFoundException if there is an error finding the
     * given class
     */
    protected static Class getTimerClass() throws ClassNotFoundException {
        String timerClassName = null;
        
        // first check if timer class or 
        if (timerClass != null) {
            timerClassName = timerClass;
        } else {
            // try the property
            timerClassName = 
                    System.getProperty("com.sun.voip.hrtimer.util.TimerClass");
        }
        
        // if we got a class, load it
        Class clazz;
        if (timerClassName != null) {
            logger.fine("Timer class is " + timerClassName);
            clazz = Class.forName(timerClassName);
        } else {
            // choose a system-appropriate class
            clazz = getSystemTimerClass();
        }
        
        return clazz;
    }
    
    /**
     * Get the default timer class for the current system
     * @return the default timer class for this system, or null if
     * there is none
     */
    private static Class getSystemTimerClass() {
        // try a native timer
        return NativeHighResTimer.class;
    }
    
    public static void main(String args[]) {
        long start;
        long elapsed;
        
        try {
            HighResTimer hrt = getHighResTimer();
        
            for (int i = 0; i < 20; i++) {
                start = System.nanoTime();
                hrt.sleep(20);
                elapsed = System.nanoTime() - start;
                System.out.println("HR Sleep of 20 ms = " + (elapsed / 1000000.0));
            }
            
            for (int i = 0; i < 20; i++) {
                start = System.nanoTime();
                hrt.sleep(1);
                elapsed = System.nanoTime() - start;
                System.out.println("HR Sleep of 1 ms = " + (elapsed / 1000000.0));
            } 
            
            start = System.nanoTime();
            hrt.arm(10, 10);
            for (int i = 0; i < 20; i++) {
                hrt.tick();
                elapsed = System.nanoTime() - start;
                System.out.println(i + " tick of 10 ms = " + (elapsed / 1000000.0));
            }
            
            hrt.disarm();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
