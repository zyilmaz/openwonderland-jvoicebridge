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

import com.sun.mpk20.voicelib.impl.service.voice.work.call.*;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioSource;
import com.sun.mpk20.voicelib.app.AudioSink;
import com.sun.mpk20.voicelib.app.BridgeInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallBeginEndListener;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.Util;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;

import com.sun.voip.client.connector.impl.VoiceBridgeConnection;

import java.text.ParseException;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.IOException;
import java.io.Serializable;

public class CallImpl implements Call, CallStatusListener, Serializable {

    private static final Logger logger =
        Logger.getLogger(CallImpl.class.getName());

    private CallSetup setup;

    private String id;

    private Player player;

    private boolean isMuted;

    public CallImpl(String id, CallSetup setup) throws IOException {
	this.id = id;
	this.setup = setup;
	
	if (setup.cp == null) {
	    logger.warning("CallParticipant is null!");
	    throw new IOException("CallParticipant is null!");
	}

	if (setup.cp.getCallId() == null) {
	    if (id != null) {
	        setup.cp.setCallId(Util.generateUniqueId(id));
	    } else {
		id = this.toString();

		int ix = id.indexOf("@");

		if (ix >= 0) {
		    id = id.substring(ix + 1);
		}

	        setup.cp.setCallId(id);
	    }
	} 

	String callee = setup.cp.getPhoneNumber();

	logger.finer("createCall:  callId " + this.id + " callee: " + callee
	    + " Bridge: " + setup.bridgeInfo);

	String name = setup.cp.getName();

        int end;

        if (name == null && callee != null && (end = callee.indexOf("@")) >= 0) {
            name = callee.substring(0, end);

            int start;

	    String pattern = "sip:";

            if ((start = callee.indexOf(pattern)) >= 0) {
                name = callee.substring(start + pattern.length(), end);
            }
        } 

	if (name == null) {
	    name = this.id;
	}

	setup.cp.setName(name);

	if (setup.bridgeInfo == null) {
            BridgeConnection bc = VoiceImpl.getInstance().getBridgeManager().getBridgeConnection();

            BridgeInfo bridgeInfo = new BridgeInfo();

	    bridgeInfo.privateHostName = bc.getPrivateHost();
	    bridgeInfo.privateControlPort = bc.getPrivateControlPort();
            bridgeInfo.publicHostName = bc.getPublicHost();
	    bridgeInfo.publicControlPort = bc.getPublicControlPort();
            bridgeInfo.publicSipPort = bc.getPublicSipPort();

	    setup.bridgeInfo = bridgeInfo;
	}

	//if (setup.managedListenerRef != null) {
        //    VoiceImpl.getInstance().addCallStatusListener(setup.managedListenerRef, id);
        //}

	if (VoiceImpl.getInstance().addWork(new CreateCallWork(this)) == false) {
	    callImplCommit();
	}
    }

    private void callImplCommit() {
	CallParticipant cp = setup.cp;

	if (cp.getPhoneNumber() != null) {
	    logger.log(Level.FINER, "VS:  Setting up call to "
		+ cp.getPhoneNumber() + " on bridge " 
		+ setup.bridgeInfo);
	} else {
	    logger.log(Level.FINER, "VS:  Setting up call to "
		+ cp.getInputTreatment());
	}

	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	voiceImpl.putCall(this);

	VoiceImpl.getInstance().addCallStatusListener(this, id);

	if (setup.listener != null) {
            VoiceImpl.getInstance().addCallStatusListener(setup.listener, id);
        }

	if (setup.incomingCall == false) {
	    try {
		voiceImpl.getBridgeManager().initiateCall(cp, setup.bridgeInfo);
	    } catch (IOException e) {
		logger.log(Level.INFO, e.getMessage());

		/*
		 * TODO:  There's needs to be a way to tell the difference
		 * between a fatal and a recoverable error.
		 */
		voiceImpl.getBridgeManager().addToRecoveryList(id, setup.cp);
		cleanup();
	    } catch (ParseException e) {
		logger.log(Level.INFO, "Unable to setup call:  " + e.getMessage()
		    + " " + cp);

		sendStatus(CallStatus.ENDED, 
		    " Unable to setup call:  " + e.getMessage() + " " + cp);
		cleanup();
		setup.ended = true;
		return;
	    }
	}

        Player p = voiceImpl.getPlayer(id);

        if (p != null) {
            logger.info("Call " + this + " player exists, reset privateMixes.");

            p.setPrivateMixes(true);
        }
        return;
    }

    private void cleanup() {
	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	voiceImpl.removeCallStatusListener(this, id);

	if (setup.listener != null) {
	    voiceImpl.removeCallStatusListener(setup.listener, id);
	}

	voiceImpl.removeCall(this);
    }

    public String getId() {
	return id;
    }

    public CallSetup getSetup() {
	return setup;
    }

    public void setPlayer(Player player) {
	if (VoiceImpl.getInstance().addWork(new SetPlayerWork(this, player)) == false) {
	    setPlayerCommit(player);
	}
    }

