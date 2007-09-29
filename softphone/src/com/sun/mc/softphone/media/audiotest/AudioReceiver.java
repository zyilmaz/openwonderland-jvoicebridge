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

package com.sun.mc.softphone.media.audiotest;

import java.io.BufferedInputStream;
import java.io.IOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.sun.mc.softphone.media.AudioFactory;
import com.sun.mc.softphone.media.Speaker;

/**
 * Receives data from the RtpSocket and writes it to the speaker
 */
public class AudioReceiver extends Thread {
    private Speaker speaker;

    private ServerSocket serverSocket;
    private DatagramSocket datagramSocket;

    private static int port;
    private static String device = "plughw:0,0";
    private static int sampleRate = 16000;
    private static int channels = 2;

    private static boolean tcp = false;
    private static boolean udp = false;

    private boolean initAudio = true;

    public static void main(String[] args) {
	for (int i = 0; i < args.length; i++) {
	    String arg = args[i];

            if (arg.equalsIgnoreCase("-list")) {
                AudioCommon.listDevices();
                System.exit(1);
	    } else if (arg.equalsIgnoreCase("-sampleRate") && args.length > ++i) {
		try {
		    sampleRate = Integer.parseInt(args[i]);
		} catch (NumberFormatException e) {
		    System.out.println("Invalid sample rate:  " + args[i]);
		    System.exit(1);
		}
	    } else if (arg.equalsIgnoreCase("-channels") && args.length > ++i) {
		try {
		    channels = Integer.parseInt(args[i]);
		} catch (NumberFormatException e) {
		    System.out.println("Invalid sample rate:  " + args[i]);
		    System.exit(1);
		}
	    } else if (arg.equalsIgnoreCase("-device") && args.length > ++i) {
		device = args[i];
	    } else if (arg.equalsIgnoreCase("-tcp") && args.length > ++i) {
		try {
	            port = Integer.parseInt(args[i]);
	        } catch (NumberFormatException e) {
	            System.out.println("Invalid port: " + args[i]);
	            System.exit(1);
		}
		tcp = true;
	    } else if (arg.equalsIgnoreCase("-udp") && args.length > ++i) {
		try {
	            port = Integer.parseInt(args[i]);
	        } catch (NumberFormatException e) {
	            System.out.println("Invalid port: " + args[i]);
	            System.exit(1);
		}
		udp = true;
	    } else {
		usage();
	    }
	}

	if (tcp == true && udp == true) {
	    usage();
	}

	if (tcp == false && udp == false) {
	    usage();
	}

	new AudioReceiver();
    }

    private static void usage() {
	System.out.println("Usage:  java AudioReceiver -list");
        System.out.println("    OR");
	System.out.println("Usage:  java AudioReceiver"
	    + "-udp srcHost:port | -tcp srcHost:port [-device <device>]" 
	    + "[-sampleRate <sampleRate] [-channels <channels>]");
	System.exit(1);
    }

    private static void listDevices() {
        AudioFactory audioFactory = AudioFactory.getInstance();

        String[] microphones = audioFactory.getMicrophoneList();

        System.out.println("Microphones");

        for (int i = 0; i < microphones.length; i++) {
            System.out.print("  " + microphones[i]);
        }

        String[] speakers = audioFactory.getSpeakerList();

        System.out.println("\nSpeakers");

        for (int i = 0; i < speakers.length; i++) {
            System.out.print("  " + speakers[i]);
        }

        System.out.println("");
    }

    public AudioReceiver() {
	this(tcp, true, port);
    }

    public AudioReceiver(boolean tcp, int port) {
	this(tcp, false, port);
    }

    public AudioReceiver(boolean tcp, boolean initAudio, int port) {
	this.tcp = tcp;
	this.initAudio = initAudio;
	this.port = port;

	if (initAudio == true) {
	    AudioCommon.initialize(sampleRate, channels);
	}

	speaker = AudioCommon.getSpeaker();

	if (tcp) {
	    try {
		serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                System.out.println(
		    "Can't create tcp socket! " + e.getMessage());
                System.exit(1);
	    }
	} else {
	    try {
	        datagramSocket = new DatagramSocket(port);
	    } catch (SocketException e) {
                System.out.println(
		    "Can't create udp socket! " + e.getMessage());
                System.exit(1);
	    }
	}

	setPriority(Thread.MAX_PRIORITY);
	start();
    }
    
