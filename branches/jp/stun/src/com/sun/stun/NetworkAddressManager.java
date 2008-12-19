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

package com.sun.stun;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.io.IOException;

import java.lang.reflect.Method;

import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.stun.StunClient;

public class NetworkAddressManager {

    private static final Logger logger =
        Logger.getLogger(NetworkAddressManager.class.getName());

    private NetworkAddressManager() {
    }

    public static void setLogLevel(Level newLevel) {
	logger.setLevel(newLevel);
    }

    private static Method isUp = null;
    private static Method isLoopback = null;
    private static Class networkInterfaceClass;

    private static int timeout = 100;

    static {
	try {
	    networkInterfaceClass = Class.forName("java.net.NetworkInterface");

	    isUp = networkInterfaceClass.getDeclaredMethod("isUp", 
		new Class[]{NetworkInterface.class});
	    isLoopback = networkInterfaceClass.getDeclaredMethod(
		"isLoopback", new Class[]{NetworkInterface.class});
	} catch (Exception e) {
	}

        String s = System.getProperty(
            "com.sun.mc.stun.NETWORK_INTERFACE_TIMEOUT", "100");

        try {
            timeout = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            logger.info("Invalid timeout value: " + s);
        }

    }

    public static InetAddress getPrivateLocalAddress(String server, int port, 
	    int timeout) throws UnknownHostException {

	if (server == null || port == 0) {
	    throw new UnknownHostException("Invalid server or port:  " 
		+ server + ":" + port);
	}

        /*
         * Try to connect to the TCP server to get our private local address
         * Once connected, the socket will be bound to the correct local address.
         */
	InetAddress ia;

	try {
	    ia = InetAddress.getByName(server);
	} catch (UnknownHostException e) {
	    throw new UnknownHostException("Can't resolve hostname " 
		+ server + ": " + e.getMessage());
	}

	InetSocketAddress isa = new InetSocketAddress(ia, port);

	Socket socket = new Socket();

	try {
	    socket.connect(isa, timeout);
	} catch (IOException e) {
	    throw new UnknownHostException("Unable to connect to " + isa
		+ " " + e.getMessage());
	}

	InetAddress address = socket.getLocalAddress();

	try {
	    socket.close();
	} catch (IOException e) {
	    logger.warning("Unable to close socket to " + server + ":" + port
		+ " " + e.getMessage());
	}

	logger.info("Using local address " 
	    + address.getHostAddress()
	    + " as determined by connecting to TCP server " + server
	    + ":" + port);

	return address;
    }
	
    public static InetAddress getPrivateLocalAddress(String ifName) throws UnknownHostException {
	NetworkInterface iFace;

	try {
	    iFace = NetworkInterface.getByName(ifName);
	} catch (SocketException e) {
	    throw new UnknownHostException("Unknown interface " + ifName + ": " + e.getMessage());
	}
 
	InetAddress ia = getAddress(iFace);

	if (ia != null) {
	    return ia;
	}

	throw new UnknownHostException("Unusable interface " + ifName);
    }

    public static InetAddress getPrivateLocalAddress() throws UnknownHostException {
	InetAddress ia = findLocalAddress();

	logger.info("Using local address " 
	    + ia.getHostAddress() + " selected from the list of interfaces");

	return ia;
    }

    private static InetAddress findLocalAddress() throws UnknownHostException {
	/*
	 * Look for addresses at each interface and pick one that's usable.
	 */
        try {
            Enumeration localIfaces = NetworkInterface.getNetworkInterfaces();

	    InetAddress ia = null;

	    while (localIfaces.hasMoreElements()) {
                NetworkInterface iFace = (NetworkInterface) 
		    localIfaces.nextElement();

	        ia = getAddress(iFace);

		if (ia != null) {
		    break;
		}
	    }

	    if (ia != null) {
		return ia;
	    }

	    /*
	     * We didn't find anything we liked so try the default.
	     */
            InetAddress address = InetAddress.getLocalHost();

	    if (address.toString().substring(0,3).equals("/0.")) {
		String s = "Local address " + address + " is not usable!";

		logger.fine(s);
		throw new UnknownHostException(s);
	    }

	    logger.fine("private local host is " + address);
	    return address;
        } catch (Exception e) {
	    throw new UnknownHostException("Failed to get local host! " 
		+ e.getMessage());
        }
    }

