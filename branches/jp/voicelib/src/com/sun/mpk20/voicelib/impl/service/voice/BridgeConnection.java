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

import com.sun.mpk20.voicelib.app.BridgeInfo;

import com.sun.voip.CallParticipant;
import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.impl.VoiceBridgeConnection;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author jkaplan
 */
public class BridgeConnection extends VoiceBridgeConnection {

    /** a logger */
    private static final Logger logger =
            Logger.getLogger(BridgeConnection.class.getName());
    
    /** the end-of-message pattern */
    private static final Pattern END_OF_MESSAGE = 
            Pattern.compile("^END -- (.*)");
    
    /** the underlying connection to the bridge */
    private Socket socket;
    
    /** the reader */
    private BufferedReader reader;
    
    /** the writer */
    private PrintWriter writer;
    
    private String privateHost;
    private int privateControlPort;
    private int privateSipPort;

    private String publicHost;
    private int publicControlPort;
    private int publicSipPort;

    private boolean synchronous;

    private Hashtable<String, String> conferences = 
	new Hashtable<String, String>();

    /*
     * This map contains the CallParticipant for each call on this bridge
     * connection.
     */
    private ConcurrentHashMap<String, CallParticipant> callParticipantMap = 
	new ConcurrentHashMap<String, CallParticipant>();

    private static int watchdogTimeout;
    private static int bridgePingTimeout;

    private long bridgePingTime;

    private boolean pingTimeout = false;

    private BridgeOfflineListener offlineListener;

    private SocketChannel socketChannel;

    static {
        String s = System.getProperty(
            "com.sun.sgs.impl.service.voice.watchdog.timeout");

        if (s != null) {
            try {
                watchdogTimeout = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                logger.info("Invalid watchdog timeout:  " + s
                    + ".  Defaulting to " + watchdogTimeout + " sec.");
            }
        }

	logger.info("Watchdog timeout is " + watchdogTimeout + " seconds");

        s = System.getProperty(
            "com.sun.sgs.impl.service.voice.bridge.ping.timeout");

        if (s != null) {
            try {
                bridgePingTimeout = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                logger.info("Invalid bridgePingTimeout timeout:  " + s
                    + ".  Defaulting to " + bridgePingTimeout + " sec.");
            }
        }

	logger.info("Bridge ping timeout is " + bridgePingTimeout + " seconds");
    }

    /**
     * Creates a new instance of BridgeConnection
     */
    public BridgeConnection(
	    String privateHost, int privateControlPort, int privateSipPort,
	    boolean synchronous) throws IOException {

	this(privateHost, privateControlPort, privateSipPort,
	     privateHost, privateControlPort, privateSipPort, synchronous);
    }

    public BridgeConnection(
	    String privateHost, int privateControlPort, int privateSipPort,
	    String publicHost, int publicControlPort, int publicSipPort, 
	    boolean synchronous) throws IOException {

        super(privateHost, privateControlPort);

	this.privateHost = privateHost;
	this.privateControlPort = privateControlPort;
	this.privateSipPort = privateSipPort;
	this.publicHost = publicHost;
	this.publicControlPort = publicControlPort;
	this.publicSipPort = publicSipPort;
	this.synchronous = synchronous;

	/*
	 * Connect to get bridge status
	 */
	super.connect();
	
	/*
	 * Connect in synchronous mode to get command status.
	 */
	connect(true);

	/*
	 * SocketChannels seem to perform much better than Sockets.
	 */
	InetAddress ia = InetAddress.getByName(privateHost);
        InetSocketAddress bridgeSocketAddress = 
	    new InetSocketAddress(ia, privateControlPort);

        socketChannel = SocketChannel.open(bridgeSocketAddress);
	//socketChannel.configureBlocking(false);

	Socket socket = socketChannel.socket();
        socket.setSendBufferSize(2 * 1024 * 1024);

	logger.info("Created a socket channel to " + bridgeSocketAddress
	    + " sendBufferSize " + socket.getSendBufferSize());
    }

    public void addBridgeOfflineListener(
	    BridgeOfflineListener offlineListener) {

	this.offlineListener = offlineListener;
    }

