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

public interface AudioDriver {
    
    /*
     * Flush the microphone buffer
     */
    public void flushMicrophone();

    /*
     * Get the number of bytes which can be read without blocking
     */
    public int microphoneAvailable();
	
    /* read the available bytes from the microphone 
     * @param buffer where to write the data
     * @param offset the offset???
     * @param length size of the buffer
     * @return number of bytes read
     */
    public int readMic(byte[] buffer, int offset, int length);
    
    /*
     * Flush the speaker buffer
     */
    public void flushSpeaker();

    /*
     * Get the number of bytes which can be written without blocking
     */
    public int speakerAvailable();

    /* write bytes to the speaker
     * @param the data to write
     * @param offset the offset???
     * @param length size of the buffer
     * @return number of bytes written
     */
    public int writeSpeaker(byte[] buffer, int offset, int length);
    
    /* play audio treatment */
    public void playTreatment(String treatment);
    
    /* record audio to a file 
     * @param fileName the path and name for the output file
     * @return true if successful, false otherwise
     */
    public boolean record(String fileName) throws Exception;
    
    /* get the current output volume of the speaker 
     * @return the volume as an integer between 0-100 (?)
     */
    public int checkVolume();
    
    /* set the output volume for the speaker
     * @param newVolume the volume to set, between 0-100
     */
    public void setVolume(int newVolume) throws DeviceException;
    
    /* list all of the supported input rates
     * @return an array of the supported rates
     */
    public float[] getSupportedInputSampleRates();
    
    /* list all of the supported output sample rates
     * @return an array of the supported rates
     */
    public float[] getSupportedOutputSampleRates();
    
    /* get the number of supported channels
     * @return number of channels
     */
    public int getSupportedChannels();
    
    /* list all the available input devices
     * @return an array of the input devices
     */
    public AudioDevice[] getAvailableInputDevices();
    
    /* list all the available output devices
     * @return an array of the output devices
     */
    public AudioDevice[] getAvailableOutputDevices();
    
    /* select which input device to use
     * @param device the device to use
     * @return true is successful, false otherwise
     */
    public boolean chooseInputDevice(AudioDevice device) throws DeviceException;
    
    /* select which output device to use
     * @param device the device to use
     * @return true is successful, false otherwise
     */
    public boolean chooseOutputDevice(AudioDevice device) 
        throws DeviceException;
    
    /* get the system's default audio input device
     * @return system's default audio input device
     */ 
    public AudioDevice getDefaultInputDevice();
    
    /* get the current audio input device
     * @return current audio input device
     */
    public AudioDevice getCurrentInputDevice();
    
    /* get the system's default audio output device
     * @return system's default audio output device
     */ 
    public AudioDevice getDefaultOutputDevice();
    
    /* get the current audio input device
     * @return current audio input device
     */
    public AudioDevice getCurrentOutputDevice();
    
    /* the currently selected sample rate 
     * @return current sample rate
     */
    public float getCurrentSampleRate();
    
    /* the current number of channels
     * @return current number of channels
     */
    public int getCurrentChannels();
    
    /* start using audio on the current devices
     * @param sampleRate sample rate to open at
     * @param numChannels number of channels to use
     */
    public void start(float speakerSampleRate, int speakerNumChannels, 
                      int speakerBytesPerPacket, int speakerFramesPerPacket, 
                      int speakerBytesPerFrame, int speakerBitsPerChannel,
                      float micSampleRate, int micNumChannels, 
                      int micBytesPerPacket, int micFramesPerPacket, 
                      int micBytesPerFrame, int micBitsPerChannel); 
    
    /* restart audio on the current devices
     * @param sampleRate sample rate to open at
     * @param numChannels number of channels to use
     */
    public void restart(float speakerSampleRate, int speakerNumChannels, 
                        int speakerBytesPerPacket, int speakerFramesPerPacket, 
                        int speakerBytesPerFrame, int speakerBitsPerChannel,
                        float micSampleRate, int micNumChannels, 
                        int micBytesPerPacket, int micFramesPerPacket, 
                        int micBytesPerFrame, int micBitsPerChannel);
    
    /* stop audio on current devices */
    public void stop();
    
    /* call before starting
     * @param bufferSize size of both the mic and speaker buffers
     */
    public void initialize(int bufferSize);

}
