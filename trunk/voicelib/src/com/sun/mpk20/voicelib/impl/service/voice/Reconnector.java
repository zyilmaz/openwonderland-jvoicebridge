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

package com.sun.mpk20.voicelib.impl.service.voice;

import java.io.IOException;

import java.text.ParseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatus;

public class Reconnector extends Thread {

    // logger for this class
    private static final Logger logger = Logger.getLogger(Reconnector.class.getName());

    private static int reconnectorInstance;

    private VoiceServiceImpl voiceService;

    private ConcurrentHashMap<String, CallParticipant> recoveryList =
        new ConcurrentHashMap<String, CallParticipant>();

    private boolean done;

    public Reconnector(VoiceServiceImpl voiceService) {
	this.voiceService = voiceService;

	recoveryList = voiceService.getRecoveryList();

	setName("Reconnector-" + reconnectorInstance++);
	start();
    }

    public void done() {
	done = true;

	synchronized (recoveryList) {
            recoveryList.notifyAll();
        }
    }

    private void waitForWork() {
	try {
	    logger.info(getName() + " waiting for work");
	    recoveryList.wait();
	} catch (InterruptedException ee) {
	}
    }

    public void run() {
	while (!done) {
	    ArrayList<CallParticipant> callsToReconnect =
		new ArrayList<CallParticipant>();

	    synchronized (recoveryList) {
		if (recoveryList.size() == 0) {
		    try {
			recoveryList.wait();
		    } catch (InterruptedException ee) {
		    }
		    continue;
		}

		logger.info("Recovery list size " + recoveryList.size());

	        Collection<CallParticipant> values = recoveryList.values();

		Iterator<CallParticipant> iterator = values.iterator();

		while (iterator.hasNext()) {
		    callsToReconnect.add(iterator.next());
		}
	    }

	    if (done) {
		break;
	    }

	    for (CallParticipant cp : callsToReconnect) {
	    	String callId = cp.getCallId();

	    	if (callId.startsWith("V-") == true) {
		    logger.info("Discarding " + cp);
		    recoveryList.remove(callId);	
		    continue;
		}

		logger.info("Reconnecting " + cp);

		/*
	 	 * Only input treatments need to be restarted here.
	 	 * Softphones will be sent a message telling them
	 	 * to request a new bridge and reconnect.
	 	 */
		if (cp.getInputTreatment() != null) {
		    /*
		     * XXX timing issue here.  
		     * The call may not be ended by
		     * the time we try to restart it in which case 
		     * there is a duplicate callId.
		     *
		     * We'll get an exception here then go back to the
		     * top and retry unless there are no bridgeConnections
		     * in which case we wait for a bridge to come online.
	 	     */
		    try {
	     		voiceService.initiateCall(cp);
	            	sendOfflineStatus(cp);
	 	    } catch (IOException e) {
	     	 	logger.info(e.getMessage());

			waitForBridge();
			break;
		    } catch (ParseException e) {
			logger.info("Something is very wrong!  "
			    + "This call cannot be restarted. "
			    + e.getMessage() + " " + cp);
		    }
		} else {
		    BridgeConnection bc = waitForBridge();
		    sendOfflineStatus(cp, bc);
		}

		recoveryList.remove(callId);
	    }

	    logger.info("Recovery list now has " + recoveryList.size());

	    /*
	     * Send status with no callId to indicate the last bridge down
	     * status has been sent.
	     */
	    CallParticipant cp = new CallParticipant();
	    cp.setPhoneNumber("");
	    cp.setName("");
	    cp.setCallId("");
	    cp.setConferenceId("");
	    sendOfflineStatus(cp);
	}
    }

    private void sendOfflineStatus(CallParticipant cp) {
        sendOfflineStatus(cp, null);
    }

    private void sendOfflineStatus(CallParticipant cp, BridgeConnection bc) {
        String info = cp.getPhoneNumber();

        if (info == null) {
            info = cp.getName();
        }

        int statusCode = CallStatus.BRIDGE_OFFLINE;

        String s = "SIPDialer/1.0 " + statusCode + " "
            + CallStatus.getCodeString(statusCode)
            + " CallId='" + cp.getCallId() + "'"
            + " ConferenceId='" + cp.getConferenceId() + "'";

        if (bc != null) {
            s += " CallInfo='" + bc + "'";
        }

        CallStatus callStatus = null;

        try {
            callStatus = BridgeConnection.parseCallStatus(s);
        } catch (IOException e) {
        }

        if (callStatus == null) {
            logger.info("Unable to parse call status:  " + s);
            return;
        }

        voiceService.sendStatus(cp, cp.getCallId(), callStatus);
    }

    private BridgeConnection waitForBridge() {
        ArrayList<BridgeConnection> bridgeConnections = voiceService.getBridgeConnections();

	synchronized (bridgeConnections) {
	    while (bridgeConnections.size() == 0) {
		logger.info("Waiting for a bridge to come online "
		    + " to finish processing calls");

		try {
		    bridgeConnections.wait();
		} catch (InterruptedException ex) {
		}
	    }

	    return bridgeConnections.get(0);
	}
    }

}
