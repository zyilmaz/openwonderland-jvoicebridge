package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

import java.io.IOException;

public interface Recorder extends Serializable {

    public String getId();

    public RecorderSetup getSetup();

    public void startRecording(String recordingFile) throws IOException;

    public void pauseRecording();

    public void stopRecording() throws IOException;

    public void playRecording(String recordingFile) throws IOException;

    public void pausePlayingRecording() throws IOException;

    public void stopPlayingRecording() throws IOException;

}