    /*
     * Receive packets and send the data to the speaker
     */ 
    public void run() {
        Socket socket = null;
        DatagramPacket datagramPacket = null;

        byte[] receiveBuffer = new byte[10000];

        BufferedInputStream bufferedInputStream = null;

	if (tcp) {
	    try {
	        socket = serverSocket.accept(); // wait for connection
                bufferedInputStream = 
		    new BufferedInputStream(socket.getInputStream());
	    } catch (IOException e) {
	        System.out.println("Unable to handle connection:  "
		    + e.getMessage());
	        System.exit(1);
	    }
	} else {
	    datagramPacket = new DatagramPacket(
		receiveBuffer, receiveBuffer.length);
	}

	speaker.flush();

	long startTime = 0;
	long bytesReceived = 0;

	int packetsReceived = 0;
	int bytesDropped = 0;
	int partiallyDropped = 0;
	int flushed = 0;

        while (true) {
	    int length;

	    int n = 0;

	    if (tcp == true) {
	        try {
		    if (bytesReceived == 0) {
			System.out.println("About to read TCP data...");
		    }

	            n = bufferedInputStream.read(
			receiveBuffer, 0, receiveBuffer.length);

		    if (bytesReceived == 0) {
			System.out.println("Back from reading TCP data");
		    }
	        } catch (IOException e) {
		    System.out.println(
			"Unable to read socket! " + e.getMessage());
		    System.exit(1);
	        }
	    } else {
		try {
		    if (bytesReceived == 0) {
			System.out.println("About to read UDP data");
		    }

		    datagramSocket.receive(datagramPacket);

		    if (bytesReceived == 0) {
			System.out.println("Back from reading UDP data");
		    }
		    n = datagramPacket.getLength();
		} catch (Exception e) {
		}
	    }

	    if (n < 0) {
		System.out.println("Unable to read socket, no data!");
		System.exit(1);
	    }

	    if (n == 0) {
		System.out.println("received 0 bytes!");
		continue;
	    }

	    packetsReceived++;
	    bytesReceived += n;

	    if (bytesReceived == n) {
		// first packet, set start time
		startTime = System.currentTimeMillis();
		continue;
	    }

	    if ((packetsReceived % 500) == 0) {
		/*
		 * byte size for 20ms of data
		 */
		int dataSize = sampleRate * channels * 2 / 50;

		long elapsed = 
		    System.currentTimeMillis() - startTime;  // elapsed time

		long bytesPer20ms = 
		    20 * bytesReceived / elapsed;	// bytes per 20 ms

		long avgTime = 20 * bytesPer20ms / dataSize;

		System.out.println(packetsReceived + " pkts recvd, "
		    + ((float)bytesDropped / (float)bytesReceived * 100) 
		    + "% dropped, "
		    + partiallyDropped + " partially dropped pkts, "
		    + flushed + " speaker flushes, "
		    + (elapsed / packetsReceived) + " time betw pkts, "
		    + avgTime + " avg time for 20ms of data");
	    }

            int writeSize = Math.min(speaker.available(), n);

            if (speaker.available() < n) {
                speaker.flush(); 

                int available = speaker.available();

		flushed++;

                bytesDropped += speaker.getBufferSize();

                writeSize = Math.min(available, n);

                if (available < n) {
                    System.out.println("no space available after flush! " 
			+ packetsReceived);
                    continue;       // something is wrong, we just flushed
                }
            } 

	    try {
	        speaker.write(receiveBuffer, 0, n);
	    } catch (IOException e) {
		System.out.println("Unable to write to speaker:  " 
		    + e.getMessage());
		System.exit(1);
	    }
	}
    }

}