    public static CallStatus parseCallStatus(String s) throws IOException {
	return VoiceBridgeConnection.parseCallStatus(s);
    }

    public String getPrivateHost() {
	return privateHost;
    }

    public int getPrivateControlPort() {
	return privateControlPort;
    }

    public int getPrivateSipPort() {
	return privateSipPort;
    }

    public String getPublicHost() {
	return publicHost;
    }

    public int getPublicControlPort() {
	return publicControlPort;
    }

    public int getPublicSipPort() {
	return publicSipPort;
    }

    public String getPublicAddress() {
	return publicHost + ":" + publicSipPort;
    }

    public void monitorConference(String conferenceId) throws IOException {
	int ix;

	String s = "";

	String c = conferenceId;

	if ((ix = conferenceId.indexOf(":")) > 0) {
	    s += "cc=" + conferenceId + "\n";

	    c = conferenceId.substring(0, ix);

	    s += "wgo=" + c + ":" + c + ":noCommonMix=true" + "\n";
	}

	synchronized (conferences) {
	    /*
	     * Remember conferences we're monitoring
	     * so we don't monitor more than once.
	     */
	    if (conferences.get(conferenceId) == null) {
		s += "mcc=" + true + ":" + c + "\n";

		sendMessage(s);
	        conferences.put(conferenceId, conferenceId);

		logger.finest("Monitoring conference " + s + " " + this);
	    } else {
		logger.finest("Already monitoring " + s + " " + this);
	    }
	}
    }

    public Hashtable<String, String> getConferences() {
	return conferences;
    }

    public void monitorConferences(Hashtable<String, String> conferences) {
        Collection<String> c = conferences.values();

        Iterator<String> it = c.iterator();

        while (it.hasNext()) {
	    try {
	        monitorConference(it.next());
	    } catch (IOException e) {
		logger.info("Unable to monitor conference " + e.getMessage());
	    } 
        }
    }

    public void addCall(CallParticipant cp) {
	callParticipantMap.put(cp.getCallId(), cp);

	logger.finer("added call " + cp.getCallId() + " " 
	    + callParticipantMap.size());
    }

    /*
     * Called by endCall when call ends.
     */
    private int removeCall(CallParticipant cp) {
	callParticipantMap.remove(cp.getCallId());

	logger.finer("removed call " + cp.getCallId() + " "
	    + callParticipantMap.size());

        return callParticipantMap.size();
    }

    private int removeCall(String callId) {
	 callParticipantMap.remove(callId);

	 logger.finer("removed call " + callId + " "
	    + callParticipantMap.size());

        return callParticipantMap.size();
    }

    public int getNumberOfCalls() {
        return callParticipantMap.size();
    }
	
    public CallParticipant getCallParticipant(String callId) {
	return callParticipantMap.get(callId);
    }

    public String setupCall(CallParticipant cp) throws IOException {
	monitorConference(cp.getConferenceId());

	addCall(cp);

	BridgeResponse br = sendWithResponse(cp.getCallSetupRequest());

        logger.fine("setupCall status " + br.getStatus());
        
	switch (br.getStatus()) {
	case SUCCESS:
            logger.finest("setupCall contents " + br.getContents());
	    return br.getContents();

	default:
	    removeCall(cp);

	    throw new IOException("setupCall failed:  " 
		+ br.getMessage());
	}
    }

    public void migrateCall(CallParticipant cp, boolean cancel) throws IOException {
	if (isConnected() == false) {
	    return;
	}

	BridgeResponse br;

	if (cancel == true) {
	    br = sendWithResponse("cancelMigration=" + cp.getCallId() + "\n");
	} else {
	    cp.setMigrateCall(true);
	    br = sendWithResponse(cp.getCallSetupRequest());
	}

        logger.fine("migrate status " + br.getStatus());
        
	switch (br.getStatus()) {
	case SUCCESS:
            logger.fine("migrate contents " + br.getContents());
	    return;

	default:
	    logger.fine("migrate failed:  " + br.getMessage());
	    throw new IOException("migrate failed:  " + br.getMessage());
	}
    }

