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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractService;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TransactionScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.Spatializer;
import com.sun.mpk20.voicelib.app.DefaultSpatializer;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupSetup;
import com.sun.mpk20.voicelib.app.BridgeInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentSetup;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceService;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;

import java.io.IOException;
import java.io.Serializable;

import java.text.ParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;

/**
 * This is an implementation of <code>VoiceService</code> that works on a
 * single node. 
 *
 * @since 1.0
 * @author Joe Provino
 */
public class VoiceServiceImpl extends AbstractService implements VoiceService,
	CallStatusListener, NonDurableTransactionParticipant {

    private ThreadLocal<ArrayList<Work>> localWorkToDo =
       new ThreadLocal<ArrayList<Work>>() {
           protected ArrayList<Work> initialValue() {
               return new ArrayList<Work>();
           }
       }; 

    /**
     * The identifier used for this <code>Service</code>.
     */
    public static final String NAME = VoiceServiceImpl.class.getName();

    // logger for this class
    private static final LoggerWrapper logger = 
	new LoggerWrapper(Logger.getLogger(NAME));

    /**
     * The name prefix used to bind all service-level objects associated
     * with this service.
     */
    public static final String DS_PREFIX = NAME + ".";

    /* The name of the list for call status listeners */
    private static final String DS_CALL_STATUS_LISTENERS = 
	DS_PREFIX + "CallStatusListeners";

    // flags indicating that configuration has been done successfully,
    // and that we're still in the process of configuring
    private boolean isConfigured = false;
    private boolean isConfiguring = false;

    // the system's task scheduler, where tasks actually run
    private TransactionScheduler transactionScheduler;

    // the state map for each active transaction
    private ConcurrentHashMap<Transaction,TxnState> txnMap;

    private BridgeManager bridgeManager;

    /**
     * Creates an instance of <code>VoiceServiceImpl</code>. 
     *
     * @param properties startup properties
     * @param systemRegistry the registry of system components
     */

    /*
     * Service interface .95 darkstar server.
     */
    public VoiceServiceImpl(Properties properties,
                            ComponentRegistry systemRegistry,
			    TransactionProxy txnProxy) {

	super(properties, systemRegistry, txnProxy, logger);

        txnMap = new ConcurrentHashMap<Transaction,TxnState>();

        // the scheduler is the only system component that we use
        transactionScheduler = systemRegistry.getComponent(TransactionScheduler.class);

	bridgeManager = new BridgeManager(this);
	bridgeManager.configure(properties);
    }

    public String getTypeName() {
	return "VoiceService";
    }

    public BridgeInfo getVoiceBridge() throws IOException {
	return bridgeManager.getVoiceBridge();
    }

    public void createCall(CallSetup setup) throws IOException {
	getTxnState();

	Work work = new Work(Work.SETUPCALL);
	work.cp = setup.cp;
	work.bridgeInfo = setup.bridgeInfo;

        localWorkToDo.get().add(work);
    }

    public void muteCall(String callId, boolean isMuted) throws IOException {
	getTxnState();

	Work work = new Work(Work.MUTECALL, callId);
	work.isMuted = isMuted;

	localWorkToDo.get().add(work);
    }

    public void transferCall(CallParticipant cp) throws IOException {
	getTxnState();

	Work work = new Work(Work.MIGRATECALL);
	work.cp = cp;

	localWorkToDo.get().add(work);
    }

    public void transferToConference(String callId, String conferenceId) 
	    throws IOException {

	bridgeManager.transferCall(callId, conferenceId);
    }

    public void playTreatmentToCall(String callId, String treatment) 
	    throws IOException {

	getTxnState();

	Work work = new Work(Work.PLAYTREATMENTTOCALL, callId);
	work.treatment = treatment;

	localWorkToDo.get().add(work);
    }

    public void pauseTreatmentToCall(String callId, String treatment) 
	    throws IOException {

	getTxnState();

	Work work = new Work(Work.PAUSETREATMENTTOCALL, callId);
	work.treatment = treatment;

	localWorkToDo.get().add(work);
    }

    public void stopTreatmentToCall(String callId, String treatment) 
	    throws IOException {

	getTxnState();

	Work work = new Work(Work.STOPTREATMENTTOCALL, callId);
	work.treatment = treatment;

	localWorkToDo.get().add(work);
    }

    public void endCall(String callId) throws IOException {
	try {
	    bridgeManager.getBridgeConnection(callId);
	} catch (IOException e) {
	    logger.log(Level.INFO, "can't find connection for " + callId
		+ " " + e.getMessage());

	    return;	// nothing to do
	}

	logger.log(Level.INFO, "ending call " + callId);

	getTxnState();

	Work work = new Work(Work.ENDCALL, callId);

	localWorkToDo.get().add(work);
    }

    public void setPrivateMix(String targetCallId, String fromCallId,
	    double[] privateMixParameters) throws IOException {

	getTxnState();

	if (targetCallId == null) {
	    throw new IOException("Invalid targetCallId " + targetCallId);
	}

	if (fromCallId == null) {
	    throw new IOException("Invalid fromCallId " + fromCallId);
	}

	logger.log(Level.FINEST, "setPrivateMix for " + targetCallId 
	    + " from " + fromCallId
	    + " privateMixParameters: " + privateMixParameters[0] 
	    + "," + privateMixParameters[1] + "," + privateMixParameters[2]
	    + "," + privateMixParameters[3]);

	Work work = new Work(Work.SETPRIVATEMIX, targetCallId);
	work.fromCallId = fromCallId;
	work.privateMixParameters = privateMixParameters;

	//    localWorkToDo.get().add(work);

	bridgeManager.setPrivateMix(work);
    }

    public void monitorConference(String conferenceId) throws IOException {
	bridgeManager.monitorConference(conferenceId);
    }

    public void setLogLevel(Level level) {
	logger.getLogger().setLevel(level);
    }

    public void setSpatialAudio(boolean enabled) throws IOException {
	bridgeManager.setSpatialAudio(enabled);
    }

    public void setSpatialMinVolume(double spatialMinVolume) 
	    throws IOException {

	bridgeManager.setSpatialMinVolume(spatialMinVolume);
    }

    public void setSpatialFalloff(double spatialFalloff) throws IOException {
	bridgeManager.setSpatialFalloff(spatialFalloff);
    }

    public void setSpatialEchoDelay(double spatialEchoDelay) 
	    throws IOException {

	bridgeManager.setSpatialEchoDelay(spatialEchoDelay);
    }

    public void setSpatialEchoVolume(double spatialEchoVolume) 
	    throws IOException {

	bridgeManager.setSpatialEchoVolume(spatialEchoVolume);
    }

    public void setSpatialBehindVolume(double spatialBehindVolume) 
	    throws IOException {

	bridgeManager.setSpatialBehindVolume(spatialBehindVolume);
    }

    public void newInputTreatment(String callId, String treatment) 
	    throws IOException {

	getTxnState();

	Work work = new Work(Work.NEWINPUTTREATMENT, callId);

	work.treatment = treatment;

	localWorkToDo.get().add(work);
    }

    public void stopInputTreatment(String callId) throws IOException {
	getTxnState();

	localWorkToDo.get().add(new Work(Work.STOPINPUTTREATMENT, callId));
    }

    public void restartInputTreatment(String callId) throws IOException {
	getTxnState();

	localWorkToDo.get().add(new Work(Work.RESTARTINPUTTREATMENT, callId));
    }

    public void startRecording(String callId, String recordingFile) throws IOException {
	getTxnState();

	Work work = new Work(Work.STARTRECORDING, callId);
	work.recordingFile = recordingFile;

	localWorkToDo.get().add(work);
    }

    public void pauseRecording(String callId, String recordingFile) throws IOException {
	logger.log(Level.WARNING, "pauseRecording is not yet implemented");
    }

    public void stopRecording(String callId, String recordingFile) throws IOException {
	getTxnState();

	Work work = new Work(Work.STOPRECORDING, callId);
	work.recordingFile = null;

	localWorkToDo.get().add(work);
    }

    public void playRecording(String callId, String recordingFile) throws IOException {
	newInputTreatment(callId, recordingFile);
    }

    public void pausePlayingRecording(String callId, String recordingFile) throws IOException {
	logger.log(Level.WARNING, "pausePlayingRecording is not yet implemented");
    }


    public void stopPlayingRecording(String callId, String recordingFile) 
	throws IOException {
    }

    public void addCallStatusListener(ManagedCallStatusListener mcsl) {
	logger.log(Level.WARNING, "addCallStatusListener " + mcsl);

        CallStatusListeners listeners = getCallStatusListeners();

	/*
	 * Create a reference to mcsl and keep that.
	 */
        synchronized (listeners) {
	    listeners.add(dataService.createReference(mcsl));
	    logger.log(Level.WARNING, "VS:  listeners size " + listeners.size());
	}
    }

    public void removeCallStatusListener(ManagedCallStatusListener mcsl) {
	logger.log(Level.WARNING, "removeCallStatusListener " + mcsl);

        CallStatusListeners listeners = getCallStatusListeners();

	synchronized (listeners) {
	    listeners.remove(dataService.createReference(mcsl));
	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void doReady() {
	logger.log(Level.INFO, "Voice Service is ready.");

        transactionScheduler.scheduleTask(new ReadyNotifier(), taskOwner); 
    }

    private class ReadyNotifier implements KernelRunnable {
        public ReadyNotifier() {
        }

        public String getBaseTaskType() {
            return ReadyNotifier.class.getName();
        }

        public void run() throws Exception {
            /*
             * This runs in a transaction and the txnProxy
             * is usable at this point.  It's okay to get a manager
             * or another service.
             *
             * This method could get called multiple times if
             * ExceptionRetryStatus is thrown.
             */
	    AppContext.getManager(VoiceManager.class).ready();	
	}
    }


    @Override
    protected void handleServiceVersionMismatch(Version oldVersion,
                                                Version currentVersion) {
        throw new IllegalStateException(
                    "unable to convert version:" + oldVersion +
                    " to current version:" + currentVersion);
    }

    /*
     * Get voice bridge notification and pass it along.
     * When called, this is not in a transaction.
     *
     * The work here must be done in a transaction.
     */
    public void callStatusChanged(CallStatus status) {
	logger.log(Level.FINEST, "Call status changed:  " + status);

	/*
	 * Treat bridge shutdown as a failed bridge which
	 * the watchdog will detect.
	 */
	if (status.toString().indexOf("System shutdown") >= 0) {
	    return;
	}

        if (status.getCallId() != null && 
		status.getCode() == CallStatus.ESTABLISHED) {

            String incomingCall = status.getOption("IncomingCall");

            if (incomingCall != null && incomingCall.equals("true")) {
		bridgeManager.putCallConnection(status);
            }
	}

        transactionScheduler.scheduleTask(new CallStatusNotifier(status), taskOwner); 
    }

    /**
     * {@inheritDoc}
     */
    public boolean prepare(Transaction txn) throws Exception {
        logger.log(Level.FINEST, "prepare");

        // resolve the current transaction and the local state
        TxnState txnState = txnMap.get(txn);

        // make sure that we're still actively participating
        if (txnState == null) {
            logger.log(Level.FINER, "not participating in txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + "is no " +
                                            "longer participating in this " +
                                            "transaction");
        }

        // make sure that we haven't already been called to prepare
        if (txnState.prepared) {
            logger.log(Level.FINER, "already prepared for txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + " has " +
                                            "already been prepared");
        }

        // mark ourselves as being prepared
        txnState.prepared = true;

        logger.log(Level.FINEST, "prepare txn succeeded " + txn);
        
        // if we joined the transaction it's because we have reserved some
        // task(s) or have to cancel some task(s) so always return false

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void commit(Transaction txn) {
        logger.log(Level.FINEST, "VS:  committing txn: " + txn);

        // see if we we're committing the configuration transaction
        if (isConfiguring) {
            isConfigured = true;
            isConfiguring = false;
        }

        // resolve the current transaction and the local state, removing the
        // state so we can't accidentally use it further in the future
        TxnState txnState = txnMap.remove(txn);

        // make sure that we're still actively participating
        if (txnState == null) {
            logger.log(Level.WARNING, "not participating in txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + "is no " +
                                            "longer participating in this " +
                                            "transaction");
        }

        // make sure that we were already called to prepare
        if (! txnState.prepared) {
            logger.log(Level.WARNING, "were not prepared for txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + " has " +
                                            "not been prepared");
        }

        ArrayList<Work> workToDo;

        workToDo = localWorkToDo.get();

	logger.log(Level.FINEST, "workToDo size " + workToDo.size());

	for (int i = 0; i < workToDo.size(); i++) {
	    Work work = workToDo.get(i);

	    switch (work.command) {
	    case Work.SETUPCALL:
		if (work.cp.getPhoneNumber() != null) {
		    logger.log(Level.FINER, "VS:  Setting up call to "
			+ work.cp.getPhoneNumber() + " on bridge " 
			+ work.bridgeInfo);
		} else {
		    logger.log(Level.FINER, "VS:  Setting up call to "
			+ work.cp.getInputTreatment());
		}

		String callId = work.cp.getCallId();

		try {
		    bridgeManager.initiateCall(work.cp, work.bridgeInfo);
		} catch (IOException e) {
		    logger.log(Level.INFO, e.getMessage());

		    /*
		     * TODO:  There's needs to be a way to tell the difference
		     * between a fatal and a recoverable error.
		     */
		    bridgeManager.addToRecoveryList(work.cp.getCallId(), 
			work.cp);
		} catch (ParseException e) {
		    logger.log(Level.INFO, "Unable to setup call:  " + e.getMessage()
			+ " " + work.cp);

		    sendStatus(CallStatus.INFO, work.cp.getCallId(), 
			"Unable to setup call:  " + e.getMessage() + " " 
			+ work.cp);
		}
		break;

	    case Work.SETPRIVATEMIX:
		// XXX Shouldn't get here.  Private Mixes are applied immediately
                logger.log(Level.FINEST, "commit setting private mix for "
                    + work.targetCallId + " from " + work.fromCallId
                    + " privateMixParameters "
                    +  work.privateMixParameters[0] + ":"
                    +  work.privateMixParameters[1] + ":"
                    +  work.privateMixParameters[2] + ":"
                    +  work.privateMixParameters[3]);

		bridgeManager.setPrivateMix(work);
		break;

	    case Work.NEWINPUTTREATMENT:
		try {
		    bridgeManager.newInputTreatment(work.targetCallId,
			work.treatment);
		} catch (IOException e) {
		    logger.log(Level.WARNING, "Unable to start input treatment "
			+ work.treatment + " for " + work.targetCallId
			+ " " + e.getMessage());
	        }
		break;

	    case Work.STOPINPUTTREATMENT:
		try {
		    bridgeManager.stopInputTreatment(work.targetCallId);
		} catch (IOException e) {
		    logger.log(Level.WARNING, "Unable to stop input treatment for "
			+ work.targetCallId + " " + e.getMessage());
		}
		break;

	    case Work.RESTARTINPUTTREATMENT:
		try {
		    bridgeManager.restartInputTreatment(work.targetCallId);
		} catch (IOException e) {
		    logger.log(Level.WARNING, "Unable to restart input treatment for "
			+ work.targetCallId + " " + e.getMessage());
		}
		break;

	    case Work.PLAYTREATMENTTOCALL:
		try {
		    bridgeManager.playTreatmentToCall(work.targetCallId, 
		        work.treatment);
		} catch (IOException e) {
		    logger.log(Level.INFO, "Unable to play treatment " 
			+ work.treatment + " to call " + work.targetCallId
			+ " " + e.getMessage());
		}
		break;

	    case Work.PAUSETREATMENTTOCALL:
		try {
		    bridgeManager.pauseTreatmentToCall(work.targetCallId, 
		        work.treatment);
		} catch (IOException e) {
		    logger.log(Level.INFO, "Unable to pause treatment " 
			+ work.treatment + " to call " + work.targetCallId
			+ " " + e.getMessage());
		}
		break;

	    case Work.STOPTREATMENTTOCALL:
		try {
		    bridgeManager.stopTreatmentToCall(work.targetCallId, 
		        work.treatment);
		} catch (IOException e) {
		    logger.log(Level.INFO, "Unable to stop treatment " 
			+ work.treatment + " to call " + work.targetCallId
			+ " " + e.getMessage());
		}
		break;

	    case Work.MIGRATECALL:
		try {
		    bridgeManager.migrateCall(work.cp);
		} catch (IOException e) {
		    logger.log(Level.INFO, "Unable to migrate to call " 
			+ work.targetCallId
			+ " " + e.getMessage());

		    sendStatus(CallStatus.MIGRATE_FAILED, work.targetCallId,
			e.getMessage());
		}
		break;

	    case Work.ENDCALL:
		try {
		    bridgeManager.endCall(work.targetCallId);
		} catch (IOException e) {
		    logger.log(Level.INFO, "Unable to end call " + work.targetCallId
			+ " " + e.getMessage());
		}
		break;

	    case Work.MUTECALL:
		try {
 		    bridgeManager.muteCall(work.targetCallId, work.isMuted);
		} catch (IOException e) {
		    logger.log(Level.INFO, "Unable to " 
			+ (work.isMuted ? " Mute " : " Unmute ")
			+ " call " + work.targetCallId
			+ " " + e.getMessage());
		}
		break;

	    case Work.STARTRECORDING:
		try {
		    bridgeManager.startRecording(work.targetCallId,
			work.recordingFile);
		} catch (IOException e) {
		    logger.log(Level.WARNING, "Unable to start recording "
			+ work.recordingFile + " for " + work.targetCallId
			+ " " + e.getMessage());
	        }
		break;

	    case Work.STOPRECORDING:
		try {
		    bridgeManager.stopRecording(work.targetCallId);
		} catch (IOException e) {
		    logger.log(Level.WARNING, "Unable to stop recording "
			+ work.targetCallId + " " + e.getMessage());
		}
		break;

	    default:
		logger.log(Level.WARNING, "Unknown work command " + work.command);
	    }
	}

	localWorkToDo.get().clear();

	bridgeManager.commit();

        logger.log(Level.FINEST, "commit txn succeeded " + txn);
    }

    /**
     * {@inheritDoc}
     */
    public void prepareAndCommit(Transaction txn) throws Exception {
        logger.log(Level.FINEST, "prepareAndCommit on txn: " + txn);

        prepare(txn);
        commit(txn);
    }

    /**
     * {@inheritDoc}
     */
    public void abort(Transaction txn) {
	logger.log(Level.INFO, txn.getAbortCause().getMessage());

        localWorkToDo.get().clear();

        // resolve the current transaction and the local state, removing the
        // state so we can't accidentally use it further in the future
        TxnState txnState = txnMap.remove(txn);

        // make sure that we were participating
        if (txnState == null) {
            logger.log(Level.WARNING, "not participating txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + "is " +
                                            "not participating in this " +
                                            "transaction");
        }
    }

    @Override
    public void doShutdown() {
	logger.log(Level.INFO, "Shutdown Voice service");
    }

    /**
     * Private helper that gets the transaction state, or creates it (and
     * joins to the transaction) if the state doesn't exist.
     */
    private TxnState getTxnState() {
        // resolve the current transaction and the local state
        Transaction txn = txnProxy.getCurrentTransaction();
        TxnState txnState = txnMap.get(txn);

        // if it didn't exist yet then create it and join the transaction
        if (txnState == null) {
            txnState = new TxnState();
            txnMap.put(txn, txnState);
            txn.join(this);
        } else {
            // if it's already been prepared then we shouldn't be using
            // it...note that this shouldn't be a problem, since the system
            // shouldn't let this case get tripped, so this is just defensive
            if (txnState.prepared) {
                logger.log(Level.WARNING, "already prepared txn: " + txn);

                throw new IllegalStateException("Trying to access prepared " +
                                                "transaction for scheduling");
            }
        }

        return txnState;
    }

    private CallStatusListeners getCallStatusListeners() {
	CallStatusListeners listeners = null;

	try {
	    listeners = (CallStatusListeners) dataService.getServiceBinding(
		DS_CALL_STATUS_LISTENERS);
	} catch (NameNotBoundException e) {
	    listeners = new CallStatusListeners();

	    try {
		dataService.setServiceBinding(DS_CALL_STATUS_LISTENERS, 
		    listeners);
	    }  catch (RuntimeException re) {
                logger.log(Level.WARNING, "failed to bind pending map " + re.getMessage());
                throw re;
            }
	}

        return (CallStatusListeners) dataService.getServiceBinding(
	    DS_CALL_STATUS_LISTENERS);
    }

    /**
     * Inner class that is used to track state associated with a single
     * transaction. This is indexed in the local transaction map.
     */
    private static final class TxnState {
	public boolean prepared = false;
    }

    private void sendStatus(int statusCode, String callId, String info) {
        String s = "SIPDialer/1.0 " + statusCode + " "
            + CallStatus.getCodeString(statusCode)
            + " CallId='" + callId + "'"
            + " CallInfo='" + info + "'";

	CallStatus callStatus = null;

	try {
	    callStatus = BridgeConnection.parseCallStatus(s);
	} catch (IOException e) {
	}

	if (callStatus == null) {
	    logger.log(Level.INFO, "Unable to parse call status:  " + s);
	    return;
	}

	callStatusChanged(callStatus);
    }

    private static final class CallStatusListeners extends 
	    ArrayList<ManagedReference> implements ManagedObject,
	    Serializable {

	 private static final long serialVersionUID = 1;
    }

    private class CallStatusNotifier implements KernelRunnable {
	private final CallStatus status;

	public CallStatusNotifier(CallStatus status) {
	    this.status = status;
	}

	public String getBaseTaskType() {
	    return CallStatusNotifier.class.getName();
	}

	public void run() throws Exception {
	    /*
	     * This runs in a transaction and the txnProxy
	     * is usable at this point.  It's okay to get a manager
	     * or another service.
	     *
	     * This method could get called multiple times if
	     * ExceptionRetryStatus is thrown.
	     */
	    CallStatusListeners listeners = getCallStatusListeners();

	    ManagedReference[] listenerList;

	    synchronized (listeners) {
	        listenerList = listeners.toArray(new ManagedReference[0]);
	    }

	    for (int i = 0; i < listenerList.length; i++) {
	        ManagedCallStatusListener mcsl = (ManagedCallStatusListener)
		    listenerList[i].get();

		logger.log(Level.WARNING, "Notifying listener " + i + " status " + status);

	        try {
		    mcsl.callStatusChanged(status);
	        } catch (IllegalStateException e) {
		    logger.log(Level.INFO, "Can't send status:  " + status 
		        + " " + e.getMessage() + " removed listener");
	        }
	    }
        }

    }

}
