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

    public void setSpatializer(String callId, Spatializer spatializer);

    public void setPrivateSpatializer(String targetCallId, String sourceCallId,
	Spatializer spatializer);

    public void callEstablished(String callId) throws IOException;

    public void newInputTreatment(String callId, String treatment) 
	throws IOException;

    public void stopInputTreatment(String callId) throws IOException;

    public void restartInputTreatment(String callId) throws IOException;

    public void endCall(String callId) throws IOException;

    public void muteCall(String callId, boolean isMuted) throws IOException;

    public void restorePrivateMixes() throws IOException;

    public void setPosition(String callId, double x, double y, double z)
	throws IOException;

    public void setOrientation(String callId, double orientation) 
	throws IOException;

    public void setAttenuationRadius(String callId, double attenuationRadius)
	throws IOException;

    public void setAttenuationVolume(String callId, double attenuationVolume)
	throws IOException;

    public void setPrivateMix(String sourceCallId, String targetCallId, 
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

}
