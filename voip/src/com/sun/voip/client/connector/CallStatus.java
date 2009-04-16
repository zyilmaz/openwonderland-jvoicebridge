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

import java.io.Serializable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Represents call status, as returned from the bridge. Call status comes
 * to us as a string, something like:
 *
 * SIPDialer/1.0 100 INVITED CallId='8' ConferenceId='JonCon' CallInfo='22500'
 *
 * We split this into 5 fields:
 *   Originator:   SIPDialer/1.0
 *   Code:         100
 *   CallId:       8
 *   ConferenceId: JonCon
 *   CallInfo:     22500
 */
public class CallStatus implements Serializable {
    /** call code values */
    public static final int UNINITIALIZED   = 000;
    public static final int INVITED         = 100;
    public static final int ANSWERED        = 110;
    public static final int TIMEOUT         = 120;
    public static final int NOANSWER        = 127;
    public static final int ESTABLISHED     = 200;
    public static final int NUMBEROFCALLS   = 220;
    public static final int TREATMENTDONE   = 230;
    public static final int STARTEDSPEAKING = 250;
    public static final int STOPPEDSPEAKING = 259;
    public static final int DTMF_KEY        = 269;
    public static final int MIGRATED        = 270;
    public static final int MIGRATE_FAILED  = 275;
    public static final int STARTEDWHISPERING = 280;
    public static final int STOPPEDWHISPERING = 289;
    public static final int ENDING          = 290;
    public static final int ENDED           = 299;
    public static final int BUSY            = 486;
    public static final int BRIDGE_OFFLINE  = 666;
    public static final int MUTED	    = 937;
    public static final int UNMUTED	    = 938;
    public static final int INFO	    = 888;
    public static final int UNKNOWN         = 999;
    
    /** the version */
    private String version;
    
    /** the code */
    private int code;
    
    /** other options */
    private Map options;

    /** known keys */
    private static final String CALL_ID_KEY = "CallId";
    private static final String CONFERENCE_ID_KEY = "ConferenceId";
    private static final String CALL_INFO_KEY = "CallInfo";
    private static final String DTMF_KEY_KEY = "DTMFKey";
    private static final String INFO_KEY = "Info";
    
    /** 
     * Use <code>getInstance()</code> instead.
     */
    private CallStatus() {
    }
    
    /**
     * Get a new instance of <code>CallStatus</code>.
     * @param version the version number
     * @param code the code
     * @param options a map of key/value pairs for the other
     *        parameters
     */
    public static CallStatus getInstance(String version, int code, 
                                         Map options)
    {
        CallStatus cs = new CallStatus();
        cs.version = version;
        cs.code = code;
        cs.options = options;
        return cs;
    }
    
    /**
     * Get the version
     * @return the version
     */
    public String getVersion() {
        return version;
    }
    
    /**
     * Get the code
     * @return the code
     */
    public int getCode() {
        return code;
    }
    
    /**
     * Get the call id
     * @return the call id
     */
    public String getCallId() {
        return (String) options.get(CALL_ID_KEY);
    }
    
    /**
     * Get the conference id
     * @return the conference id
     */
    public String getConferenceId() {
        return (String) options.get(CONFERENCE_ID_KEY);
    }
    
    /**
     * Get the call info
     * @return the call info
     */
    public String getCallInfo() {
        return (String) options.get(CALL_INFO_KEY);
    }

    /**
     * Get the DTMF key for a DTMF key event
     * @return the dtmf key, or null
     */
    public String getDtmfKey() {
	return (String) options.get(DTMF_KEY_KEY);
    }
    
    /**
     * Get an arbitrary option from the status line
     * @param key the key from the status line
     * @return the value matching the given key, or null
     */
    public String getOption(String key) {
        return (String) options.get(key);
    }
    
    /**
     * Get all options, as a map from key to value
     * @return the map of options
     */
    public Map getOptions() {
        return Collections.unmodifiableMap(options);
    }
    
    /**
     * Map a call state into a string
     * @param state the call code number
     * @return the corresponding string, or "Unknown" if this is not 
     * a known code
     */
    public static String getCodeString(int code) {
        switch (code) {
            case UNINITIALIZED:
                return "Uninitialized";
            case INVITED:
                return "Invited";
            case ANSWERED:
                return "Answered";
            case TIMEOUT:
                return "Timeout";
            case NOANSWER:
                return "No Answer";
            case ESTABLISHED:
                return "Established";
            case NUMBEROFCALLS:
                return "Number of Calls";
            case TREATMENTDONE:
                return "Treatment Done";
            case STARTEDSPEAKING:
                return "Speaking";
            case STOPPEDSPEAKING:
                return "Stopped Speaking";
            case DTMF_KEY:
                return "DTMF Key";
            case MIGRATED:
                return "Migrated";
            case MIGRATE_FAILED:
                return "Migration Failed";
            case STARTEDWHISPERING:
                return "Started Whispering";
            case STOPPEDWHISPERING:
                return "Stopped Whispering";
            case ENDING:
                return "Ending";
            case ENDED:
                return "Ended";
	    case BRIDGE_OFFLINE:
		return "BridgeOffline";
            case BUSY:
                return "Busy";
	    case MUTED:
		return "Muted";
	    case UNMUTED:
		return "Unmuted";
	    case INFO:
		return "Info";
            default:
                return "Unknown";
        }
    }
    
    /**
     * Get a string representation of this status
     * @return a String version of this status
     */
    public String toString() {
        String str = "CallStatus: " + getCodeString(getCode()) + " Options:";
        for (Iterator i = options.entrySet().iterator(); i.hasNext();) {
            Map.Entry me = (Map.Entry) i.next();
            str += " " + me.getKey() + "=" + me.getValue();
        }
        
        return str;
    }
} 
