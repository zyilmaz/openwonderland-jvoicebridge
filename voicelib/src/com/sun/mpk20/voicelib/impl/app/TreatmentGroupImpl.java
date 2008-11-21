package com.sun.mpk20.voicelib.impl.app;

import com.sun.sgs.app.AppContext;

import com.sun.mpk20.voicelib.app.TreatmentGroup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.Util;
import com.sun.mpk20.voicelib.app.VoiceManager;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;

import java.io.IOException;
import java.io.Serializable;

import java.util.Collection;
import java.util.Iterator;

import java.util.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class TreatmentGroupImpl implements TreatmentGroup, CallStatusListener, Serializable {

    private static final Logger logger =
        Logger.getLogger(PlayerImpl.class.getName());

    private String id;

    private ConcurrentHashMap<String, Treatment> treatments = new ConcurrentHashMap();

    private int numberTreatmentsDone;

    public TreatmentGroupImpl(String id) {
	this.id = Util.generateUniqueId(id);
    }

    public String getId() {
	return id;
    }

    public void addTreatment(Treatment treatment) {
	String callId = treatment.getCall().getId();

	treatments.put(callId, treatment);
	AppContext.getManager(VoiceManager.class).addCallStatusListener(this, callId);
	restartTreatments();
    }

    public void removeTreatment(Treatment treatment) {
	String callId = treatment.getCall().getId();

	AppContext.getManager(VoiceManager.class).removeCallStatusListener(this, callId);
	treatments.remove(callId);
	restartTreatments();
    }

    public ConcurrentHashMap<String, Treatment> getTreatments() {
	return treatments;
    }

    /*
     * Restart treatments in the group if there's more than one call
     */
    private void restartTreatments() {
	logger.fine("Restarting input treatments for " + id);

	Collection<Treatment> c = treatments.values();

	Iterator<Treatment> it = c.iterator();

	VoiceManager vm = AppContext.getManager(VoiceManager.class);

	while (it.hasNext()) {
	    Treatment treatment = it.next();

	    String callId = treatment.getCall().getId();

	    try {
		logger.fine("Restarting input treatment " + treatment);
	        vm.getBackingManager().restartInputTreatment(callId);
	    } catch (IOException e) {
	        logger.warning("Unable to restart treatment for " + callId
		    + " " + e.getMessage());
	    }
	}
    }

    public void callStatusChanged(CallStatus status) {
	int code = status.getCode();

	String callId = status.getCallId();

	if (callId == null) {
	    return;
	}

	Treatment treatment = treatments.get(callId);

	switch (code) {
        case CallStatus.ESTABLISHED:
        case CallStatus.MIGRATED:
            logger.fine("callEstablished: " + callId);
	    restartTreatments();
            break;

	case CallStatus.TREATMENTDONE:
	    logger.finer("Treatment done: " + status);

	    numberTreatmentsDone++;

	    if (numberTreatmentsDone == treatments.size()) {
		numberTreatmentsDone = 0;
	    }

	    restartTreatments();
	    break;

        case CallStatus.ENDED:
	    logger.info(status.toString());
	    removeTreatment(treatment);
	    break;

	case CallStatus.BRIDGE_OFFLINE:
	    logger.info("Bridge offline: " + status);
	    
	    /*
	     * Treatments are automatically restarted here.
	     * Clients are notified that the bridge went down
	     * and they notify the softphone which then reconnects.
	     */
	    restartTreatments();
	    break;
        }
    }

    public String toString() {
	return id;
    }
    
}
