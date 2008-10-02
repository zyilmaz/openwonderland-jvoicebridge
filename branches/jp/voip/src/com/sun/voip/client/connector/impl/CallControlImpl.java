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

import com.sun.voip.CallParticipant;
import com.sun.voip.client.connector.CallControl;
import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Implements the call control interface
 */
public class CallControlImpl implements CallControl, CallStatusListener {
    /** the bridge connector this call control can talk to */
    private VoiceBridgeConnection connection;
    
    /** the call id */
    private String callId;
    
    /** the conference if of the conference we are currently in */
    private String conferenceId;
    
    /** whether or not whispering is enabled */
    private boolean whisperEnabled;
    
    /** the whisper group we are currently whispering in, or null if
     *  we're not whispering
     */
    private String whisperGroup;
    
    /** a list of all whisper groups we are a member of (array of String) */
    private ArrayList inGroups;
    
    /** a list of all whisper groups that don't allow you to stop whispering */
    private ArrayList lockingGroups;
    
    /**
     * List of CallStatusListeners for this CallControl
     */
    private Set callStatusListeners;

    private boolean isStartingUp;
    private boolean isEstablished;
    
    /** a logger */
    private static final Logger logger =
            Logger.getLogger(CallControlImpl.class.getName());
    
    /** 
     * Creates a new instance of CallControlImpl 
     * @param connection a voice bridge connection, not connected
     */
    protected CallControlImpl(VoiceBridgeConnection connection, String callId,
                              String conferenceId) 
    {
        this.callId = callId;
        this.conferenceId = conferenceId;
        this.connection = connection;
        this.inGroups = new ArrayList();
        this.lockingGroups = new ArrayList();
        callStatusListeners = new HashSet();
    }
   
    public void placeCall(CallParticipant participant) throws IOException {
        if (isEstablished()) {
            throw new IOException("Call is already established.  Use migration");
        }
        
        participant.setCallId(getCallId());
        participant.setConferenceId(getConferenceId());        
        
        connection.addCallStatusListener(this);
        
	connection.connect();

        isStartingUp = true;
        connection.placeCall(participant);
    }
    
    public void transferCall(String callId, String conferenceId) 
        throws IOException 
    {
        // set the callId to the id of the call being transferred
        this.callId = callId;
        
        connection.addCallStatusListener(this);
        
        connection.connect();

        connection.transferCall(callId, conferenceId);
        
        // note that global conference id is set later, when the status
        // is received by callStatusChanged
        // this.conferenceId = conferenceId;
        
        // fake a status message
        Map options = new HashMap();
        options.put("CallId", getCallId());
        options.put("ConferenceId", conferenceId);
        CallStatus cs = CallStatus.getInstance("1.0", CallStatus.ESTABLISHED, 
                                               options);
        connection.notifyListeners(cs);
    }
    
    public void existingCall(CallParticipant participant) throws IOException {
        participant.setCallId(getCallId());
        
        connection.addCallStatusListener(this);
        connection.connect();
        //....  done.  we're already connected.
    }

    public void migrateCall(CallParticipant mParticipant) throws IOException {
        connection.isConnected();
        mParticipant.setCallId(getCallId());
        mParticipant.setMigrateCall(true);

        isStartingUp = true;
        connection.migrateCall(mParticipant);
    }

    public void cancelMigration() throws IOException {
        connection.cancelMigration(getCallId());
    }

    public boolean isEstablished() {
        return isEstablished;
    }
    
    public String getCallId() {
        return callId;
    }
    
    public String getConferenceId() {
        return conferenceId;
    }
    
    public void setMute(boolean isMuted) throws IOException {
        connection.setMute(getCallId(), isMuted);
    }
    
    public void setDetectWhileMuted(boolean value) throws IOException {
        connection.setDetectWhileMuted(getCallId(), value);
    }
    
    public void setMuteConference(boolean value) throws IOException {
        connection.setMuteConference(getCallId(), value);
    }
    