    public void endCall(String callId) throws IOException {
        if (callId.equals("0") == false && callParticipantMap.get(callId) == null) {
	    logger.fine("Call not found " + callId);
	    return;
	}

	if (isConnected() == false) {
	    return;
	}

	BridgeResponse br;

	br = sendWithResponse("cancel=" + callId + "\n");

        logger.fine("endCall status " + br.getStatus());
        
	switch (br.getStatus()) {
	case SUCCESS:
            logger.finest("endCall contents " + br.getContents());

	    return;

	default:
	    throw new IOException("endCall failed:  " + br.getMessage());
	}
    }

    public void muteCall(String callId, boolean isMuted) throws IOException {
        BridgeResponse br;

        br = sendWithResponse("mute=" + isMuted + ":" + callId + "\n");

        logger.fine("muteCall status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("muteCall contents " + br.getContents());

            return;

        default:
            throw new IOException("muteCall failed:  " + br.getMessage());
        }
    }

    public void transferCall(String callId, String conferenceId) throws IOException {
        BridgeResponse br;

        br = sendWithResponse("transferCall=" + callId + ":" + conferenceId
	    + "\n");

        logger.fine("transferCall status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("transferCall contents " + br.getContents());

            return;

        default:
            throw new IOException("transferCall failed:  " + br.getMessage());
        }
    }

    /**
     * Set the stereo volumes for a call.All values are from 0.0 (mute) to
     * 1.0 (normal) and higher to increase volume.  Normally volumes are
     * [1.0, 0.0, 0.0, 1.0].  These volumes are private to the source
     * call.
     *
     * @param sourceCallId the call id that hears the volume changes
     * @param targetCallId the call id that has its volumes changed
     * @param privateMixParameters and array of 4 doubles.
     * front/back, left/right, up/down, volume
     */
    public void setPrivateMix(String sourceCallId, String targetCallId,
            double[] privateMixParameters) throws IOException {

        String s = "pmx=" +  privateMixParameters[0] + ":" 
	    +  privateMixParameters[1] + ":"
	    +  privateMixParameters[2] + ":"
	    +  privateMixParameters[3] + ":"
	    + targetCallId + ":" + sourceCallId + "\n";

	int n = socketChannel.write(
	    ByteBuffer.wrap(s.getBytes(), 0, s.length()));

	if (n < s.length()) {
	    logger.info("Tried to write " + s.length() 
		+ ", actually wrote " + n);
	}

if (false) {
	BridgeResponse br = sendWithResponse(s + "\n");

        logger.finest("setPrivateMix status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finer("setPrivateMix success");
	    return;

        default:
            throw new IOException("setPrivateMix failed:  "
              + br.getMessage());
        }
}
    }

    public void setPrivateMix(String commands) throws IOException {
	int n = socketChannel.write(ByteBuffer.wrap(commands.getBytes(), 0, 
	    commands.length()));

	if (n < commands.length()) {
	    logger.info("Tried to write " + commands.length() 
		+ ", actually wrote " + n);
	}
    }

    /**
     * Start a new input treatment
     */
    public void newInputTreatment(String callId, String treatment) 
	    throws IOException {

        BridgeResponse br = sendWithResponse("startInputTreatment="
	    + treatment + ":" + callId + "\n");

        logger.finest("newInputTreatment status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("newInputTreatment success");
            return;

        default:
            throw new IOException("newInputTreatment failed:  "
              + br.getMessage());
        }
    }

    public void pauseInputTreatment(String callId, boolean isPaused) throws IOException {
        BridgeResponse br = sendWithResponse("pauseInputTreatment="
	    + isPaused + ":" + callId + "\n");

        logger.finest("pauseInputTreatment status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("pauseInputTreatment success");
            return;

        default:
            throw new IOException("pauseInputTreatment failed:  "
              + br.getMessage());
        }
    }

    public void resumeInputTreatment(String callId) throws IOException {
        BridgeResponse br = sendWithResponse("resumeTreatmentToCall="
	    + callId + "\n");

        logger.finest("resumeTreatment status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("resumeTreatment success");
            return;

        default:
            throw new IOException("resumeTreatment failed:  "
              + br.getMessage());
        }
    }

