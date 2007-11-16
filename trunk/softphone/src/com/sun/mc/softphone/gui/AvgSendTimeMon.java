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

public class AvgSendTimeMon implements DataUpdater {
    private PerfMon perfMon;

    private MediaManagerImpl mediaManagerImpl;

    private JRadioButton button;

    private boolean closed;

    private long lastTime;
    private int packetsSent;

    public AvgSendTimeMon(JRadioButton button, Point location, int width,
	    int height) {

	this.button = button;

        mediaManagerImpl = (MediaManagerImpl) MediaManagerFactory.getInstance();

	perfMon = new PerfMon("Average Send Time", this, location, width, height);

	lastTime = System.currentTimeMillis();
	packetsSent = mediaManagerImpl.getPacketsSent();
    }

    public void setVisible(boolean isVisible) {
	perfMon.setVisible(isVisible);
    }

    public int getData() {
	int packetsSent = mediaManagerImpl.getPacketsSent();

	long now = System.currentTimeMillis();

	long elapsed = now - lastTime;

	int n = packetsSent - this.packetsSent;

	int avgSendTime = 0;

	if (n != 0 && elapsed != 0) {
	    avgSendTime = (int) (Math.round(
	        ((double) elapsed / n) * 1000) / 1000D);
	}

	this.packetsSent = packetsSent;
	lastTime = now;
	return avgSendTime;
    }

    public void windowClosed() {
	button.setSelected(false);
	closed = true;
    }

    public boolean isClosed() {
	return closed;
    }

}
