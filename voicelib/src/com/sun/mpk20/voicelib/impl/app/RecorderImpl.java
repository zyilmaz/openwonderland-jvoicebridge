package com.sun.mpk20.voicelib.impl.app;

import com.sun.sgs.app.AppContext;

import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
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

	Call call = AppContext.getManager(VoiceManager.class).createCall(this.id, callSetup);

	//backingManager.setupCall(cp, setup.x / scale, setup.z / scale, setup.y / scale, 
	//    0, setup.spatializer, null);

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
    }

    public void pauseRecording() {
    }

    public void stopRecording() throws IOException {
    }

    public void playRecording(String recordingFile) throws IOException {
    }

    public void pausePlayingRecording() throws IOException {
    }

    public void stopPlayingRecording() throws IOException {
    }

}
