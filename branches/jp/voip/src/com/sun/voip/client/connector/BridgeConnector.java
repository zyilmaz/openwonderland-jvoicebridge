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

package com.sun.voip.client.connector;

import java.io.IOException;
import com.sun.voip.CallParticipant;

/**
 * Bridge Service Interface
 * These methods provide a way to set up, manage and 
 * terminate calls and conferences.
 *
 * Calls are set up by using a socket to the conference Bridge software.
 */
public interface BridgeConnector {

    /**
     * Creates a conference abstraction.
     * @param conferenceId the conference id to use
     * @param name the display name of the conference
     * @param quality the audio quality to use
     */
    public void createConference(String conferenceId, String name,
                                 String quality) 
        throws IOException;
    
    /**
     * Removes the conference abstraction and disconnects any calls that may
     * be using it.
     * @param conferenceId the conferenceId to remove
     */
    public void removeConference(String conferenceId) throws IOException;
    
    /**
     * Creates a call control object that can be used to place and manipulate
     * an individual call.  Note that a <code>CallControl</code> can only
     * be used once.  Calling <code>placeCall()</code> more than once will
     * raise an exception.
     * 
     * @param conferenceId the conferenceId to create the call in
     */
    public CallControl createCallControl(String conferenceId);
   
    /**
     * Creates a call control object that can be used to place and manipulate
     * an individual call.  Note that a <code>CallControl</code> can only
     * be used once.  Calling <code>placeCall()</code> more than once will
     * raise an exception.
     * 
     * @param callId the callId of the call to create or null to have one
     * automatically generated
     * @param conferenceId the conferenceId to create the call in
     */
    public CallControl createCallControl(String callId, String conferenceId);
    
    /**
     * Get a call control object for the given call id
     *
     * @param callId the callId of the given call
     * @return a call control for the given id, or null if none exists
     */
    public CallControl getCallControl(String callId);
    
    /**
     * Play an audio file to all calls in a conference.
     * @param conferenceId the conference to play the treatment to
     * @param treatment String identifying path of treatment file.
     */
    public void playTreatmentToConference(String conferenceId, String treatment)
	throws IOException;

    /**
     * Play an audio file when someone enters a conference.
     * @param conferenceId the conference to set the answer treatment for
     * @param treatment String identifying path of treatment file.
     */
    public void setAnswerTreatment(String conferenceId, String treatment) 
        throws IOException;
    
    /**
     * Record the audio of a conference.  This includes all audio from
     * all calls in the conference.  Individual calls can elect not to be
     * recorded when the call is set up.
     * @param conferenceId String conference identifier
     * @param recordingFile String with absolute path of file to
     *			    contain the recording.
     */
    public void recordConference(String conferenceId, String recordingFile) 
        throws IOException;

    /**
     * Stop recording a conference.
     * @param conferenceId String conference identifier
     */
    public void stopRecordingConference(String conferenceId) 
	throws IOException;
    
    /**
     * Create a whisper group with the given name
     * @param conferenceId the conference to create the whisper group in
     * @param name the name of the whisper group 
     */
    public void createWhisperGroup(String conferenceId, String name) 
        throws IOException;

    /**
     * Create a whisper group with the given name and options
     * @param conferenceId the conference to create the whisper group in
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
	throws IOException;
    
    /**
     * Destroy a whisper group
     * @param conferenceId the conference to remove the whisper group from
     * @param name the name of the whisper group to destroy
     */
    public void destroyWhisperGroup(String conferenceId, String name)
        throws IOException;
    
    /**
     * Add a call to an existing whisper group
     *
     * @param whisperGroup the name of the whisper group to add the call to
     * @param callId the callId to add to the whisper group
     */
    public void addCallToWhisperGroup(String whisperGroup, String callId)
        throws IOException;
    
    /**
     * Remove a call from a whisper group
     *
     * @param whisperGroup the name of the whisper group to remove the call from
     * @param callId the callId to remove from the whisper group
     */
    public void removeCallFromWhisperGroup(String whisperGroup, String callId)
        throws IOException;
     
    /**
     * Disconnect from the bridge and clean up any resources that are in use.
     */
    public void disconnect();
}
