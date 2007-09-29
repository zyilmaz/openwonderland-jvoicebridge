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

import java.io.*;
import java.util.*;
import java.net.*;

/**
 * Call somebody...
 */
public class Hangup {
    public Hangup() {
    }
        
    public static void main(String[] args) {
        new Hangup().hangup(args);
    }

    private void hangup(String[] args) {
	String serverName = 
	    System.getProperty("com.sun.voip.server.BRIDGE_SERVER_NAME",
	        "centaur.east.sun.com");

	int serverPort = Integer.getInteger(
	    "com.sun.awc.voip.server.Bridge.PORT", 6666).intValue();

	String request = "cancel=" + args[0] + "\ndetach\n\n";

	System.err.println(request);

	try {
	    Socket socket = new Socket(serverName, serverPort);
	    socket.getOutputStream().write(request.getBytes());
	} catch (Exception e) {
	    System.err.println(
		"Can't create socket " + serverName + ":" + serverPort);
	    return;
	}
    }

}
