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

package bridgemonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import com.sun.voip.PerfMon;
import com.sun.voip.DataUpdater;

import java.awt.Point;

public class CallMonitor extends Thread {

    public static final int RECEIVE_MONITOR_PORT = 7777;

    private Socket socket;
    private OutputStream output;
    private BufferedReader bufferedReader;

    private CallMonitorListener listener;

    private ReceivedPacketsMonitor receivedPacketsMonitor;

    private AverageReceiveTimeMonitor averageReceiveTimeMonitor;

    private JitterMonitor jitterMonitor;

    public static void main(String[] args) {
	if (args.length != 2) {
	    System.out.println("Usage:  java <bridge server> <callId>");
	    System.exit(1);
	}

	try {
	    new CallMonitor(args[0], args[1]);
	} catch (IOException e) {
	    System.out.println(e.getMessage());
	}
    }

    public CallMonitor(String server, String callId) throws IOException {
	this(new Point(0, 0), 0, null, server, callId);
    }

    public CallMonitor(Point location, int height, CallMonitorListener listener,
	    String server, String callId) throws IOException {

	this.listener = listener;

	socket = new Socket();

	InetAddress ia;

	try {
	    ia = InetAddress.getByName(server);
	} catch (UnknownHostException e) {
	    throw new IOException("Unknown host " + server + " "
		+ e.getMessage());
	}

        String s = System.getProperty("bridgemonitor.RECEIVE_MONITOR_PORT");

        int port = RECEIVE_MONITOR_PORT;

        if (s != null) {
            try {
                port = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Invalid ReceiveMonitor port:  "
                    + e.getMessage() + ".  Defaulting to " + port);
            }
        }

	socket.connect(new InetSocketAddress(ia, port));

        output = socket.getOutputStream();

        bufferedReader = new BufferedReader(
	    new InputStreamReader(socket.getInputStream()));

	callId += "\n";

	output.write(callId.getBytes());

	time = System.currentTimeMillis();

	receivedPacketsMonitor = new ReceivedPacketsMonitor(
	    new Point((int)location.getX(), (int)location.getY() + height));

	averageReceiveTimeMonitor = new AverageReceiveTimeMonitor(
	    new Point((int) location.getX() + 330, 
	    (int) location.getY() + height));

	jitterMonitor = new JitterMonitor(new Point((int) location.getX() + 660,
	    (int) location.getY() + height));

	start();
    }

    public void run() {
	while (true) {
	    String s = null;

	    try {
                s = bufferedReader.readLine();

		if (s == null) {
		    done();
		}

		if (s.indexOf("Invalid callId") >= 0) {
		    System.out.println(s);
		    done();
		}

		if (s.indexOf("CallEnded") >= 0) {
		    System.out.println(s);
		    done();
		}

		String[] tokens = s.split(":");

		if (tokens.length != 2) {
		    System.out.println("Missing data:  " + s);
		    done();
		}

		String pattern = "PacketsReceived=";

		int ix = tokens[0].indexOf(pattern);

		if (ix < 0) {
		    System.out.println("Missing " + pattern + " " + s);
		    done();
		}

		try {
		    packetsReceived = Integer.parseInt(
			tokens[0].substring(ix + pattern.length()));
		} catch (NumberFormatException e) {
		    System.out.println("Invalid number for received packets:  "
			+ s);
		    done();
		}
		
		pattern = "JitterBufferSize=";

		ix = tokens[1].indexOf(pattern);

		if (ix < 0) {
		    System.out.println("Missing " + pattern + " " + s);
		    done();
		}

		try {
		    jitter = Integer.parseInt(
			tokens[1].substring(ix + pattern.length()));
		} catch (NumberFormatException e) {
		    System.out.println("Invalid number for jitter:  " + s);
		    done();
		}
	    } catch (IOException e) {
	 	System.err.println("can't read socket! " 
		    + socket + " " + e.getMessage());
		done();
	    }
        }
    }

    public void quit() {
        receivedPacketsMonitor.quit();

        averageReceiveTimeMonitor.quit();

        jitterMonitor.quit();

	done();
    }

    private void done() {
	if (listener != null) {
	    listener.callMonitorDone();
	}
    }

    private int packetsReceived;
    private int jitter;
    private long time;

    public int getPacketsReceived() {
	return packetsReceived;
    }

    public int getJitter() {
	return jitter;
    }

    public void setTime(long time) {
	this.time = time;
    }

    public long getTime() {
	return time;
    }

    class ReceivedPacketsMonitor implements DataUpdater {
        private PerfMon monitor;

        private int packetsReceived;

	public ReceivedPacketsMonitor(Point location) {
	    monitor = new PerfMon("Received Packets", this,
		location, 330, 110);
	}
	
	public int getData() {
	    int p = getPacketsReceived();

	    int n = p - packetsReceived;
	
	    packetsReceived = p;

	    return n;
	}

	public void windowClosed() {
	    quit();
	}

	public void quit() {
	    monitor.stop();
	}
    }

    class AverageReceiveTimeMonitor implements DataUpdater {
        private PerfMon monitor;

        private int packetsReceived;

	public AverageReceiveTimeMonitor(Point location) {
	    monitor = new PerfMon("Average Receive Time", this,
		location, 330, 110);
	}
	
	public int getData() {
	    int p = getPacketsReceived();

	    int n = p - packetsReceived;
	
	    packetsReceived = p;

	    long time = System.currentTimeMillis();

	    long elapsed = time - getTime();

	    if (n == 0) {
		setTime(time);
		return 0;
	    }

	    int avg = (int) (elapsed / n);

	    //System.out.println("elapsed " + elapsed + " n " + n
	    //	+ " avg " + avg);

	    setTime(time);
	    return avg;
	}

	public void windowClosed() {
	    quit();
	}

	public void quit() {
	    monitor.stop();
	}
    }

    class JitterMonitor implements DataUpdater {
        private PerfMon monitor;

        private int jitter;

	public JitterMonitor(Point location) {
	    monitor = new PerfMon("Jitter", this,
		location, 330, 110);
	}
	
	public int getData() {
	    return getJitter();
	}

	public void windowClosed() {
	    quit();
	}

	public void quit() {
	    monitor.stop();
	}
    }
}
