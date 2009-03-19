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

import com.sun.mpk20.voicelib.impl.service.voice.work.recorder.*;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;
import com.sun.mpk20.voicelib.app.VoiceService;
import com.sun.mpk20.voicelib.app.Util;

import com.sun.voip.CallParticipant;

import java.io.IOException;
import java.io.Serializable;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Logger;
import java.util.logging.Level;

public class RecorderImpl implements Recorder, Serializable {

    private static final Logger logger = Logger.getLogger(RecorderImpl.class.getName());

    private String id;
    private RecorderSetup setup;

    private String recordingFile;

    private Call call;

    public RecorderImpl(String id, RecorderSetup setup) throws IOException {
	this.id = Util.generateUniqueId(id);
	this.setup = setup;

	if (VoiceImpl.getInstance().addWork(new CreateRecorderWork(this)) == false) {
	    recorderImplCommit();
	}
    }

    private void recorderImplCommit() {
	CallParticipant cp = new CallParticipant();

	VoiceManagerParameters parameters = 
	    VoiceImpl.getInstance().getVoiceManagerParameters();

	cp.setConferenceId(parameters.conferenceId);
	cp.setInputTreatment("null");
	cp.setRecorder(true);
	cp.setName(this.id);

	cp.setCallId(this.id);

	cp.setRecordDirectory(setup.recordDirectory);

	CallSetup callSetup = new CallSetup();

	callSetup.cp = cp;
	
	logger.info("New recorder at (" + setup.x + ":" + setup.z + ":" + setup.y + ")");

	try {
	    call = new CallImpl(this.id, callSetup);
	} catch (IOException e) {
	    logger.warning("Unable to create call for recorder:  " + this.id
	 	+ ":  " + e.getMessage());
	    return;
	}

	PlayerSetup playerSetup = new PlayerSetup();

        playerSetup.x = setup.x;
        playerSetup.y = setup.y;
        playerSetup.z = setup.z;
	playerSetup.isLivePlayer = true;

        Player player = new PlayerImpl(call.getId(), playerSetup);

        call.setPlayer(player);
        player.setCall(call);

	parameters.livePlayerAudioGroup.addPlayer(player,
	    new AudioGroupPlayerInfo(true, AudioGroupPlayerInfo.ChatType.PUBLIC));

	logger.finest("back from starting recorder...");

	VoiceImpl.getInstance().putRecorder(this);
    }

    private void removeRecorderCommit() {
	VoiceImpl.getInstance().removeRecorder(this);
    }

    public String getId() {
	return id;
    }

    public RecorderSetup getSetup() {
	return setup;
    }

    public void startRecording(String recordingFile) throws IOException {
	if (VoiceImpl.getInstance().addWork(new StartRecorderWork(this, recordingFile)) == false) {
	    startRecordingCommit(recordingFile);
	}
    }

    private void startRecordingCommit(String recordingFile) {
	try {
	    VoiceImpl.getInstance().getBridgeManager().startRecording(id, recordingFile);
	} catch (IOException e) {
	    logger.log(Level.WARNING, "Unable to start recording "
	        + recordingFile + " for " + id + " " + e.getMessage());
	    return;
	}

	this.recordingFile = recordingFile;

	System.out.println("RECORDING TO " + this.recordingFile);
    }

    public void pauseRecording() throws IOException {
	if (recordingFile == null) {
	    throw new IOException("Not recording");
	}

	if (VoiceImpl.getInstance().addWork(new PauseRecorderWork(this)) == false) {
	    pauseRecordingCommit();
	}
    }

    private void pauseRecordingCommit() {
	call.getPlayer().setRecording(false);
    }

    public void stopRecording() throws IOException {
	System.out.println("STOP RECORDING " + recordingFile);

	if (recordingFile == null) {
	    throw new IOException("Not recording");
	}

	if (VoiceImpl.getInstance().addWork(new StopRecorderWork(this)) == false) {
	    stopRecordingCommit();
	}
    }

    private void stopRecordingCommit()  {
	try {
	    VoiceImpl.getInstance().getBridgeManager().stopRecording(id);
	} catch (IOException e) {
	    logger.log(Level.WARNING, "Unable to stop recording "
		+ id + " " + e.getMessage());
	    return;
	}

	call.getPlayer().setRecording(false);
	recordingFile = null;
    }

    public void playRecording(String recordingFile) throws IOException {
	if (VoiceImpl.getInstance().addWork(new PlayRecorderWork(this, recordingFile)) == false) {
	    playRecordingCommit(recordingFile);
	}
    }

    private void playRecordingCommit(String recordingFile) {
	try {
            VoiceImpl.getInstance().getBridgeManager().newInputTreatment(id, recordingFile);
	} catch (IOException e) {
            logger.log(Level.WARNING, "Unable to play recording "
                + recordingFile + " for " + id + " " + e.getMessage());
	    return;
        }

	this.recordingFile = recordingFile;
    }

    public void pausePlayingRecording() throws IOException {
	if (recordingFile == null) {
	    throw new IOException("Not playing");
	}

	if (VoiceImpl.getInstance().addWork(new PauseRecorderWork(this)) == false) {
	    pausePlayingRecordingCommit();
	}
    }

    private void pausePlayingRecordingCommit() throws IOException {
    }

    public void stopPlayingRecording() throws IOException {
	if (recordingFile == null) {
	    throw new IOException("Not playing");
	}

	if (VoiceImpl.getInstance().addWork(new StopPlayingRecorderWork(this)) == false) {
	    stopPlayingRecordingCommit();
	}
    }

    private void stopPlayingRecordingCommit() {
	try {
            VoiceImpl.getInstance().getBridgeManager().stopInputTreatment(id);
	} catch (IOException e) {
            logger.log(Level.WARNING, "Unable to stop input treatment for "
                + id + " " + e.getMessage());
	    return;
        }

	recordingFile = null;
    }

    public void commit(RecorderWork work) {
	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	if (work instanceof CreateRecorderWork) {
	    recorderImplCommit();
	    return;
	} 

	if (work instanceof RemoveRecorderWork) {
	    removeRecorderCommit();
	    return;
	} 

	if (work instanceof StartRecorderWork) {
	    startRecordingCommit(((StartRecorderWork) work).recordingFile);
	    return;
	}

	if (work instanceof StopRecorderWork) {
	    stopRecordingCommit();
	    return;
	} 

	if (work instanceof PauseRecorderWork) {
	    pauseRecordingCommit();
	    return;
	} 

	if (work instanceof PlayRecorderWork) {
	    playRecordingCommit(((PlayRecorderWork) work).recordingFile);
	    return;
	} 

	if (work instanceof StopPlayingRecorderWork) {
	    stopPlayingRecordingCommit();
	    return;
	}

	logger.warning("Unknown RecorderWork:  " + work);
    }

}
