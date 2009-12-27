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
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import com.sun.voip.Util;

import com.sun.mc.softphone.media.AudioFactory;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;

/**
 * sends data from the microphone to a remote host
 * receives data from a remote host and writes it to the speaker
 */
public class AudioTestTcp {

    private InetSocketAddress isa;

    private String device = "plughw:0,0";
    private int sampleRate = 16000;
    private int channels = 2;

    private String serverAddress = "173.76.32.79";
    private int serverPort = 5060;

    private boolean server = false;

    private boolean noAudio;

    public static void main(String[] args) {
	new AudioTestTcp(args);
    }
  
    public AudioTestTcp(String[] args) {
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
            } else if (arg.equalsIgnoreCase("-port") && args.length > ++i) {
		server = true;

		try {
		    serverPort = Integer.parseInt(args[i]);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid server port: " + args[i]);
                    System.exit(1);
                }
            } else if (arg.equalsIgnoreCase("-server")) {
		if (args.length > i + 2) {
		   serverAddress = args[++i];

		   try {
		       serverPort = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid server port: " + args[i]);
                        System.exit(1);
                    }
		} else {
		    usage();
		}
            } else if (arg.equalsIgnoreCase("-noAudio")) {
		noAudio = true;
            } else {
		usage();
	    }
        }

	initialize();
    }

    private static void usage() {
	System.out.println("Usage:  java AudioTransmitter -list");
	System.out.println("	OR");
        System.out.println("Usage:  java AudioTransmitter "
            + "-server <Host> <port> " + "-port <server port "
	    + "[-device <device>] [-sampleRate <sampleRate]"
            + "[-channels <channels>] ");
        System.exit(1);
    }

    private void initialize() {
	if (noAudio == false) {
	    AudioCommon.initialize(sampleRate, channels);
	}

	Socket socket = null;

	if (server) {
	    ServerSocket serverSocket = null;

	    try {
		System.out.println("Server socket is listening on port " + serverPort);

                serverSocket = new ServerSocket(serverPort);
            } catch (IOException e) {
                System.out.println(
                    "Can't create tcp socket! " + e.getMessage());
                System.exit(1);
            }

	    try {
                socket = serverSocket.accept(); // wait for connection
		System.out.println("Accepted a connection from " + socket.getInetAddress());
            } catch (IOException e) {
                System.out.println("Unable to handle connection:  "
                    + e.getMessage());
                System.exit(1);
            }
	} else { 
            try {
            	isa = new InetSocketAddress(InetAddress.getByName(serverAddress), serverPort);
	    } catch (UnknownHostException e) {
            	System.out.println("UnknownHost: " + serverAddress);
            	System.exit(1);
            }

	    socket = new Socket();

	    boolean firstTime = true;

	    System.out.println("Connecting to " + isa);

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
	} 

	new MicrophoneReader(socket);
	new SpeakerWriter(socket);
    }

    /*
     * Read data from the microphone and send the data to the socket
     */
    class MicrophoneReader extends Thread {
	BufferedOutputStream output;

	Microphone microphone;

	public MicrophoneReader(Socket socket) {
	    try {
	        output = new BufferedOutputStream(socket.getOutputStream());
	    } catch (IOException e) {
		System.out.println("Unable to get socket output stream:  " + e.getMessage());
		System.exit(1);
	    }

	    if (noAudio == false) {
	        microphone = AudioCommon.getMicrophone();
	    }

	    start();
	}

        public void run() {
	    boolean firstTime = true;

	    byte[] microphoneData;

	    if (microphone != null) {
	        microphone.flush();

	        microphoneData = new byte[AudioCommon.getMicrophoneBufferSize()];
	    } else {
		microphoneData = new byte[320];
	    }

	    System.out.println("Microphone data length " + microphoneData.length);

            while (true) {
	        /*
	         * Read linear data from the microphone
	         */ 
	        if (firstTime) {
		    System.out.println("reading mic...");
	        }

		if (microphone != null) {
	            try {
	                microphone.read(microphoneData, 0, microphoneData.length);
	            } catch (IOException e) {
		        System.out.println("Unable to read microphone: "
		            + e.getMessage());
		        System.exit(1);
	            }
		} else {
		    try {
			Thread.sleep(20);
		    } catch (InterruptedException e) {
		    }
		}

	        if (firstTime) {
		    System.out.println("back from reading mic...");
	        }

	        try {
		    if (firstTime) {
			System.out.println("writing tcp socket...");
		    }

	 	    Util.dump("mic data", microphoneData, 0, 16);

	            output.write(microphoneData);   
		    output.flush();

		    if (firstTime) {
			System.out.println("back from writing tcp socket...");
		    }
	        } catch (IOException e) {
		    System.out.println(
			"Unable to write to socket! " + e.getMessage());
		    System.exit(1);
	        }

	        firstTime = false;
            }
        }
    }

    /*
     * Read data from the network and write it to the speaker.
     */
    class SpeakerWriter extends Thread {
	BufferedInputStream input;

	Speaker speaker;

	public SpeakerWriter(Socket socket) {
	    try {
	        input = new BufferedInputStream(socket.getInputStream());
	    } catch (IOException e) {
		System.out.println("Unable to get socket input stream:  " + e.getMessage());
		System.exit(1);
	    }

	    if (noAudio == false) {
	        speaker = AudioCommon.getSpeaker();
	    }

	    start();
	}

	public void run() {
            byte[] receiveBuffer = new byte[10000];

	    boolean firstTime = true;

	    if (speaker != null) {
	        speaker.flush();
	    }

            while (true) {
	        int n = 0;

	        try {
		    if (firstTime) {
			System.out.println("About to read TCP data...");
		    }

	            n = input.read(receiveBuffer, 0, receiveBuffer.length);

		    if (firstTime) {
			System.out.println("Back from reading TCP data");
		    }

		    Util.dump("speaker data ", receiveBuffer, 0, 16);
	        } catch (IOException e) {
		    System.out.println(
			"Unable to read socket! " + e.getMessage());
		    System.exit(1);
	        }

	        if (n < 0) {
		    System.out.println("Unable to read socket, no data!");
		    System.exit(1);
	        }

	        if (n == 0) {
		    System.out.println("received 0 bytes!");
		    continue;
	        }

		firstTime = false;

		if (speaker == null) {
		    continue;
		}

                int writeSize = Math.min(speaker.available(), n);

                if (speaker.available() < n) {
                    speaker.flush(); 

                    int available = speaker.available();

                    writeSize = Math.min(available, n);

                    if (available < n) {
                        System.out.println("no space available after flush!");
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

}
