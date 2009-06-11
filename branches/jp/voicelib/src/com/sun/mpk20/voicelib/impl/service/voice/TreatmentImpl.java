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

import com.sun.mpk20.voicelib.impl.service.voice.work.treatment.*;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Spatializer;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentSetup;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;
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

    private Call call;

    //private CallStatusListener listener;

    public TreatmentImpl(String id, TreatmentSetup setup) throws IOException {
    	this.setup = setup;
	this.id = Util.generateUniqueId(id);

	logger.info("setupTreatment:  id " + this.id + " treatment " + setup.treatment);

	if (VoiceImpl.getInstance().addWork(new CreateTreatmentWork(this)) == false) {
	    treatmentImplCommit();
	    return;
	} 
    }
        
    private void treatmentImplCommit() {
	CallParticipant cp = new CallParticipant();

	cp.setConferenceId(
	    VoiceImpl.getInstance().getVoiceManagerParameters().conferenceId);

	cp.setInputTreatment(setup.treatment);
	cp.setName(this.id);
	//cp.setVoiceDetection(true);
        
	CallSetup callSetup = new CallSetup();

	callSetup.cp = cp;
	callSetup.listener = setup.listener;
	callSetup.managedListenerRef = callSetup.managedListenerRef;

	String callId = id.replaceAll(":", "_");

	VoiceImpl.getInstance().addCallStatusListener(this, callId);

	try {
	    call = new CallImpl(callId, callSetup);
	} catch (IOException e) {
	    logger.info("Unable to setup treatment " + setup.treatment
		+ " " + e.getMessage());
	    return;
	}

	PlayerSetup playerSetup = new PlayerSetup();

	playerSetup.x = setup.x;
	playerSetup.y = setup.y;
	playerSetup.z = setup.z;
	playerSetup.publicSpatializer = setup.spatializer;

	PlayerImpl player = new PlayerImpl(call.getId(), playerSetup);
	
	call.setPlayer(player);
	player.setCall(call);

        AudioGroupPlayerInfo info = new AudioGroupPlayerInfo(true,
            AudioGroupPlayerInfo.ChatType.PUBLIC);

	VoiceManagerParameters parameters = 
	    VoiceImpl.getInstance().getVoiceManagerParameters();

        parameters.stationaryPlayerAudioGroup.addPlayer(player, info);

	VoiceImpl.getInstance().putTreatment(this);
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

    public void setTreatment(String newTreatment) {
	if (VoiceImpl.getInstance().addWork(new SetTreatmentWork(this, newTreatment)) == false) {
	    setTreatmentCommit(newTreatment);
	}
    }

    public void setTreatmentCommit(String newTreatment) {
	// not sure yet what to do.
    }

    public void pause(boolean isPaused) {
	if (VoiceImpl.getInstance().addWork(new PauseTreatmentWork(this, isPaused)) == false) {
	    pauseCommit(isPaused);
	}
    }

    public void pauseCommit(boolean isPaused) {
	try {
	    VoiceImpl.getInstance().getBridgeManager().pauseInputTreatment(id, isPaused);
	} catch (IOException e) {
	    logger.warning("Unable to pause or resume treatment " + id + ": " + e.getMessage());
	}
    }

    public void restart(boolean isPaused) {
	if (VoiceImpl.getInstance().addWork(new RestartTreatmentWork(this, isPaused)) == false) {
	    restartCommit(isPaused);
	}
    }

    public void restartCommit(boolean isPaused) {
	try {
	    VoiceImpl.getInstance().getBridgeManager().restartInputTreatment(id);
	} catch (IOException e) {
	    logger.warning("Unable to pause treatment " + id + ": " + e.getMessage());
	}

	pauseCommit(isPaused);
    }

    public void stop() {
	if (VoiceImpl.getInstance().addWork(new StopTreatmentWork(this)) == false) {
	    stopCommit();
	}
    }

    public void stopCommit() {
	VoiceImpl.getInstance().removeTreatment(this);

	try {
	    call.end(true);
	} catch (IOException e) {
	    logger.warning("Unable to end call for treatment " + getId());
	}
    }

    public void callStatusChanged(CallStatus status) {
	switch (status.getCode()) {
        case CallStatus.ESTABLISHED:
            logger.fine("callEstablished: " + status.getCallId());
	    break;
	
        case CallStatus.MIGRATED:
	    //setup.established = true;
            break;

        case CallStatus.ENDED:
	    logger.warning("Treatment ended:  " + status);

	    VoiceImpl.getInstance().removeTreatment(this);

	    if (call != null) {
	        try {
	            call.end(true);
	        } catch (IOException e) {
		    logger.warning("Unable to end call:  " + call + " "
		        + e.getMessage());
	        }
	    }
	
	    break;
        }

	//if (listener != null) {
	//    listener.callStatusChanged(status);
	//}
    }

    public void commit(TreatmentWork work) {
	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	if (work instanceof CreateTreatmentWork) {
	    treatmentImplCommit();
	    return;
	}

	if (work instanceof SetTreatmentWork) {
	    setTreatmentCommit(((SetTreatmentWork) work).newTreatment);
	    return;
	}

	if (work instanceof StopTreatmentWork) {
	    stopCommit();
	    return;
	}

	if (work instanceof PauseTreatmentWork) {
	    pauseCommit(((PauseTreatmentWork) work).isPaused);
	    return;
	}

	if (work instanceof RestartTreatmentWork) {
	    restartCommit(((RestartTreatmentWork) work).isPaused);
	    return;
	}

	logger.warning("Uknown TreatmentWork:  " + work);
    }

    public String dump() {
	return "  " + id;
    }

    public String toString() {
	return id;
    }

}
