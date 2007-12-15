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

package com.sun.voip.client;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;

import	javax.sound.sampled.DataLine;
import	javax.sound.sampled.SourceDataLine;
import	javax.sound.sampled.TargetDataLine;
import	javax.sound.sampled.AudioFormat;
import	javax.sound.sampled.AudioSystem;
import	javax.sound.sampled.LineUnavailableException;
import	javax.sound.sampled.Line;
import	javax.sound.sampled.Mixer;

/**
 * Uses the Java Sound API to send input from a microphone to a
 * speaker.
 */ 
public class AudioLoop extends Thread {

    /**
     * The size of the buffer used locally to read data from the
     * microphone and send it to the speaker.
     */
    static private final int TRANSFER_BUFFER_SIZE = 320;

    private TargetDataLine microphone;
    private SourceDataLine speaker;

    private String outputFile;

    /**
     * Class constructor
     */
    public AudioLoop() throws LineUnavailableException {
    }

    /**
     * Sets up the microphone.
     */
    private void setupMicrophone() 
	throws LineUnavailableException {
        
        /* Set up the microphone.
         */
        AudioFormat inputFormat = new AudioFormat (
            8000.0F,     // Sample rate (Hz)
            16,          // Sample size (bits)
            1,           // Channels (2 = stereo)
            true,        // Signed
            true);       // False == little endian
        
        DataLine.Info microphoneInfo =
            new DataLine.Info(TargetDataLine.class,
                              inputFormat);
        
        microphone = (TargetDataLine) AudioSystem.getLine(microphoneInfo);
        microphone.open(inputFormat, TRANSFER_BUFFER_SIZE);

	System.out.println("microphone using " 
	    + microphone.getClass().toString());
    }
    
    /** the default buffer size, in milliseconds */     
    public static final int BUFFER_SIZE_DEFAULT = 120;
    /** the property to read for the buffer size */     
    public static final String BUFFER_SIZE_PROPERTY =   
        "com.sun.voip.client.headset.buffer";

    private boolean isDirectAudio(Line line) {
        return line.getClass().toString().indexOf("DirectAudioDevice") >= 0;
    }

    /*
     * Set up the speaker to using the DirectAudioDevice
     */
    private void setupSpeaker() throws LineUnavailableException {
	/*
         * Set up the speaker.
         */ 
        AudioFormat audioFormat = new AudioFormat (
            8000.0F,     	// Sample rate (Hz)
            16,        		// Sample size (bits)
            1,               	// Channels (2 = stereo)
	    true,	    	// signed
            true);           	// False == little endian

        DataLine.Info speakerInfo =
            new DataLine.Info(SourceDataLine.class, audioFormat);

        speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);

	if (speaker == null) {
	    throw new LineUnavailableException(
		"No audio device for the speaker!");
	}

	if (isDirectAudio(speaker) == false) {
	    System.out.println("Searching for Direct Audio for the speaker...");

	    Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

            for (int i = 0; i < aInfos.length; i++) {
            	try {
                    Mixer mixer = AudioSystem.getMixer(aInfos[i]);

		    speaker = (SourceDataLine) mixer.getLine(speakerInfo);

		    if (isDirectAudio(speaker)) {
		        break;
		    }
            	} catch (Exception e) {
                    e.printStackTrace();
            	}
            }
	}

	if (isDirectAudio(speaker) == false) {
	    System.out.println("No DirectAudioDevice found for the speaker");
	}

	System.out.println("speaker using " + speaker.getClass().toString());

        int millis = BUFFER_SIZE_DEFAULT;
            
        String bufferSize = System.getProperty(BUFFER_SIZE_PROPERTY);

        if (bufferSize != null) {
            millis = Integer.parseInt(bufferSize);
        }
            
	int size = (int)
	    (millis * (long)audioFormat.getFrameRate() / 1000 * 
	    audioFormat.getFrameSize());

        speaker.open(audioFormat, size);

        System.out.println("Speaker using " + millis + " millisecond buffer "
	    + speaker.getBufferSize() + " bytes");
            
        speaker.start();
	speaker.flush();
    }

    /**
     * Read from the microphone and write to the speaker forever.
     */
    private BufferedOutputStream bo;
    private FileOutputStream fo;

    public void run() {
        byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];

	try {
            setupMicrophone();
            setupSpeaker();
            speaker.start();
            microphone.start();
	} catch (LineUnavailableException e) {
	    System.err.println("Unable to start microphone... " 
		+ e.getMessage());
	    System.exit(1);
	}

        while (true) {
	    //long start = System.currentTimeMillis();
            int	numBytesRead = microphone.read(buffer,
                                               0,
                                               TRANSFER_BUFFER_SIZE);

	    if (outputFile != null) {
	        try {
	            bo.write(buffer, 0, numBytesRead);
	        } catch (IOException e) {
		}
	    }

	    //long elapsed = System.currentTimeMillis() - start;

	    //System.err.println("elapsed:  " + elapsed + " bytes read " 
	    //	+ numBytesRead);

            speaker.write(buffer, 0, numBytesRead);
        }
    }

    public static void main(String[] args) {
        try {
            AudioLoop audioLoop = new AudioLoop();

	    audioLoop.initialize(args);

            audioLoop.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }    

    private void initialize(String[] args) {
	if (args.length == 0) {
	    return;
	}

	outputFile = args[0];

	File recordFile = new File(outputFile);

        try {
            recordFile.createNewFile();
            fo = new FileOutputStream(recordFile);
            bo = new BufferedOutputStream(fo, 1024);
        } catch (IOException e) {
	    System.out.println("Can't create recording file " + outputFile);
	    System.exit(1);
	}

	/*
	 * Write .au header
	 */
	byte[] buf = new byte[4];

	try {
	    buf[0] = 0x2e;
	    buf[1] = 0x73;
	    buf[2] = 0x6e;
	    buf[3] = 0x64;
	    bo.write(buf, 0, 4);	// magic

	    buf[0] = 0;
	    buf[1] = 0;
	    buf[2] = 0;
	    buf[3] = 32;
	    bo.write(buf, 0, 4);	// hdr_size

	    buf[3] = 0;	
	    bo.write(buf, 0, 4);	// data_size (unknown)

	    buf[3] = 3;
	    bo.write(buf, 0, 4);	// linear encoding

	    buf[2] = 0x1f;
	    buf[3] = (byte)0x40;
	    bo.write(buf, 0, 4);	// 8000 bits per second sample rate

	    buf[2] = 0;
	    buf[3] = 1;	
	    bo.write(buf, 0, 4);	// number of channels

	    buf[3] = 0;
	    bo.write(buf, 0, 4);	// pad
	    bo.write(buf, 0, 4);	// pad
	} catch (IOException e) {
	    System.out.println("Can't write recording file header " 
		+ outputFile);
	    System.exit(1);
	}
    }
	
}
