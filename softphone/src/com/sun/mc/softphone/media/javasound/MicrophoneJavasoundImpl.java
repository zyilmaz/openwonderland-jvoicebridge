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

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import  javax.sound.sampled.AudioFormat;
import  javax.sound.sampled.AudioSystem;
import  javax.sound.sampled.DataLine;
import  javax.sound.sampled.Line;
import  javax.sound.sampled.LineUnavailableException;
import  javax.sound.sampled.Mixer;
import  javax.sound.sampled.Port;
import  javax.sound.sampled.TargetDataLine;

import	com.sun.voip.AudioConversion;
import	com.sun.voip.Logger;
import	com.sun.voip.RtpPacket;
import	com.sun.voip.SampleRateConverter;
import	com.sun.voip.Util;

import	com.sun.voip.MediaInfo;
import	com.sun.voip.Recorder;

import	com.sun.mc.softphone.common.Utils;

import  com.sun.mc.softphone.media.MediaManager;
import  com.sun.mc.softphone.media.Microphone;
import  com.sun.mc.softphone.media.MicrophoneListener;
import  com.sun.mc.softphone.media.Speaker;

/**
 * reads data from the microphone 
 */
public class MicrophoneJavasoundImpl implements Microphone {

    private TargetDataLine microphone;

    private boolean isMuted = false;

    private boolean done;

    private int sampleRate;
    private int channels;
    private int bufferSize;

    private double volumeLevel = 1.0D;
    
    /**
     * Constructors.
     */
    public MicrophoneJavasoundImpl(int sampleRate, int channels,
	    int bufferSize) throws IOException {
        
	this.sampleRate = sampleRate;
	this.bufferSize = bufferSize;
	this.channels = channels;

	try {
            setupMicrophone();
	} catch (LineUnavailableException e) {
	    throw new IOException(e.getMessage());
	}
        
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("New Microphone " + sampleRate + "/" + channels
		+ " bufferSize " + bufferSize);
        }

	double volumeLevel = Utils.getDoublePreference(VOLUME_LEVEL);

