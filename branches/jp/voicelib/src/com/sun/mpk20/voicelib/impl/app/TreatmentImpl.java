package com.sun.mpk20.voicelib.impl.app;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.mpk20.voicelib.app.AmbientSpatializer;
import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Spatializer;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentSetup;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceService;
import com.sun.mpk20.voicelib.app.Util;

import java.lang.Integer;

import java.math.BigInteger;

import java.io.IOException;
import java.io.Serializable;

import java.util.Enumeration;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;

public class TreatmentImpl implements Treatment, CallStatusListener {

    private static final Logger logger =
        Logger.getLogger(TreatmentImpl.class.getName());

    private String id;
    private TreatmentSetup setup;

    Call call;

    public TreatmentImpl(String id, TreatmentSetup setup) throws IOException {
    	this.setup = setup;
	this.id = Util.generateUniqueId(id);

	logger.info("setupTreatment:  id " + this.id + " treatment " + setup.treatment);
        
	VoiceManager vm = AppContext.getManager(VoiceManager.class);

	CallParticipant cp = new CallParticipant();

	cp.setConferenceId(vm.getConferenceId());

	cp.setInputTreatment(getTreatmentFile(setup.treatment));
	cp.setName(this.id);
	//cp.setVoiceDetection(true);
        
        // get a spatializer
        Spatializer spatializer = null;
        
	if (setup.lowerLeftX != setup.upperRightX) {
	     spatializer = new AmbientSpatializer(
		setup.lowerLeftX, setup.lowerLeftY, setup.lowerLeftZ,
		setup.upperRightX, setup.upperRightY, setup.upperRightZ);
	}

	CallSetup callSetup = new CallSetup();

	callSetup.cp = cp;

	callSetup.listener = setup.listener;

	try {
	    call = vm.createCall(this.id, callSetup);
	} catch (IOException e) {
	    logger.info("Unable to setup treatment " + setup.treatment
		+ " " + e.getMessage());

	    throw new IOException("Unable to setup treatment " + setup.treatment
                + " " + e.getMessage());
	} 

	logger.finest("back from starting treatment...");
    }

    public String getId() {
	return id;
    }

    public TreatmentSetup getSetup() {
	return setup;
    }
  
    public Call getCall() {
	return call;
    }

    public void setTreatment(String treatment) {
	// tell backing manager
    }

    public void stop() {
	// need to tell vm to remove treatment
    }

    /**
     * Resolve a treatment name into an absolute file name.  If the treatment
     * name does not start with the path separator, prepend the value of
     * the audioDir property to it to get the file name
     * @param treatment the unresolved treatment name
     * @return the resolved treatment name
     */
    private String getTreatmentFile(String treatment) {
	/*
	 * If it's a URL, leave it alone
	 * XXX there must be a better way to check for this!
	 */
	if (treatment.startsWith("http:")) {
	    return treatment;
	}

        String ps = System.getProperty("file.separator");
        if (!treatment.startsWith(ps)) {
	    VoiceManager vm = AppContext.getManager(VoiceManager.class);
            treatment = vm.getAudioDirectory() + ps + treatment;
        }
        
        return treatment;
    }

    public void callStatusChanged(CallStatus status) {
	VoiceManager vm = AppContext.getManager(VoiceManager.class);

	switch (status.getCode()) {
        case CallStatus.ESTABLISHED:
	    PlayerSetup playerSetup = new PlayerSetup();

	    playerSetup.x = setup.lowerLeftX;
	    playerSetup.y = setup.lowerLeftY;
	    playerSetup.z = setup.lowerLeftZ;
	    
	    Player player = vm.createPlayer(call.getId(), playerSetup);
	
	    call.setPlayer(player);
	    player.setCall(call);

	    vm.getDefaultLivePlayerAudioGroup().addPlayer(player,
                new AudioGroupPlayerInfo(true, AudioGroupPlayerInfo.ChatType.PUBLIC));

            AudioGroupPlayerInfo info = new AudioGroupPlayerInfo(false,
                AudioGroupPlayerInfo.ChatType.PUBLIC);

            info.defaultSpeakingAttenuation = 0;

            vm.getDefaultStationaryPlayerAudioGroup().addPlayer(player, info);
	
        case CallStatus.MIGRATED:
            logger.fine("callEstablished: " + status.getCallId());

	    //setup.established = true;
            break;

        case CallStatus.ENDED:
	    logger.warning("Treatment ended:  " + status);

	    try {
	        vm.endCall(call, true);
	    } catch (IOException e) {
		logger.warning("Unable to end call:  " + call + " "
		    + e.getMessage());
	    }
	
	    break;
        }
    }

    public String toString() {
	return id;
    }

}