    public void stopInputTreatment(String callId) throws IOException {
        BridgeResponse br = sendWithResponse("stopInputTreatment="
	    + callId + "\n");

        logger.finest("stopInputTreatment status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("stopInputTreatment success");
            return;

        default:
            throw new IOException("stopInputTreatment failed:  "
              + br.getMessage());
        }
    }

    /**
     * Restart input treatment for a call
     */
    public void restartInputTreatment(String callId) throws IOException {
        BridgeResponse br = sendWithResponse("restartInputTreatment="
	    + callId + "\n");

        logger.finest("restartInputTreatment status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("restartInputTreatment success");
            return;

        default:
            throw new IOException("restartInputTreatment failed:  "
              + br.getMessage());
        }
    }

    public void playTreatmentToCall(String callId, String treatment) 
	    throws IOException {

        BridgeResponse br = sendWithResponse("playTreatmentToCall="
            + treatment + ":" + callId + "\n");

        logger.finest("playTreatmentToCall status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("playTreatmentToCall success");
            return;

        default:
            throw new IOException("playTreatmentToCall failed:  "
              + br.getMessage());
        }
    }

    public void pauseTreatmentToCall(String callId, String treatment)
	    throws IOException {

        BridgeResponse br = sendWithResponse("pauseTreatmentToCall="
            + callId + ":" + treatment + "\n");

        logger.finest("pauseTreatmentToCall status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("pauseTreatmentToCall success");
            return;

        default:
            throw new IOException("pauseTreatmentToCall failed:  "
              + br.getMessage());
        }
    }

    public void stopTreatmentToCall(String callId, String treatment) 
	    throws IOException {

        BridgeResponse br = sendWithResponse("stopTreatmentToCall="
            + callId  + ":" + treatment + "\n");

        logger.finest("stopTreatmentToCall status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("stopTreatmentToCall success");
            return;

        default:
            throw new IOException("stopTreatmentToCall failed:  "
              + br.getMessage());
        }
    }

    public void setSpatialAudio(boolean enabled) throws IOException {
	BridgeResponse br = sendWithResponse("spatialAudio="
            + enabled + "\n");

        logger.finest("setSpatialAudio status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("setSpatialAudio success");
            return;

        default:
            throw new IOException("setSpatialAudio failed:  "
              + br.getMessage());
        }
    }

    public void setSpatialMinVolume(double spatialMinVolume) throws IOException {
        BridgeResponse br = sendWithResponse("smv="
            + spatialMinVolume + "\n");

        logger.finest("setSpatialMinVolume status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("setSpatialMinVolume success");
            return;

        default:
            throw new IOException("setSpatialMinVolume failed:  "
              + br.getMessage());
        }
    }

    public void setSpatialFalloff(double spatialFalloff) throws IOException {
        BridgeResponse br = sendWithResponse("spatialFalloff="
            + spatialFalloff + "\n");

        logger.finest("setSpatialAudio status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("setSpatialFalloff success");
            return;

        default:
            throw new IOException("setSpatialFalloff failed:  "
              + br.getMessage());
        }
    }

    public void setSpatialEchoDelay(double spatialEchoDelay) throws IOException {
        BridgeResponse br = sendWithResponse("spatialEchoDelay="
            + (Math.round(spatialEchoDelay * 100000) / 100000.) + "\n");

        logger.finest("setSpatialAudio status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("setSpatialEchoDelay success");
            return;

        default:
            throw new IOException("setSpatialEchoDelay failed:  "
              + br.getMessage());
        }
    }

    public void setSpatialEchoVolume(double spatialEchoVolume) throws IOException {
        BridgeResponse br = sendWithResponse("spatialEchoVolume="
            + (Math.round(spatialEchoVolume * 100000) / 100000.) + "\n");

        logger.finest("setSpatialAudio status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("setSpatialEchoVolume success");
            return;

        default:
            throw new IOException("setSpatialEchoVolume failed:  "
              + br.getMessage());
        }
    }

    public void setSpatialBehindVolume(double spatialBehindVolume) 
	    throws IOException {

        BridgeResponse br = sendWithResponse("spatialBehindVolume="
            + (Math.round(spatialBehindVolume * 100000) / 100000.) + "\n");

        logger.finest("setSpatialAudio status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("setSpatialBehindVolume success");
            return;

        default:
            throw new IOException("setSpatialBehindVolume failed:  "
              + br.getMessage());
        }
    }