	if (volumeLevel != -1.0D) {
	    this.volumeLevel = volumeLevel;
	}
    }
	
    /*
     * All done.  Stop everything.
     */
    public void done() {
	//Logger.println("mic done called");

	if (done) {
	    return;
	}

	done = true;

	isMuted = false;

	//Logger.println("Closing the microphone...");

 	microphone.close();

        microphone = null;

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Microphone closed");
	}
    }

    private boolean isDirectAudio(Line line) {
	//Logger.println("got " + line.getClass().toString());
	return line.getClass().toString().indexOf("DirectAudioDevice") >= 0;
    }

    /*
     * Setup the microphone using the DirectAudioDevice
     */
    private void setupMicrophone() throws LineUnavailableException {
	done = false;

        /*
	 * Set up the microphone.
         */ 
        AudioFormat audioFormat = new AudioFormat(
            sampleRate,         	     // Sample rate (Hz)
            MediaManager.BITS_PER_SAMPLE,    // Sample size (bits)
            channels,           	     // Channels (2 = stereo)
            true,               	     // Signed
            true);              	     // False == little endian
    
	String device = Utils.getPreference(Microphone.MICROPHONE_PREFERENCE);

	if (device != null && device.length() > 0 && 
		device.equalsIgnoreCase("Default") == false) {

	    if (setupMicrophone(device) == true) {
		if (Logger.logLevel >= Logger.LOG_INFO) {
	            Logger.println("Using specified microphone:  " + device);
		}
        	startMic(audioFormat);
	        return;
	    }

	    Logger.println("Specified microphone not available:  " + device);
	} else {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Using default microphone");
	    }
	}

        DataLine.Info microphoneInfo =
            new DataLine.Info(TargetDataLine.class, audioFormat);

	try {
            microphone = (TargetDataLine) AudioSystem.getLine(microphoneInfo);

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println(
                    "Microphone:  AudioSystem.getLine() returned "
                    + microphone);
            }
        } catch (IllegalArgumentException e) {
	    Logger.println("AudioSystem.getline failed!");
            throw new LineUnavailableException(e.getMessage());
	}

	if (microphone == null) {
	    throw new LineUnavailableException(
		 "No audio device for the microphone!");
	}

	if (isDirectAudio(microphone) == false) {
	    if (searchDirectAudio(microphoneInfo) == false) {
                String s = System.getProperty("java.version");

                if (s.indexOf("1.5.") >= 0) {
	            Logger.println(
		        "No DirectAudioDevice found for the microphone");
	        }
	    }
	}
        
	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Microphone Using:  " + microphone);
	}

        startMic(audioFormat);
    }
    
    private boolean setupMicrophone(String device) 
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

		Line.Info[] infos = mixer.getTargetLineInfo();

		for (int j = 0; j < infos.length; j++) {
                    Line line = (Line) mixer.getLine(infos[j]);

                    if (line instanceof TargetDataLine) {
			microphone = (TargetDataLine) line;

			if (Logger.logLevel >= Logger.LOG_INFO) {
			    Logger.println("Found mic:  " + j);
			}
			break;
		    }
		}
	    } catch (Exception e) {
		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Exception!  " + e.getMessage());
		}
	    }
	}

	return microphone != null;
    }

    private boolean searchDirectAudio(DataLine.Info microphoneInfo) 
	    throws LineUnavailableException {

	Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

	String s = Utils.getPreference(
	    "com.sun.mc.softphone.media.USE_EXTERNAL_MIC");

	Logger.println("Searching for Direct Audio "
	    + "for the microphone...");

        for (int i = 0; i < aInfos.length; i++) {
            try {
                Mixer mixer = AudioSystem.getMixer(aInfos[i]);

		TargetDataLine m = (TargetDataLine) 
		    mixer.getLine(microphoneInfo);

		if (isDirectAudio(m)) {
		    Logger.println("Found direct audio "
			+ " Name: " + aInfos[i].getName()
			+ " Vendor: " + aInfos[i].getVendor()
			+ " Version: " + aInfos[i].getVersion()
			+ " Description: " + aInfos[i].getDescription()
			+ " Class: " + m);

		    microphone = m;
		    return true;
		} else {
		     Logger.println("Skipping non-direct audio " 
			+ " Name: " + aInfos[i].getName()
			+ " Vendor: " + aInfos[i].getVendor()
			+ " Version: " + aInfos[i].getVersion()
			+ " Description: " + aInfos[i].getDescription()
			+ " Class: " + m);
		}
            } catch (Exception e) {
            }
	}

	return false;
    }

    /**
     * Start the microphone
     */
    private void startMic(AudioFormat format) 
	    throws LineUnavailableException {

        microphone.open(format, bufferSize);

        int actualBufferSize = microphone.getBufferSize();

        if (bufferSize != actualBufferSize) {
            Logger.println("Microphone set buffer size to "
                + bufferSize + " but actual size is "
                + actualBufferSize);

	    bufferSize = actualBufferSize;
        }

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("microphone buffer size "
                + getBufferSizeMillis(actualBufferSize)
                + " milliseconds, " + microphone.getBufferSize() + " bytes");
        }

        microphone.start();
	microphone.flush();
    }

    private SampleRateConverter sampleRateConverter;

    public void read(byte[] linearData, int offset, int len) 
	    throws IOException {

	if (microphone == null) {
	    return;
	}

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
		+ "for BlueTooth microphone");

            try {
                sampleRateConverter = new SampleRateConverter(
		    "javasound mic",
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

	    Timer timer = new Timer();
	    
	    timer.schedule(new TimerTask() {
		public void run() {
		    Logger.println("Microphone read did not complete!");
		    microphone.close();
		}
	    }, 3000);

            n = microphone.read(linearData, offset + o, bufferLength);

	    timer.cancel();

            o += n;
            bufferLength -= n;
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
        return millis * sampleRate * channels *
            MediaManager.BYTES_PER_SAMPLE / 1000;
    }

    public int getBufferSizeMillis(int bufferSize) {
        int bytesPerMillisecond = sampleRate * channels *
            MediaManager.BYTES_PER_SAMPLE / 1000;

        return bufferSize / bytesPerMillisecond;
    }

    public int available() {
	if (microphone == null) {
	    return 0;
	}
	return microphone.available();
    }

    public void flush() {
	if (microphone == null) {
	    return;
	}

	microphone.flush();
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
	int channels = 1;
	int duration = 0;

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equalsIgnoreCase("L") == true) {
		listMixers();
		continue;
	    } else if (args[i].equalsIgnoreCase("-sampleRate")) {
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
            microphone = new MicrophoneJavasoundImpl(sampleRate, channels,
		2 * dataSize);

            speaker = new SpeakerJavasoundImpl(sampleRate, channels,
		8 * dataSize);
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
                    speaker.write(buffer, 0, buffer.length);
        	} catch (IOException e) {
        	    e.printStackTrace();
        	}
	    }
	} else {
	    while (duration > 0) {
	        long start = System.currentTimeMillis();

		try {
                    microphone.read(buffer, 0, buffer.length);
                    speaker.write(buffer, 0, buffer.length);
        	} catch (IOException e) {
        	    e.printStackTrace();
        	}

		duration -= (System.currentTimeMillis() - start);
	    }
        }
    }

    private static void listMixers() {
        Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

        for (int i = 0; i < aInfos.length; i++) {
            try {
                Mixer mixer = AudioSystem.getMixer(aInfos[i]);

                Logger.println("" + i +": " + aInfos[i].getName() + ", "
                                   + aInfos[i].getVendor() + ", "
                                   + aInfos[i].getVersion() + ", "
                                   + aInfos[i].getDescription()
                                   + " class " + mixer);

                printLines(mixer, mixer.getSourceLineInfo());
                printLines(mixer, mixer.getTargetLineInfo());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void printLines(Mixer mixer, Line.Info[] infos) {
        for (int i = 0; i<infos.length; i++) {
            try {
                if (infos[i] instanceof Port.Info) {
                    Port.Info info = (Port.Info) infos[i];
                    Logger.println("  Port "+info);
                }
                if (infos[i] instanceof DataLine.Info) {
                    DataLine.Info info = (DataLine.Info) infos[i];
                    Logger.println("  Line " + info + " (max. " 
			+ mixer.getMaxLines(info) + " simultaneously): ");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void printStatistics() {
    }

}