    private void setPlayerCommit(Player player) {
	this.player = player;
    }

    public Player getPlayer() {
	return player;
    }

    public void mute(boolean isMuted) throws IOException {
	if (VoiceImpl.getInstance().addWork(new MuteCallWork(this, isMuted)) == false) {
	    muteCommit(isMuted);
	}
    }

    private void muteCommit(boolean isMuted) {
	try {
	    VoiceImpl.getInstance().getBridgeManager().muteCall(id, isMuted);
	} catch (IOException e) {
            logger.warning("Unable to mute call " + this + ":  " + e.getMessage());
	    return;
        }

	this.isMuted = isMuted;

	if (player != null) {
            player.setPrivateMixes(true);
	}
    }

    public boolean isMuted() {
	return isMuted;
    }

    public void transferToConference(String conferenceId) throws IOException {
	if (VoiceImpl.getInstance().addWork(
	        new TransferCallToConferenceWork(this, conferenceId)) == false) {

	    transferToConferenceCommit(conferenceId);
	}
    }

    private void transferToConferenceCommit(String conferenceId) {
        String[] tokens = conferenceId.split(":");

	try {
            VoiceImpl.getInstance().getBridgeManager().transferCall(id, tokens[0]);
	} catch (IOException e) {
	    logger.warning("Unable to transfer call " + this + " to conference "
		+ tokens[0]);
	}
    }

    public void transfer(CallParticipant cp) throws IOException {
	if (VoiceImpl.getInstance().addWork(new MigrateCallWork(this, cp)) == false) {
	    transferCommit(cp);
	}
    }

    private void transferCommit(CallParticipant cp) {
	try {
            VoiceImpl.getInstance().getBridgeManager().migrateCall(cp);
        } catch (IOException e) {
            logger.log(Level.INFO, "Unable to migrate to call "
                + this + " " + e.getMessage());

	    sendStatus(CallStatus.MIGRATE_FAILED, e.getMessage());
	}
    }

    public void newInputTreatment(String treatment) {
	if (VoiceImpl.getInstance().addWork(new NewInputTreatmentWork(this, treatment)) == false) {
	    newInputTreatmentCommit(treatment);
	}
    }

    private void newInputTreatmentCommit(String treatment) {
	try {
            VoiceImpl.getInstance().getBridgeManager().newInputTreatment(id, treatment);
	} catch (IOException e) {
            logger.log(Level.WARNING, "Unable to start input treatment "
                + treatment + " for " + id + " " + e.getMessage());
	}
    }

    public void restartInputTreatment() {
	if (VoiceImpl.getInstance().addWork(new RestartInputTreatmentWork(this)) == false) {
	    restartInputTreatmentCommit();
	}
    }