    public void forwardData(String sourceCallId, String targetCallId)
	    throws IOException {

	String cmd = "forwardData="
	    + targetCallId + ":" + sourceCallId + "\n";

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("forwardData status " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("forwardData success: " + cmd);
            return;

        default:
            throw new IOException("forwardData failed:  " + cmd
		+ " " + br.getMessage());
        }
    }

    public String getBridgeStatus() throws IOException {
	String cmd = "gs\n";

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("getStatus " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("getStatus success: " + cmd);
            return br.getContents().toString();

        default:
            throw new IOException("getStatus failed:  " + cmd
		+ " " + br.getMessage());
        }
    }

    public String getCallInfo() throws IOException {
       String cmd = "ci\n";

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("getCallInfo " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("getCallInfo success: " + cmd);
            return br.getContents().toString();

        default:
            throw new IOException("getCallInfo failed:  " + cmd
                + " " + br.getMessage());
        }
    }

    public String getCallStatus(String callId) throws IOException {
       String cmd = "gcs=" + callId + "\n";

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("getCallStatus " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("getCallStatus success: " + cmd);
            return br.getContents().toString();

        default:
            throw new IOException("getCallStatus failed:  " + cmd
                + " " + br.getMessage());
        }
    }

    public String getBridgeInfo() throws IOException {
       String cmd = "tp\n";

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("getBridgeInfo " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("getBridgeInfo success: " + cmd);

	    return br.getContents().toString();

        default:
            throw new IOException("getBridgeInfo failed:  " + cmd
                + " " + br.getMessage());
        }
    }

    public String suspend(boolean suspend) throws IOException {
	String cmd;

	if (suspend) {
	    cmd = "suspend\n";
	} else {
	    cmd = "resume\n";
	}

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("getStatus " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("suspend success: " + cmd);
            return br.getMessage();

        default:
            throw new IOException("suspend failed:  " + cmd
		+ " " + br.getMessage());
        }
    }

    public String startRecordingToCall(String callId, String recordingFile) 
	    throws IOException {
	
	String cmd = "recordToMember=t:" + callId + ":" + recordingFile 
	    + ":" + "au" + "\n";

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("getStatus " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("recordTomember success: " + cmd);
            return br.getMessage();

        default:
            throw new IOException("recordTomember failed:  " + cmd
		+ " " + br.getMessage());
        }
    }

    public String stopRecordingToCall(String callId) throws IOException {
	String cmd = "recordToMember=f:" + callId + "\n";

        BridgeResponse br = sendWithResponse(cmd);

        logger.finest("getStatus " + br.getStatus());

        switch (br.getStatus()) {
        case SUCCESS:
            logger.finest("stop recording success: " + cmd);
            return br.getMessage();

        default:
            throw new IOException("stop recording failed:  " + cmd
		+ " " + br.getMessage());
        }
    }

    /**
     * Send a message to the bridge and wait for response.  This method waits 
     * for a status confirmation from the bridge, and returns whatever that
     * status is once it is complete.
     * @param message the message to send
     * @return the status and message received from the bridge
     * @throws IOException if there is an error sending the message
     */
    private BridgeConnection getBridgeConnection() {
	return this;
    }

    private synchronized BridgeResponse sendWithResponse(String message)
        throws IOException
    {
        logger.finest("sendWithResponse() - send: '" + message + "'");
        
	checkConnection();

        // send the message
        sendImpl(message);
    
	Timer timer = new Timer();

	if (watchdogTimeout > 0) {
	    timer.schedule(new TimerTask() {
                public void run() {
		    logger.info("No response from bridge! " 
			+ getBridgeConnection());

		    disconnect();
		    sendBridgeOfflineNotification();
                }}, watchdogTimeout * 1000);
	}

        // read the response
        StringBuffer contents = new StringBuffer();
        String line;

        while ((line = reader.readLine()) != null) {
	    timer.cancel();
            // see if this was an end-of-message line
            Matcher m = END_OF_MESSAGE.matcher(line);
            if (m.matches()) {
                String status = m.group(1).trim();
                return new BridgeResponse(status, contents.toString());
            }
            
            // it's not -- add it to the contents
	    
            contents.append(line + "\n");
        }
        
        timer.cancel();

        // if we got here, the stream closed before we received the
        // end-of-message line.  This means an abnormal termination.
        throw new IOException("Unexpected end of stream.  Message so far: " +
                              contents.toString());
    }
    
