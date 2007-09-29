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

/*
 * Construct buffer containing DTMF tones
 */
package com.sun.mc.softphone.media;

import java.io.IOException;

import java.util.Hashtable;

import com.sun.voip.Logger;
import com.sun.voip.LowPassFilter;
import com.sun.voip.MediaInfo;
import com.sun.voip.Recorder;
import com.sun.voip.RtpPacket;
import com.sun.voip.Util;

public class DtmfBuffer extends Thread {

    private double amplitude = 2048.0;
    private double sampleRate = 8000.0;
    private int channels = 1;
    private int sample = 0;

    private double f1 = 697.0;
    private double f2 = 1209.0;

    private Speaker speaker;

    public DtmfBuffer() {
    }

    public DtmfBuffer(Speaker speaker) {
	this((double)speaker.getSampleRate(), speaker.getChannels());
	this.speaker = speaker;
    }

    public DtmfBuffer(Microphone microphone) {
	this((double)microphone.getSampleRate(), microphone.getChannels());
    }

    public DtmfBuffer(double sampleRate, int channels) {
        this.sampleRate = sampleRate;
	this.channels = channels;

	setPriority(Thread.MAX_PRIORITY);
    }

    public void setDtmf(String key) {
        switch(key.toLowerCase().charAt(0)) {
        case '1': f1=697.0; f2=1209.0; 
	    break;

        case '2': f1=697.0; f2=1336.0; 
	    break;

        case 'a': f1=697.0; f2=1336.0; 
	    break;

        case 'b': f1=697.0; f2=1336.0; 
	    break;

        case 'c': f1=697.0; f2=1336.0; 
	    break;

        case '3': f1=697.0; f2=1477.0; 
	    break;

        case 'd': f1=697.0; f2=1477.0; 
	    break;

        case 'e': f1=697.0; f2=1477.0; 
	    break;

        case 'f': f1=697.0; f2=1477.0; 
	    break;

        case '4': f1=770.0; f2=1209.0; 
	    break;

        case 'g': f1=770.0; f2=1209.0; 
	    break;

        case 'h': f1=770.0; f2=1209.0; 
	    break;

        case 'i': f1=770.0; f2=1209.0; 
	    break;

        case '5': f1=770.0; f2=1336.0; 
	    break;

        case 'j': f1=770.0; f2=1336.0; 
	    break;

        case 'k': f1=770.0; f2=1336.0; 
	    break;

        case 'l': f1=770.0; f2=1336.0; 
	    break;

        case '6': f1=770.0; f2=1477.0; 
	    break;

        case 'm': f1=770.0; f2=1477.0; 
	    break;

        case 'n': f1=770.0; f2=1477.0; 
	    break;

        case 'o': f1=770.0; f2=1477.0; 
	    break;

        case '7': f1=852.0; f2=1209.0; 
	    break;

        case 'p': f1=852.0; f2=1209.0; 
	    break;

	case 'q': f1=852.0; f2=1209.0; 
	    break;

        case 'r': f1=852.0; f2=1209.0; 
	    break;

        case 's': f1=852.0; f2=1209.0; 
	    break;

        case '8': f1=852.0; f2=1336.0; 
	    break;

        case 't': f1=852.0; f2=1336.0; 
	    break;

        case 'u': f1=852.0; f2=1336.0; 
	    break;

        case 'v': f1=852.0; f2=1336.0; 
	    break;

        case '9': f1=852.0; f2=1477.0; 
	    break;

        case 'w': f1=852.0; f2=1477.0; 
	    break;

        case 'x': f1=852.0; f2=1477.0; 
	    break;

        case 'y': f1=852.0; f2=1477.0; 
	    break;

        case 'z': f1=852.0; f2=1477.0; 
	    break;

        case '*': f1=941.0; f2=1209.0; 
	    break;

        case '0': f1=941.0; f2=1336.0; 
	    break;

        case '#': f1=941.0; f2=1477.0; 
	    break;

	/*
	case 'A': f1=697.0; f2=1633.0; 
	    break;

	case 'B': f1=770.0; f2=1633.0; 
	    break;

	case 'C': f1=852.0; f2=1633.0; 
	    break;

	case 'D': f1=941.0; f2=1633.0; 
	    break;

	*/
        }

        sample = 0;
    }

    public void fillBuffer(byte[] buffer) {
        fillBuffer(buffer, 0, buffer.length);
    }

    public void fillBuffer(byte[] buffer, int offset, int length) {
        double v1;
        double v2;
        int value;

        for (int i = 0; i < length; i += (2 * channels)) {
            v1 = amplitude*Math.sin(sample*2*Math.PI*f1/sampleRate);
            v2 = amplitude*Math.sin(sample*2*Math.PI*f2/sampleRate);
            value = (int)(v1+v2);

	    for (int n = 0; n < channels; n++) { 
                buffer[(n * 2) + i + offset] = (byte)((value>>8)&0xFF);
                buffer[(n * 2) + i + offset + 1] = (byte)(value&0xFF);
	    }

            sample++;
        }
    }

