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

import javax.swing.JRadioButton;

import com.sun.mc.softphone.media.MediaManagerImpl;
import com.sun.mc.softphone.media.MediaManagerFactory;

import com.sun.voip.PerfMon;
import com.sun.voip.DataUpdater;

public class ReceiveMon implements DataUpdater {
    private PerfMon perfMon;

    private MediaManagerImpl mediaManagerImpl;

    private int lastPacketsReceived;

    private JRadioButton button;

    private boolean closed;

    public ReceiveMon(JRadioButton button, Point location, int width,
	    int height) {

	this.button = button;

        mediaManagerImpl = (MediaManagerImpl) MediaManagerFactory.getInstance();

	perfMon = new PerfMon("Received Packets vs Time", this, location,
	    width, height);

	lastPacketsReceived = mediaManagerImpl.getPacketsReceived();
    }

    public void setVisible(boolean isVisible) {
	perfMon.setVisible(isVisible);
    }

    public int getData() {
	int packetsReceived = mediaManagerImpl.getPacketsReceived();

	int ret = packetsReceived - lastPacketsReceived;
	lastPacketsReceived = packetsReceived;
	
	return ret;
    }

    public void windowClosed() {
        button.setSelected(false);
	closed = true;
    }

    public boolean isClosed() {
	return closed;
    }

}
