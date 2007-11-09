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

import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;

public interface VoiceHandler {

    public String getVoiceBridge();

    public String setupCall(String callId, String sipUrl, String bridge);

    public void setSpatializer(String callId, Spatializer spatializer);

    public void setPrivateSpatializer(String targetCallId, String sourceCallId,
	Spatializer spatializer);

    public void removePrivateSpatializer(String targetCallId, 
	String sourceCallId);

    public void setIncomingSpatializer(String targetCallId,
	Spatializer spatializer);

    public void removeIncomingSpatializer(String targetCallId);

    public String setupTreatment(String id, String treatment, String group, 
	ManagedCallStatusListener listener,
	double lowerLeftX, double lowerLeftY, double lowerLeftZ,
	double upperRightX, double upperRightY, double upperRightZ);

    public void newInputTreatment(String callId, String treatment, 
	String group);

    public void stopInputTreatment(String callId);

    public void playTreatmentToCall(String callId, String treatment);

    public void pauseTreatmentToCall(String callId, String treatment);

    public void stopTreatmentToCall(String callId, String treatment);

    public void endCall(String callId);

    public void disconnectCall(String callId);
    
    public void muteCall(String callId, boolean isMuted);

    public void setFallOff(double fallOff);

    public void setFallOffFunction(String s);

    public void setFullVolumeRadius(double fullVolumeRadius);

    public void setZeroVolumeRadius(double zeroVolumeRadius);

    public void setMaximumVolume(double maximumVolume);

    public void setSpatialMinVolume(double spatialMinVolume);

    public void setSpatialFallOff(double spatialFallOff);

    public void setSpatialEchoDelay(double spatialEchoDelay);

    public void setSpatialEchoVolume(double spatialEchoVolume);

    public void setSpatialBehindVolume(double spatialBehindVolume);

    public void addCallStatusListener(ManagedCallStatusListener listener);

    public void addCallStatusListener(ManagedCallStatusListener listener, 
	String callId);
	
    public void removeCallStatusListener(ManagedCallStatusListener listener);

    public void addCallBeginEndListener(ManagedCallBeginEndListener listener);

    public void removeCallBeginEndListener(ManagedCallBeginEndListener listener);

    public void setPositionAndOrientation(String callId , double x, double y, 
	double z, double orientation);

    public void setPosition(String callId , double x, double y, double z);

    public void setOrientation(String callId, double orientation);

    public void setAttenuationRadius(String callId,
	double attenuationRadius);

    public void setAttenuationVolume(String callId,
	double attenuationVolume);

    public void addWall(double startX, double startY, double endX, double endY, 
	double characteristic);

    public void setVoiceManagerParameters(VoiceManagerParameters p);

    public VoiceManagerParameters getVoiceManagerParameters();

}