    public void restartInputTreatmentCommit() {
	try {
            VoiceImpl.getInstance().getBridgeManager().restartInputTreatment(id);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to restart input treatment for "
                + id + " " + e.getMessage());
        }
    }

    public void stopInputTreatment() {
	if (VoiceImpl.getInstance().addWork(new StopInputTreatmentWork(this)) == false) {;
	    stopInputTreatmentCommit();
	}
    }

    private void stopInputTreatmentCommit() {
	try {
            VoiceImpl.getInstance().getBridgeManager().stopInputTreatment(id);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to stop input treatment for "
                + id + " " + e.getMessage());
        }
    }

    public void playTreatment(String treatment) throws IOException {
	if (VoiceImpl.getInstance().addWork(new PlayTreatmentWork(this, treatment)) == false) {
	    playTreatmentCommit(treatment);
	} 
    }

    private void playTreatmentCommit(String treatment) {
	try {
            VoiceImpl.getInstance().getBridgeManager().playTreatmentToCall(id, treatment);
	} catch (IOException e) {
            logger.log(Level.INFO, "Unable to play treatment "
                + treatment + " to call " + id + " " + e.getMessage());
        }
    }

    public void pauseTreatment(String treatment, boolean isPaused)
	   throws IOException {

	if (VoiceImpl.getInstance().addWork(new PauseTreatmentWork(this, treatment, isPaused)) == false) {
	    pauseTreatmentCommit(treatment, isPaused);
	}
    }

    private void pauseTreatmentCommit(String treatment, boolean isPaused) {
	try {
            VoiceImpl.getInstance().getBridgeManager().pauseTreatmentToCall(id, treatment);
	} catch (IOException e) {
            logger.log(Level.INFO, "Unable to pause treatment "
                + treatment + " to call " + id + " " + e.getMessage());
        }
    }

    public void stopTreatment(String treatment) throws IOException {
	if (VoiceImpl.getInstance().addWork(new StopTreatmentWork(this, treatment)) == false) {
	    stopTreatmentCommit(treatment);
	}
    }

    private void stopTreatmentCommit(String treatment) {
	try {
            VoiceImpl.getInstance().getBridgeManager().stopTreatmentToCall(id, treatment);
	} catch (IOException e) {
	    logger.log(Level.INFO, "Unable to stop treatment "
                + treatment + " to call " + id + " " + e.getMessage());
	}
    }

    public void end(boolean removePlayer) throws IOException {
	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	if (voiceImpl.addWork(new EndCallWork(this, removePlayer)) == false) {
	    endCommit(removePlayer);
	} else {
	    voiceImpl.removeCall(this);

	    if (setup.managedListenerRef != null) {
		voiceImpl.removeCallStatusListener(setup.managedListenerRef.get(), id);
	    }
	}
    }

    private void endCommit(boolean removePlayer) {
	if (setup.ended == false) {
	    try {
                VoiceImpl.getInstance().getBridgeManager().endCall(id);
            } catch (IOException e) {
                logger.log(Level.INFO, "Unable to end call " + id
                    + " " + e.getMessage());
            }
	}

	Player player = getPlayer();

	if (removePlayer) {
	    if (player != null) {
		AudioGroup[] audioGroups = player.getAudioGroups();

		for (int i = 0; i < audioGroups.length; i++) {
		    audioGroups[i].removePlayer(player);
		}

	        VoiceImpl.getInstance().removePlayer(player);
	    }
	} else {
	    if (player != null) {
	        player.setCall(null);
	    }
	}
    }

    public void callStatusChanged(CallStatus callStatus) {
	int code = callStatus.getCode();

	String callId = callStatus.getCallId();

        if (callId == null || id.equals(callId) == false) {
            return;
        }

	logger.finer("Call:  callStatus " + callStatus);

	switch (code) {
        case CallStatus.ESTABLISHED:
        case CallStatus.MIGRATED:
            logger.warning("callEstablished: " + callId);

	    String s = callStatus.getOption("IncomingCall");

	    if (s != null && s.equals("true")) {
		handleIncomingCall(callStatus);
	    }

            break;

        case CallStatus.STARTEDSPEAKING:
            break;

        case CallStatus.STOPPEDSPEAKING:
            break;

        case CallStatus.ENDED:
	    logger.info(callStatus.toString());
	    cleanup();
	    setup.ended = true;
	    break;
	}
    }

    private void handleIncomingCall(CallStatus callStatus) {
    }

    public String sourceMoved(AudioSource source,
	    double x, double y, double z, double orientation, double attenuation) {

	return null;
    }

    public String sinkMoved(AudioSink sink,
             double x, double y, double z, double orientation, double attenuation) {
	
	return null;
    }

    public void commit(CallWork work) {
	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	if (work instanceof CreateCallWork) {
	    callImplCommit();
	    return;
	} 

	if (work instanceof SetPlayerWork) {
	    setPlayerCommit(((SetPlayerWork) work).player);
	}

	if (work instanceof NewInputTreatmentWork) {
	    newInputTreatmentCommit(((NewInputTreatmentWork) work).treatment);
	    return;
	} 

	if (work instanceof StopInputTreatmentWork) {
	    stopInputTreatmentCommit();
	    return;
	}

	if (work instanceof RestartInputTreatmentWork) {
	    restartInputTreatmentCommit();
	    return;
	}

	if (work instanceof StopInputTreatmentWork) {
	    stopInputTreatmentCommit();
	    return;
	}

	if (work instanceof PlayTreatmentWork) {
	    playTreatmentCommit(((PlayTreatmentWork) work).treatment);
	    return;
	}

	if (work instanceof PauseTreatmentWork) {
	    PauseTreatmentWork w = (PauseTreatmentWork) work;
	    pauseTreatmentCommit(w.treatment, w.isPaused);
	    return;
	}

	if (work instanceof StopTreatmentWork) {
	    StopTreatmentWork w = (StopTreatmentWork) work;
	    stopTreatmentCommit(w.treatment);
	}

	if (work instanceof TransferCallToConferenceWork) {
	    transferToConferenceCommit(((TransferCallToConferenceWork) work).conferenceId);
	    return;
	}

	if (work instanceof MigrateCallWork) {
	    transferCommit(((MigrateCallWork) work).cp);
	    return;
	} 

	if (work instanceof EndCallWork) {
	    endCommit(((EndCallWork) work).removePlayer);
	    return;
	}

	if (work instanceof MuteCallWork) {
	    muteCommit(((MuteCallWork) work).isMuted);
	    return;
	}
    }

    private void sendStatus(int statusCode, String info) {
        String s = "SIPDialer/1.0 " + statusCode + " "
            + CallStatus.getCodeString(statusCode)
            + " CallId='" + id + "'"
            + " CallInfo='" + info + "'";

        CallStatus callStatus = null;

        try {
            callStatus = VoiceBridgeConnection.parseCallStatus(s);

            if (callStatus == null) {
                logger.info("Unable to parse call status:  " + s);
                return;
            }

	    callStatusChanged(callStatus);
        } catch (IOException e) {
            logger.info("Unable to parse call status:  " + s
		+ " " + e.getMessage());
        }
    }

    public String toString() {
	return setup.cp.toString();
    }

    public String dump() {
	return setup.cp + (isMuted ? " MUTED" : "");
    }

}
