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

import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Migrate a call to another phone.
 */
public class Migrate {
    public Migrate() {
    }
        
    public static void main(String[] args) {
        new Migrate().migrate(args);
    }

    private void migrate(String[] args) {
	if (args.length != 2) {
	    System.err.println(
		"Usage:  java Migrate <callId> <new Phone Number>");

	    System.exit(1);
	}
	
	String serverName = 
	    System.getProperty("com.sun.voip.server.BRIDGE_SERVER_NAME",
	        "centaur.east.sun.com");

	int serverPort = Integer.getInteger(
	    "com.sun.voip.server.Bridge.PORT", 6666).intValue();

	try {
	    Socket socket = new Socket(serverName, serverPort);
	    DataInputStream input =
                new DataInputStream(socket.getInputStream());
	    OutputStream output = socket.getOutputStream();

	    String request = "ci\n";
	    output.write(request.getBytes());

	    String response = getResponse(input);

	    int i;

	    if ((i = response.indexOf(args[0])) < 0) {
		System.err.println(args[0] + " is not in a conference");
		System.exit(1);
	    }

	    /*
	     * Find the conference this call is in
	     * We expect the response to look like this:
	     *
	     *     Conference Id:  xxx
	     *          jp@20315
	     *	        ...
	     *     Conference Id:  yyy
	     *          will@20433
	     *          ...
	     *	   \n\n
	     */
	    int n;
	
	    String conferenceId = null;

	    while ((n = response.indexOf("Conference Id:")) < i) {
		if (n < 0) {
		    break;
		}

		response = response.substring(n + 16);

		int end;

		if ((end = response.indexOf("\n")) < 0) {
		    System.err.println("bad response from conference bridge");
		    System.exit(1);
		}

	    	conferenceId = response.substring(0, end);

		System.out.println("found " + args[0] + " in conference " 
		    + conferenceId);
	    }

	    if (conferenceId == null) {
		System.err.println("can't find 'Conference Id'");
		System.exit(1);
	    }

	    request = "pn=" + args[1] + "\nc=" + conferenceId + "\n\n";
	    output.write(request.getBytes());

	    if (waitForResponse(input, " 200 ") == null) {
		System.err.println("No answer at " + args[1]);
		return;
	    }

	    request = "cancel=" + args[0] + "\ndetach\n\n";
	    output.write(request.getBytes());
	} catch (IOException e) {
	    System.err.println(
		"Can't create socket " + serverName + ":" + serverPort);
	    return;
	}
    }

    private String getResponse(DataInputStream input) {
	String response = "";

	byte[] buf = new byte[1024];

        try {
	    do {
                int len;

                len = input.read(buf, 0, buf.length);
                response += new String(buf, 0, len);
	    } while (response.indexOf("\n\n") < 0);
        } catch (IOException e) {
            System.out.println("IOException " + e.getMessage());
        }
	return response;
    }

    private String waitForResponse(DataInputStream input, 
	    String desiredResponse) {

	String response = null;

        try {
            byte[] buf = new byte[1024];

            int len;

            while ((len = input.read(buf, 0, buf.length)) > 0) {
                String s = new String(buf, 0, len);

                if (s.indexOf(desiredResponse) >= 0) {
                    response = s;
		    break;
                }
            }
        } catch (IOException e) {
            System.out.println("IOException " + e.getMessage());
        }
	return response;
    }

}
