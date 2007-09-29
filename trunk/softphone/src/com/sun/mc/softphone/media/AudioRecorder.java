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

package com.sun.mc.softphone.media;

import java.io.IOException;
import java.util.Vector;

import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.Recorder;

/*
 * This will record both sides of a conversation.
 */
public class AudioRecorder extends Thread {

    private Recorder recorder;

    /*
     * We need a Vector for local data because the microphone read
     * doesn't complete until the microphone buffer is full.  
     * When the read completes, reads will continue to complete immediately
     * until the buffer is empty.
     */
    private Vector localDataVector = new Vector();

    private byte[] remoteData;

    private boolean done;

    public AudioRecorder(String file, MediaInfo mediaInfo) throws IOException {
	recorder = new Recorder(file, "au", mediaInfo);
	start();
    }

    public void done() {
  	done = true;
    }

    public void record(byte[] data, int offset, int len, boolean local) 
	    throws IOException {

	if (done || recorder == null) {
	    throw new IOException("Can't record remote audio!");
	}

	synchronized(this) {
	    if (local) {
	        byte[] localData = new byte[len];

	        for (int i = 0; i < len; i++) {
		    localData[i] = data[i + offset];
	        }
	        localDataVector.add(localData);
	    } else {
	        remoteData = new byte[len];

	        for (int i = 0; i < len; i++) {
		    remoteData[i] = data[i + offset];
	        }
	    }
	}
    }

    public void run() {
	while (!done) {
	    long start = System.currentTimeMillis();

	    try {
		Thread.sleep(10);
	    } catch (InterruptedException e) {
	    }

	    while (System.currentTimeMillis() - start < 20) {
		;   // busy wait
	    }

	    synchronized(this) {
		byte[] data = mix();

		if (data == null) {
		    continue;
		}

		try {
		    recorder.write(data, 0, data.length);
		} catch (IOException e) {
		    Logger.println("Failed to record audio data: " + e.getMessage());
		    break;	
		}

		if (localDataVector.size() > 0) {
		    localDataVector.remove(0);
		}

		remoteData = null;
	    }
	}

	recorder.done();
	recorder = null;
    }

    /*
     * Mix local and remote data
     */
    public byte[] mix() {
        if (localDataVector.size() == 0 && remoteData == null) {
	    return null;
	}

	if (localDataVector.size() == 0) {
	    return remoteData;
	}

	if (remoteData == null) {
	    return (byte[]) localDataVector.get(0);
	}

	byte[] inData = (byte[]) localDataVector.get(0);
	byte[] outData = remoteData;

	int len = inData.length;

	if (inData.length > outData.length) {
	    outData = inData;
	    len = remoteData.length;
	}

	for (int i = 0; i < len; i += 2) {
            int c = (inData[i] << 8) | (inData[i + 1] & 0xff);

            int o = (outData[i] << 8) | (outData[i + 1] & 0xff);

            int m;

            m = clip(o + c);
            
            outData[i] = (byte) ((m >> 8) & 0xff);
            outData[i + 1] = (byte) (m & 0xff);
        }

	return outData;
    }

    private short clip(int sample) {
        if (sample > 32767) {
            return (short)32767;
        }

        if (sample < -32768) {
            return (short)-32768;
        }

        return (short)sample;
    }

}
