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

package com.sun.mc.softphone.media.coreaudio;

import com.sun.mc.softphone.media.NativeLibUtil;
import java.io.IOException;

import java.lang.reflect.Constructor;

import java.util.prefs.Preferences;
import java.util.ArrayList;

import com.sun.voip.Logger;

import com.sun.mc.softphone.media.AudioServiceProvider;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;

public class CoreAudioAudioServiceProvider implements AudioServiceProvider {

    private static final String INTEL_CORE_AUDIO_NAME = "libmedia.intel.jnilib";
    private static final String POWERPC_CORE_AUDIO_NAME = "libmedia.powerpc.jnilib";

    private static AudioDriver audioDriver;

    private Microphone microphone;
    private Speaker speaker;

    public CoreAudioAudioServiceProvider() throws IOException {
        Logger.println("Loading CoreAudio provider");
        
       
        // load the libarary
        String arch = System.getProperty("os.arch");
        
        String nativeLibraryName;
        if (arch.equalsIgnoreCase("i386")) {
            nativeLibraryName = INTEL_CORE_AUDIO_NAME;
        } else {
            nativeLibraryName = POWERPC_CORE_AUDIO_NAME;
        }

        Logger.println("Loading native library: " + nativeLibraryName);
        NativeLibUtil.loadLibrary(getClass(), nativeLibraryName);
        return;
    }

    public void initialize(int sampleRate, int channels,
	    int microphoneSampleRate, int microphoneChannels,
	    int microphoneBufferSize, int speakerBufferSize) 
	    throws IOException {

	shutdown();	// stop old driver if running

        audioDriver = new AudioDriverMac();

        audioDriver.initialize(microphoneBufferSize, speakerBufferSize);

	Logger.println("Initializing audio driver to " + sampleRate
	    + "/" + channels + " microphoneBufferSize " + microphoneBufferSize
	    + " speakerBufferSize " + speakerBufferSize);

        /*
         * The speaker is always set to 2 channels.
         * Resampling is done if necessary.
         *
         * When set to 1 channel, sound only comes
         * out of 1 channel instead of 2.
         */
        audioDriver.start(
            sampleRate,         // speaker sample rate
            2,                  // speaker channels
            2*2,                // speaker bytes per packet
            1,                  // speaker frames per packet
            2*2,                // speaker frame size
            16,                 // speaker bits per channel
            microphoneSampleRate,// microphone sample rate
            microphoneChannels,  // microphone channels,
            2*microphoneChannels,// microphone bytes per packet
            1,                  // microphone frames per packet
            2*microphoneChannels,// microphone bytes per frame
            16);                // microphone bits per channel

	initializeMicrophone(microphoneSampleRate, microphoneChannels, 
	    microphoneBufferSize);
	initializeSpeaker(sampleRate, channels, speakerBufferSize);
    }

    public void shutdown() {
	if (audioDriver != null) {
	    audioDriver.stop();
	    audioDriver = null;
	}
    }

    public Microphone getMicrophone() {
	return microphone;
    }

    public String[] getMicrophoneList() {
	return new String[0];
    }

    private void initializeMicrophone(int sampleRate, int channels,
	    int microphoneBufferSize) throws IOException {

	microphone = new MicrophoneCoreAudioImpl(sampleRate, channels, 
	    microphoneBufferSize, audioDriver);
    }

    public Speaker getSpeaker() {
	return speaker;
    }

    public String[] getSpeakerList() {
	return new String[0];
    }

    private void initializeSpeaker(int sampleRate, int channels,
	    int speakerBufferSize) throws IOException {

        speaker = new SpeakerCoreAudioImpl(sampleRate, channels, 
	    speakerBufferSize, audioDriver);
    }

}
