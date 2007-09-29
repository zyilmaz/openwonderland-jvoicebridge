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

import java.io.IOException;

import java.util.ArrayList;

import com.sun.voip.AudioConversion;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.Recorder;
import com.sun.voip.RtpPacket;
import com.sun.voip.SampleRateConverter;
import com.sun.voip.Util;

import com.sun.mc.softphone.common.Utils;

import com.sun.mc.softphone.media.AudioFactory;
import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.MicrophoneListener;
import com.sun.mc.softphone.media.Speaker;

/**
 * reads data from the microphone 
 */
public class MicrophoneCoreAudioImpl implements Microphone {

    private boolean isMuted = false;

    private boolean done;

    private static double volumeLevel = 1.0D;
    
    private AudioDriver audioDriver;

    private int sampleRate;
    private int channels;

    private int bufferSize;

    private SampleRateConverter sampleRateConverter;

    private boolean duplicateMicrophoneChannel = false;

    /**
     * Constructors.
     */
    public MicrophoneCoreAudioImpl(int sampleRate, int channels,
	    int bufferSize, AudioDriver audioDriver) throws IOException {
        
	this.sampleRate = sampleRate;
	this.channels = channels;
	this.bufferSize = bufferSize;
	this.audioDriver = audioDriver;

        double volumeLevel = Utils.getDoublePreference(VOLUME_LEVEL);

        if (volumeLevel != -1D) {
            this.volumeLevel = volumeLevel;
        }

	AudioDevice mic = audioDriver.getDefaultInputDevice();

	if (mic.channelsPerFrame < channels) {
	    Logger.println("Microphone resampling from "
		+ mic.channelsPerFrame + " to " + channels + " channels");

	    duplicateMicrophoneChannel = true;
	}

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("New Microphone:  " + sampleRate + "/" + channels);
	}
    }
    
    /*
     * All done.  Stop everything.
     */
    public void done() {
	if (done) {
	    return;
	}

	done = true;

	isMuted = false;

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Microphone closed");
	}
    }

    public void read(byte[] linearData, int offset, int len) 
	    throws IOException {

        String s = Utils.getPreference(
            "com.sun.mc.softphone.media.USE_EXTERNAL_MIC");

        /*
         * Special case for bluetooth microphone which always
         * sends data at 8000/2 samples per second.
         */
        if (s != null && s.equalsIgnoreCase("BT8000") &&
                (sampleRate != 8000 || channels != 2)) {

	    readBlueToothMicrophone(linearData, offset, len);
	} else {
	    readMicrophone(linearData, offset, len);
	}
    
	adjustVolume(linearData, offset, len);

	notifyListeners(linearData, offset, len);
    }

    private void readBlueToothMicrophone(byte[] linearData, 
	    int offset, int len) {

        /*
         * get length for 8000/2 
         */
        int ms = getBufferSizeMillis(len);

        int blueToothLen = ms * 8000 * 2 * 2 / 1000;

	byte[] buf = new byte[blueToothLen];

	readMicrophone(buf, 0, blueToothLen);

        if (sampleRateConverter == null) {
            Logger.println("Resampling from 8000/2 to sampleRate/channels "
		+ " for BlueTooth microphone");

            try {
                sampleRateConverter = new SampleRateConverter(
		    "Core Audio Mic",
                    8000, 2, sampleRate, channels);
            } catch (IOException e) {
                Logger.println("Microphone create resampler! "
                    + e.getMessage());
            }
        }

        try {
            byte[] data = sampleRateConverter.resample(
                buf, 0, blueToothLen);

            for (int i = 0; i < data.length; i++) {
                linearData[i + offset] = data[i + offset];
            }
        } catch (IOException e) {
            Logger.println("Microphone can't resample! "
                + e.getMessage());
        }
    }

    private void readMicrophone(byte[] linearData, int offset, int len) {
        int o = 0;
        int bufferLength = len;

        while (bufferLength > 0) {
            int n;

            n = audioDriver.readMic(linearData, offset + o, bufferLength);

            o += n;
            bufferLength -= n;
        }

	int ix = offset;

	if (duplicateMicrophoneChannel) {
	    for (int i = 0; i < len; i += 4) {
		linearData[ix + 2] = linearData[ix];
		linearData[ix + 3] = linearData[ix + 1];
		ix += 4;
	    }
	}
    }

    private void adjustVolume(byte[] linearData, int offset, int len) {
	if (isMuted()) {
	    for (int i = 0; i < len; i++) {
		 linearData[i + offset] = AudioConversion.PCM_SILENCE;
	    }
	} else {
            if (volumeLevel != 1.0D) {
		int inIx = offset;

                for (int i = 0; i < len; i += 2) {
                    int sample = (((int)linearData[inIx]) << 8) | 
			(linearData[inIx + 1] & 0xff);

                    sample *= volumeLevel;

                    if (sample > 32767) {
                        sample = 32767;
                    } else if (sample < -32768) {
                        sample = -32768;
                    }

                    linearData[inIx] = (byte)((sample >> 8) & 0xff);
                    linearData[inIx + 1] = (byte)(sample & 0xff);

		    inIx += 2;
                }
            }
	}
    }

    ArrayList listeners = new ArrayList();

    public void addListener(MicrophoneListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(MicrophoneListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(byte[] linearData,
            int offset, int length) {

        synchronized (listeners) {
            for (int i = 0; i < listeners.size(); i++) {
                MicrophoneListener listener = (MicrophoneListener)
                    listeners.get(i);

                listener.microphoneData(linearData, offset, length);
            }
        }
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

    public int getBufferSize() {
	return bufferSize;
    }

    public int getBufferSizeMillis() {
	return getBufferSizeMillis(bufferSize);
    }

    public int getBufferSize(int millis) {
        return millis * (int)sampleRate * channels *
            MediaManager.BYTES_PER_SAMPLE / 1000;
    }

    public int getBufferSizeMillis(int bufferSize) {
       int bytesPerMillisecond = sampleRate * channels *
            MediaManager.BYTES_PER_SAMPLE / 1000;

        return bufferSize / bytesPerMillisecond;
    }

    public int available() {
	return audioDriver.microphoneAvailable();
    }

    public void flush() {
    }

    public void mute(boolean isMuted) {
	this.isMuted = isMuted;
    }

    public boolean isMuted() {
	return isMuted;
    }

    public void setVolumeLevel(double volumeLevel) {
        this.volumeLevel = volumeLevel;

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Setting Microphone volume to " + volumeLevel);
        }

	Utils.setPreference(VOLUME_LEVEL, String.valueOf(volumeLevel));
    }

    public double getVolumeLevel() {
        return volumeLevel;
    }

    private static void usage(String msg) {
        Logger.println(msg);
        Logger.println("Usage:  java Microphone [-sampleRate <sampleRate>]");
        Logger.println("                        [-channels <channels>]");
        Logger.println("                        [-duration <seconds>]");

        System.exit(1);
    }

    /*
     * Test to read microphone and write to the speaker.
     */
    public static void main(String[] args) {
	int sampleRate = 44100;
	int channels = 2;
	int duration = 0;

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equalsIgnoreCase("-sampleRate")) {
		i++;

		if (i >= args.length) {
		    usage("missing sampleRate");
		}

		try {
		    sampleRate = Integer.parseInt(args[i]);
		} catch (NumberFormatException e) {
		    usage("invalid sample rate");
		}
	    } else if (args[i].equalsIgnoreCase("-channels")) {
		i++;

		if (i >= args.length) {
		    usage("missing channels");
		}

		try {
		    channels = Integer.parseInt(args[i]);
		} catch (NumberFormatException e) {
		    usage("invalid number of channels");
		}
	    } else if (args[i].equalsIgnoreCase("-duration")) {
		i++;

		if (i >= args.length) {
		    usage("missing duration");
		}

		try {
		    duration = Integer.parseInt(args[i]) * 1000;
		} catch (NumberFormatException e) {
		    usage("invalid duration ");
		}
	    }
	}
	   
	Microphone microphone = null;
	Speaker speaker = null;

	int dataSize = RtpPacket.getDataSize(RtpPacket.PCM_ENCODING,
	    sampleRate, channels);

        try {
	    AudioFactory audioFactory = AudioFactory.getInstance();

            audioFactory.initialize(sampleRate, channels, sampleRate,
		channels, 2 * dataSize, 8 * dataSize);

            microphone = audioFactory.getMicrophone();
            speaker = audioFactory.getSpeaker();
        } catch (IOException e) {
            Logger.println(e.getMessage());
            System.exit(1);
        }

        byte[] buffer = new byte[dataSize];

        microphone.flush();
        speaker.flush();

	if (duration == 0) {
            while (true) {
		try {
                    microphone.read(buffer, 0, buffer.length);
		} catch (IOException e) {
		    Logger.println("Unable to read microphone:  "
			+ e.getMessage());
		    System.exit(1);
		}

		try {
                    speaker.write(buffer, 0, buffer.length);
		} catch (IOException e) {
		    Logger.println("Unable to write Speaker:  "
			+ e.getMessage());
		}
	    }
	} else {
	    while (duration > 0) {
	        long start = System.currentTimeMillis();

		try {
                    microphone.read(buffer, 0, buffer.length);
		} catch (IOException e) {
                    Logger.println("Unable to read microphone:  "
                        + e.getMessage());
                    System.exit(1);
                }

		try {
                    speaker.write(buffer, 0, buffer.length);
		} catch (IOException e) {
                    Logger.println("Unable to write Speaker:  "
                        + e.getMessage());
                }

		duration -= (System.currentTimeMillis() - start);
	    }
        }
    }

    public void printStatistics() {
    }

}
