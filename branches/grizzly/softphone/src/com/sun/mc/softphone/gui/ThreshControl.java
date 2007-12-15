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

package com.sun.mc.softphone.gui;

import java.awt.Point;
import com.sun.mc.softphone.media.AudioTransmitter;

public class ThreshControl implements PCPanelListener {

    private ParameterControl parameterControl;

    private Parameter thresh;

    public ThreshControl(String title) {
	double initialOffThresh = (double)AudioTransmitter.getOffThresh();

	double initialPowerThresholdLimit = 
	    AudioTransmitter.getPowerThresholdLimit();

        thresh = new Parameter(
	    initialOffThresh, initialPowerThresholdLimit, 
	    1D, .01D, 1D, 1.0D, 100, 2.0D);

	parameterControl = new ParameterControl(title, thresh);
	
	parameterControl.addPCPanelListener(this);

	setString((int)initialOffThresh, initialPowerThresholdLimit);
    }

    public void locationChanged(Point location) {
	/*
	 * x = offThresh, y = powerThresholdLimit
	 * offThresh ranges from 1 to 100
	 * powerThresholdLimit ranges from 1.0 to 2.0
	 *
	 * The y coordinate has already been adjusted so that 0 is at the bottom
	 */
	double range = thresh.maxX - thresh.minX;

	int offThresh = (int)
	    ((double)location.getX() / parameterControl.getWidth() * range);

	offThresh += thresh.minX;

	range = thresh.maxY - thresh.minY;

	double powerThresholdLimit = (double)
	    (location.getY() / parameterControl.getHeight() * range);

	powerThresholdLimit += thresh.minY;

	//System.out.println("location " + location + " offThresh = " 
	//    + offThresh + " pow " + powerThresholdLimit);

	AudioTransmitter.setCnThresh(offThresh);
	AudioTransmitter.setPowerThresholdLimit(powerThresholdLimit);

	setString(offThresh, powerThresholdLimit);
    }

    private void setString(int offThresh, double powerThresholdLimit) {
	String s = "offThresh = " + offThresh + ", powerThresholdLimit = " 
	    + powerThresholdLimit;

	parameterControl.setString(s);
    }

    public void setVisible(boolean visible) {
	parameterControl.setVisible(visible);
    }
	
}
