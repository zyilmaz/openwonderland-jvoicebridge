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

import com.sun.voip.Logger;
import com.sun.voip.RtpPacket;
import com.sun.voip.TreatmentManager;
import java.io.IOException;
import javax.sound.sampled.LineUnavailableException;

import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.MediaManagerFactory;

/*
 * Play an audioFile to the speaker
 */
public class AudioFilePlayer extends Thread {
    private TreatmentManager treatmentManager;
    private boolean done = false;
    private boolean finished = false;
    private Speaker speaker;

    public AudioFilePlayer(String audioFile, int repeatCount,
	    Speaker speaker) throws IOException {

	this.speaker = speaker;

	treatmentManager = new TreatmentManager(audioFile, repeatCount,
	    speaker.getSampleRate(), speaker.getChannels()); 

	if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
	    Logger.println("AudioFilePlayer playing " + audioFile + " "
		+ speaker.getSampleRate() + "/" + speaker.getChannels());
	}

	start();
    }

    public void done() {
	treatmentManager.stopTreatment();
	done = true;

	synchronized (this) {
	    while (!finished) {
		try {
		    wait();
		} catch (InterruptedException e) {
		}
	    }
	}
    }

    public void run() {
	long start = System.currentTimeMillis();

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Playing audio file... " 
		+ treatmentManager.getId());
	}

	try {
	    while (!done) {
                byte[] speakerData = 
                    treatmentManager.getLinearDataBytes(RtpPacket.PACKET_PERIOD);
            
                if (speakerData == null) {
                    break;
                }
            
	        speaker.start();

	        while (!done && speaker.available() < 2 * speakerData.length) {
                    try {
                        Thread.sleep(RtpPacket.PACKET_PERIOD);
                    } catch (InterruptedException e) {
                    }
	            speaker.start();
	        }

                try {
	            speaker.start();
                    speaker.write(speakerData, 0, speakerData.length);
                } catch (IOException e) {
                    Logger.println("Unable to write to speaker:  " 
		        + e.getMessage());
                    break;
                }
            }

            speaker.flush();
	} finally {
	    synchronized(this) {
	        finished = true;
	        notifyAll();
	    }
	}

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Done playing audio file... "
	        + treatmentManager.getId());
	}
    }

    public static void main(String[] args) {
	if (args.length != 3) {
	    Logger.println(
		"Usage:  java AudioFilePlayer <file to play>"
		+ " <sampleRate> <channels>");
	    System.exit(1);
	}

	MediaManager mediaManager = MediaManagerFactory.getInstance();

	int sampleRate = Integer.parseInt(args[1]);
	int channels = Integer.parseInt(args[2]);

	try {
	    Speaker speaker = mediaManager.getSpeaker(sampleRate, channels);

	    AudioFilePlayer AudioFilePlayer = new AudioFilePlayer(args[0], 0,
	        speaker);
	} catch (IOException e) {
	    Logger.println(e.getMessage());
	}
    }

}
