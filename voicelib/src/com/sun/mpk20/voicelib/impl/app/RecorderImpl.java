package com.sun.mpk20.voicelib.impl.app;

import com.sun.sgs.app.AppContext;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.VoiceManager;
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

    private static double scale;

    private static VoiceService backingManager;

    static {
        backingManager =  AppContext.getManager(VoiceManager.class).getBackingManager();

        scale =  AppContext.getManager(VoiceManager.class).getScale();
    }

    public RecorderImpl(String id, RecorderSetup setup) throws IOException {
	this.id = Util.generateUniqueId(id);
	this.setup = setup;

	CallParticipant cp = new CallParticipant();

        String conference = System.getProperty(
	   "com.sun.sgs.impl.service.voice.DEFAULT_CONFERENCE", 
	   VoiceManager.DEFAULT_CONFERENCE);

	cp.setConferenceId(conference);

	cp.setInputTreatment("null");
	cp.setRecorder(true);
	cp.setName(this.id);

	cp.setCallId(this.id);

	cp.setRecordDirectory(setup.recordDirectory);

	CallSetup callSetup = new CallSetup();

	callSetup.cp = cp;
	
	logger.info("New recorder at (" + setup.x + ":" + setup.z + ":" + setup.y + ")");

	VoiceManager vm = AppContext.getManager(VoiceManager.class);

	Call call = vm.createCall(this.id, callSetup);

	PlayerSetup playerSetup = new PlayerSetup();

        playerSetup.x = setup.x;
        playerSetup.y = setup.y;
        playerSetup.z = setup.z;
	playerSetup.isLivePlayer = true;

        Player player = AppContext.getManager(VoiceManager.class).createPlayer(call.getId(), playerSetup);

        call.setPlayer(player);
        player.setCall(call);

	vm.getDefaultLivePlayerAudioGroup().addPlayer(player,
	    new AudioGroupPlayerInfo(true, AudioGroupPlayerInfo.ChatType.PUBLIC));

	logger.finest("back from starting recorder...");
    }

    public String getId() {
	return id;
    }

    public RecorderSetup getSetup() {
	return setup;
    }

    public static Recorder getRecorder(String id) {
	return AppContext.getManager(VoiceManager.class).getRecorders().get(id);
    }

    public void removeRecorder() {
	AppContext.getManager(VoiceManager.class).getRecorders().remove(getId());
    }
	
    public void startRecording(String recordingFile) throws IOException {
	Player player = AppContext.getManager(VoiceManager.class).getPlayer(id);

	if (player == null) {
	    logger.warning("can't find player for " + id);
	    return;
	}

	this.recordingFile = recordingFile;

	player.setRecording(true);

	backingManager.startRecording(id, recordingFile);
    }

    public void pauseRecording() throws IOException {
	Player player = AppContext.getManager(VoiceManager.class).getPlayer(id);

	if (player == null) {
	    logger.warning("can't find player for " + id);
	    return;
	}

	player.setRecording(false);

	backingManager.pauseRecording(id, recordingFile);
    }

    public void stopRecording() throws IOException {
	Player player = AppContext.getManager(VoiceManager.class).getPlayer(id);

	if (player == null) {
	    logger.warning("can't find player for " + id);
	    return;
	}

	player.setRecording(false);

	backingManager.stopRecording(id, recordingFile);
    }

    public void playRecording(String recordingFile) throws IOException {
	Player player = AppContext.getManager(VoiceManager.class).getPlayer(id);

	if (player == null) {
	    logger.warning("can't find player for " + id);
	    return;
	}

	this.recordingFile = recordingFile;

	backingManager.playRecording(id, recordingFile);
    }

    public void pausePlayingRecording() throws IOException {
	Player player = AppContext.getManager(VoiceManager.class).getPlayer(id);

	if (player == null) {
	    logger.warning("can't find player for " + id);
	    return;
	}

	backingManager.pausePlayingRecording(id, recordingFile);
    }

    public void stopPlayingRecording() throws IOException {
	Player player = AppContext.getManager(VoiceManager.class).getPlayer(id);

	if (player == null) {
	    logger.warning("can't find player for " + id);
	    return;
	}

	backingManager.stopPlayingRecording(id, recordingFile);
    }

}
