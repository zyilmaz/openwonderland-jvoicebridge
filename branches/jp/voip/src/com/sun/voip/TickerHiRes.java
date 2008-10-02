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

package com.sun.voip;

import com.sun.voip.util.hrtimer.HighResTimer;
import com.sun.voip.util.hrtimer.HighResTimerException;
import com.sun.voip.util.hrtimer.HighResTimerFactory;

public class TickerHiRes implements Ticker {

    private String id;

    private HighResTimer highResTimer;

    private boolean armed;

    private long startTime;
    private int count;

    public TickerHiRes(String id) throws TickerException {
	this.id = id;

	try {
	    highResTimer = HighResTimerFactory.getHighResTimer();
	} catch (HighResTimerException e) {
	    throw new TickerException("Can't create HighResTimer " 
		+ e.getMessage());
	}
    }

    public void arm(long delay, long timePeriod) {
	startTime = System.currentTimeMillis();
	count = 0;

	highResTimer.arm(timePeriod, timePeriod);

	armed = true;
    }

    public void disarm() {
	highResTimer.disarm();
    }

    public void tick() throws TickerException {
	if (!armed) {
	    throw new TickerException(id + ":  ticker not armed");
	}
	
	highResTimer.tick();
	count++;
    }

    public void printStatistics() {
       if (count > 0) {
            Logger.println(id
                + " average time between ticks "
                + ((float) (System.currentTimeMillis() - startTime) /
                (float) count) + " ms");
       }
    }

}
