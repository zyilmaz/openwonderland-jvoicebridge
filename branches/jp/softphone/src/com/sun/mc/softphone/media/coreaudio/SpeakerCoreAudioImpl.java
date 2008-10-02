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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import  com.sun.mc.softphone.common.Utils;

import	com.sun.voip.DotAuAudioSource;
import  com.sun.voip.Logger;
import	com.sun.voip.RtpPacket;
import	com.sun.voip.SampleRateConverter;
import	com.sun.voip.TreatmentManager;

import	com.sun.mc.softphone.media.MediaManager;
import	com.sun.mc.softphone.media.Speaker;

/**
 * Manages the Speaker
 */
public class SpeakerCoreAudioImpl implements Speaker {

    private int numWrites;

    private boolean done = false;

    private double volumeLevel = 1.0D;
    
    private AudioDriver audioDriver;

    private SampleRateConverter sampleRateConverter;

    int sampleRate;
    int channels;

    private int bufferSize;
    private int chunkSize;

    public SpeakerCoreAudioImpl(int sampleRate, int channels, 
	    int bufferSize, AudioDriver audioDriver) throws IOException {

	this.sampleRate = sampleRate;
	this.channels = channels;
	this.bufferSize = bufferSize;
	this.audioDriver = audioDriver;

        chunkSize = RtpPacket.getDataSize(RtpPacket.PCM_ENCODING, sampleRate,
            channels);

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("New Speaker:  " + sampleRate + "/" + channels
	        + " bufferSize " + bufferSize);
	}

	if (this.channels != 2) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Speaker resampling " + channels 
		    + "  channel to 2");
	    }

	    try {
                sampleRateConverter = new SampleRateConverter(
		    "Core audio Speaker",
                    this.sampleRate, 1, this.sampleRate, 2);
            } catch (IOException e) {
                Logger.println("AudioReceiver can't resample! "
                    + e.getMessage());
                return;
            }
	}

	double volumeLevel = Utils.getDoublePreference(VOLUME_LEVEL);

	if (volumeLevel != -1D) {
	    this.volumeLevel = volumeLevel;
	}
    
	flush();
    }

    public void done() {
	if (done) {
	    return;
	}

	done = true;

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Speaker closed");
	}

	flush();
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
	return audioDriver.speakerAvailable();
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
	if (done) {
	    return 0;
	}

	if (isRunning() == false) {
	    start();
	}

	/*
	 * Break the buffer up into 20 ms chunks, write as much as we can,
	 * then wait until there's room for more.
	 */
	int len = length;

	while (len > 0) {
	    int writeLength = Math.min(len, chunkSize);

	    while (!done && available() < chunkSize) {
		try {
		    Thread.sleep(RtpPacket.PACKET_PERIOD);
		} catch (InterruptedException e) {
		}
	    }

	    writeChunk(buffer, offset, writeLength);

	    offset += writeLength;
	    len -= writeLength;
	}
		
	return length;
    }

    private int writeChunk(byte[] buffer, int offset, int length) {
        buffer = applyVolume(buffer, offset, length);
  
        byte[] data = buffer;

	if (sampleRateConverter != null) {
            try {
                data = sampleRateConverter.resample(buffer, offset, length);
		length = data.length - offset;
            } catch (IOException e) {
                Logger.println("Resample error " + e.getMessage());
                return 0;
            }
	}

	//Logger.println("writeSpeaker:  
	//   + " original length " + length + " offset " + offset 
	//   + " resampled length " + (data.length - offset));

        int num = audioDriver.writeSpeaker(data, offset, length);

        //Util.dump("write to speaker:  " + num + " offset "
        //    + offset + " size " + size, data, 32);

        if (num == 0) {
            //System.out.println("dropped packet");
        }

	numWrites++;
	return length;
    }

    private byte[] applyVolume(byte[] buffer, int offset, int length) {
        if (volumeLevel != 1.0D) {
	    int inIx = offset;

	    for (int i = 0; i < length; i += 2) {
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
        
        return buffer;
    }
    
    public void start() {
    }

    public void stop() {
    }

    public boolean isRunning() {
        return true;
    }

    public void drain() {
    }

    public void flush() {
	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            System.out.println("Flushing speaker...");
        }
        audioDriver.flushSpeaker();
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

	int sampleRate = 44100;
	int channels = 2;

	try {
            AudioDriver audioDriver = new AudioDriverMac();

	    int dataSize = RtpPacket.getDataSize(RtpPacket.PCM_ENCODING,
	        sampleRate, channels);

	    int bufferSize = 8 * dataSize;

            audioDriver.initialize(bufferSize, bufferSize);

            audioDriver.start(
		sampleRate, channels, 2*channels, 1, 2*channels, 16,
                sampleRate, channels, 2*channels, 1, 2*channels, 16);

            speaker = new SpeakerCoreAudioImpl(sampleRate, channels, 
		bufferSize, audioDriver);
	} catch (Exception e) {
	    Logger.println(e.getMessage());
	    System.exit(1);
	}

	while (true) {
            try {
                TreatmentManager t = new TreatmentManager(args[0], 0,
                    speaker.getSampleRate(), speaker.getChannels());

                while (true) {
                    byte[] audioData =
                        t.getLinearDataBytes(RtpPacket.PACKET_PERIOD);

                    if (audioData == null) {
                        break;  // end of file
                    }

                    speaker.write(audioData, 0, audioData.length);
                }
            } catch (IOException e) {
                Logger.println(e.getMessage());
            }

           System.out.println("Press return to play the file again...");

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
