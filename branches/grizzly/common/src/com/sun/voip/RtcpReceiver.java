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

public class RtcpReceiver extends Thread {
    private boolean done;
    private DatagramSocket rtcpSocket;
    private byte[] rtcpData;

    /*
     * The rtcpSocket needs to be passed in here because
     * it is obtained the same the the rtpSocket is obtained
     * and ports for the two socket must be an even/odd pair.
     */
    public RtcpReceiver(DatagramSocket rtcpSocket) {
	this.rtcpSocket = rtcpSocket;

	setName("RtcpReceiver-" + rtcpSocket.getLocalPort());
	setPriority(Thread.NORM_PRIORITY);
        start();
    }

    public void end() {
	done = true;
	rtcpSocket.close();
    }

    long timeLastReportReceived;

    /*
     * Receive both sender and receiver reports.
     */
    public void run() {
        rtcpData = new byte[1500];

        DatagramPacket packet = new DatagramPacket(rtcpData, rtcpData.length);

        while (!done) {
            try {
                rtcpSocket.receive(packet);              // receive RTCP data

		timeLastReportReceived = System.currentTimeMillis();

		byte[] data = packet.getData();

		if ((data[1] & 0xff) == 200) {
	            new RtcpSenderPacket(packet).printReport();
		} else if ((data[1] & 0xff) == 201) {
	    	    new RtcpReceiverPacket(packet).printReport();
		} else {
	    	    Util.dump("unknown RTCP packet", data, 0, 16);
		}
            } catch (Exception e) {
                if (!done) {
                    Logger.error("RtcpReceiver:  receive failed! " 
			+ e.getMessage());
		    end();
		}
            }
        }
    }

    public long secondsSinceLastReport() {
	if (timeLastReportReceived == 0) {
	    timeLastReportReceived = System.currentTimeMillis();
	    return 0;
	}

	return (System.currentTimeMillis() - timeLastReportReceived) / 1000;
    }

}
