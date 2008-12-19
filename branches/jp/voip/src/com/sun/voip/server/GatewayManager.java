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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;

import com.sun.voip.Logger;

public class GatewayManager {

    private static ArrayList<InetSocketAddress> voIPGateways = new ArrayList();
    private static ArrayList<String> voIPGatewayLoginInfo = new ArrayList();

    private GatewayManager() {
    }

    public static void getGatewayInfo() {
        /*
         * get IPs of the voip gateways
         */
        String gateways = System.getProperty(
	    "com.sun.voip.server.VoIPGateways", "");

        /*
         * parse voIPGateways and create the list with IP addresses
         * of VoIP gateways
         */
        if (setVoIPGateways(gateways) == false) {
            Logger.error("Invalid VoIP gateways " + gateways);
        }

	if (voIPGateways.size() == 0) {
	    Logger.println("There are no VoIP gateways.  "
		+ "You cannot make calls to the phone system.");
            Logger.println("If you want to use the phone system "
		+ "you can specify VoIP gateways with "
		+ "-Dcom.sun.voip.server.VoIPGateways.");
	} else {
            Logger.println("VoIP gateways: " + getAllVoIPGateways());
            Logger.println("");
	}
    }

    public static void registerGateways() {
	for (int i = 0; i < voIPGatewayLoginInfo.size(); i++) {
	    String loginInfo = voIPGatewayLoginInfo.get(i);

	    if (loginInfo.length() > 0) {
		new RegisterProcessing(voIPGateways.get(i), loginInfo);
	    }
	}
    }

    public static ArrayList<InetSocketAddress> getVoIPGateways() {
        return voIPGateways;
    }

    /**
     * Set the IP address of the VoIPGateway.
     * @param ip String with dotted IP address
     */
    public static boolean setVoIPGateways(String gateways) {
	ArrayList<InetSocketAddress>voIPGateways = new ArrayList();
	ArrayList<String> voIPGatewayLoginInfo = new ArrayList();

        gateways = gateways.replaceAll("\\s", "");

	if (gateways.length() == 0) {
	    return true;
	}

	String[] g = gateways.split(",");

        for (int i = 0; i < g.length; i++) {
	    String[] gatewayInfo = g[i].split(";");

	    InetSocketAddress gateway = getVoIPGateway(gatewayInfo[0]);

	    if (gateway == null) {
		Logger.println("Invalid gateway ignored:  " + gatewayInfo);
		continue;
	    }

	    voIPGateways.add(gateway);

	    if (gatewayInfo.length > 1) {
		voIPGatewayLoginInfo.add(gatewayInfo[1]);    
		new RegisterProcessing(gateway, gatewayInfo[1]);
	    } else {
		voIPGatewayLoginInfo.add("");
	    }
	}

	if (voIPGateways.size() > 0) {
	    GatewayManager.voIPGateways = voIPGateways;
	    GatewayManager.voIPGatewayLoginInfo = voIPGatewayLoginInfo;
	    return true;
	}

	return false;
    }

    public static InetSocketAddress getVoIPGateway(String gatewayInfo) {
	if (gatewayInfo == null || gatewayInfo.length() == 0) {
	    return null;
	}

	InetSocketAddress isa;

        try {
            /*
             * Make sure address is valid
             */
	    int port = SipServer.SIP_PORT;

	    String[] tokens = gatewayInfo.split(":");

            InetAddress ia = InetAddress.getByName(tokens[0]);

	    if (tokens.length > 1) {
		String[] p = tokens[1].split(";");

		try {
		    port = Integer.parseInt(p[0]);
		} catch (NumberFormatException e) {
		    Logger.println("Invalid SIP port " + gatewayInfo + e.getMessage());
		    return null;
		}
	    }

	    isa = new InetSocketAddress(ia, port);
        } catch (UnknownHostException e) {
	    Logger.println("Unknwown host " + gatewayInfo);
            return null;
        }
	
	return isa;
    }

    /**
     * Get String with all VoIP Gateways.
     */
    public static String getAllVoIPGateways() {
        String s = "";

        for (int i = 0; i < voIPGateways.size(); i++) {
	    InetSocketAddress isa = voIPGateways.get(i);
	   
	    s += isa.getAddress().getHostAddress();
	    s += ":" + isa.getPort();

	    String gatewayLoginInfo = voIPGatewayLoginInfo.get(i);

	    if (gatewayLoginInfo.length() > 0) {
	        s += ";" + voIPGatewayLoginInfo.get(i);
	    }

            if (i < voIPGateways.size() - 1) {
                s += ", ";
            }
        }

        return s;
    }

}
