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

package com.sun.mc.softphone.media.alsa;

import java.io.IOException;

import java.lang.reflect.Constructor;

import java.util.prefs.Preferences;

import com.sun.voip.Logger;

import com.sun.mc.softphone.media.AudioServiceProvider;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.NativeLibUtil;
import com.sun.mc.softphone.media.Speaker;

public class AlsaAudioServiceProvider implements AudioServiceProvider {

    private static final String ALSA_I386_NAME = "libMediaFrameworkI386.so";

    private static final String ALSA_AMD64_NAME = "libMediaFrameworkAmd64.so";

    private static AudioDriver audioDriver;

    private Microphone microphone;
    private Speaker speaker;

    public AlsaAudioServiceProvider() throws IOException {
        audioDriver = new AudioDriverAlsa();

	if (System.getProperty("os.arch").contains("amd64")) {
	    NativeLibUtil.loadLibrary(getClass(), ALSA_AMD64_NAME);
	} else {
	    NativeLibUtil.loadLibrary(getClass(), ALSA_I386_NAME);
	}
    }

    public void initialize(int speakerSampleRate, int speakerChannels,
	    int microphoneSampleRate, int microphoneChannels,
	    int microphoneBufferSize, int speakerBufferSize) 
	    throws IOException {

	shutdown();	// stop old driver if running

	Logger.println("Initializing audio driver to " + speakerSampleRate
	    + "/" + speakerChannels + " bufferSize " + speakerBufferSize);

	synchronized (audioDriver) {
            speaker = new SpeakerAlsaImpl(speakerSampleRate, speakerChannels, 
	        speakerBufferSize, audioDriver);

	    microphone = new MicrophoneAlsaImpl(microphoneSampleRate, 
	        microphoneChannels, microphoneBufferSize, audioDriver);
	}
    }

    public void shutdown() {
	synchronized (audioDriver) {
	    audioDriver.stop();
	}
    }

    public Microphone getMicrophone() {
	return microphone;
    }

    public String[] getMicrophoneList() {
        return audioDriver.getAvailableOutputDevices();
    }


    public Speaker getSpeaker() {
	return speaker;
    }

    public String[] getSpeakerList() {
	return audioDriver.getAvailableInputDevices();
    }

}
