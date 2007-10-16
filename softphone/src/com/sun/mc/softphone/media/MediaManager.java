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

package com.sun.mc.softphone.media;

import com.sun.mc.softphone.SipCommunicator;
import com.sun.mc.softphone.sip.SipManager;

import java.io.IOException;

public interface MediaManager {
    
    /*
     * Set the default sample rate and channels to the highest
     * values known to work in all situations especially over VPN.
     * These values will be negotiated down as needed.
     */
    public static final float DEFAULT_SAMPLE_RATE = 16000.0F;
    public static final int DEFAULT_CHANNELS = 2;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;

    public void initialize(String encryptionKey, String encryptionAlgorithm,
                           boolean disableAudio) throws IOException;
    
    public void mediaChanged(String encoding, int sampleRate, int channels,
                             boolean notifyRemote);
    
    public void setSipCommunicator(SipCommunicator sipCommunicator);

    public void setSipManager(SipManager sipManager);

    public void start() throws IOException;
    public boolean isStarted();
    public void restart() throws IOException;
    public void stop() throws IOException;
    
    public void startDtmf(String key);
    public void stopDtmf();

    public void mute(boolean isMuted);
    public boolean isMuted();
    
    public Microphone getMicrophone() throws IOException;
    public Speaker getSpeaker() throws IOException;

    public Microphone getMicrophone(int sampleRate, int channels)
        throws IOException;
 
    public Speaker getSpeaker(int sampleRate, int channels)
        throws IOException;

    public double getMicrophoneVolume();
    public void setMicrophoneVolume(double volume);

    public double getSpeakerVolume();
    public void setSpeakerVolume(double volume);

    public void startPlayingFile(String file) throws IOException;
    public void startPlayingFile(String file, int repeatCount) 
        throws IOException;
    public void stopPlayingAllFiles();
    public void stopPlayingFile();

    public void playTreatment(String file, int repeats) throws IOException;
    public void pauseTreatment(boolean pause);
    public void stopTreatment();
    
    public void setRemoteSdpData(String remoteSdpData);
    public String generateSdp(boolean answer) throws IOException;
    public String generateSdp(String callee) throws IOException;
    
    public void addCallDoneListener(CallDoneListener listener);
    public void removeCallDoneListener(CallDoneListener listener);
    
    public void playRecording(String path, boolean isLocal,
	    PacketGeneratorListener listener) throws IOException;

    public void stopPlaying(boolean isLocal);

    public void startRecording(String path, String recordingType, 
	    boolean isLocal, CallDoneListener listener) throws IOException;

    public void stopRecording(boolean isLocal);

}
