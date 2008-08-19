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

import java.awt.*;
import java.awt.event.*;
import java.util.Hashtable;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

class PerfMonDialog extends JFrame {

    private JRadioButton rbReceivedPackets;
    private JRadioButton rbMissingPackets;
    private JRadioButton rbTransmittedPackets;
    private JRadioButton rbMicOverflow;
    private JRadioButton rbAvgSendTime;
    private JRadioButton rbAvgRecvTime;
    private JRadioButton rbJitter;

    private int numberOfWindows = 0;

    private int graphWidth = 330;
    private int graphHeight = 100;
    
    public PerfMonDialog() {

        super("Performance Monitor");

	Container contentPane = getContentPane();
        contentPane.setLayout(new GridLayout(5,1));

	Dimension d = new Dimension(350, 150);
	setMinimumSize(d);
	setMaximumSize(d);
	setPreferredSize(d);

        rbReceivedPackets = new JRadioButton(receivedPacketsAction);
	rbReceivedPackets.setText("Received Packets");
	contentPane.add(rbReceivedPackets);
	
        rbTransmittedPackets = new JRadioButton(transmitPacketsAction);
	rbTransmittedPackets.setText("Transmitted Packets");
	contentPane.add(rbTransmittedPackets);

        rbMicOverflow = new JRadioButton(micOverflowAction);
	rbMicOverflow.setText("Microphone Overflow");
	//contentPane.add(rbMicOverflow);

        rbMissingPackets = new JRadioButton(missingPacketsAction);
	rbMissingPackets.setText("Missing Packets");
	contentPane.add(rbMissingPackets);

        rbAvgSendTime = new JRadioButton(avgSendTimeAction);
	rbAvgSendTime.setText("Average Send Time");
	contentPane.add(rbAvgSendTime);

        rbAvgRecvTime = new JRadioButton(avgRecvTimeAction);
	rbAvgRecvTime.setText("Average Receive Time");
	contentPane.add(rbAvgRecvTime);

        rbJitter = new JRadioButton(jitterAction);
	rbJitter.setText("Jitter");
	contentPane.add(rbJitter);
    }

    private Point getGraphLocation() {
	int x = (int) (getLocation().getX() + getWidth());

	int y = (int) (getLocation().getY() + (numberOfWindows / 2) * graphHeight);

	if ((numberOfWindows % 2) != 0) {
	    x += graphWidth;
	}

	numberOfWindows++;

	return new Point(x, y);
    }

    private ReceiveMon receiveMon;

    private Action receivedPacketsAction =
        new AbstractAction("Received Packets") {
            public void actionPerformed(ActionEvent evt) {
		if (rbReceivedPackets.isSelected()) {
		    if (receiveMon == null || receiveMon.isClosed()) {
			Point location = getGraphLocation();

		 	receiveMon = new ReceiveMon(rbReceivedPackets, 
			    location, graphWidth, graphHeight);
		    }

		    receiveMon.setVisible(true);
		} else {
		    if (receiveMon != null) {
			receiveMon.setVisible(false);
		    }
		}
            }
    };

    private TransmitMon transmitMon;

    private Action transmitPacketsAction =
        new AbstractAction("Transmitted Packets") {
            public void actionPerformed(ActionEvent evt) {
		if (rbTransmittedPackets.isSelected()) {
		    if (transmitMon == null || transmitMon.isClosed()) {
			Point location = getGraphLocation();

		 	transmitMon = new TransmitMon(rbTransmittedPackets,
			    location, graphWidth, graphHeight);
		    }

		    transmitMon.setVisible(true);
		} else {
		    if (transmitMon != null) {
			transmitMon.setVisible(false);
		    }
		}
            }
    };

    private MicOverflowMon micOverflowMon;

    private Action micOverflowAction =
        new AbstractAction("Microphone Overflow") {
            public void actionPerformed(ActionEvent evt) {
		if (rbMicOverflow.isSelected()) {
		    if (micOverflowMon == null || micOverflowMon.isClosed()) {
			Point location = getGraphLocation();

		 	micOverflowMon = new MicOverflowMon(rbMicOverflow,
			    location, graphWidth, graphHeight);
		    }

		    micOverflowMon.setVisible(true);
		} else {
		    if (micOverflowMon != null) {
			micOverflowMon.setVisible(false);
		    }
		}
            }
    };

    private MissingPacketsMon missingPacketsMon;

    private Action missingPacketsAction =
        new AbstractAction("Missing Packets") {
            public void actionPerformed(ActionEvent evt) {
		if (rbMissingPackets.isSelected()) {
		    if (missingPacketsMon == null || missingPacketsMon.isClosed()) {
			Point location = getGraphLocation();

		 	missingPacketsMon = new MissingPacketsMon(rbMissingPackets,
			    location, graphWidth, graphHeight);
		    }

		    missingPacketsMon.setVisible(true);
		} else {
		    if (missingPacketsMon != null) {
			missingPacketsMon.setVisible(false);
		    }
		}
            }
    };

    private AvgSendTimeMon avgSendTimeMon;

    private Action avgSendTimeAction =
        new AbstractAction("Average Send Time") {
            public void actionPerformed(ActionEvent evt) {
		if (rbAvgSendTime.isSelected()) {
		    if (avgSendTimeMon == null || avgSendTimeMon.isClosed()) {
			Point location = getGraphLocation();

		 	avgSendTimeMon = new AvgSendTimeMon(rbAvgSendTime,
			    location, graphWidth, graphHeight);
		    }

		    avgSendTimeMon.setVisible(true);
		} else {
		    if (avgSendTimeMon != null) {
			avgSendTimeMon.setVisible(false);
		    }
		}
            }
    };

    private AvgRecvTimeMon avgRecvTimeMon;

    private Action avgRecvTimeAction =
        new AbstractAction("Average Receive Time") {
            public void actionPerformed(ActionEvent evt) {
		if (rbAvgRecvTime.isSelected()) {
		    if (avgRecvTimeMon == null || avgRecvTimeMon.isClosed()) {
			Point location = getGraphLocation();

		 	avgRecvTimeMon = new AvgRecvTimeMon(rbAvgRecvTime,
			    location, graphWidth, graphHeight);
		    }

		    avgRecvTimeMon.setVisible(true);
		} else {
		    if (avgRecvTimeMon != null) {
			avgRecvTimeMon.setVisible(false);
		    }
		}
            }
    };

    private JitterMon jitterMon;

    private Action jitterAction =
        new AbstractAction("Jitter") {
            public void actionPerformed(ActionEvent evt) {
		if (rbJitter.isSelected()) {
		    if (jitterMon == null || jitterMon.isClosed()) {
			Point location = getGraphLocation();

		 	jitterMon = new JitterMon(rbJitter,
			    location, graphWidth, graphHeight);
		    }

		    jitterMon.setVisible(true);
		} else {
		    if (jitterMon != null) {
			jitterMon.setVisible(false);
		    }
		}
            }
    };

}