    private static InetAddress getAddress(NetworkInterface iFace) {
	if (isUsable(iFace) == false) {
	    return null;
	}

	Enumeration addresses = iFace.getInetAddresses();

	logger.fine("Interface name: " + iFace.getName());

	InetAddress possibleAddress = null;

        while (addresses.hasMoreElements()) {
            InetAddress address = (InetAddress) addresses.nextElement();

	    logger.fine("Address: " + address);

            if (address instanceof Inet4Address == false) {
		logger.fine("Skipping non-IPV4 address " + address);
		continue;
	    }

	    if (address.isAnyLocalAddress() || 
		isWindowsAutoConfiguredIPv4Address(address) ||
		address.toString().substring(0,3).equals("/0.")) {

                logger.fine("Skipping " + address);
                continue;
            }

	    if (iFace.getName().startsWith("cipsec") && 
		isReachable(address, timeout)) {

		logger.info("Using cipsec " + address);
		return address;
	    }

	    if (iFace.getName().startsWith("ip.tun") &&
                isReachable(address, timeout)) {

		logger.info("Using ip.tun " + address);
                return address;
            }

	    if (address.isLoopbackAddress() == false &&
                address.toString().substring(0,3).equals("/0.") == false) {

	        if (possibleAddress == null || possibleAddress.isLinkLocalAddress()) {
		    logger.fine("Setting possible address to " + possibleAddress);
		    possibleAddress = address;
		}
		continue;
            } 
	}

	return possibleAddress;
    }

    private static boolean isUsable(NetworkInterface iFace) {
	try {
	    if (isUp != null) {
                if ((Boolean) isUp.invoke(networkInterfaceClass, new Object[]{iFace}) == false) {
                    return false;
                }
	    }

            if (isLoopback != null) {
                if ((Boolean) isLoopback.invoke(networkInterfaceClass, new Object[]{iFace}) == true) {
                    return false;
	        }
            }
	} catch (Exception e) {
	}

	return true;  // we can't tell if it's usable or not.
    }

    private static boolean isLinkLocalIPv4Address(InetAddress addr) {
        byte address[] = addr.getAddress();

        if ((address[0] & 0xff) == 10) {
            return true;
	}

        if ((address[0] & 0xff) == 172
            && (address[1] & 0xff) >= 16 && address[1] <= 31) {
            return true;
	}

        if ((address[0] & 0xff) == 192
            && (address[1] & 0xff) == 168) {
            return true;
	}

        return false;
    }

    private static boolean isWindowsAutoConfiguredIPv4Address(InetAddress addr) {
        return (addr.getAddress()[0] & 0xff) == 169
            && (addr.getAddress()[1] & 0xff) == 254;
    }

    private static boolean isReachable(InetAddress address, int timeout) {
	try {
            if (address.isReachable(timeout) == false) {
                return false;
            }
	} catch (IOException e) {
	    logger.info("can't reach " + address + " " + e.getMessage());
	    return false;
	}

        return true;
    }   

    /*
     * Ask stunServer to resolve socket.getAddress().
     */
    public static InetSocketAddress getPublicAddressFor(
	    InetSocketAddress stunServer, DatagramSocket socket) 
	    throws IOException {

	StunClient stunClient = new StunClient(stunServer, socket);

	return stunClient.getMappedAddress();
    }
	    
    public static void main(String[] args) {
	if (args.length != 4) {
	    System.out.println("Usage:  java com.sun.stun.NetworkAddressManager <stun server> "
		+ "<stun port> <private address> <private port>");
	    System.exit(1);
	}

	NetworkAddressManager.setLogLevel(Level.FINEST);

	int stunPort = Integer.parseInt(args[1]);

	InetSocketAddress isa = new InetSocketAddress(args[0], stunPort);

	System.out.println("stun server " + isa);

	InetAddress ia = null;

	try {
	    ia = InetAddress.getByName(args[2]);
	} catch (UnknownHostException e) {
	    System.out.println(e.getMessage());
	    System.exit(1);
	}

	int privatePort = Integer.parseInt(args[3]);

	DatagramSocket socket = null;

	try {
	    socket = new DatagramSocket(privatePort, ia);
	} catch (SocketException e) {
	    System.out.println(e.getMessage());
	    System.exit(1);
	}

	try {
	    System.out.println("public address " 
	        + NetworkAddressManager.getPublicAddressFor(isa, socket));
	} catch (IOException e) {
	    System.out.println(e.getMessage());
	    System.exit(1);
	}
    }

}
