package com.sun.mc.softphone.gui;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import com.sun.voip.Logger;

import com.sun.mc.softphone.SipCommunicator;

public class UDPTester extends Thread {

    private UDPTesterListener listener;
    private int seconds;
    private DatagramSocket socket;

    public UDPTester(UDPTesterListener listener, int port, int seconds) throws IOException {
	this.listener = listener;
	this.seconds = seconds;

        socket = new DatagramSocket(port, SipCommunicator.getPrivateLocalAddress());
	socket.setSoTimeout(5000);

	start();
    }

    private boolean done;

    public synchronized void done() {
	done = true;

	close();
    }

    private void close() {
	if (socket != null) {
	    socket.close();
	    socket = null;
	}
    }

    public void run() {
        byte[] buf = new byte[100000];

        DatagramPacket packet = new DatagramPacket(buf, buf.length);

	Logger.println("Ready to receive data on " 
		+ socket.getLocalAddress() + ":" + socket.getLocalPort());

	int expectedPacketNumber = 0;

	long endTime = System.currentTimeMillis() + seconds * 1000 ;

	while (!done && System.currentTimeMillis() < endTime) {
            try {
                socket.receive(packet);

		int packetNumber = (int) (((buf[0] << 24) & 0xff000000) |
                        ((buf[1] << 16) & 0xff0000) |
                        ((buf[2] << 8) & 0xff00) | (buf[3] & 0xff));

                println("Received packet " + packetNumber
		    + " from " + packet.getAddress().toString() + " length " 
		    + packet.getLength());

		if (packetNumber != expectedPacketNumber) {
		    println("Expected packet " 
			+ expectedPacketNumber + " but got " + packetNumber);
		}

		expectedPacketNumber = packetNumber + 1;

		try {
		    socket.send(packet);
		} catch (IOException e) {
		    println("Unable to send packet back!");
		}
	    } catch (SocketTimeoutException e) {
		println("Timeout, no data received!");
            } catch (IOException e) {
		if (!done) {
                   println("received failed " + e.getMessage());
		}
            }
	}

	close();
    }

    private void println(String s) {
	Logger.println(s);

	if (listener != null) {
	    listener.status(s);
	}
    }

}
