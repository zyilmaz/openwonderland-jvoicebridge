/*
 * IncomingCallHandler.java  (2004)
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * 
 * Unpublished - rights reserved under the Copyright Laws of the United States.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this document. In
 * particular, and without limitation, these intellectual property rights may
 * include one or more of the U.S. patents listed at http://www.sun.com/patents
 * and one or more additional patents or pending patent applications in the
 * U.S. and in other countries.
 * 
 * SUN PROPRIETARY/CONFIDENTIAL.
 * 
 * U.S. Government Rights - Commercial software. Government users are subject
 * to the Sun Microsystems, Inc. standard license agreement and applicable
 * provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties. Sun, Sun
 * Microsystems, the Sun logo, Java, Jini, Solaris and Sun Ray are trademarks
 * or registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries, exclusively
 * licensed through X/Open Company, Ltd.
 */

package com.sun.mpk20.voicelib.impl.service.voice;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;

import java.io.IOException;

import java.util.HashMap;

import java.util.logging.Logger;

/**
 * Listen for incoming calls, play treatments to prompt the caller for user id
 * and meeting id.  Transfer the call to the appropriate meeting.
 */
public class IncomingCallHandler extends Thread {
    /** a logger */
    private static final Logger logger =
            Logger.getLogger(IncomingCallHandler.class.getName());

    private static int defaultCallAnswerTimeout = 90;  // 90 seconds

    private HashMap<String, CallHandler> callTable = new HashMap();

    private static final int DEFAULT_TIMEOUT = 30000;
    private static int timeout = DEFAULT_TIMEOUT;

    private boolean needPassCode = true;

    private VoiceServiceImpl voiceService;
    private BridgeManager bridgeManager;

    private HashMap<String, String> conferenceMap = new HashMap();

    /**
     * Constructor.
     */
    public IncomingCallHandler(VoiceServiceImpl voiceService, 
	    BridgeManager bridgeManager) {

	this.voiceService = voiceService;
	this.bridgeManager = bridgeManager;
    }

    public void addConference(String conferenceId, String conferenceCode) {
	String[] tokens = conferenceId.split(":");

	synchronized (conferenceMap) {
	    conferenceMap.put(conferenceCode, tokens[0]);
	}
    }
	
    /*
     * Called when status for a call changes.
     */
    public boolean callStatusChanged(CallStatus status) {
	String callId = status.getCallId();

	int code = status.getCode();

	if (callId == null) {
	    return false;	// initial message doesn't have a CallId.
	}

	if (code == CallStatus.ESTABLISHED) {
	    String incomingCall = status.getOption("IncomingCall");

	    if (incomingCall == null || incomingCall.equals("false")) {
		return false;		// it's not an incoming call
	    }

	    /*
	     * New incoming call
	     */
	    String bridgeInfo = status.getOption("BridgeInfo");

	    BridgeConnection bc = bridgeManager.findBridge(bridgeInfo);

	    if (bc == null) {
		logger.warning("Can't find bridge connection for "
		    + bridgeInfo);
	  	return false;
	    }
		    
	    CallHandler callHandler = new CallHandler(callId, bc,
		status, timeout);

	    synchronized(callTable) {
	        callTable.put(callId, callHandler);
	    }

	    return true;
	}

 	/*
	 * Find existing call and send status
	 */
	CallHandler callHandler;

	synchronized(callTable) {
	    callHandler = callTable.get(callId);
	}

	if (callHandler == null) {
	    //logger.warning("No CallHandler for call " + callId);
	    return false;
	}

	callHandler.callStatusNotification(status);

        if (code == CallStatus.ENDED) {
	    synchronized(callTable) {
	        callTable.remove(callId);
	    }
	}

        return false;
    }

    private class CallHandler extends Thread {
	CallStatus establishedStatus;

        private String userId = "";
        private String meetingCode = "";
	private String passCode = "";

        private String callId;
 	private String conferenceId;
        private String name;

        private int state = WAITING_FOR_USER_ID;
	private int attemptCount = 0;
    
        private static final int WAITING_FOR_USER_ID      = 0;
        private static final int WAITING_FOR_MEETING_CODE = 1;
        private static final int WAITING_FOR_PASS_CODE    = 2;
        private static final int IN_MEETING		  = 3;
	private static final int CALL_ENDED		  = 4;

