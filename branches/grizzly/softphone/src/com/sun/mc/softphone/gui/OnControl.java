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

import com.sun.mc.softphone.media.AudioTransmitter;

import java.awt.Point;

public class OnControl implements PCPanelListener {

    private ParameterControl parameterControl;

    private Parameter on;

    public OnControl(String title) {
	int initialOnThresh = AudioTransmitter.getOnThresh();
	int initialCnThresh = AudioTransmitter.getCnThresh();

	on = new Parameter(initialOnThresh, initialCnThresh, 
	    1D, 1D, 1D, 1D, 100D, 100D);

        parameterControl = new ParameterControl(title, on);
        
        parameterControl.addPCPanelListener(this);

	setString(initialOnThresh, initialCnThresh);
    }

    public void locationChanged(Point location) {
        /*
         * x = onThresh, y = CnThresh
	 * OnThresh and CnThresh range from 0 to 20.
         *
         * The y coordinate has already been adjusted so that 0 is at the bottom
         */
        double range = on.maxX - on.minX;

        int onThresh = (int)
            ((double)location.getX() / parameterControl.getWidth() * range);

        onThresh += on.minX;

        range = on.maxY - on.minY;

        int cnThresh = (int)
	    ((double)(location.getY() / parameterControl.getHeight()) * range);

        cnThresh += on.minY;

	AudioTransmitter.setOnThresh(onThresh);
	AudioTransmitter.setCnThresh(cnThresh);

	setString(onThresh, cnThresh);
    }

    public void setString(int onThresh, int cnThresh) {
        String s = "onThresh = " + onThresh + ", cnThresh = " + cnThresh;

        parameterControl.setString(s);
    }

    public void setVisible(boolean visible) {
        parameterControl.setVisible(visible);
    }

}

