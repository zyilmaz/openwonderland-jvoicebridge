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

package com.sun.mc.softphone.media.javasound;

import java.io.IOException;

import java.lang.reflect.Constructor;

import com.sun.voip.Logger;

import com.sun.mc.softphone.media.AudioServiceProvider;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;

public class JavasoundAudioServiceProvider implements AudioServiceProvider {

    private Microphone microphone;
    private Speaker speaker;

    public JavasoundAudioServiceProvider() {
	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Using JavasoundAudioServiceProvider");
	}
    }

    public void initialize(int sampleRate, int channels,
	    int microphoneSampleRate, int microphoneChannels,
	    int microphoneBufferSize, int speakerBufferSize) 
	    throws IOException {

	initializeMicrophone(microphoneSampleRate, microphoneChannels, 
	    microphoneBufferSize);
	initializeSpeaker(sampleRate, channels, speakerBufferSize);
    }

    public void shutdown() {
    }

    public Microphone getMicrophone() {
	return microphone;
    }

    public String[] getMicrophoneList() {
	return GetDataLines.getMicrophones();
    }

    private void initializeMicrophone(int sampleRate, int channels,
	    int microphoneBufferSize) throws IOException {

	microphone = new MicrophoneJavasoundImpl(sampleRate, channels,
	    microphoneBufferSize);
    }

    public Speaker getSpeaker() {
	return speaker;
    }

    public String[] getSpeakerList() {
	return GetDataLines.getSpeakers();
    }

    private void initializeSpeaker(int sampleRate, int channels,
	    int speakerBufferSize) throws IOException {

        speaker = new SpeakerJavasoundImpl(sampleRate, channels,
	    speakerBufferSize);
    }

}
