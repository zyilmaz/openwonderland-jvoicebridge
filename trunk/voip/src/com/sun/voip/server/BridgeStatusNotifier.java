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

package com.sun.voip.server;

import java.io.DataOutputStream;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import com.sun.voip.Logger;

public class BridgeStatusNotifier extends Thread {
	InetSocketAddress isa;

    public BridgeStatusNotifier(String server) {
        String[] tokens = server.split(":");

        if (tokens.length != 2) {
            Logger.println("Missing server port for listener: " + server);
 	    return;
	}

	try {
	    int port = Integer.parseInt(tokens[1]);

	    isa = new InetSocketAddress(InetAddress.getByName(tokens[0]), port);
	} catch (NumberFormatException e) {
	    Logger.println("Invalid server port for listener:  " + server);
	} catch (UnknownHostException e) {
	    Logger.println("Invalid listener host:  " + e.getMessage());
	}

	start();
    }

    private Socket socket;

    private boolean done;

    public void done() {
	done = true;

	try {
	    socket.close();
	} catch (IOException e) {
	    Logger.println("Unable to close socket:  " + e.getMessage());
	}
    }

    private static boolean suspendPing;
    private static int suspendSeconds = 0;

    public static void suspendPing(int suspendSeconds) {
	BridgeStatusNotifier.suspendSeconds = suspendSeconds;
	suspendPing = true;
    }

    public static void resumePing() {
	suspendPing = false;
    }

    public void run() {
	boolean firstTime = true;

        while (!done) {
	    try {
	        socket = new Socket();
	        socket.connect(isa);
	    } catch (IOException e) {
	        if (firstTime) {
		    firstTime = false;
		    Logger.println("Unable to notify " + isa
		        + " that a bridge is online:  "
			+ e.getMessage());
		    Logger.println("Retrying once a second...");
	        }

	        try {
		    Thread.sleep(1000);
	        } catch (InterruptedException ee) {
	        }

	        continue;
	    }

	    try {
	        DataOutputStream output = new DataOutputStream(
		    socket.getOutputStream());
	
	        String s = "BridgeUP:" + Bridge.getLocalHost().getHostAddress() 
		    + ":" + SipServer.getSipAddress().getPort() + ":"
		    + Bridge.getControlPort() + "\r\n";

		while (!done) {
		    if (suspendPing == false) {
	                output.write(s.getBytes());
		
		        if (firstTime) {
	                    Logger.println("Successfully notified " + isa
		                + " that this bridge is up");
		        }
		    }

	            try {
		        Thread.sleep(1000);
	            } catch (InterruptedException ee) {
	            }

		    if (suspendSeconds != 0) {
			suspendSeconds--;

			if (suspendSeconds == 0) {
			    resumePing();
			}
		    }

		    firstTime = false;
	            continue;
		}
	    } catch (IOException e) {
		if (socket.isClosed() == false) {
	            Logger.println("Unable to notify " + isa
			+ " that a bridge is online:  " 
			+ e.getMessage());

		    /*
		     * Go back to the top and try to reconnect.
		     */
		    firstTime = false;
		    continue;
		} 

		/*
		 * The remote side disconnected.
		 */
		Logger.println(isa + " has disconnected");
		done = true;
	    }
        }

	Logger.println("Bridge status notifier to " + isa + " done");
    }

}
