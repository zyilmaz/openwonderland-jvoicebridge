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

package com.sun.voip.client;

import com.sun.voip.CallParticipant;
import com.sun.voip.CallEvent;
import com.sun.voip.CallEventListener;
import com.sun.voip.CallState;

import java.io.*;
import java.util.*;
import java.net.*;

/**
 * Call somebody...
 */
public class Call implements CallEventListener {
    public Call() {
    }
        
    public static void main(String[] args) {
        Call call = new Call();

	if (args.length == 1) {
	    call.placeCall(args);
	} else {
	    while (true) {
	        call.placeCall(args);
	    }
	}
    }

    private void placeCall(String[] args) {
	if (args.length < 1) {
	    System.err.println("Usage:  Call <phoneNumber> [<conference id>]");
	    System.exit(1);
	}

	CallParticipant cp = new CallParticipant();

	cp.setPhoneNumber(args[0]);

	if (args.length > 1) {
	    cp.setConferenceId(args[1]);
	} else {
	    cp.setConferenceId("xxx");
	}

	//cp.setCallAnsweredTreatment("ring_tone.au");

	cp.setCallEndTreatment("call_end.au");

	//cp.setFirstConferenceMemberTreatment("first.au");

	cp.setConferenceJoinTreatment("join.au");

	cp.setConferenceLeaveTreatment("leave.au");

	cp.setVoiceDetection(true);

	String serverName = 
	    System.getProperty("com.sun.voip.server.BRIDGE_SERVER_NAME",
	        "proteus.east.sun.com");

	int serverPort = Integer.getInteger(
	    "com.sun.voip.server.Bridge.PORT", 6666).intValue();

	try {
	    bridgeConnector = new BridgeConnector(serverName, serverPort);
	} catch (IOException e) {
	    System.out.println("Can't connect to " + serverName
		+ ":" + serverPort + " " + e.getMessage());
	    System.exit(1);
	}

	bridgeConnector.addCallEventListener(this);

	sendCommand(cp.getCallSetupRequest());

	synchronized (this) {
	    try {
		wait();
	    } catch (InterruptedException e) {
	    }
	}
    }

    BridgeConnector bridgeConnector;

    private void sendCommand(String cmd) {
	try {
            bridgeConnector.sendCommand(cmd);
	} catch (IOException e) {
	    System.out.println("Can't send command to bridge:  " 
		+ e.getMessage());
	    System.exit(1);
	}
    }

    public void callEventNotification(CallEvent callEvent) {
	if (callEvent.equals(CallEvent.STATE_CHANGED) &&
		callEvent.getCallState().equals(CallState.ESTABLISHED)) {

	    sendCommand("cancel");

	    synchronized (this) {
		notifyAll();
	    }
	}

	if (callEvent.equals(CallEvent.STATE_CHANGED) &&
		callEvent.getCallState().equals(CallState.ENDED)) {
	    //System.exit(0);
	}
    }

}
