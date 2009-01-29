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

import java.util.*;
import java.nio.*;

import com.sun.voip.Logger;

public class AudioDriverMac extends Object implements AudioDriver {
    
    /* member variables */
    public  AudioDevice currentOutputDevice = null;
    public  AudioDevice currentInputDevice = null;
    
    private ByteBuffer  speakerByteBuffer;
    private ByteBuffer  micByteBuffer;
    
    /* for JNI */
    // MediaManagerImpl has already loaded this library.
    //static { System.loadLibrary("MediaFramework"); }
    
    /* native methods */
    private native AudioDevice[] nGetInputDevices();
    private native AudioDevice[] nGetOutputDevices();
    private native AudioDevice   nGetDefaultOutputDevice();
    private native AudioDevice   nGetDefaultInputDevice();
    private native float[]    nGetSupportedSampleRates(int id,boolean input);
    private native void       nStart(
         float speakerSampleRate, int speakerNumChannels, 
         int speakerBytesPerPacket, int speakerFramesPerPacket, 
         int speakerBytesPerFrame, int speakerBitsPerChannel, 
         
         float micSampleRate, int micNumChannels, 
         int micBytesPerPacket, int micFramesPerPacket, 
         int micBytesPerFrame, int micBitsPerChannel
    );

    private native void   nStop();
    private native int    nWriteSpeaker();
    private native int    nReadMic(int bytes);
    
    private native boolean   nSetOutputDevice(int id);
    private native boolean   nSetInputDevice(int id);
    
    /* must acquire the lock before working on the object */
    private native void   nAcquireSpeakerBufferLock();
    private native void   nReleaseSpeakerBufferLock();
    private native void   nAcquireMicBufferLock();
    private native void   nReleaseMicBufferLock();
    
    private native void   nInitializeBuffers(ByteBuffer speakerBuffer,
                                             ByteBuffer micBuffer);
    /* methods for the speaker buffer */
    private native int    nGetSpeakerBufferWritePosition();
    private native int    nGetSpeakerBufferReadPosition();
    private native void   nSetSpeakerBufferWritePosition(int value);
    private native void   nSetSpeakerBufferReadPosition(int value);
    
    /* methods for the microphone buffer */
    private native int    nGetMicBufferWritePosition();
    private native int    nGetMicBufferReadPosition();
    private native void   nSetMicBufferWritePosition(int value);
    private native void   nSetMicBufferReadPosition(int value);
    
    public static void main (String[] args) {
        AudioDriverMac adm = new AudioDriverMac();
    }
    
    /* start off with the default devices as the selected devices */
    public AudioDriverMac() {
	println("enter");
        currentOutputDevice = nGetDefaultOutputDevice();
        currentInputDevice = nGetDefaultInputDevice();
        
	Logger.println("Default output device:  " + currentOutputDevice.verboseDescription());
	Logger.println("Default input device:  " + currentInputDevice.verboseDescription());

	AudioDevice[] inputDevices = nGetInputDevices();

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    for (int i = 0; i < inputDevices.length; i++) {
		println(inputDevices[i].toString());
	    }
	}