 	private static final String ENTER_USER_ID = "enter_user_id.au";
	private static final String BAD_USER_ID_1 = "bad_user_id_1.au";
	private static final String BAD_USER_ID_2 = "bad_user_id_2.au";

	private static final String ENTER_MEETING_CODE = 
	    "enter_meeting_code.au";

	private static final String BAD_MEETING_CODE_1 = 
	    "bad_meeting_code_1.au";

	private static final String BAD_MEETING_CODE_2 = 
	    "bad_meeting_code_2.au";

	private static final String BAD_MEETING_CODE_3 =
	    "bad_meeting_code_3.au";

	private static final String INVALID_PASS_CODE =
	    "badcode.au";

	private static final String INCOMING_TIMEOUT =
	    "incoming_timeout.au";

	private static final String ENTER_PASS_CODE =
	    "enter_pass_code.au";

	/*
	 * Messages for a Restricted Meeting
         */
	private static final String RESTRICTED_PASS_CODE =
	    "restricted.au";

	/*
	 * Messages for an Open Meeting
	 */
	private static final String ENTER_OPTIONAL_PASS_CODE =
	    "open.au";

	/*
	 * Messages for a Closed Meeting
	 */
	private static final String ENTER_REQUIRED_PASS_CODE =
	    "closed.au";

	private static final String PASS_CODE_REQUIRED =
	    "nocode.au";

	private static final String NO_ACCESS =
	    "noaccess.au";

	private static final String JOIN_CLICK = "joinCLICK.au";

        private String lastMessagePlayed;

	private BridgeConnection bc;

	public CallHandler(String callId, BridgeConnection bc, 
	        CallStatus establishedStatus, int timeout) {

	    this.callId = callId;
	    this.bc = bc;
	    this.establishedStatus = establishedStatus;

	    logger.warning("New Call Handler for call " + callId);

	    state = WAITING_FOR_USER_ID;

	    playTreatment(ENTER_USER_ID);

	    start();
	}

	public void run() {
	    /*
	     * Timeout handler to re-prompt user
	     */
	    long startTime = System.currentTimeMillis();

	    while (state == WAITING_FOR_USER_ID || 
		   state == WAITING_FOR_MEETING_CODE ||
		   state == WAITING_FOR_PASS_CODE) {

		int currentState = state;

		try {
		    Thread.sleep(timeout);

		    if (state != WAITING_FOR_USER_ID &&
			    state != WAITING_FOR_MEETING_CODE &&
			    state != WAITING_FOR_PASS_CODE) {

			break;
		    }

		    if (currentState == state) {
			if (System.currentTimeMillis() - startTime >=
			        defaultCallAnswerTimeout * 1000) {
			    
			    playTreatment(INCOMING_TIMEOUT);

			    //
			    // TODO (maybe)
			    //
			    // We'd like to wait until the treatment is done
			    // before cancelling the call.
			    // Need a way to specify an end treatment after
			    // the call is started.
			    //
			    try {
				Thread.sleep(5000);	
			    } catch (InterruptedException e) {
			    }

			    cancelCall(callId);
			    break;
			}

			playTreatment(lastMessagePlayed);
		    }
		} catch (InterruptedException e) {
		    logger.warning("Interrupted!");
		}
	    }
	}

        private void cancelCall(String callId) {
	    try {
		Thread.sleep(8000);
	    } catch (InterruptedException e) {
	    }

	    try {
	        bc.endCall(callId);
	    } catch (IOException e) {
		logger.warning("Unable to end call " + callId
		    + " " + e.getMessage());
	    }
	}

	private void playTreatment(String treatment) {
	    try {
	        bc.playTreatmentToCall(callId, treatment);
	        lastMessagePlayed = treatment;
	    } catch (IOException e) {
		logger.warning("Unable to play treatment " 
		    + treatment + " to call " + callId
		    + " " + e.getMessage());
	    }
	}

