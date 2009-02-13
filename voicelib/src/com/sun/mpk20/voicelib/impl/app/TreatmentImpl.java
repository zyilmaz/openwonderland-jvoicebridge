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

package com.sun.mpk20.voicelib.impl.app;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

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

public class TreatmentImpl implements Treatment, CallStatusListener, Serializable {

    private static final Logger logger =
        Logger.getLogger(TreatmentImpl.class.getName());

    private String id;
    private TreatmentSetup setup;

    Call call;

    CallStatusListener listener;

    public TreatmentImpl(String id, TreatmentSetup setup) throws IOException {
    	this.setup = setup;

	this.id = Util.generateUniqueId(id);

	logger.info("setupTreatment:  id " + this.id + " treatment " + setup.treatment);
        
	/*
	 * This is in a transaction and can abort.
	 * In that case we've already created the treatment.
	 */
        DataManager dm = AppContext.getDataManager();

	WarmStartTreatments warmStartTreatments;

        try {
            warmStartTreatments = (WarmStartTreatments) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTS);
        } catch (NameNotBoundException e) {
            try {
                warmStartTreatments = new WarmStartTreatments();
                dm.setBinding(WarmStartInfo.DS_WARM_START_TREATMENTS, warmStartTreatments);
            }  catch (RuntimeException re) {
                logger.warning("failed to bind warm start treatment map " + re.getMessage());
                throw re;
            }
        }

	warmStartTreatments.put(this.id, setup);

	VoiceManager vm = AppContext.getManager(VoiceManager.class);

	CallParticipant cp = new CallParticipant();

	cp.setConferenceId(vm.getConferenceId());

	cp.setInputTreatment(setup.treatment);
	cp.setName(this.id);
	//cp.setVoiceDetection(true);
        
	CallSetup callSetup = new CallSetup();

	callSetup.cp = cp;

	callSetup.listener = this;

	listener = setup.listener;

	String callId = this.id.replaceAll(":", "_");

	try {
	    call = vm.createCall(callId, callSetup);
	} catch (IOException e) {
	    logger.info("Unable to setup treatment " + setup.treatment
		+ " " + e.getMessage());

	    vm.getTreatments().remove(this.id);

	    throw new IOException("Unable to setup treatment " + setup.treatment
                + " " + e.getMessage());
	} 
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
	VoiceManager vm = AppContext.getManager(VoiceManager.class);

	vm.getTreatments().remove(this);

	try {
	    vm.endCall(call, true);
	} catch (IOException e) {
	    logger.warning("Unable to end call for treatment " + getId());
	}
    }

    public void callStatusChanged(CallStatus status) {
	VoiceManager vm = AppContext.getManager(VoiceManager.class);

	switch (status.getCode()) {
        case CallStatus.ESTABLISHED:
            logger.fine("callEstablished: " + status.getCallId());

	    PlayerSetup playerSetup = new PlayerSetup();

	    playerSetup.x = setup.x;
	    playerSetup.y = setup.y;
	    playerSetup.z = setup.z;
	    playerSetup.publicSpatializer = setup.spatializer;

	    Player player = vm.createPlayer(call.getId(), playerSetup);
	
	    call.setPlayer(player);
	    player.setCall(call);

            AudioGroupPlayerInfo info = new AudioGroupPlayerInfo(true,
                AudioGroupPlayerInfo.ChatType.PUBLIC);

            vm.getDefaultStationaryPlayerAudioGroup().addPlayer(player, info);
	    break;
	
        case CallStatus.MIGRATED:
	    //setup.established = true;
            break;

        case CallStatus.ENDED:
	    logger.warning("Treatment ended:  " + status);

	    vm.getTreatments().remove(this);

	    try {
	        vm.endCall(call, true);
	    } catch (IOException e) {
		logger.warning("Unable to end call:  " + call + " "
		    + e.getMessage());
	    }
	
	    break;
        }

	if (listener != null) {
	    listener.callStatusChanged(status);
	}

    }

    public String dump() {
	return "  " + id;
    }

    public String toString() {
	return id;
    }

}
