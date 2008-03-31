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

package com.sun.voip.client.connector.test;

import com.sun.voip.CallParticipant;
import com.sun.voip.client.connector.BridgeConnector;
import com.sun.voip.client.connector.CallControl;
import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;
import com.sun.voip.client.connector.impl.BridgeConnectorImpl;
import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple program that demonstrates how to place a call using
 * the bridge connector.  Once the call is placed, its status is
 * monitored until it exits.
 *
 * @author jkaplan
 */
public class PlaceCall implements CallStatusListener {
    /** the bridge connector */
    private BridgeConnector bridge;
    
    /** the name of the conference to connect to */
    private String conference;
    
    /** the phone number to dial */
    private String number;

    /** the callId */
    private String callId;
    
    /** the call controller */
    private CallControl control;
    
    /** 
     * Place and monitor a call to the given host, port and phone number
     * @param host the host name of the bridge to connect to
     * @param port the port number to connect to
     * @param conference the name of the conference to connect to
     * @param number the phone number or SIP address to dial
     */
    public PlaceCall(String host, int port, String conference, String number, 
	    String callId) {

        bridge = new BridgeConnectorImpl(host, port);
    
        this.conference = conference;
        this.number = number;
	this.callId = callId;
    }
    
    /**
     * Connect to the bridge and place the call
     */
    public void placeCall() throws IOException {
        // create a call participant to call
        CallParticipant cp = new CallParticipant();
        cp.setPhoneNumber(number);
        cp.setDtmfDetection(true);
        cp.setVoiceDetection(true);
        
        // create the call control, and set up a listener
        control = bridge.createCallControl(callId, conference);
        control.addCallStatusListener(this);
        
        // now place the call
        control.placeCall(cp);
    }
    
    /**
     * Called when the call status changes
     * @param status the status that changed
     */
    public void callStatusChanged(CallStatus status) {
        System.out.println("Call " + status.getCallId() + ": " + status);
        System.out.println();
        
        switch(status.getCode()) {
            case CallStatus.ESTABLISHED:
                // play a message
                try {
                    control.playTreatment("welcome.au");
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
                break;
            case CallStatus.ENDED:
                // clean up
                bridge.disconnect();
                break;
        }
    }
    
    public static void main(String args[]) {
        if (args.length < 4) {
            System.err.println("Usage: PlaceCall <bridge host> <bridge port>" +
                               "<confefence name> <phone number> [<callId>]");
            System.exit(-1);
        }
        
        // crank up the loggers
        //Logger voipLogger = Logger.getLogger("com.sun.voip");
        //voipLogger.setLevel(Level.FINEST);
        //Handler[] handlers = Logger.getLogger("").getHandlers();
        //for ( int index = 0; index < handlers.length; index++ ) {
        //    handlers[index].setLevel(Level.FINEST);
        //}
        
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String conference = args[2];
        String number = args[3];
	String callId = null;

	if (args.length > 4) {
	    callId = args[4];
	}
        
        try {
            PlaceCall pc = new PlaceCall(host, port, conference, number, callId);
            pc.placeCall();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
