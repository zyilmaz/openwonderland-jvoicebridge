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

import java.nio.ByteBuffer;

import java.util.ArrayList;

import com.sun.voip.Logger;

import com.sun.mc.softphone.SipCommunicator;

import com.sun.mc.softphone.common.Utils;

import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;

public class AudioDriverAlsa extends Object implements AudioDriver {
    
    /* native methods */
    private native String[] nGetInputDevices();

    private native String[] nGetOutputDevices();

    private native int nInitializeMicrophone(String device,
	int sampleRate, int channels, int micBufferSize);

    private native int nInitializeSpeaker(String device,
	int sampleRate, int channels, int speakerBufferSize);

    private native void   nStop();

    private native int    nWriteSpeaker(short[] buffer, int len);

    private native int    nReadMic(short[] buffer, int len);
    
    private native int    nFlushMicrophone();

    private native int    nFlushSpeaker();

    private native int	  nMicrophoneAvailable();

    private native int	  nSpeakerAvailable();

    private Object micLock = new Object();
    private Object speakerLock = new Object();

    private int speakerBufferSize;

    public static void main (String[] args) {
        new AudioDriverAlsa();
    }
    
    public AudioDriverAlsa() {
    }
    
    public void initializeMicrophone(int sampleRate, int channels,
	    int bufferSize) throws IOException {

	println("initializeMicrophone");

	String device = "plughw:0,0";

	String microphone = 
	    Utils.getPreference(Microphone.MICROPHONE_PREFERENCE);

	if (microphone != null) {
	    int ix;
	    if (microphone.indexOf("plughw:") != 0 ||
	           (ix = microphone.indexOf(" ")) < 0) {

	        Logger.println("Invalid microphone preference:  " + microphone);
		Utils.setPreference(Microphone.MICROPHONE_PREFERENCE, "");
	    } else {
		device = microphone.substring(0, ix);
	    }
	}
	
	Logger.println("Using Microphone " + device);

	synchronized (micLock) {
	    int ret = nInitializeMicrophone(device, sampleRate, channels, 
		bufferSize);

	    if (ret < 0) {
		SipCommunicator.softphoneProblem("Unable to initialize microphone. " 
		   + device + " error " + ret);

		throw new IOException("Unable to initialize microphone:  " 
		    + device + " error " + ret);
	    };
	}

	println("initializeMicrophone leave");
    }

    public void initializeSpeaker(int sampleRate, int channels,
	    int bufferSize) throws IOException {

	println("initializeSpeaker");

	speakerBufferSize = bufferSize;

        String device = "plughw:0,0";

        String speaker = Utils.getPreference(Speaker.SPEAKER_PREFERENCE);

        if (speaker != null) {
            int ix; 
            if (speaker.indexOf("plughw:") != 0 ||
                   (ix = speaker.indexOf(" ")) < 0) {

                Logger.println("Invalid speaker preference:  " + speaker);
            } else {
                device = speaker.substring(0, ix);
		Logger.println("Using speaker preference " + speaker);
            }
        }

        Logger.println("Using Speaker " + device);

	synchronized (speakerLock) {
	    int ret = nInitializeSpeaker(device, sampleRate, channels, 
		bufferSize);

	    if (ret < 0) {
		SipCommunicator.softphoneProblem("Unable to initialize speaker. " 
		    + device + " error " + ret);

		throw new IOException("Unable to initialize speaker:  " 
		    + device + " error " + ret);
	    }
	}

	println("initializeSpeaker leave");
    }

    public void flushMicrophone() throws IOException {
	println("flushMicrophone enter");
	
	synchronized(micLock) {
	    int ret = nFlushMicrophone();

	    if (ret < 0) {
		throw new IOException("Unable to flush microphone:  " + ret);
	    }
	}

	println("flushMicrophone leave");
    }

    public int microphoneAvailable() throws IOException {
	println("microphoneAvailable enter");

	int available;

	synchronized(micLock) {
            available = nMicrophoneAvailable();
	}

	if (available < 0) {
	    throw new IOException("microphoneAvailable failed:  "
		+ available);
	}

	return available;
    }

    /* read the available bytes from the microphone 
     * @param buffer where to write the data
     * @param offset the offset
     * @param length size of the buffer
     * @return number of bytes read
     */
    public int readMic(byte[] buffer, int offset, int len) 
	    throws IOException {

	short[] shortBuffer = new short[len / 2];

	int ret;

	synchronized (micLock) {
            ret = nReadMic(shortBuffer, shortBuffer.length);
	}

	if (ret < 0) {
	    throw new IOException("readMic failed:  " + ret);
	}

	int outIx = offset;

	/*
	 * Copy data to a byte array.
	 */
	for (int i = 0; i < shortBuffer.length; i++) {
	    buffer[outIx++] = (byte) ((shortBuffer[i] >> 8) & 0xff);
	    buffer[outIx++] = (byte) (shortBuffer[i] & 0xff);
	}

	return len;
    }
    
    /*
     * Flush the speaker buffer
     */
    public void flushSpeaker() throws IOException {
	//println("flushSpeaker enter");

	synchronized(speakerLock) {
            int ret = nFlushSpeaker();

	    if (ret < 0) {
		throw new IOException("flushSpeaker failed:  " + ret);
	    }
	}

	//println("flushSpeaker leave");
    }

    /*
     * Get number of bytes which can be written without blocking.
     */
    public int speakerAvailable() throws IOException {

	synchronized (speakerLock) {
	    int available  = nSpeakerAvailable();

	    if (available < 0) {
	        throw new IOException("speakerAvailable failed:  "
		    + available);
	    }

	    return available;
	}
    }

    /* write bytes to the speaker
     * @param the data to write
     * @param offset the offset
     * @param length size of the buffer
     * @return number of bytes written
     */  
    public int writeSpeaker(byte[] byteData, int offset, int len) 
	    throws IOException {

	short[] data = new short[len / 2];

	int inIx = offset;

	for (int i = 0; i < data.length; i++) {
	    data[i] = (short) ((byteData[inIx++] & 0xff) |
	    	((byteData[inIx++] << 8) & 0xff00));
	}

	synchronized(speakerLock) {
            int ret = nWriteSpeaker(data, data.length);
	
	    if (ret < 0) {
		throw new IOException("writeSpeaker failed:  " + ret);
	    }

            return ret;
	}
    }
    
    /* 
     * list all the available input devices
     * @return an array of the input devices
     */
    public String[] getAvailableInputDevices() {
	println("getAvailableInputDevices");
	
        return nGetInputDevices();
    }
    
    /* 
     * list all the available output devices
     * @return an array of the output devices
     */
    public String[] getAvailableOutputDevices() {
	println("getAvailableOutputDevices");

        return nGetOutputDevices();
    }
    
    /* stop audio on current devices */
    public void stop() {
	println("Audio system stopped");

	println("stop enter");

	synchronized (micLock) {
	    synchronized (speakerLock) {
        	nStop();
	    }
	}

	println("stop leave");
    }
    
    private void println(String s) {
	if (Logger.logLevel < Logger.LOG_MOREINFO) {
	    return;
	}

	Logger.println("AudioDriverAlsa:  " + s);
    }

}
