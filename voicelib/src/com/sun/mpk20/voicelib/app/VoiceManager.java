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

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.voip.client.connector.CallStatusListener;

import java.math.BigInteger;

import java.io.IOException;
import java.io.Serializable;

import java.util.logging.Level;

//add javadoc

/**
 * The VoiceManager interface is an API providing call setup and control.
 */
public interface VoiceManager extends Serializable {

    /*
     * VoiceManager
     */
    public void setLogLevel(Level level);

    public Level getLogLevel();

    public void setVoiceManagerParameters(VoiceManagerParameters parameters);

    public VoiceManagerParameters getVoiceManagerParameters();

    /*
     * Voice bridge parameters.
     */
    public void setVoiceBridgeParameters(VoiceBridgeParameters parameters);
	
    /**
     * Call Control
     */
    public BridgeInfo getVoiceBridge() throws IOException;

    public Call createCall(String id, CallSetup setup) throws IOException;

    public Call getCall(String id); 

    public Call[] getCalls();

    public void endCall(Call call, boolean removePlayer) throws IOException;

    public void addCallStatusListener(CallStatusListener listener);

    public void addCallStatusListener(CallStatusListener listener, String callId);

    public void removeCallStatusListener(CallStatusListener listener);

    public void removeCallStatusListener(CallStatusListener listener, String callId);

    public void addCallBeginEndListener(CallBeginEndListener listener);

    public void removeCallBeginEndListener(CallBeginEndListener listener);

    /*
     * Player control
     */
    public Player createPlayer(String id, PlayerSetup setup);

    public Player getPlayer(String id); 

    public Player[] getPlayers();

    public void removePlayer(Player player);
    
    public int getNumberOfPlayersInRange(double x, double y, double z);

    /*
     * Audio Group control
     */
    public AudioGroup createAudioGroup(String id, AudioGroupSetup setup);

    public AudioGroup getAudioGroup(String id);

    public void removeAudioGroup(AudioGroup audioGroup);

    /*
     * Treatment control
     */
    public TreatmentGroup createTreatmentGroup(String id);

    public TreatmentGroup getTreatmentGroup(String id);

    public void removeTreatmentGroup(TreatmentGroup group) throws IOException;

    public Treatment createTreatment(String id, TreatmentSetup setup) throws IOException;

    public Treatment getTreatment(String id);

    /*
     * Recorder control
     */
    public Recorder createRecorder(String id, RecorderSetup setup) throws IOException;

    public Recorder getRecorder(String id);

    /*
     * Spatial audio Control
     */
    public void setSpatialAudio(boolean enabled) throws IOException;

    public void setSpatialMinVolume(double spatialMinVolume) throws IOException;

    public void setSpatialFalloff(double spatialFalloff) throws IOException;

    public void setSpatialEchoDelay(double spatialEchoDelay) throws IOException;

    public void setSpatialEchoVolume(double spatialEchoVolume)
        throws IOException;

    public void setSpatialBehindVolume(double spatialBehindVolume)
        throws IOException;

    public void scheduleTask(KernelRunnable runnable, long startTime);

    public String dump(String command);

    public void testUDPPort(String host, int port, int duration) throws IOException;

}
