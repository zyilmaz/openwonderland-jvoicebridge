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
 
package com.sun.voip;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import com.sun.voip.Logger;

public class NetworkTester extends Thread {
    public static final int NETWORK_TESTER_PORT = 7777;

    private DatagramSocket socket;

    public static void main(String[] args) {
        int port = 8888;

        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
	}

	try {
	    new NetworkTester(port);
	} catch (IOException e) {
	    Logger.println(e.getMessage());
	}
    }

    public NetworkTester() throws IOException {
	this(0);
    }

    public NetworkTester(int port) throws IOException {
	if (port == 0) {
	    port = NETWORK_TESTER_PORT;
	}

        try {
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(1024*256*2);

	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println(
		    "NetworkTester:  Ready to receive data on UDP port " 
	            + port);
	    }
        } catch (SocketException e) {
	    throw new IOException("NetworkTester:  socket exception " 
		+ e.getMessage());
        }

	start();
    }

    private boolean done;

    public void done() {
	done = true;

	socket.close();
    }

    public void run() {
        byte[] buf = new byte[3540];

        DatagramPacket p = new DatagramPacket(buf, buf.length);

        while (!done) {
            try {
                socket.receive(p);

		int n = (buf[0] << 24) | ((buf[1] << 16) & 0xff0000) |
                    ((buf[2] << 8) & 0xff00) | (buf[3] & 0xff);

		if (n <= 1 || Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Got packet " + n + " from "
		        + p.getAddress().toString() 
			+ " length " + p.getLength());
		}

		socket.send(p);		// echo packet
            } catch (IOException e) {
		if (!done) {
                    Logger.println("received failed " + e.getMessage());
		}
		break;
            }
        }
    }

}
