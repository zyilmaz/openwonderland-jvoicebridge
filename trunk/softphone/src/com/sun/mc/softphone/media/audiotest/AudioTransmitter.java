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

import java.io.IOException;
import java.io.OutputStream;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.Vector;

import com.sun.mc.softphone.media.AudioFactory;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;

/**
 * sends data from the microphone to a remote host
 */
public class AudioTransmitter extends Thread {
    private Microphone microphone;
    private Speaker speaker;

    private Socket socket;
    private DatagramSocket datagramSocket;
    private DatagramPacket datagramPacket;
    private InetSocketAddress isa;

    private static String device = "plughw:0,0";
    private static int sampleRate = 16000;
    private static int channels = 2;

    private static String destHostPort;
    private static String destHost;
    private static int destPort;

    private OutputStream output;

    private byte[] microphoneData;

    private static boolean tcp = false;
    private static boolean udp = false;

    private static boolean startReceiver;

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
		destHostPort = args[i];
                tcp = true;
            } else if (arg.equalsIgnoreCase("-udp") && args.length > ++i) {
		destHostPort = args[i];
                udp = true;
            } else if (arg.equalsIgnoreCase("-r")) {
		startReceiver = true;
            } else {
		usage();
	    }
        }

        if (tcp == true && udp == true) {
            usage();
        }

	if (tcp == false && udp == false) {
	    new AudioTransmitter();
	} else {
            String tokens[] = destHostPort.split(":");

            if (tokens.length != 2) {
                System.out.println("Invalid destination host:  " + destHost);
		usage();
            }

            destHost = tokens[0];

            try {
                destPort = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port: " + tokens[1]);
                System.exit(1);
            }

	    InetSocketAddress isa = null;

            try {
                isa = new InetSocketAddress(InetAddress.getByName(destHost), 
		    destPort);
            } catch (UnknownHostException e) {
                System.out.println("UnknownHost: " + destHost);
                System.exit(1);
            }
		            
            if (isa.getAddress().getHostAddress().equals("127.0.0.1")) {
		startReceiver = true;
	    }

	    new AudioTransmitter(isa);
	}
    }

    private static void usage() {
	System.out.println("Usage:  java AudioTransmitter -list");
	System.out.println("	OR");
        System.out.println("Usage:  java AudioTransmitter "
            + "-udp destHost:port | -tcp destHost:port "
	    + "[-device <device>] [-sampleRate <sampleRate]"
            + "[-channels <channels>] [-r]");
        System.exit(1);
    }

    public AudioTransmitter() {
	initialize();
    }

    public AudioTransmitter(InetSocketAddress isa) {
	this.isa = isa;
	initialize();
    }

    private void initialize() {
	AudioCommon.initialize(sampleRate, channels);

	microphone = AudioCommon.getMicrophone();

	microphoneData = new byte[AudioCommon.getMicrophoneBufferSize()];

	if (tcp == true) {
	    try {
		socket = new Socket();

		if (startReceiver == true) {
		    new AudioReceiver(true, destPort);
		} 

		boolean firstTime = true;

		while (true) {
		    try {
		        socket.connect(isa);
			break;
		    } catch (IOException e) {
			if (firstTime) {
			    firstTime = false;
			    
			    System.out.println("Waiting for "
				+ isa + " to listen for connections...");
			}

			try {
			    Thread.sleep(1000);
			} catch (InterruptedException ee) {
			}
		    }
		}
	        output = socket.getOutputStream();
	    } catch (IOException e) {
	        System.out.println(
		    "Can't get socket output stream! " + e.getMessage());
	        System.exit(1);
	    }
	} else if (udp == true) {
	    try {
		datagramSocket = new DatagramSocket();

		if (startReceiver == true) {
		    new AudioReceiver(false, destPort);
		}

	        datagramPacket = new DatagramPacket(
		    microphoneData, microphoneData.length, isa);
	    } catch (IOException e) {
	        System.out.println(
		    "Can't create socket/packet! " + e.getMessage());
	        System.exit(1);
	    }
	} else {
	    speaker = AudioCommon.getSpeaker();
	}

	start();
    }

    /*
     * Read data from the microphone and send the data to the socket
     */
    public void run() {
	boolean firstTime = true;

	boolean stoppedSending = false;

	long start = System.currentTimeMillis();

	microphone.flush();

        while (true) {
	    /*
	     * Read linear data from the microphone
	     */ 
	    if (firstTime) {
		System.out.println("reading mic...");
	    }

	    try {
	        microphone.read(microphoneData, 0, microphoneData.length);
	    } catch (IOException e) {
		System.out.println("Unable to read microphone: "
		    + e.getMessage());
		System.exit(1);
	    }

	    if (firstTime) {
		System.out.println("back from reading mic...");
	    }

	    long now = System.currentTimeMillis();

	    //System.out.println("elapsed ms:  " + (now - start));

	    start = now;

	    if (tcp == true) {
	        try {
		    if (firstTime) {
			System.out.println("writing tcp socket...");
		    }

	            output.write(microphoneData);   

		    if (firstTime) {
			System.out.println("back from writing tcp socket...");
		    }
	        } catch (IOException e) {
		    System.out.println(
			"Unable to write to socket! " + e.getMessage());
		    System.exit(1);
	        }
	    } else if (udp == true) {
		try {
		    if (firstTime) {
			System.out.println("writing udp socket...");
		    }
		    datagramSocket.send(datagramPacket);

		    if (firstTime) {
			System.out.println("back from writing udp socket...");
		    }
	        } catch (IOException e) {
		    System.out.println(
			"Unable to send to socket! " + e.getMessage());
		    System.exit(1);
		}
	    } else {
		/*
	 	 * Loopback 
		 */
		try {
		    speaker.write(microphoneData, 0, microphoneData.length);
		} catch (IOException e) {
		    System.out.println("Unable to write to speaker: "
			+ e.getMessage());
		    System.exit(1);
		}
	    }

	    firstTime = false;
        }
    }

}