    /**
     * Implementation of send
     * @param message the message to send
     * @throws IOException if there is an error sending
     */
    private synchronized void sendImpl(String message) throws IOException {
        // send the message
        if (writer == null) {
            throw new IOException("Not connected");
        }
        
	checkConnection();

        writer.print(message);
        writer.flush();
    }
    
    public boolean isConnected() {
	if (socket == null) {
	    logger.finest("socket is null");
	    return false;
	}

	if (socket.isClosed()) {
	    logger.finest("socket is closed");
	    return false;
	}

	if (socket.isConnected() == false) {
	    logger.finest("socket is not connected");
	    return false;
	}

	if (super.isConnected() == false) {
	    logger.finest("super is not connected");
	    return false;
	}

	if (socket.isOutputShutdown() || socket.isInputShutdown()) {
	    return false;
	}

	return true;
    }

    private void checkConnection() throws IOException {
	if (isConnected() == false) {
	    throw new IOException("Not connected!");
	}
    }

    /**
     * Get the connection to the bridge, opening the socket if necessary.  This
     * method also creates the socket, reader and writer class variables.
     * @param force if true, force a new connection
     * @throws IOException if there an error connecting to the
     * bridge
     */
    private synchronized void connect(boolean force) throws IOException {
        if (socket == null || socket.isClosed() || force) {
            logger.fine("Connecting to " + privateHost + " " 
		+ privateControlPort);
            
            socket = new Socket(privateHost, privateControlPort);
            
	    socket.setSendBufferSize(256*1024);

	    if (watchdogTimeout != 0) {
	        new ConnectionWatchdog(this);
		gotBridgePing();
	    }

            Reader isr = new InputStreamReader(socket.getInputStream());
            Writer osw = new OutputStreamWriter(socket.getOutputStream());
            reader = new BufferedReader(isr);
            writer = new PrintWriter(osw);
            
	    writer.println("sm=true");

            // do some initial setup on the connection
            writer.flush();
            String s = reader.readLine(); // read first line

	    String publicAddress = publicHost + ":" + publicSipPort;

	    String pattern = "BridgePublicAddress='";

	    int ix = s.indexOf(pattern);

	    int end = -1;;

	    if (ix >= 0) {
		s = s.substring(ix + pattern.length());

		end = s.indexOf("'");
	    }

	    if (end >= 0) {
		s = s.substring(0, end);

		String[] tokens = s.split(":");

		try {
		    publicAddress = 
			InetAddress.getByName(tokens[0]).getHostAddress();
		} catch (UnknownHostException e) {
		    logger.info("Unknown host: " + tokens[0]);
		}

		try {
		    publicAddress += ":" + Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
		    logger.info("Invalid bridge public address:  "
			+ tokens[1]);
		} catch (ArrayIndexOutOfBoundsException e) {
		    logger.info("Missing port number");
		}
	    } 

	    logger.info("Bridge public address is " + publicAddress);
        } else {
	    logger.info("Already connected to " + socket);
	}
    }
    
    public void disconnect() {
        if (socket == null) {
	    return;
	}

	logger.info("disconnect");

	super.disconnect();

	try {
	    socket.close();
	} catch (IOException e) {
	}

	socket = null;
    }

    /**
     * A response from the bridge
     */
    static class BridgeResponse {
        private enum Status {
            SUCCESS, FAILURE, UNKNOWN;
        }
        
        private Status status;
        private String message;
        private String contents;
        
        public BridgeResponse(String status, String contents) {
            parseStatus(status);
            this.contents = contents;
        }
        
        public Status getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getContents() {
            return contents;
        }
        
        private void parseStatus(String statusStr) {
            if (statusStr == null) {
                status = Status.UNKNOWN;
            } else if (statusStr.startsWith("SUCCESS")) {
                status = Status.SUCCESS;
            } else if (statusStr.startsWith("FAILURE:")) {
                status = Status.FAILURE;
                message = statusStr.substring("FAILURE:".length()).trim();
            } else {
                status = Status.UNKNOWN;
                message = "Unrecognized status: " + statusStr;
            }
        }
    }

