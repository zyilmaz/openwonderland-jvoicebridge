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

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;


import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo.ChatType;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Spatializer;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentCreatedListener;
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

public class TreatmentImpl implements Treatment, CallStatusListener {

    private static final Logger logger =
        Logger.getLogger(TreatmentImpl.class.getName());

    private String id;
    private TreatmentSetup setup;

    private Call call;

    //private CallStatusListener listener;

    public TreatmentImpl(String id, TreatmentSetup setup) throws IOException {
    	this.setup = setup;
	this.id = Util.generateUniqueId(id).replaceAll(":", "_");

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

	VoiceImpl.getInstance().addCallStatusListener(this, id);

	try {
	    call = new CallImpl(id, callSetup);
	} catch (IOException e) {
	    logger.info("Unable to setup treatment " + setup.treatment
		+ " " + e.getMessage());
	    System.out.println("Unable to setup treatment " + setup.treatment
		+ " " + e.getMessage());
	    return;
	}

	Player player = VoiceImpl.getInstance().getPlayer(id);

	if (player == null) {
	    //System.out.println("Creating new player for " + id);

	    PlayerSetup playerSetup = new PlayerSetup();

	    playerSetup.x = setup.x;
	    playerSetup.y = setup.y;
	    playerSetup.z = setup.z;
	    playerSetup.publicSpatializer = setup.spatializer;

	    player = new PlayerImpl(call.getId(), playerSetup);
	} else {
	    player.setPublicSpatializer(setup.spatializer);
	}
	
	call.setPlayer(player);
	player.setCall(call);

        AudioGroupPlayerInfo info = new AudioGroupPlayerInfo(true,
            AudioGroupPlayerInfo.ChatType.PUBLIC);

	info.listenAttenuation = 0;

	AudioGroup[] audioGroups = player.getAudioGroups();

	for (int i = 0; i < audioGroups.length; i++) {
	    AudioGroup audioGroup = audioGroups[i];

	    AudioGroupPlayerInfo playerInfo = audioGroup.getPlayerInfo(player);

	    if (playerInfo.isSpeaking && 
		    playerInfo.chatType.equals(ChatType.PUBLIC) == false) {

		info.speakingAttenuation = 0;
		break;
	    }
	}

	VoiceManagerParameters parameters = 
	    VoiceImpl.getInstance().getVoiceManagerParameters();

	//System.out.println("Treatment Adding " + info + " for " + player.getId());

        parameters.stationaryPlayerAudioGroup.addPlayer(player, info);

	VoiceImpl.getInstance().putTreatment(this);

	if (setup.treatmentCreatedListener != null) {
	    VoiceImpl.getInstance().scheduleTask(
		new Notifier(setup.treatmentCreatedListener, this, player));
	}
    }

    private class Notifier implements KernelRunnable, NonDurableTransactionParticipant {
	private TreatmentCreatedListener listener;
	private Treatment treatment;
	private Player player;

	public Notifier(TreatmentCreatedListener listener, Treatment treatment, Player player) {
	    this.listener = listener;
	    this.treatment = treatment;
	    this.player = player;
	}

	public String getBaseTaskType() {
	    return Notifier.class.getName();
	}

	public void run() throws Exception {
            VoiceImpl.getInstance().joinTransaction(this);

	    /*
	     * This runs in a transaction and the txnProxy
	     * is usable at this point.  It's okay to get a manager
	     * or another service.
	     *
	     * This method could get called multiple times if
	     * ExceptionRetryStatus is thrown.
	     */
	    listener.treatmentCreated(treatment, player);
        }

        public boolean prepare(Transaction txn) throws Exception {
            return false;
	}

        public void abort(Transaction t) {
	}

	public void prepareAndCommit(Transaction txn) throws Exception {
            prepare(txn);
            commit(txn);
	}

	public void commit(Transaction t) {
	}

        public String getTypeName() {
	    return "AudioGroupNotifier";
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
	    if (call == null) {
		logger.warning("Call is null");
		return;
	    }

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
	    treatmentEnded();
	    break;
	}
    }

    private boolean treatmentEnded;

    public void treatmentEnded() {
	//System.out.println("Treatment ended:  " + treatmentEnded);

	if (treatmentEnded) {
	    return;
	}

	treatmentEnded = true;

	VoiceImpl.getInstance().removeTreatment(this);

	if (call == null) {
	    return;
	}

	try {
	    call.end(false);
	} catch (IOException e) {
	    logger.warning("Unable to end call:  " + call + " "
		+ e.getMessage());
	    call = null;
	}
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
	return "  " + toString();
    }

    public boolean equals(Object o) {
        if (o instanceof Treatment == false) {
            return false;
        }

        return ((Treatment) o).getId().equals(id);
    }

    public String toString() {
	return id + ": " + setup.treatment;
    }

}
