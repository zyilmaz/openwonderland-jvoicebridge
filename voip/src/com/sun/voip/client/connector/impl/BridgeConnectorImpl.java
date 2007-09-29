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

package com.sun.voip.client.connector.impl;

import com.sun.voip.client.connector.BridgeConnector;
import com.sun.voip.client.connector.CallControl;
import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Implementation of the test package
 */
public class BridgeConnectorImpl implements BridgeConnector { 
    /** the default bridge port */
    public static final int DEFAULT_BRIDGE_PORT = 6666;
    
    /** the timeout on the connection timer */
    private static final long CONNECTION_TIMEOUT = 5 * 60 * 1000;
            
    /** Hostname (or ip address) of the machine running the VoiceBridge. */
    private String bridgeHost;

    /** Port where the VoiceBridge listens for commands.*/
    private int bridgePort;
           
    /** Our own connection for connecting to the bridge */
    private VoiceBridgeConnection connection;
    
    /** The timer, to timeout the connector after 5 minutes of idle */
    private Timer connectionTimer;
    
    /** The current task */
    private TimerTask connectionTask;

    /** The set of call control objects we have created, mapped by call id */
    private Map callControls;
    
    /** Map of conference id to VoiceBridgeconnection */
    private Map connections;
    
    /** counter to generate unique callIDs. */
    private static int callCounter = 0;

    /** suffix for unique callIDs */
    private String bridgeToken;
    
    /** the logger */
    private static final Logger logger =
        Logger.getLogger(BridgeConnectorImpl.class.getName());
    
    /** 
     * Creates a new instance of Bridge with the default port.
     * Identical to calling new BridgeImpl(host, DEFAULT_BRIDGE_PORT).
     * @param host the hostname of the bridge to connect to
     */
    public BridgeConnectorImpl(String host) {
        this(host, DEFAULT_BRIDGE_PORT);
    }
    
    /** 
     * Creates a new instance of Bridge that connects to the given
     * hostname and port.  The actual connection to the voice bridge is
     * made on demand, for example the first time a conference is created.
     * @param host the hostname of the bridge to connect to
     * @param port the port number to connect to on the bridge
     */
    public BridgeConnectorImpl(String host, int port) {
        this(host, port, null);
    }
    
    /** 
     * Creates a new instance of Bridge with the given hostname, port and
     * id token.  The id token will be used in the call ids of calls that 
     * are placed by this connector.  For example, if the id is "Duke", calls
     * will have the ids "1/Duke", "2/Duke", etc.  No two connection to the
     * same bridge should have the same id.  The default implementation uses
     * the hash value of this object as the key.
     * @param host the hostname to connect to
     * @param port the port number to connect to
     * @param id the id token, or null to use a default token
     */
    public BridgeConnectorImpl(String host, int port, String id) {
        this.bridgeHost = host;
        this.bridgePort = port;
    
        // create the timer
        connectionTimer = new Timer();
        callControls = new HashMap();
        connections = new HashMap();
        bridgeToken = id;
        
        // use the default
        if (bridgeToken == null) {
            bridgeToken = String.valueOf(this.hashCode());
        }
        
        logger.config("BridgeHost: " + bridgeHost);
        logger.config("BridgePort: " + bridgePort);
    }
     
    /**
     * Disconnect from the bridge.  Get rid of any VoiceBridgeconnections
     * that are hanging around
     */
    public synchronized void disconnect() {
        for (Iterator i = connections.values().iterator(); i.hasNext();) {
            VoiceBridgeConnection vbc = (VoiceBridgeConnection) i.next();
            vbc.disconnect();
            i.remove();
        }
        removeconnection();
        
        // shut down the timer
        connectionTimer.cancel();
    }
 
    public synchronized CallControl createCallControl(String conferenceId) 
    {
        return createCallControl(null, conferenceId);
    }
    
    public synchronized CallControl createCallControl(String callId, 
                                                      String conferenceId) 
    {
        VoiceBridgeConnection connection = 
            (VoiceBridgeConnection) connections.get(conferenceId);
        if (connection == null) {
            connection = new VoiceBridgeConnection(bridgeHost, bridgePort);
           
            // create a listener to clean up calls that have ended.
            connection.addCallStatusListener(new CallControlHandler(connection));
            connections.put(conferenceId, connection);
        } 
        
        if (!connection.isConnected()) {
	    try {
                connection.connect();
	    } catch (IOException e) {
		logger.warning(e.getMessage());
	    }
        }
        
        if (callId == null) {
            int count = ++callCounter;
            callId = count + "/" + bridgeToken;
        }
        
        CallControlImpl control = new CallControlImpl(connection, callId, 
                                                      conferenceId);
        callControls.put(callId, control);
        
        return control;
    }
       
    /**
     * Get a call control for the given call id
     *
     * @return the call control for the given call id
     */
    public CallControl getCallControl(String callId) {
        synchronized (callControls) {
            return (CallControl) callControls.get(callId);
        }
    }
 
    public void createConference(String conferenceId, String name, 
                                 String quality) 
        throws IOException
    {
        logger.finer("Create " + conferenceId + ":" + name + ":" + quality);
        
        VoiceBridgeConnection connection = getconnection();
        connection.createConference(conferenceId, name, quality);
    }
    
    public void removeConference(String conferenceId) 
        throws IOException
    {
        logger.finer("Remove " + conferenceId);
        
        VoiceBridgeConnection connection = getconnection();
        connection.removeConference(conferenceId);
    }
    
    /**
     * Play an audio file to all calls in a conference.
     * @param conferenceId the conference to play the treatment to
     * @param treatment String identifying path of treatment file.
     */
    public void playTreatmentToConference(String conferenceId, String treatment) 
        throws IOException 
    {
        logger.finer("Play " + treatment + " to " + conferenceId);
        
        VoiceBridgeConnection connection = getconnection();
        connection.playTreatmentToConference(conferenceId, treatment);
    }
    
