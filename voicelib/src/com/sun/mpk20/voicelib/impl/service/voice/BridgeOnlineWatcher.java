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

package com.sun.mpk20.voicelib.impl.service.voice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.logging.Level;
import java.util.logging.Logger;

public class BridgeOnlineWatcher extends Thread {

    private static final Logger logger = Logger.getLogger(
	    BridgeOnlineWatcher.class.getName());

    private static final int BRIDGE_ONLINE_WATCHER_PORT = 6668;

    private int bridgeOnlineWatcherPort = BRIDGE_ONLINE_WATCHER_PORT;

    private VoiceServiceImpl voiceService;

    public BridgeOnlineWatcher(VoiceServiceImpl voiceService) {
	this.voiceService = voiceService;

	String s = System.getProperty(
	    "com.sun.voip.server.BRIDGE_ONLINE_WATCHER_PORT");

	if (s != null) {
	    try {
		bridgeOnlineWatcherPort = Integer.parseInt(s);
	    } catch (NumberFormatException e) {
		logger.info("Invalid bridge online watcher port: " + s
		    + ".  Defaulting to " + bridgeOnlineWatcherPort);
	    }
	}

	start();
    }

    public void run() {
	ServerSocket serverSocket;

	try {
	    serverSocket = new ServerSocket(bridgeOnlineWatcherPort);
	} catch (IOException e) {
	    logger.info("Unable to create server socket:  " 
		+ e.getMessage());
	    return;
	}

	while (true) {
	    Socket socket;

	    try {
		socket = serverSocket.accept(); // wait for a connection
	    } catch (IOException e) {
		logger.info("Unable to accept connection: " 
		    + e.getMessage());
		continue;
	    }

	    logger.fine("New connection accepted from " 
	        + socket.getRemoteSocketAddress());

	    try {
		new BridgeOnlineReader(socket);
	    } catch (IOException e) {
		logger.info("Unable to start BridgeOnlineReader for "
	            + socket.getRemoteSocketAddress() + e.getMessage());
	    } 
	}
    }

    class BridgeOnlineReader extends Thread {

        private Socket socket;
        private BufferedReader bufferedReader;

        public BridgeOnlineReader(Socket socket) throws IOException {
	    this.socket = socket;

       	    bufferedReader = new BufferedReader(
	        new InputStreamReader(socket.getInputStream()));

	    start();
	}

	public void run() {
	    InetSocketAddress isa = (InetSocketAddress) 
	        socket.getRemoteSocketAddress();

	    String bridgeAddress = null;

	    try {
	        while (true) {
	            bridgeAddress = bufferedReader.readLine();

		    if (bridgeAddress == null) {
			logger.info("Bridge " + isa + " has disconnected");
			break;
		    }

	            String s = "BridgeUP:";

	            int ix = bridgeAddress.indexOf(s);

	            if (ix >= 0) {
		        bridgeAddress = bridgeAddress.substring(s.length());

			/*
			 * Don't we have to notify all the voice services?
			 * XXX
			 */
	                voiceService.connect(bridgeAddress);
	            } else {
		        logger.info("Unexpected data:  " + bridgeAddress);
	            }
	        }
	    } catch (IOException e) {
	        logger.info("Unable to read data from " + isa
	    	    + " " + e.getMessage());		    
	    }
	}
    }

}