    public void setConferenceSilenced(boolean value) throws IOException {
        connection.setConferenceSilenced(getCallId(), value);
    }
    
    public void setVolumeFactor(float volumeFactor) throws IOException {
        connection.setVolumeFactor(getCallId(), volumeFactor);
    }
    
    public void setStereoVolumes(float leftLeft, float leftRight, 
                                 float rightLeft, float rightRight) 
        throws IOException
    {
        connection.setStereoVolumes(getCallId(), leftLeft, leftRight,
                                   rightLeft, rightRight);
    }
    
    public void setPrivateVolumes(String targetCallId, 
                                  float leftLeft, float leftRight, 
                                  float rightLeft, float rightRight) 
        throws IOException
    {
        connection.setPrivateVolumes(getCallId(), targetCallId,
                                   leftLeft, leftRight,
                                   rightLeft, rightRight);
    }
    
    public void playTreatment(String treatment) throws IOException {
        connection.playTreatmentToCall(getCallId(), treatment);
    }
    
    public void stopAllTreatments() throws IOException {
	connection.stopAllTreatmentsToCall(getCallId());
    }

    public boolean isLockingWhisperGroup(String groupID) {
        return lockingGroups.contains(groupID);
    }
    
    /**
     * Create a whisper group in the conference this call is currently in
     */
    public void createWhisperGroup(String whisperGroupId,
                                   boolean trans, boolean locked,
				   float conferenceVolumeLevel) 
	throws IOException
    {
        if (getConferenceId() == null) {
            throw new IOException("Conference id not set");
        }
        
	connection.createWhisperGroup(getConferenceId(), whisperGroupId, 
                                     trans, locked, conferenceVolumeLevel);
        if (locked) {
            logger.fine("Adding " + whisperGroupId + " to lockingGroups");
            lockingGroups.add(whisperGroupId);
        }
    }

    /**
     * Destroy a whisper group
     */
    public void destroyWhisperGroup(String whisperGroupId)
	throws IOException
    {
        if (getConferenceId() == null) {
            throw new IOException("Conference id not set");
        }
        
	connection.destroyWhisperGroup(getConferenceId(), whisperGroupId);
        if (lockingGroups.remove(whisperGroupId)) {
            logger.fine("Removed " + whisperGroupId + " from lockingGroups");
        }
    }

    /*
     * Add this call to the whisper group
     */
    public void addCallToWhisperGroup(String whisperGroupId)
	throws IOException
    {
        synchronized(inGroups) {
            connection.addCallToWhisperGroup(whisperGroupId, callId);
            if (!inGroups.contains(whisperGroupId)) {
                inGroups.add(whisperGroupId);
            }
        }
    }

    /*
     * Remove this call from the whisper group
     */
    public void removeCallFromWhisperGroup(String whisperGroupId) 
	throws IOException
    {
        synchronized(inGroups) {
            if (!inGroups.remove(whisperGroupId)) {
                System.out.println("Attempted to remove call from un-joined " +
                        "whisper group: "+whisperGroupId);
            }
            connection.removeCallFromWhisperGroup(whisperGroupId, callId);
            // XXX null out current group if it matches
//            if (whisperGroupId.equals(this.whisperGroup)) {
//                this.whisperGroup = null;
//            }
        }
    }

    /**
     * Return whether or not whispering is enabled on this call control
     *
     * @return true if whispering is enabled, or false if not
     */
    public boolean isWhisperEnabled() {
        return whisperEnabled;
    }
    
    /**
     * Enable or disable whispering on this call
     *
     * @param enabled if true, this call can be added to whisper groups.  If
     * false it can't
     */
    public void setWhisperEnabled(boolean enabled) 
        throws IOException
    {
        connection.setWhisperEnabled(getCallId(), enabled);
        whisperEnabled = enabled;
    }
    