    /**
     * Play an audio file when someone enters a conference.
     * @param conferenceId the conference to set the answer treatment for
     * @param treatment String identifying path of treatment file.
     */
    public void setAnswerTreatment(String conferenceId, String treatment) 
        throws IOException 
    {
        logger.finer("Answer treatment " + treatment + " to " + conferenceId);
        
        VoiceBridgeConnection connection = getconnection();
        connection.setAnswerTreatment(conferenceId, treatment);
    }
    
    /**
     * Record the audio of a conference.  This includes all audio from
     * all calls in the conference.  Individual calls can elect not to be
     * recorded when the call is set up.
     * @param conferenceId String conference identifier
     * @param recordingFile String with absolute path of file to
     *			    contain the recording.
     */
    public void recordConference(String conferenceId, String recordingFile) 
        throws IOException 
    {
        logger.finer("Record " + conferenceId + " to " + recordingFile);
        
        VoiceBridgeConnection connection = getconnection();
        connection.recordConference(conferenceId, recordingFile);
    }
    
    /**
     * Stop recording a conference.
     * @param conferenceId String conference identifier
     */
    public void stopRecordingConference(String conferenceId) 
        throws IOException 
    {
        logger.finer("Stop recording " + conferenceId);
        
        VoiceBridgeConnection connection = getconnection();
        connection.stopRecordingConference(conferenceId);
    }
    
    /** 
     * Create a whisper group with a given name for the given call ids
     * @param conferenceId the conference id to create the whisper group in
     * @param name the name of the whisper group to create
     */
    public void createWhisperGroup(String conferenceId, String name) 
        throws IOException 
    {
        logger.finer("Create whisper group: " + name + " in " + conferenceId);
        VoiceBridgeConnection connection = getconnection();
        connection.createWhisperGroup(conferenceId, name);
    }
    
     /**
     * Create a whisper group with the given name and options
     * @param conferenceId the conference id of the whisper group to create
     * @param name the name of the whisper group 
     * @param trans if true, the whisper group will be transient, and go away 
     * when everyone leaves
     * @param locked if true, clients will not be able to say
     * "whisper=false" on this whisper group
     * @param conferenceVolume the volume level of the main conference
     * heard by the whisper group.
     */
    public void createWhisperGroup(String conferenceId, String name, 
            boolean trans, boolean locked, float conferenceVolume)
	throws IOException 
    {
        logger.finer("Create whisper group: " + name + " in " + conferenceId + 
                " transient: " + trans + " locked: " + locked + 
                " conferenceVolume: " + conferenceVolume);
        VoiceBridgeConnection connection = getconnection();
        connection.createWhisperGroup(conferenceId, name, trans, locked, 
                                     conferenceVolume);
    }
    
    /**
     * Remove a whisper group with the given name
     * @param conferenceId the conference id to remove the whisper group from
     * @param name the name of the group to remove
     */
    public void destroyWhisperGroup(String conferenceId, String name) 
        throws IOException 
    {
        logger.finest("Remove whisper group: " + name);
    
        VoiceBridgeConnection connection = getconnection();
        connection.destroyWhisperGroup(conferenceId, name);
    }
 
    /**
     * Add a call to an existing whisper group
     *
     * @param whisperGroup the whisper group to add the call to
     * @param callId the id of the call to add
     */
    public void addCallToWhisperGroup(String whisperGroup, String callId) 
        throws IOException 
    {
        logger.finer("Add call " + callId + " to whisper group " +
                     whisperGroup);
           
        VoiceBridgeConnection connection = getconnection();
        connection.addCallToWhisperGroup(whisperGroup, callId); 
    }
    
    /**
     * Remove a call to an existing whisper group
     *
     * @param whisperGroup the whisper group to remove the call from
     * @param callId the id of the call to remove
     */
    public void removeCallFromWhisperGroup(String whisperGroup, String callId) 
        throws IOException 
    {
        logger.finer("Remove call " + callId + " from whisper group " +
                     whisperGroup);
           
        VoiceBridgeConnection connection = getconnection();
        connection.removeCallFromWhisperGroup(whisperGroup, callId); 
    }
    
    /**
     * Get the voice bridge connection
     */
    private synchronized VoiceBridgeConnection getconnection() {
        if (connectionTask != null) {
            connectionTask.cancel();
        }
        
        if (connection == null) {
            connection = new VoiceBridgeConnection(bridgeHost, bridgePort);
        }
        
        if (!connection.isConnected()) {
	    try {
                connection.connect();
	    } catch (IOException e) {
		logger.warning(e.getMessage());
	    }
        }
     
        connectionTask = new TimerTask() {
            public void run() {
                removeconnection();
            }
        };
        connectionTimer.schedule(connectionTask, CONNECTION_TIMEOUT);
        
        return connection;
    }
    
    /**
     * Remove the current voice bridge connection
     */
    public synchronized void removeconnection() {
        if (connectionTask != null) {
            connectionTask.cancel();
            connectionTask = null;
        }
        
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }
    
    /**
     * A class to monitor a VoiceBridgeconnection and disconnect it when it
     * is no longer in use.
     */
    class CallControlHandler implements CallStatusListener {
        private VoiceBridgeConnection connection;
        
        public CallControlHandler(VoiceBridgeConnection connection) {
            this.connection = connection;
        }
        
        public void callStatusChanged(CallStatus status) {
            if (status.getCode() == CallStatus.ENDED) {
                callControls.remove(status.getCallId());
                
                if (callControls.isEmpty()) {
                    connection.disconnect();
                }
            }
        }
    }
}
