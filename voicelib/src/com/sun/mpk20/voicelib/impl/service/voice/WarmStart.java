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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.mpk20.voicelib.app.BridgeInfo;
import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentGroup;
import com.sun.mpk20.voicelib.app.TreatmentSetup;

import com.sun.voip.client.connector.CallStatus;

import java.io.IOException;

import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.Logger;

public class WarmStart {

    private static final Logger logger =
        Logger.getLogger(WarmStart.class.getName());

    private boolean callsEnded;
    private boolean treatmentGroupsRestarted;
    private boolean treatmentsRestarted;
    private boolean recordersRestarted;

    VoiceImpl voiceImpl;

    private HashMap<String, TreatmentGroup> treatmentGroups = new HashMap();

    public WarmStart(VoiceImpl voiceImpl) {
	this.voiceImpl = voiceImpl;

	System.out.println("WARM START");

	if (callsEnded == false) {
	    endCalls();
	}

	if (treatmentGroupsRestarted == false) {
	    restartTreatmentGroups();
	}

	if (treatmentsRestarted == false) {
	    restartTreatments();
	}

        if (recordersRestarted == false) {
	    restartRecorders();
	}
    }

    private void endCalls() {
        WarmStartCalls warmStartCalls;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartCalls = (WarmStartCalls) dm.getBinding(
		WarmStartInfo.DS_WARM_START_CALLS);
        } catch (NameNotBoundException e) {
	    logger.fine("There are no calls to end...");
	    return;
	}

	Enumeration<String> keys = warmStartCalls.keys();

	while (keys.hasMoreElements()) {
	    String callID = keys.nextElement();

	    BridgeManager bridgeManager = voiceImpl.getBridgeManager();

	    BridgeInfo info = warmStartCalls.get(callID);

	    BridgeConnection bc = bridgeManager.findBridge(
		info.publicHostName, String.valueOf(info.publicSipPort));

	    if (bc == null) {
		logger.warning("Unable to find BridgeConnection for " + callID);
		sendStatus(callID);
		continue;
	    }

	    try {
		bc.endCall(callID);
	    } catch (IOException e) {
		logger.warning("Unable to end call " + callID
		    + ": " + e.getMessage());
	    }
	    sendStatus(callID);
	}

	warmStartCalls.clear();
	callsEnded = true;
    }

    private void sendStatus(String callID) {
	HashMap<String, String> options = new HashMap();
		
	options.put("CallId", callID);
	options.put("ConferenceId", voiceImpl.getConferenceId());
	options.put("Reason", "Warm Start");

	CallStatus status = CallStatus.getInstance("SIPDialer/1.0", CallStatus.ENDED, options);

	voiceImpl.callStatusChanged(status);
    }

    private void restartTreatmentGroups() {
	logger.fine("Restarting treatmentGroups...");

        WarmStartTreatmentGroups warmStartTreatmentGroups;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartTreatmentGroups = (WarmStartTreatmentGroups) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTGROUPS);
        } catch (NameNotBoundException e) {
	    System.out.println("There are no treatment groups to restart...");
	    return;
	}

	for (String groupId : warmStartTreatmentGroups) {
	    if (voiceImpl.getTreatmentGroup(groupId) == null) {
		treatmentGroups.put(groupId, voiceImpl.createTreatmentGroup(groupId));
		System.out.println("Restarted treatment group " + groupId);
	    } else {
		System.out.println("Treatment group is already started:  " + groupId);
	    }
	}

	treatmentGroupsRestarted = true;
    }

    private void restartTreatments() {
	System.out.println("Restarting treatments...");

        WarmStartTreatments warmStartTreatments;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartTreatments = (WarmStartTreatments) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTS);
        } catch (NameNotBoundException e) {
	    System.out.println("There are no treatments to restart...");
	    return;
	}

	System.out.println("Treatments to restart:  " + warmStartTreatments.size());

	Enumeration<String> keys = warmStartTreatments.keys();

	while (keys.hasMoreElements()) {
	    String treatmentId = keys.nextElement();

	    if (voiceImpl.getTreatment(treatmentId) == null) {
		WarmStartTreatmentInfo info = warmStartTreatments.get(treatmentId);
                try {
                    Treatment treatment = voiceImpl.createTreatment(treatmentId, info.setup);

                    if (info.groupId != null) {
                        logger.fine("Looking for group " + info.groupId);
                        TreatmentGroup group = treatmentGroups.get(info.groupId);

			if (group == null) {
			    System.out.println("Unable to find treatmentGroup " + info.groupId);
			} else {
                            group.addTreatment(treatment);
			}
                    }
		    System.out.println("Created treatment " + treatmentId);
                } catch (IOException e) {
                    System.out.println("Unable to create treatment " + treatmentId + " "
                    + e.getMessage());
                }
	    } else {
		System.out.println("Treatment is already started:  " + treatmentId);
	    }
	}

	treatmentsRestarted = true;
    }

    private void restartRecorders() {
	logger.fine("Restarting recorders...");

        WarmStartRecorders warmStartRecorders;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartRecorders = (WarmStartRecorders) dm.getBinding(
		WarmStartInfo.DS_WARM_START_RECORDERS);
        } catch (NameNotBoundException e) {
	    logger.fine("There are no recorders to restart...");
	    return;
	}

	Enumeration<String> keys = warmStartRecorders.keys();

	while (keys.hasMoreElements()) {
	    String recorderId = keys.nextElement();

	    if (voiceImpl.getRecorder(recorderId) == null) {
		try {
		    voiceImpl.createRecorder(recorderId, warmStartRecorders.get(recorderId));
		    logger.fine("Restarted recorder " + recorderId);
            	} catch (IOException e) {
                    logger.warning("Unable to restart recorder:  " + recorderId);
                }
	    } else {
		logger.fine("Recorder is already started:  " + recorderId);
	    }
	}

	recordersRestarted = true;
    }

}
