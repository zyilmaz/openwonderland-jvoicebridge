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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.io.IOException;

import java.util.logging.Logger;

import com.sun.mpk20.voicelib.impl.service.voice.BridgeConnection;

public class CallInfoUpdater extends Thread {

    private static final Logger logger =
            Logger.getLogger(BridgeMonitor.class.getName());

    private int x;
    private BridgeConnection bc;
    private String callId;

    private JFrame jFrame;
    private JTextArea jTextArea;
    
    private boolean done;

    public CallInfoUpdater(int x, BridgeConnection bc, String callId) {
	this.x = x;
	this.bc = bc;
	this.callId = callId;

	jFrame = new JFrame("CallStatus for " + callId);
        
	String text;

	try {
	    text = bc.getCallStatus(callId);
	} catch (IOException e) {
	    text = "Unable to get call status for " + callId
		+ " " + e.getMessage();
	}

        jTextArea = new JTextArea(text);
        JScrollPane jScrollPane = new JScrollPane(jTextArea);

        jFrame.add(jScrollPane);
	jFrame.pack();
	jFrame.setLocation(x, 0);
	jFrame.setVisible(true);
	jFrame.toFront();

	start();
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

}