	AudioDevice[] outputDevices = nGetOutputDevices();

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    for (int i = 0; i < outputDevices.length; i++) {
		println(outputDevices[i].toString());
	    }
	}

        /* not sure where these should actually go */
        nSetOutputDevice(currentOutputDevice.id);
        nSetInputDevice(currentInputDevice.id);
	println("return");
    }
    
    public void flushMicrophone() {
	println("flushMicrophone enter");
        nAcquireMicBufferLock();

            nSetMicBufferWritePosition(0);
    	    nSetMicBufferReadPosition(0);

        nReleaseMicBufferLock();
	println("flushMicrophone leave");
    }

    public int microphoneAvailable() {
	println("microphoneAvailable enter");
        nAcquireMicBufferLock();

            int writeAt = nGetMicBufferWritePosition();
            int readAt = nGetMicBufferReadPosition();
            int available = writeAt - readAt;
            
        nReleaseMicBufferLock();

	println("microphoneAvailable leave");
        return available;
    }

    /* read the available bytes from the microphone 
     * @param buffer where to write the data
     * @param offset the offset
     * @param length size of the buffer
     * @return number of bytes read
     */
    public int readMic(byte[] buffer, int offset, int length) {
        /* Tell the native side to do some converting */
        nReadMic(length);
        
        /* Get the lock before mucking with the buffer */
        nAcquireMicBufferLock();
            int writeAt = nGetMicBufferWritePosition();
            int readAt = nGetMicBufferReadPosition();
            int available = writeAt - readAt;
            
            /* get out if there is not enough data */
            if (available < length) {
                /* should never get here, cause we block till there is enough
                 * now */
                nReleaseMicBufferLock();
                return 0;
            }
            
            //System.out.println("\t\t\t\t\t\t\t\t\t\t\t" +
            //                    "mic w:" + writeAt + " r:" + readAt);
            try {
                /* set up the buffer for the operation */
                micByteBuffer.position(readAt);
                micByteBuffer.limit(writeAt);
                
                micByteBuffer.get(buffer, offset, length);
                nSetMicBufferReadPosition(readAt + length);
            } catch (BufferUnderflowException bue) {
                /* should never actually get here */
                nReleaseMicBufferLock();
                return 0;  
            } 
            
        nReleaseMicBufferLock();
        return length;
    }
    
    /*
     * Flush the speaker buffer
     */
    public void flushSpeaker() {
	println("flushSpeaker enter");
        nAcquireSpeakerBufferLock();

    	    nSetSpeakerBufferWritePosition(0);
	    nSetSpeakerBufferReadPosition(0);
   
        nReleaseSpeakerBufferLock();
	println("flushSpeaker leave");
    }

    /*
     * Get number of bytes which can be written without blocking.
     */
    public int speakerAvailable() {
        nAcquireSpeakerBufferLock();
        
        int writeAt = nGetSpeakerBufferWritePosition();
        int readAt = nGetSpeakerBufferReadPosition();
        int capacity = speakerByteBuffer.capacity();
        int used = writeAt - readAt;
            
        nReleaseSpeakerBufferLock();

	return capacity - used;
    }

    /* write bytes to the speaker
     * @param the data to write
     * @param offset the offset
     * @param length size of the buffer
     * @return number of bytes written
     */  
    public int writeSpeaker(byte[] buffer, int offset, int length) {
        /* Get the lock before mucking with the buffer */
        nAcquireSpeakerBufferLock();
        
            /* do the compacting, if necessary */
            int writeAt = nGetSpeakerBufferWritePosition();
            int readAt = nGetSpeakerBufferReadPosition();
            int capacity = speakerByteBuffer.capacity();
            int used = writeAt - readAt;
            
            //System.out.println("speak w:" + writeAt + " r:" + readAt);
            
            /* no chance, even if we compact, so just get out */
            if (capacity - used < length) {
                nReleaseSpeakerBufferLock();
		println("writeSpeaker leave, no space");
                return 0;
            } 
            
            /* we will have enough room, but only if we compact it */
            if (capacity - writeAt < length || writeAt > capacity) {
                /* compact the buffer to make enough room */
                //System.out.print("w:" + writeAt + " r:" + readAt);
                speakerByteBuffer.position(readAt);
                speakerByteBuffer.limit(writeAt);
                speakerByteBuffer.compact();
                //System.out.print(" --> Compacted -- > ");
                //System.out.println("compacting speaker buffer");
                
                /* set the variables over on the native side */
                nSetSpeakerBufferWritePosition(speakerByteBuffer.position());
                nSetSpeakerBufferReadPosition(0);
                
                //System.out.println("w:" + nGetSpeakerBufferWritePosition()
                //                   + " r:" + nGetSpeakerBufferReadPosition()
                //                   + " c:" + capacity);
            }
            
            /* do the writing */
            speakerByteBuffer.position(nGetSpeakerBufferWritePosition());
            speakerByteBuffer.put(buffer, offset, length);
            nSetSpeakerBufferWritePosition(nGetSpeakerBufferWritePosition()
                                            + length);
        
        nReleaseSpeakerBufferLock();
        return length;
        //return nWriteSpeaker();
    }
    
    /* play audio treatment */
    public void playTreatment(String treatment) {
	println("playTreatment");
    }
    
    /* record audio to a file 
     * @param fileName the path and name for the output file
     * @return true if successful, false otherwise
     */
    public boolean record(String fileName) throws Exception {
	println("record");
        return false;
    }
    
    /* get the current output volume of the speaker 
     * @return the volume as an integer between 0-100 (?)
     */
    public int checkVolume() {
	println("checkVolume");
        return 0;
    }
    
    /* set the output volume for the speaker
     * @param newVolume the volume to set, between 0-100
     */
    public void setVolume(int newVolume) throws DeviceException {
	println("setVolume");
    }
    
    /* list all of the supported input sample rates
     * @return an array of the supported rates
     */
    public float[] getSupportedInputSampleRates() {
	println("getSupportedInputSampleRates");
        int id = currentInputDevice.id;
        return importantRatesIn(nGetSupportedSampleRates(id, true));
    }
    
    /* list all of the supported output sample rates
     * @return an array of the supported rates
     */
    public float[] getSupportedOutputSampleRates() {
	println("getSupportedOutputSampleRates");
        int id = currentOutputDevice.id;
        return importantRatesIn(nGetSupportedSampleRates(id, false));
    }
    
    /* get the number of supported channels
     * @return number of channels
     */
    public int getSupportedChannels() {
	println("getSupportedChannels");
        return 0;
    }
    
    /* list all the available input devices
     * @return an array of the input devices
     */
    public AudioDevice[] getAvailableInputDevices() {
	println("getAvailableInputDevices");
	return nGetInputDevices();
    }
    
    /* list all the available output devices
     * @return an array of the output devices
     */
    public AudioDevice[] getAvailableOutputDevices() {
	println("getAvailableOutputDevices");
	return nGetOutputDevices();
    }
    
    /* select which input device to use
     * @param device the device to use
     * @return true is successful, false otherwise
     */
    public boolean chooseInputDevice(AudioDevice device) 
        throws DeviceException 
    {
	println("chooseInputDevice");
        currentInputDevice = device;
        nSetInputDevice(device.id);
        return true;
    }
    
    /* select which output device to use
     * @param device the device to use
     * @return true is successful, false otherwise
     */
    public boolean chooseOutputDevice(AudioDevice device)
        throws DeviceException
    {
	println("chooseOutputDevice");
        currentOutputDevice = device;
        nSetOutputDevice(device.id);
        return true;
    }
    
    /* get the system's default audio input device
     * @return system's default audio input device
     */ 
    public AudioDevice getDefaultInputDevice() {
	println("getDefaultInputDevice");
        return nGetDefaultInputDevice();
    }
    
    /* get the current audio input device
     * @return current audio input device
     */
    public AudioDevice getCurrentInputDevice() {
	println("getCurrentInputDevice");
        return currentInputDevice;
    }
    
    /* get the system's default audio output device
     * @return system's default audio output device
     */ 
    public AudioDevice getDefaultOutputDevice() {
	println("getDefaultOutputDevice");
        return nGetDefaultOutputDevice();
    }
    
    /* get the current audio input device
     * @return current audio input device
     */
    public AudioDevice getCurrentOutputDevice() {
	println("getCurrentOutputDevice");
        return currentOutputDevice;
    }
    
    /* the currently selected sample rate 
     * @return current sample rate
     */
    public float getCurrentSampleRate() {
        println("getCurrentSampleRate");
        return 0.f;
    }
    
    /* the current number of channels
     * @return current number of channels
     */
    public int getCurrentChannels() {
	println("getCurrentChannels");
        return 0;
    }
    
    /* start using audio on the current devices
     * @param sampleRate sample rate to open at
     * @param numChannels number of channels to use
        
        For a local 8k recording, we have:
        - - - - - - - - - - - - - - - - - - - -
        Sample Rate:        8000.000000
        Format ID:          lpcm
        Format Flags:       E
        Bytes per Packet:   2
        Frames per Packet:  1
        Bytes per Frame:    2
        Channels per Frame: 1
        Bits per Channel:   16
        - - - - - - - - - - - - - - - - - - - -
     */
    public void start(float speakerSampleRate, int speakerNumChannels, 
                      int speakerBytesPerPacket, int speakerFramesPerPacket, 
                      int speakerBytesPerFrame, int speakerBitsPerChannel,
                      float micSampleRate, int micNumChannels, 
                      int micBytesPerPacket, int micFramesPerPacket, 
                      int micBytesPerFrame, int micBitsPerChannel) 
    {
	Logger.println("Audio System started, speaker "
	    + speakerSampleRate + "/" + speakerNumChannels
	    + ", mic " + micSampleRate + "/" + micNumChannels);

        nStart(speakerSampleRate, speakerNumChannels, speakerBytesPerPacket,
               speakerFramesPerPacket, speakerBytesPerFrame, 
               speakerBitsPerChannel, micSampleRate, micNumChannels,
               micBytesPerPacket, micFramesPerPacket, micBytesPerFrame,
               micBitsPerChannel);
	println("start leave");
    }
    
    /* restart audio on the current devices
     * @param sampleRate sample rate to open at
     * @param numChannels number of channels to use
     */
    public void restart(float speakerSampleRate, int speakerNumChannels, 
                        int speakerBytesPerPacket, int speakerFramesPerPacket, 
                        int speakerBytesPerFrame, int speakerBitsPerChannel,
                        float micSampleRate, int micNumChannels, 
                        int micBytesPerPacket, int micFramesPerPacket, 
                        int micBytesPerFrame, int micBitsPerChannel)
    {
	println("restart enter");
        start(speakerSampleRate, speakerNumChannels, speakerBytesPerPacket,
              speakerFramesPerPacket, speakerBytesPerFrame, 
              speakerBitsPerChannel, micSampleRate, micNumChannels,
              micBytesPerPacket, micFramesPerPacket, micBytesPerFrame,
              micBitsPerChannel);
	println("restart leave");
    }
    
    /* stop audio on current devices */
    public void stop() {
	Logger.println("Audio system stopped");

	println("stop enter");

        nStop();

	println("stop leave");
    }
    
    /* set up the buffer and whatever else needs setting up before startup */
    public void initialize(int microphoneBufferSize, int speakerBufferSize) {    
	println("initialize enter, mic buffer size " + microphoneBufferSize
	    + " speaker buffer size " + speakerBufferSize);

        micByteBuffer = ByteBuffer.allocateDirect(microphoneBufferSize);
        speakerByteBuffer = ByteBuffer.allocateDirect(speakerBufferSize);

        nInitializeBuffers(speakerByteBuffer, micByteBuffer);
	println("initialize leave ");
    }
    
    /**************************************************************************/
    /* local functions */
    /**************************************************************************/
    /* this will take in an array of floats in the form:
     *      minA, maxA, minB, maxB,...
     * like CoreAudio gives out, and will return an array of floats containing
     * the sample rates encompased by these numbers that we care about
     */
    private float[] importantRatesIn(float[] in) {
	println("importantRatesIn enter");

        float[] careAbout = { 8000.f, 16000.f, 32000.f, 44100.f };
        Vector haveThese = new Vector();
        for (int i = 0; i < careAbout.length; i++) {
            for (int j = 0; j < in.length; j += 2) {
                if (careAbout[i] >= in[j] &&
                    careAbout[i] <= in[j+1])
                {
                    haveThese.add(new Float(careAbout[i]));
                    break;
                }
            }
        }
        
        float[] ret = new float[haveThese.size()];
        for (int i = 0; i < haveThese.size(); i++) {
            ret[i] = ((Float)haveThese.get(i)).floatValue();
        }
         
	println("importantRatesIn leave");
        return ret;
    }

    private void println(String s) {
	if (Logger.logLevel < Logger.LOG_MOREINFO) {
	    return;
	}

	Logger.println("AudioDriverMac:  " + s);
    }

}
