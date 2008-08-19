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

package com.sun.voip.client;

import com.sun.voip.CallParticipant;
import com.sun.voip.CallEvent;
import com.sun.voip.CallEventListener;
import com.sun.voip.CallState;

import java.io.*;
import java.util.*;
import java.net.*;

/**
 * Connect to the bridge for an existing call
 */
public class Connect extends Thread implements CallEventListener {
    private String userName;
    private String callId;
     
    public static void main(String[] args) {
        new Connect().connect(args);
    }

    private void connect(String[] args) {
	if (args.length < 1) {
	    System.err.println("Usage:  java Connect <user name>");
	    System.exit(1);
	}

	userName = args[0];

	start();

	synchronized(this) {
	    try {
	        wait(2000);   // wait until userName is found
	    } catch (InterruptedException e) {
	    }
	}

	if (callId == null) {
	    System.out.println("There is no call for '" + userName + "'");
	    System.exit(1);
	}

	BufferedReader bufferedReader =
            new BufferedReader(new InputStreamReader(System.in));

	System.out.println("Command     Meaning");
	System.out.println("-------     -------");
	System.out.println("  L         Left ear only");
	System.out.println("  R         Right ear only");
	System.out.println("  LR        Move from left to right");
	System.out.println("  RL        Move from right to left");
	System.out.println("  M         Mono");
	System.out.println("  S         Stereo");
	System.out.println("  V=<float> Set Volume level and switch to stereo");
	System.out.println("  -<cmd>    Send <cmd> to bridge socket");
	System.out.println("");

	String s = null;

	while (true) {
            try {
	        s = bufferedReader.readLine();
	    } catch (IOException e) {
                System.out.println("Can't read socket!");
                System.exit(1);
            }

	    if (s == null) {
		System.exit(0);
	    }

	    if (s.length() == 0) {
		continue;
	    }

	    if (s.charAt(0) == '-') {
		writeSocket(s.substring(1));
	    } else {
	        processCommand(s);
	    }
	}
    }

    private float volumeLevel = 1.0F;

    private void processCommand(String s) {
	String request = null;

	String cmd = "iv=";

	if (s.charAt(0) == 'o' || s.charAt(0) == 'O') {
	    if (s.length() < 2) {
		System.out.println("Missing parameters '" + s + "'");
		return;
	    }
	    s = s.substring(1);

	    cmd = "ov=";
	}

	if (s.equalsIgnoreCase("L")) {
	    /*
	     * Left ear only
	     */
	    request = cmd + volumeLevel + ":0:" + volumeLevel + ":0";
	    System.out.println("Left");
	} else if (s.equalsIgnoreCase("R")) {
	    /*
	     * Right ear only
	     */
	    request = cmd + "0:" + volumeLevel + ":0:" + volumeLevel;
	    System.out.println("Right");
	} else if (s.equalsIgnoreCase("LR")) {
	    /*
	     * Move from left to right
	     */
	    leftToRight(cmd);
	    return;
	} else if (s.equalsIgnoreCase("RL")) {
	    /*
	     * Move from right to left
	     */
	    rightToLeft(cmd);
	    return;
	} else if (s.equalsIgnoreCase("M")) {
	    /*
	     * Mono
	     */
	    request = cmd + volumeLevel + ":" + volumeLevel
		+ ":" + volumeLevel + ":" + volumeLevel;
	    System.out.println("Mono");
	} else if (s.equalsIgnoreCase("S")) {
	    /*
	     * Stereo
	     */
	    request = cmd + volumeLevel + ":0:0:" + volumeLevel;
	    System.out.println("Stereo");
	} else if (s.indexOf("V=") == 0 || s.indexOf("v=") == 0) {
	    /*
	     * Set volume
	     */
	    if (s.length() < 3) {
		System.out.println("Invalid volume command '" + s + "'");
		return;
	    }

	    try {
		volumeLevel = Float.parseFloat(s.substring(2));
	    } catch (NumberFormatException e) {
		System.out.println("Invalid volume specified '" + s + "'");
		return;
	    }

	    request = cmd + volumeLevel + ":0:0:" + volumeLevel;
	    System.out.println("Setting volume to " + volumeLevel 
		+ " and setting to stereo");
	} else {
	    System.out.println("Invalid command '" + s + "'");
	    return;
	}

	request += ":" + callId;
	writeSocket(request);
    }

    private void leftToRight(String cmd) {
	String request;

	for (int i = 0; i < 100; i++) {
	    float left = (float)((100.0F - i) / 100);
	    float right = (float)(i / 100F);

	    request = cmd + (left * volumeLevel) + ":" 
		+ (right * volumeLevel) + ":" + (left * volumeLevel) + ":"
		+ (right * volumeLevel) + ":" + callId;

	    writeSocket(request);

	    try {
		Thread.sleep(20);
	    } catch (InterruptedException e) {
	    }
	}
    }

    private void rightToLeft(String cmd) {
	String request;

        for (int i = 0; i < 100; i++) {
            float left = (float)(i / 100F);
            float right = (float)((100.0F - i) / 100);

            request = cmd + (left * volumeLevel) + ":" 
                + (right * volumeLevel) + ":" + (left * volumeLevel) + ":"
                + (right * volumeLevel + ":" + callId);

            writeSocket(request);

            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
            }   
        }
    }

    public void callEventNotification(CallEvent event) {
	if (event.equals(CallEvent.STATE_CHANGED) &&
		event.getCallState().equals(CallState.ENDED)) {

	    System.exit(0);
	}

	System.out.println(event.toString());
    }

    private OutputStream output;
    private Socket socket;

    private void writeSocket(String request) {
	request += "\n";

	try {
	    output.write(request.getBytes());
	} catch (IOException e) {
	    System.out.println("Can't write to socket!");
	    System.exit(1);
	}
    }


    public void run() {
	String serverName = 
	    System.getProperty("com.sun.voip.server.BRIDGE_SERVER_NAME",
	        "escher.east.sun.com");

	int serverPort = Integer.getInteger(
	    "com.sun.voip.server.Bridge.PORT", 6666).intValue();

	System.out.println("Connecting to the remote host " + serverName
	    + ":" + serverPort);

	//
	// Open a tcp connection to the remote host at the well-known port.
	//
	BufferedReader bufferedReader = null;

	try {
	    socket = new Socket(serverName, serverPort);
            output = socket.getOutputStream();

            bufferedReader = new BufferedReader(
		new InputStreamReader(socket.getInputStream()));

	    writeSocket("ci");
	} catch (IOException e) {
	    System.err.println("can't connect to " + serverName 
		+ "." + serverPort + " " + e.getMessage());

	    System.exit(1);
	}

	while (true) {
	    String s = null;

	    try {
                s = bufferedReader.readLine();
	    } catch (IOException e) {
	 	System.err.println("can't read socket! " + e.getMessage());
		System.exit(1);
	    }

	    if (s == null) {
	 	System.err.println("can't read socket!");
		System.exit(1);
	    }

            if (callId != null) {
	        callEventNotification(new CallEvent(s));
	    } else {
	        int ix = 0;

		if ((ix = s.indexOf("::")) < 0) {
		    continue;
		}

		if (s.lastIndexOf(userName) >= 0) {
                    callId = s.substring(0, ix);

		    callId = callId.replaceAll("[\\s]", "");
                    System.out.println("Call Id is " + callId);

	 	    synchronized(this) {
	    	        notifyAll();
                    }
		}
	    }
        }
    }

}
