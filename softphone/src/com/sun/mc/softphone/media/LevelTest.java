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

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import javax.sound.sampled.LineUnavailableException;

import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.MediaManagerFactory;

import com.sun.voip.Util;

/**
 *
 * @author mw17785
 */
public class LevelTest {
    private Microphone microphone;
    private Speaker speaker;
    private Socket sock;
    private DataOutputStream dos;
    private int sampleSize;
    
    /** Creates a new instance of LevelTest */
    public LevelTest(int port, float dur, float sampleRate, int channels) {
        try {
            MediaManager mediaManager = MediaManagerFactory.getInstance();
            microphone = mediaManager.getMicrophone();
            speaker = mediaManager.getSpeaker();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
	}
	
	sampleSize = microphone.getSampleSizeInBits() / 8 * microphone.getChannels();

        try {
            if (port>0) {
                sock = new Socket("localhost", port);
                sock.setTcpNoDelay(true);
                dos = new DataOutputStream(sock.getOutputStream());
            }
            performTest(dur);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException ioe) {}
            }
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException ioe) {}
            }
        }
        System.exit(0);
    }

    
    public void performTest(float dur) {
        try {
            new AudioFilePlayer("dtmf:1", 1, speaker);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        float rate = microphone.getSampleRate();
        int chunksize = (int)(sampleSize*rate*0.02f);
        byte[] data = new byte[chunksize*(int)(50*dur)];
        int loc = 0;
	int lastLevel = 0;

        while (loc<data.length) {
	    try {
                microphone.read(data, loc, chunksize);
	    } catch (IOException e) {
		System.out.println("Unable to read microphone!  "
		    + e.getMessage());
		break;
	    }

            float level = processChunk(data, loc, chunksize);
            if (dos != null) {
                try {
                    dos.writeFloat(level);
                    dos.flush();
                } catch (IOException ioe) {
                    try {
                        dos.close();
                    } catch (Exception e) {}
                    dos = null;
                }
            } else {
                if (level>1) {
                    level = 1;
                }
                if (level<0) {
                    level = 0;
                }

		for (int i = 0; i < lastLevel; i++) {
		    System.out.print("");
		}

		for (int i = 0; i < lastLevel; i++) {
		    System.out.print(" ");
		}
		    
		for (int i = 0; i < lastLevel; i++) {
		    System.out.print("");
		}

		lastLevel = (int) (level * 20);

                System.out.print(
		    "=====================".substring(0, lastLevel));

                System.out.flush();
            }
            loc+= chunksize;
        }

	System.out.println("");

        // close socket NOW, if it exists
        if (dos != null) {
            try {
                dos.close();
            } catch (Exception e) {}
            dos = null;
        }
        if (sock != null) {
            try {
                sock.close();
            } catch (Exception e) {}
            sock = null;
        }
        // play back audio
	try {
            speaker.write(data, 0, data.length);
	} catch (IOException e) {
	    System.out.println("Unable to write speaker!  "
		+ e.getMessage());
	}
    }
    
    private float processChunk(byte[] linearData, int offset, int length) {
	return processChunk(linearData, offset, length, sampleSize);
    }

    public static float processChunk(byte[] linearData, int offset, int length, int sampleSize) {
        long ourmax = 0;
        //System.out.prinltn("Got "+length+" bytes of microphone data");
        for (int i=offset; i<offset+length; i+= sampleSize) {
            long sample = ((long)linearData[i]<<8) + ((long)linearData[i+1] & 0xFF);
            sample = Math.abs(sample);
            if (sample > ourmax) {
                ourmax = sample;
            }
        }
        return ((float)((double)ourmax / (double)(1<<15)));
    }

    
    public static void main(String[] args) {
        int port = -1;
        float dur = 4;
        float sampleRate = 44100;
        int channels = 2;
        boolean fail = false;
        for(int i=0; i<args.length; i++) {
            if (args[i].equals("-port")) {
                i++;
                if (args.length>i) {
                    try {
                        port = Integer.parseInt(args[i]);
                    } catch (NumberFormatException nfe) {
                        fail = true;
                    }
                }
            } else if (args[i].equals("-sampleRate")) {
                i++;
                if (args.length>i) {
                    try {
                        sampleRate = Float.parseFloat(args[i]);
                    } catch (NumberFormatException nfe) {
                        fail = true;
                    }
                }
            } else if (args[i].equals("-channels")) {
                i++;
                if (args.length>i) {
                    try {
                        channels = Integer.parseInt(args[i]);
                    } catch (NumberFormatException nfe) {
                        fail = true;
                    }
                }
            } else if (args[i].equals("-duration")) {
                i++;
                if (args.length>i) {
                    try {
                        dur = Float.parseFloat(args[i]);
                    } catch (NumberFormatException nfe) {
                        fail = true;
                    }
                }
            }
        }
        new LevelTest(port, dur, sampleRate, channels);
    }
}
