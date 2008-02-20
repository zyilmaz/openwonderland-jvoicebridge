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

package com.sun.mpk20.voicelib.app;

import java.io.IOException;

import java.util.logging.Level;

import com.sun.voip.CallParticipant;

/**
 * Provides facilities for voice.
 */
public interface VoiceManager {

    public void monitorConference(String conferenceId) throws IOException;

    public String getVoiceBridge();

    public void setupCall(CallParticipant cp, double x, double y, double z,
	double orientation, Spatializer spatializer, String bridge) 
	throws IOException;

    public void transferCall(String callId, String confereneId) throws IOException;

    public void setPublicSpatializer(String callId, Spatializer spatializer);

    public void setPrivateSpatializer(String targetCallId, String sourceCallId,
	Spatializer spatializer);

    public void setIncomingSpatializer(String targetCallId, 
        Spatializer spatializer);

    public void setTalkAttenuator(String callId, double talkAttenuator);

    public double getTalkAttenuator(String callId);

    public void setListenAttenuator(String callId, double listenAttenuator);

    public double getListenAttenuator(String callId);

    public void callEstablished(String callId) throws IOException;

    public void createPlayer(String callId, double x, double y, double z,
	double orientation);

    public void removePlayer(String callId);

    public void newInputTreatment(String callId, String treatment) 
	throws IOException;

    public void stopInputTreatment(String callId) throws IOException;

    public void restartInputTreatment(String callId) throws IOException;

    public void playTreatmentToCall(String callId, String treatment) 
	throws IOException;

    public void pauseTreatmentToCall(String callId, String treatment) 
	throws IOException;

    public void stopTreatmentToCall(String callId, String treatment) 
	throws IOException;

    public void endCall(String callId) throws IOException;

    public void endCall(String callId, boolean tellBackingManager) 
	throws IOException;

    public void disconnectCall(String callId) throws IOException;

    public void muteCall(String callId, boolean isMuted) throws IOException;

    public void restorePrivateMixes() throws IOException;

    public void setPositionAndOrientation(String callId , double x, double y, 
	double z, double orientation) throws IOException;

    public void setPosition(String callId, double x, double y, double z)
	throws IOException;

    public void setOrientation(String callId, double orientation) 
	throws IOException;

    public void setAttenuationRadius(String callId, double attenuationRadius)
	throws IOException;

    public void setAttenuationVolume(String callId, double attenuationVolume)
	throws IOException;

    public void setMasterVolume(String callId, double masterVolume);

    public double getMasterVolume(String callId);

    public void setPrivateMix(String targetCallId, String fromCallId, 
	double[] privateMixParameters) throws IOException;

    public void addCallStatusListener(ManagedCallStatusListener mcsl);

    public void setSpatialAudio(boolean enabled) throws IOException ;

    public void setSpatialMinVolume(double spatialMinVolume) throws IOException ;

    public void setSpatialFallOff(double spatialFallOff) throws IOException ;

    public void setSpatialEchoDelay(double spatialEchoDelay) throws IOException ;

    public void setSpatialEchoVolume(double spatialEchoVolume) throws IOException ;

    public void setSpatialBehindVolume(double spatialBehindVolume) throws IOException ;

    public void addWall(double startX, double startY, 
	double endX, double endY, double characteristic) throws IOException;

    public DefaultSpatializer getDefaultSpatializer();

    public void setParameters(VoiceManagerParameters p);

    public VoiceManagerParameters getParameters();

    public void setLogLevel(Level level);

    public int getNumberOfPlayersInRange(double x, double y, double z);

    public int getNumberOfPlayersInRange(String callId);

    public void startRecording(String callId, String recordingFile)
	throws IOException;

    public void stopRecording(String callId)
	throws IOException;

    public void playRecording(String callId, String recordingFile)
	throws IOException;

    public void migrateCall(String callId, String phoneNumber) 
	throws IOException;

}