	public void callStatusNotification(CallStatus status) {
	    int code = status.getCode();

            if (code == CallStatus.ENDED) {
                logger.warning("Call ended, leaving the meeting...");
		state = CALL_ENDED;
                leaveMeeting();
                return;
            }

            int ix;

	    /*
	     * We're only interested in dtmf keys
	     */
            if (code != CallStatus.DTMF_KEY) {
		return;
	    }

            String dtmfKey = status.getDtmfKey();
	    
            if (state == WAITING_FOR_USER_ID) {
		getUserId(dtmfKey);
	    } else if (state == WAITING_FOR_MEETING_CODE) {
		getMeetingCode(dtmfKey);
	    } else if (state == WAITING_FOR_PASS_CODE) {
		getPassCode(dtmfKey);
	    }
        }

	private void getUserId(String dtmfKey) {
            if (!dtmfKey.equals("#")) {
                if (userId.length() == 0 && dtmfKey.equals("*")) {
                    userId += "?";          // replace * with ?
                } else {
                    userId += dtmfKey;      // accumulate userId
                }
                return;
            }

            attemptCount++;

            logger.warning("Hello " + userId);

            state = WAITING_FOR_MEETING_CODE;

            playTreatment(ENTER_MEETING_CODE);

            attemptCount = 0;
	}

	private void getMeetingCode(String dtmfKey) {
            if (!dtmfKey.equals("#")) {
                meetingCode += dtmfKey;  // accumulate meeting code
                return;
            }

            attemptCount++;

            if ((conferenceId = findConference()) != null) {
                logger.warning("Found conference " + conferenceId);

                if (needPassCode == false) {
                    if (transferCall() == true) {
                        state = IN_MEETING;
                        return;
                    }

                    meetingCode = "";
                    return;
                }

                state = WAITING_FOR_PASS_CODE;

                // XXX
                //
                // if (meeting.isRestricted() {
                //     playTreatment(ENTER_RESTRICTED_PASS_CODE);
                // } else if (meeting.isOpen() {
                //     playTreatment(ENTER_OPTIONAL_PASS_CODE);
                // } else {
                //     playTreatment(ENTER_REQUIRED_PASS_CODE);
                // }

                // XXX
                playTreatment(ENTER_OPTIONAL_PASS_CODE);

                attemptCount = 0;
                return;
            }

	    playTreatment(BAD_MEETING_CODE_1);

            meetingCode = "";
	}

	private void getPassCode(String dtmfKey) {
            if (!dtmfKey.equals("#")) {
		passCode += dtmfKey;   // accumulate pass code
		return;
	    }

	    attemptCount++;

	    logger.warning("Got pass code:  " + passCode);

	    /*
	     * For now, allow people to join without a passCode
	     */ 
	    if (passCode.length() == 0) {
                // if (meeting.isClosed() {
                //     playTreatment(PASS_CODE_REQUIRED);
		//	   attemptCount = 0;
		//	   passCode = "";
		//	   return;
                // }

		if (transferCall() == true) {
		    state = IN_MEETING;
		    return;
		}

		state = WAITING_FOR_MEETING_CODE;
                attemptCount = 0;
                meetingCode = "";
                passCode = "";
                return;
	    }

	    int intPassCode;

	    try {
		intPassCode = Integer.parseInt(passCode);
	    } catch (NumberFormatException e) {
                playTreatment(INVALID_PASS_CODE);

		passCode = "";
		return;
	    }

            if (transferCall() == true) {
                state = IN_MEETING;
                return;
            }

	    attemptCount = 0;
	    passCode = "";
	    state = WAITING_FOR_USER_ID;
	}

	/*
	 * Find the conference
         */
	private String findConference() {
            logger.warning("Looking for conference with meeting code: " 
		+ meetingCode);

	    synchronized (conferenceMap) {
	        return conferenceMap.get(meetingCode);
	    }
	}

	private boolean transferCall() {
	    try {
                logger.warning("Transferring call " + callId 
		    + " to conference " + conferenceId);

                bc.muteCall(callId, false);
		bc.transferCall(callId, conferenceId);
		voiceService.callStatusChanged(establishedStatus, false);

		playTreatment(JOIN_CLICK);
            } catch (IOException e) {
                logger.warning(e.getMessage());
                return false;
            }

	    return true;
        }

	/*
	 * The call has ended, leave the meeting
	 */
        private void leaveMeeting() {
        }

    }

}
