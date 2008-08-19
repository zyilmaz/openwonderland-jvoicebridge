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

import  javax.sound.sampled.AudioFormat;
import  javax.sound.sampled.AudioSystem;
import  javax.sound.sampled.DataLine;
import  javax.sound.sampled.Line;
import  javax.sound.sampled.LineUnavailableException;
import  javax.sound.sampled.Mixer;
import  javax.sound.sampled.SourceDataLine;

import  com.sun.mc.softphone.common.Utils;

import  com.sun.mc.softphone.media.MediaManager;
import  com.sun.mc.softphone.media.Speaker;

import  com.sun.voip.Logger;
import	com.sun.voip.RtpPacket;
import	com.sun.voip.DotAuAudioSource;
import  com.sun.voip.TreatmentManager;

import  java.io.BufferedReader;
import  java.io.InputStreamReader;
import  java.io.IOException;

/**
 * Manages the Speaker
 */
public class SpeakerJavasoundImpl implements Speaker {

    private SourceDataLine speaker;	// current open speaker
    private int numWrites;

    private boolean done = false;

    private double volumeLevel = 1.0D;
    
    private int sampleRate;
    private int channels;
    private int bufferSize;
    private int chunkSize;

    public SpeakerJavasoundImpl(int sampleRate, int channels,
	    int bufferSize) throws IOException {

	this.sampleRate = sampleRate;
	this.channels = channels;
	this.bufferSize = bufferSize;
	
        chunkSize = RtpPacket.getDataSize(RtpPacket.PCM_ENCODING,
            sampleRate, channels);

	try {
            setupSpeaker();
	} catch (LineUnavailableException e) {
	    throw new IOException(e.getMessage());
	}

	if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("New speaker " + sampleRate + "/" + channels
	 	+ " bufferSize " + bufferSize);
	}
        
        double volumeLevel = Utils.getDoublePreference(VOLUME_LEVEL);

