package com.sun.mpk20.voicelib.app;

import com.sun.voip.CallParticipant;

import java.io.IOException;
import java.io.Serializable;

import java.util.logging.Level;

public interface VoiceService extends Serializable {

    public BridgeInfo getVoiceBridge() throws IOException;

    public void createCall(CallSetup setup) throws IOException;

    public void muteCall(String id, boolean isMuted) throws IOException;

    public void transferCall(CallParticipant cp) throws IOException;

    public void transferToConference(String id, String conferenceId) throws IOException;

    public void playTreatmentToCall(String id, String treatment) throws IOException;

    public void pauseTreatmentToCall(String id, String treatment) throws IOException;

    public void stopTreatmentToCall(String id, String treatment) throws IOException;

    public void newInputTreatment(String callId, String treatmen) throws IOException;

    public void stopInputTreatment(String callId) throws IOException;

    public void restartInputTreatment(String id) throws IOException;

    public void endCall(String id) throws IOException;

    public void setPrivateMix(String id1, String id2, double[] privateMixParameters) throws IOException;

    public void monitorConference(String conferenceId) throws IOException;

    public void addCallStatusListener(ManagedCallStatusListener listener);

    public void removeCallStatusListener(ManagedCallStatusListener listener);

    public void setLogLevel(Level level);

    public void setSpatialAudio(boolean enabled) throws IOException;

    public void setSpatialMinVolume(double spatialMinVolume) throws IOException;

    public void setSpatialFalloff(double spatialFalloff) throws IOException;

    public void setSpatialEchoDelay(double spatialEchoDelay) throws IOException;

    public void setSpatialEchoVolume(double spatialEchoVolume) throws IOException;

    public void setSpatialBehindVolume(double spatialBehindVolume) throws IOException;

    public void startRecording(String callId, String recordingFile) throws IOException;

    public void pauseRecording(String callId, String recordingFile) throws IOException;

    public void stopRecording(String callId, String recordingFile) throws IOException;

    public void playRecording(String callId, String recordingFile) throws IOException;

    public void pausePlayingRecording(String callId, String recordingFile) throws IOException;

    public void stopPlayingRecording(String callId, String recordingFile) throws IOException;

}
