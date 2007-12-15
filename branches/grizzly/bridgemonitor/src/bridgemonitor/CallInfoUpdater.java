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

package bridgemonitor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.io.IOException;

import java.util.logging.Logger;

import com.sun.mpk20.voicelib.impl.service.voice.BridgeConnection;

public class CallInfoUpdater extends Thread implements CallMonitorListener {

    private static final Logger logger =
            Logger.getLogger(BridgeMonitor.class.getName());

    private int x;
    private BridgeConnection bc;
    private String callId;

    private JFrame jFrame;
    private JTextArea jTextArea;
    
    private CallMonitor callMonitor;

    private boolean done;

    public CallInfoUpdater(int x, final BridgeConnection bc, 
	    final String callId) {

	this.x = x;
	this.bc = bc;
	this.callId = callId;

	jFrame = new JFrame("CallStatus for " + callId);
        
        GridBagLayout gridBag = new GridBagLayout();
        GridBagConstraints constraints;
        Insets insets;

	jFrame.setLayout(gridBag);

	String text;

	try {
	    text = bc.getCallStatus(callId);
	} catch (IOException e) {
	    text = "Unable to get call status for " + callId
		+ " " + e.getMessage();
	}

        jTextArea = new JTextArea(text);
        JScrollPane jScrollPane = new JScrollPane(jTextArea);

        insets = new Insets(12, 12, 0, 0);  // top, left, bottom, right
        constraints = new GridBagConstraints(
            0, 0, 1, 1,                     // x, y, width, height
            0.0, 0.0,                       // weightx, weighty
            GridBagConstraints.NORTH,        // anchor
            GridBagConstraints.NONE,        // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(jScrollPane, constraints);
        jFrame.add(jScrollPane);

	JButton monitorCallButton = new JButton("Monitor");

	monitorCallButton.addActionListener(
	    new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                if (callMonitor != null) {
		    callMonitor.quit();
		}

		try {
		    Point location = new Point(
			(int) jFrame.getLocation().getX(),
			jFrame.getY());

		    callMonitor = new CallMonitor(location, jFrame.getHeight(),
			getCallMonitorListener(), bc.getPrivateHost(), 
			callId);
		} catch (IOException e) {
		    logger.info("Unable to start call monitor:  " 
			+ e.getMessage());
		}
            }
        });

        insets = new Insets(12, 7, 0, 12);  // top, left, bottom, right

        constraints = new GridBagConstraints(
            0, 1, 1, 1,                     // x, y, width, height
            0.0, 0.0,                       // weightx, weighty
            GridBagConstraints.SOUTH,        // anchor
            GridBagConstraints.NONE,        // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(monitorCallButton, constraints);

	jFrame.add(monitorCallButton, -1);

	jFrame.pack();
	jFrame.setLocation(x, 0);
	jFrame.setVisible(true);
	jFrame.toFront();

	start();
    }
    
    public CallMonitorListener getCallMonitorListener() {
	return this;
    }

    public void done() {
        done = true;
    }

    public void run() {
	while (!done) {
	    String status;

	    try {
	        status = bc.getCallStatus(callId);

	        jTextArea.setText(status);

		try {
		    Thread.sleep(3000);
		} catch (InterruptedException e) {
		}
	    } catch (IOException e) {
	        logger.info(e.getMessage());
	        jTextArea.setText("Unable to get call status for " + callId
		    + " " + e.getMessage());

		break;
	    }
	}
    }

    public void callMonitorDone() {
	callMonitor = null;
    }

}