    public void bridgeOffline(BridgeConnection bc) {
	String s = bc.getPrivateHost() + "_" + bc.getPrivateControlPort();

	ArrayList<CallParticipant> cpArray = getCallParticipantArray();

	while (cpArray.size() > 0) {
	    CallParticipant cp = cpArray.remove(0);

	    String callId = cp.getCallId();

	    if (callId.indexOf(s) >= 0) {
		logger.fine("Ending call to disconnected bridge " + callId);

		try {
		    endCall(callId);
		} catch (IOException e) {
		    logger.info("Unable to end call to " + callId);
		}
	    }
	}
    }

    public ArrayList<CallParticipant> getCallParticipantArray() {
	ArrayList<CallParticipant> cpArray = 
	    new ArrayList<CallParticipant>();

        Collection<CallParticipant> c = callParticipantMap.values();

        Iterator<CallParticipant> it = c.iterator();

        while (it.hasNext()) {
            cpArray.add(it.next());
        }

	return cpArray;
    }

    public void gotBridgePing() {
	bridgePingTime = System.currentTimeMillis();
	pingTimeout = false;
    }

    public boolean pingTimeout() {
	return pingTimeout;
    }

    public boolean equals(BridgeConnection bc) {
	return bc.privateHost.equals(privateHost) && 
	    bc.privateControlPort == privateControlPort &&
	    bc.privateSipPort == privateSipPort &&
	    bc.publicHost.equals(publicHost) &&
	    bc.publicControlPort == publicControlPort &&
	    bc.publicSipPort == publicSipPort;
    }

    public boolean equals(String publicHost, String publicSipPort) {
	return this.publicHost.equals(publicHost) && 
	    String.valueOf(this.publicSipPort).equals(publicSipPort);
    }

    public String toString() {
	return privateHost + ":" + privateControlPort + ":" + privateSipPort + ":" 
	    + publicHost + ":" + publicControlPort + ":" + publicSipPort;
    }

    private boolean offlineNotificationSent;

    private void sendBridgeOfflineNotification() {
	synchronized (this) {
	    if (offlineNotificationSent == false && offlineListener != null) {
                logger.info("Sending bridge down notification:  " + toString());

                offlineListener.bridgeOffline(this, getCallParticipantArray());
	        offlineNotificationSent = true;
	    }
	}
    }

    class ConnectionWatchdog extends Thread {

	private BridgeConnection bridgeConnection;

	public ConnectionWatchdog(BridgeConnection bridgeConnection) {
	    this.bridgeConnection = bridgeConnection;

	    start();
	}

	public void run() {
	    bridgePingTime = System.currentTimeMillis();

	    while (bridgeConnection.isConnected()) {
	        long elapsed = 0;

	        while (bridgeConnection.isConnected()) {
		    long now = System.currentTimeMillis();
		    if (bridgePingTimeout != 0) {
		        elapsed = now - bridgePingTime;

		        if (elapsed > bridgePingTimeout * 1000) {
			    if (pingTimeout == false) {
				pingTimeout = true;
		                break;	// we haven't heard from the bridge
			    }
		        } else {
			    logger.finest("elapsed " + elapsed);
			    pingTimeout = false;
			}
		    }

		    try {
		        Thread.sleep(watchdogTimeout * 1000);
		    } catch (InterruptedException e) {
			logger.info("sleep interrupted!");
		    }

		    continue;
	        }

	        if (bridgeConnection.isConnected() == false) {
	            logger.info("Bridge " + bridgeConnection.toString()
		        + " disconnected " + callParticipantMap.size() 
			+ " calls");
	        } else {
	            logger.info("Bridge " + bridgeConnection.toString()
		        + " went offline, elapsed " + (elapsed / 1000.)
			+ " seconds, " + callParticipantMap.size() + " calls");
	        }
		
		disconnect();
		sendBridgeOfflineNotification();
		break;
	    }

	    logger.info("ConnectionWatchdog done watching " + bridgeConnection);
 	}

    }

}