        if (volumeLevel != -1D) {
            this.volumeLevel = volumeLevel;
        }
    }

    public synchronized void done() {
	if (done) {
	    return;
	}

	done = true;

	/*
	 * There seems to be a bug in the Sun Ray audio system
	 * where close() hangs sometimes if there is still data
	 * in the speaker buffer.  By sleeping for the time
	 * it would take to empty a full buffer (plus some slop),
	 * the close() seems to always complete.
	 *
	 * XXX
	 */
	try {
	    Thread.sleep(getBufferSizeMillis() + RtpPacket.PACKET_PERIOD);
	} catch (InterruptedException e) {
	}

	synchronized (speaker) {
	    speaker.flush();
	    speaker.stop();
	    speaker.close();
	}

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Speaker closed");
	}
    }

    private boolean isDirectAudio(Line line) {
	return line.getClass().toString().indexOf("DirectAudioDevice") >= 0;
    }

    /*
     * Set up the speaker to use the DirectAudioDevice
     */
    private void setupSpeaker() throws LineUnavailableException {
	done = false;

	/*
         * Set up the speaker.
         */ 
        AudioFormat audioFormat = new AudioFormat (
            sampleRate,         	     // Sample rate (Hz)
            MediaManager.BITS_PER_SAMPLE,    // Sample size (bits)
            channels,           	     // Channels (2 = stereo)
            true,               	     // signed
            true);              	     // False == little endian

        String device = Utils.getPreference(Speaker.SPEAKER_PREFERENCE);

        if (device != null && device.length() > 0 && 
		device.equalsIgnoreCase("Default") == false) {

            if (setupSpeaker(device) == true) {
		if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("Using specified speaker:  " + device);
		}
		startSpeaker(audioFormat);
                return;
            }

            Logger.println("Specified speaker not available:  " + device);
        } else {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Using default speaker");
	    }
	}

        DataLine.Info speakerInfo =
            new DataLine.Info(SourceDataLine.class, audioFormat);

	try {
            speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
	} catch (IllegalArgumentException e) {
	    Logger.println("GetLine failed " + e.getMessage());
	    throw new LineUnavailableException(e.getMessage());
	}

	if (speaker == null) {
	    Logger.println("Line unavailable...");
	    throw new LineUnavailableException(
	        "No audio device for the speaker!");
	}

	if (isDirectAudio(speaker) == false) {
	    Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

            for (int i = 0; i < aInfos.length; i++) {
            	try {
                    Mixer mixer = AudioSystem.getMixer(aInfos[i]);

		    SourceDataLine s = (SourceDataLine) 
			mixer.getLine(speakerInfo);

		    if (isDirectAudio(s)) {
			speaker = s;
			break;
		    }
            	} catch (Exception e) {
                }
            }
	}

	if (isDirectAudio(speaker) == false) {
            String s = System.getProperty("java.version");

            if (s.indexOf("1.5.") >= 0) {
	        Logger.println(
		    "No DirectAudioDevice found for the speaker");
	    }
	}

        startSpeaker(audioFormat);
    }
    
    private boolean setupSpeaker(String device)
            throws LineUnavailableException {

        Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

        for (int i = 0; i < aInfos.length; i++) {
            Mixer.Info mixerInfo = aInfos[i];

            if (GetDataLines.equals(device, mixerInfo) == false) {
		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                    Logger.println("Skipping:  " + mixerInfo.getName() + ","
                        + mixerInfo.getDescription());
		}

                continue;
            }

            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);

                Line.Info[] infos = mixer.getSourceLineInfo();

                for (int j = 0; j < infos.length; j++) {
                    Line line = (Line) mixer.getLine(infos[j]);

                    if (line instanceof SourceDataLine) {
                        speaker = (SourceDataLine) line;

			if (Logger.logLevel >= Logger.LOG_INFO) {
                            Logger.println("Found speaker:  " + j);
			}
			break;
                    }
                }
            } catch (Exception e) {
		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Exception:  " + e.getMessage());
		}
            }
        }

	return speaker != null;
    }

    private void startSpeaker(AudioFormat format) 
	    throws LineUnavailableException {

        speaker.open(format, bufferSize);

        int actualBufferSize = speaker.getBufferSize();

        if (bufferSize != actualBufferSize) {
            Logger.println("Speaker set buffer to "
                + bufferSize + " but actual size is "
                + actualBufferSize);

            bufferSize = actualBufferSize;
        }

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Speaker using " 
        	+ getBufferSizeMillis(actualBufferSize)
                + " millisecond buffer " + actualBufferSize + " bytes");
        }
            
        speaker.start();
	flush();

	//println("speaker started");
    }

    public int getSampleSizeInBits() {
        return MediaManager.BITS_PER_SAMPLE;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
	return channels;
    }

    public int available() {
	if (speaker == null) {
	    return 0;
	}

	start();
	return speaker.available();
    }

    public int getBufferSize() {
	return bufferSize;
    }

    public int getBufferSizeMillis() {
	return getBufferSizeMillis(bufferSize);
    }

    public int getBufferSize(int millis) {
        return millis * sampleRate * channels * 
	    MediaManager.BYTES_PER_SAMPLE / 1000;
    }

    public int getBufferSizeMillis(int bufferSize) {
       int bytesPerMillisecond = sampleRate * channels *
            MediaManager.BYTES_PER_SAMPLE / 1000;

        return bufferSize / bytesPerMillisecond;
    }

    public void setVolumeLevel(double volumeLevel) {
        this.volumeLevel = volumeLevel;

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Setting Speaker volume to " + volumeLevel);
	}

	Utils.setPreference(VOLUME_LEVEL, String.valueOf(volumeLevel));
    }

    public double getVolumeLevel() {
        return volumeLevel;
    }

    public synchronized int write(byte[] buffer, int offset, int length) {
	if (speaker == null) {
	    return 0;
	}

	start();

	/*
	 * Break the buffer up into 20 ms chunks, write as much as we can,
	 * then wait until there's room for more.
	 */
	int len = length;

	int sleepCount = 0;
	long start = System.currentTimeMillis();

	while (len > 0) {
	    int writeLength = Math.min(len, chunkSize);

	    while (!done && available() < chunkSize) {
		try {
		    Thread.sleep(RtpPacket.PACKET_PERIOD);
		    sleepCount++;
		} catch (InterruptedException e) {
		}
	    }

            applyVolume(buffer, offset, writeLength);
  
	    speaker.write(buffer, offset, writeLength);

	    offset += writeLength;
	    len -= writeLength;
	}

	if (sleepCount > 0) {
	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        long elapsed = System.currentTimeMillis() - start;

		Logger.println("write to speaker slept " + sleepCount
		    + " times, " + elapsed + "ms");
	    }
	}
		
	numWrites++;
	return length;
    }

    private void applyVolume(byte[] buffer, int offset, int len) {
        if (volumeLevel == (float)1.0) {
	    return;
	}

	int inIx = offset;

	for (int i = 0; i < len; i += 2) {
	    int sample = 
		(((int)buffer[inIx]) << 8) | (buffer[inIx + 1] & 0xff);

	    sample *= volumeLevel;

	    if (sample > 32767) {
		sample = 32767;
	    } else if (sample < -32768) {
		sample = -32768;
	    }

	    buffer[inIx] = (byte)((sample >> 8) & 0xff);
	    buffer[inIx + 1] = (byte)(sample & 0xff);

	    inIx += 2;
	}
    }
    
    public void start() {
	if (speaker == null || speaker.isRunning()) {
	    return;
	}

        speaker.start();
    }

    public void drain() {
	if (speaker == null) {
	    return;
	}

	start();
        speaker.drain();
    }

    public void flush() {
	if (speaker == null) {
	    return;
	}

	start();
	speaker.flush();
    }

    public boolean isRunning() {
	if (speaker == null) {
	    return false;
	}

        return speaker.isRunning();
    }

    public void stop() {
	if (speaker == null) {
	    return;
	}

	if (isRunning()) {
            speaker.stop();
	}
    }

    public void printStatistics() {
    }

    public static void main(String[] args) {
	if (args.length != 1) {
	    Logger.println("Usage:  java Speaker <input file>");
	    System.exit(1);
	}

	BufferedReader br = new BufferedReader(
	   new InputStreamReader(System.in));

	Speaker speaker = null;

	while (true) {
	    try {
	        speaker = new SpeakerJavasoundImpl(8000, 1,
		    RtpPacket.getDataSize(RtpPacket.PCM_ENCODING, 8000, 1));
    	    } catch (Exception e) {
	        Logger.println(e.getMessage());
	        System.exit(1);
	    }

	    Logger.logLevel = 5;

	    try {
	        TreatmentManager t = new TreatmentManager(args[0], 0,
		    speaker.getSampleRate(), speaker.getChannels());

	        while (true) {
                    byte[] audioData = 
		        t.getLinearDataBytes(RtpPacket.PACKET_PERIOD);

		    if (audioData == null) {
		        break;	// end of file
		    }

		    speaker.write(audioData, 0, audioData.length);
	        }
            } catch (IOException e) {
                Logger.println(e.getMessage());
            }

	    if (speaker != null) {
		speaker.done();
	    }

	    Logger.println("Press return to play the file again...");

            try {
                String line = br.readLine();
                if (line.equalsIgnoreCase("quit")) {
                    break;
                }
            } catch (IOException ioe) {
                break;
            }
	}
    }

}