    /**
     * Mute or unmute this call in whisper groups.
     * @param isMuted boolean indicating whether to mute or not.
     */
    public void setWhisperMute(boolean isMuted) 
        throws IOException
    {
        connection.setWhisperMute(getCallId(), isMuted);
    }
    
    /**
     * Get the set of whisper groups this call control is a member of
     *
     * @return the whisper groups this call is a member of
     */
    public String[] getWhisperGroups() {
        synchronized(inGroups) {
            String[] ret = new String[inGroups.size()];
            ret = (String[])inGroups.toArray(ret);
            return ret;
        }
    }
    
    /**
     * Start whispering in the given whisper group.  Note this will
     * stop whispering in any other group the user is currently
     * whispering in.
     *
     * @param whisperGroup the whisper group to start whispering in
     */
    public void startWhispering(String whisperGroup) 
        throws IOException
    {
        // make sure we're not already whispering
        // stopWhispering();
        
        // set the whispering bit on the connection
        connection.setWhispering(whisperGroup, getCallId());
        this.whisperGroup = whisperGroup;
        
        // fake a status message
        Map options = new HashMap();
        options.put("CallId", getCallId());
        options.put("ConferenceId", conferenceId);
        CallStatus cs = CallStatus.getInstance("1.0", 
            CallStatus.STARTEDWHISPERING, options);
        connection.notifyListeners(cs);
    }
    
    /**
     * Determine the current whisper group this user is whispering in
     *
     * @return the whisper group the user is actively whispering in, or null
     * if the user is not whispering
     */
    public String getWhisperingGroup() {
        return whisperGroup;
    }
    
    /**
     * Stop whispering in the current whisper group
     */
    public void stopWhispering() 
        throws IOException
    {
        if (whisperGroup != null) {
            // set the bit on the connection
            connection.setWhispering(conferenceId, getCallId());
       
            // fake a status message
            Map options = new HashMap();
            options.put("CallId", getCallId());
            options.put("ConferenceId", conferenceId);
            CallStatus cs = CallStatus.getInstance("1.0", 
                CallStatus.STOPPEDWHISPERING, options);
            connection.notifyListeners(cs);              
        
            // forget the whisper group
            whisperGroup = null;
        }
    }
    
    public void endCall() throws IOException {
	endCall(getCallId());
    }
    
    public void endCall(String callId) throws IOException {
        if (connection != null) {
            connection.endCall(callId);
        }
    }

    public void callStatusChanged(CallStatus status) {
        if (status.getCallId().equals(getCallId())) {
            // set the values of starting up and established
            int code = status.getCode();
            if (code == status.ESTABLISHED) {
                isStartingUp = false;
                isEstablished = true;
            } else if (code == status.ENDED) {
                isEstablished = false;
                isStartingUp = false;
            } else if (code == status.MIGRATED || 
                       code == status.MIGRATE_FAILED) 
            {
                isStartingUp = false;
                isEstablished = true;
            }
            
            // update the conference ID
            conferenceId = status.getConferenceId();
            
            // notify listeners
            CallStatusListener[] listeners = null;
            synchronized(callStatusListeners) {
                listeners = new CallStatusListener[callStatusListeners.size()];
                callStatusListeners.toArray(listeners);
            }
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].callStatusChanged(status);
            }
        }
    }
      
    public void addCallStatusListener(CallStatusListener listener) {
        synchronized(callStatusListeners) {
            callStatusListeners.add(listener);
        }
    }
    
    public void removeCallStatusListener(CallStatusListener listener) {
        synchronized(callStatusListeners) {
            callStatusListeners.remove(listener);
        }
    }  
    
    /**
     * Record the audio of a call
     */
    public void recordCall(String recordingFile) throws IOException {
        if (connection != null) {
            connection.recordCall(recordingFile, getCallId());
        }
    }
    
    /**
     * Stop recording the audio of a call
     */
    public void stopRecordingCall() throws IOException {
        if (connection != null) {
            connection.stopRecordingCall(getCallId());
        }
    }
}