    private boolean done;

    public void done() {
	done = true;
	interrupt();
    }

    public void run() {
	byte[] buf = new byte[speaker.getBufferSize(RtpPacket.PACKET_PERIOD)];

	speaker.flush();

	while (!done) {
            fillBuffer(buf, 0, buf.length);

	    speaker.start();

            while (!done && speaker.available() < 2 * buf.length) {
		try {
		    Thread.sleep(RtpPacket.PACKET_PERIOD);
		} catch (InterruptedException e) {
		}
	        speaker.start();
	    }

	    try {
	        speaker.write(buf, 0, buf.length);
	    } catch (IOException e) {
		Logger.println("DtmfBuffer unable to write to speaker!  "
		    + e.getMessage());
	    }
	}

	speaker.flush();
    }

    public static void main(String[] args) {
	new DtmfBuffer().record();
    }

    private void record() {
	int sampleRate = 44100;

        byte[] buf = new byte[sampleRate * 2 * 5];	// 5 seconds

        byte[] mixture = new byte[sampleRate * 2 * 5];

	/*
	 * Generate audio with sine waves for 1000 to 22000 hz
 	 * in increments of 1000 hz.
	 */
        for (double f = 1000; f < 22000; f += 1000) {
            getData(sampleRate, f, buf);
            mix(buf, mixture);
	}

	Recorder recorder;

	MediaInfo m = new MediaInfo((byte) 0, RtpPacket.PCM_ENCODING,
	    sampleRate, 1, false);

	try {
	    recorder = new Recorder("sine.au", "au", m);
            recorder.write(mixture, 0, mixture.length);
	} catch (IOException e) {
	    Logger.println("Can't create recorder:  " + e.getMessage());
	    return;
	}

	/*
	 * Run the data through a low pass filters using
	 * for the RC lpf.
	 */
	for (int i = 1; i <= 20 ; i++) {
	    double fc = i / 100D;

	    LowPassFilter.setX(fc);

	    try {
	        recorder = new Recorder("sine.lpf." 
		    + (Math.round(fc * 100) / 100D) + ".au", "au", m);
	    } catch (IOException e) {
	        Logger.println("Can't create recorder:  " + e.getMessage());
	        return;
	    }

	    LowPassFilter lpf = new LowPassFilter("sine", sampleRate, 1);
	    //LowPassFilter.setNAvg(i);
    	    //LowPassFilter.setLpfVolumeAdjustment(0.0);
	    //LowPassFilter.setParams((double)i / 100, 0D);

	    byte[] data = lpf.lpfSP(mixture);

	    //adjustVolume(data, 1 / fc / 10);

	    //lpf.printStatistics();

	    try {
                recorder.write(data, 0, data.length);
	    } catch (IOException e) {
	        Logger.println("Failed to record:  " + e.getMessage());
	    }

	    recorder.done();
        }
    }

    private void adjustVolume(byte[] data, double v) {
        for (int i = 0; i < data.length; i += 2) {
            short s = (short) (((data[i] << 8) & 0xff00) | (data[1 + 1] & 0xff));

            //short d = clip((int) (s * v));

            data[i] = (byte) ((s >> 8) & 0xff);
            data[i + 1] = (byte) (s & 0xff);
	}
    }

    private short clip(int sample) {
        if (sample > 32767) {
	    Logger.println("too big...");
            return (short)32767;
        }

        if (sample < -32768) {
	    Logger.println("too small...");
            return (short)-32768;
        }

        return (short) sample;
    }

    public void getData(int sampleRate, double f, byte[] buffer) {
	int sample = 0;

        for (int i = 0; i < buffer.length; i += 2) {
            double v = 2048 * Math.sin(sample * 2 * Math.PI * f / sampleRate);

            buffer[i] = (byte) (((int) v >> 8) & 0xff);
            buffer[i + 1] = (byte) ((int) v & 0xff);

            sample++;
        }
    }

    private void mix(byte[] buf, byte[] mixture) {
	for (int i = 0; i < buf.length; i += 2) {
	    short s = (short) (((buf[i] << 8) & 0xff00) |
		(buf[i + 1] & 0xff));

	    short m = (short) (((mixture[i] << 8) & 0xff00) |
		(mixture[i + 1] & 0xff));

	    m += s;

	    mixture[i] = (byte) ((m >> 8) & 0xff);
	    mixture[i + 1] = (byte) (m & 0xff);
	}
    }

}
