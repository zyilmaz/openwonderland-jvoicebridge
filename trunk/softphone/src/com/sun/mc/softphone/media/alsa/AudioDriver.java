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

import java.util.ArrayList;

public interface AudioDriver {
    
    /*
     * Initialize microphone
     */
    public void initializeMicrophone(int sampleRate, int channels, 
	int bufferSize) throws IOException;

    /*
     * Initialize speaker
     */
    public void initializeSpeaker(int sampleRate, int channels, 
	int bufferSize) throws IOException;

    /*
     * Flush the microphone buffer
     */
    public void flushMicrophone() throws IOException;

    /*
     * Get the number of bytes which can be read without blocking
     */
    public int microphoneAvailable() throws IOException;
	
    /* read the available bytes from the microphone 
     * @param buffer where to write the data
     * @param offset the offset???
     * @param length size of the buffer
     * @return number of bytes read
     */
    public int readMic(byte[] buffer, int offset, int length) 
	throws IOException;
    
    /*
     * Flush the speaker buffer
     */
    public void flushSpeaker() throws IOException;

    /*
     * Get the number of bytes which can be written without blocking
     */
    public int speakerAvailable() throws IOException;

    /* write bytes to the speaker
     * @param the data to write
     * @param offset the offset???
     * @param length size of the buffer
     * @return number of bytes written
     */
    public int writeSpeaker(byte[] buffer, int offset, int length) 
	throws IOException;
    
    /* list all the available input devices
     * @return an array of the input devices
     */
    public String[] getAvailableInputDevices();
    
    /* list all the available output devices
     * @return an array of the output devices
     */
    public String[] getAvailableOutputDevices();
    
    /* stop audio on current devices */
    public void stop();
    
}
