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

import com.sun.mpk20.voicelib.impl.service.voice.work.Work;
import com.sun.mpk20.voicelib.impl.service.voice.work.audiogroup.*;
import com.sun.mpk20.voicelib.impl.service.voice.work.call.*;
import com.sun.mpk20.voicelib.impl.service.voice.work.listener.*;
import com.sun.mpk20.voicelib.impl.service.voice.work.player.*;
import com.sun.mpk20.voicelib.impl.service.voice.work.recorder.*;
import com.sun.mpk20.voicelib.impl.service.voice.work.treatment.*;
import com.sun.mpk20.voicelib.impl.service.voice.work.treatmentgroup.*;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;

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
import com.sun.mpk20.voicelib.app.DefaultSpatializer;
import com.sun.mpk20.voicelib.app.DefaultSpatializers;
import com.sun.mpk20.voicelib.app.CallBeginEndListener;
import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.ManagedCallBeginEndListener;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentGroup;
import com.sun.mpk20.voicelib.app.TreatmentSetup;
import com.sun.mpk20.voicelib.app.VirtualPlayerListener;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceService;
import com.sun.mpk20.voicelib.app.VoiceBridgeParameters;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;

import java.io.IOException;
import java.io.Serializable;

import java.text.ParseException;

import java.util.ArrayList;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.prefs.Preferences;

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

    //private VoiceImpl voiceImpl;

    private static VoiceServiceImpl voiceServiceImpl;

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

	voiceServiceImpl = this;

	VoiceImpl.getInstance().initialize();
    }

    public static VoiceServiceImpl getInstance() {
	return voiceServiceImpl;
    }

    public String getTypeName() {
	return "VoiceService";
    }

    public boolean addWork(Work work) {
	try {
	    getTxnState();
	} catch (TransactionNotActiveException e) {
	    return false;
	}

	localWorkToDo.get().add(work);
	return true;
    }

    public String getConferenceId() {
	return VoiceImpl.getInstance().getConferenceId();
    }

    public BridgeManager getBridgeManager() {
	return bridgeManager;
    }

    public void monitorConference(String conferenceId) throws IOException {
	if (conferenceId == null) {
	    logger.log(Level.FINER, "Null conferenceId ignored.");
	    return;
	}

	bridgeManager.monitorConference(conferenceId);
    }

    public void setLogLevel(Level level) {
	logger.getLogger().setLevel(level);
    }

    public Level getLogLevel() {
	return logger.getLogger().getLevel();
    }

    public void setVoiceBridgeParameters(
	    VoiceBridgeParameters voiceBridgeParameters) {

	VoiceImpl.getInstance().setVoiceBridgeParameters(voiceBridgeParameters);
    }
	
    public void setVoiceManagerParameters(
	    VoiceManagerParameters voiceManagerParameters) {

	VoiceImpl.getInstance().setVoiceManagerParameters(voiceManagerParameters);
    }
	
    public VoiceManagerParameters getVoiceManagerParameters() {
	return VoiceImpl.getInstance().getVoiceManagerParameters();
    }

    /*
     * Call Control
     */
    public BridgeInfo getVoiceBridge() throws IOException {
	return bridgeManager.getVoiceBridge();
    }

    public Call createCall(String id, CallSetup setup) throws IOException {
	DataManager dm = AppContext.getDataManager();

        WarmStartCalls warmStartCalls;

        try {
            warmStartCalls = (WarmStartCalls)
		dm.getBinding(WarmStartInfo.DS_WARM_START_CALLS);
        } catch (NameNotBoundException e) {
            try {
                warmStartCalls = new WarmStartCalls();
                dm.setBinding(WarmStartInfo.DS_WARM_START_CALLS, warmStartCalls);
            }  catch (RuntimeException re) {
                logger.log(Level.WARNING, "failed to bind map for warm starting calls " 
		    + re.getMessage());
		throw new IOException("failed to bind map for warm starting calls " 
                    + re.getMessage());
            }
        }

	Call call = new CallImpl(id, setup);
        warmStartCalls.put(id, setup.bridgeInfo);
	return call;
    }

    public void endCall(Call call) {
	DataManager dm = AppContext.getDataManager();

	try {
            WarmStartCalls warmStartCalls = (WarmStartCalls)
                dm.getBinding(WarmStartInfo.DS_WARM_START_CALLS);

	    warmStartCalls.remove(call.getId());
        } catch (NameNotBoundException e) {
	    logger.log(Level.WARNING, "failed to bind map for warm starting calls "
                + e.getMessage());
	}
    }

    public Call getCall(String id) {
	return VoiceImpl.getInstance().getCall(id);
    }

    public Call[] getCalls() {
	return VoiceImpl.getInstance().getCalls();
    }

    public void endCall(Call call, boolean removePlayer) throws IOException {
	call.end(removePlayer);
    }

    public void addCallStatusListener(CallStatusListener listener) {
	addCallStatusListener(listener, null);
    }

    public void addCallStatusListener(CallStatusListener listener, String callId) {
	VoiceImpl.getInstance().addCallStatusListener(listener, callId);
    }

    public void removeCallStatusListener(CallStatusListener listener) {
	removeCallStatusListener(listener, null);
    }

    public void removeCallStatusListener(CallStatusListener listener, String callId) {
	VoiceImpl.getInstance().removeCallStatusListener(listener, callId);
    }

    public void addCallBeginEndListener(CallBeginEndListener listener) {
	VoiceImpl.getInstance().addCallBeginEndListener(listener);
    }

    public void removeCallBeginEndListener(CallBeginEndListener listener) {
	VoiceImpl.getInstance().removeCallBeginEndListener(listener);
    }

    /*
     * Player Control
     */
    public Player createPlayer(String id, PlayerSetup setup) {
	getTxnState();

	Player player = getPlayer(id);

	if (player != null) {
	    return player;
	}

	return new PlayerImpl(id, setup);
    }

    public Player getPlayer(String id) {
	return VoiceImpl.getInstance().getPlayer(id);
    }

    public Player[] getPlayers() {
	return VoiceImpl.getInstance().getPlayers();
    }

    public void removePlayer(Player player) {
	addWork(new RemovePlayerWork(player));
    }

    public int getNumberOfPlayersInRange(double x, double y, double z) {
	return VoiceImpl.getInstance().getInstance().getNumberOfPlayersInRange(x, y, z);
    }

    /*
     * Audiogroup Control
     */
    public AudioGroup createAudioGroup(String id, AudioGroupSetup setup) {
	getTxnState();

        AudioGroup audioGroup = getAudioGroup(id);

        if (audioGroup != null) {
            return audioGroup;
        }

	return new AudioGroupImpl(id, setup);
    }

    public AudioGroup getAudioGroup(String id) {
	return VoiceImpl.getInstance().getAudioGroup(id);
    }

    public void removeAudioGroup(AudioGroup audioGroup) {
	addWork(new RemoveAudioGroupWork(audioGroup));
    }

    /*
     * Treatment Control
     */
    public TreatmentGroup createTreatmentGroup(String id) {
	getTxnState();

	TreatmentGroup treatmentGroup = getTreatmentGroup(id);

	if (treatmentGroup != null) {
	    return treatmentGroup;
	}

        DataManager dm = AppContext.getDataManager();

        WarmStartTreatmentGroups warmStartTreatmentGroups = null;

        try {
            warmStartTreatmentGroups = (WarmStartTreatmentGroups)
                dm.getBinding(WarmStartInfo.DS_WARM_START_TREATMENTGROUPS);
        } catch (NameNotBoundException e) {
            try {
                warmStartTreatmentGroups = new WarmStartTreatmentGroups();
                dm.setBinding(WarmStartInfo.DS_WARM_START_TREATMENTGROUPS, warmStartTreatmentGroups);
            }  catch (RuntimeException re) {
                logger.log(Level.WARNING, "failed to bind map for warm starting treatments "
                    + re.getMessage());
            }
        }

        warmStartTreatmentGroups.add(id);
	return new TreatmentGroupImpl(id);
    }

    public TreatmentGroup getTreatmentGroup(String id) {
	return VoiceImpl.getInstance().getTreatmentGroup(id);
    }

    public void removeTreatmentGroup(TreatmentGroup treatmentGroup) {
	addWork(new RemoveTreatmentGroupWork(treatmentGroup));
    }

    public Treatment createTreatment(String id, TreatmentSetup setup) throws IOException {
	DataManager dm = AppContext.getDataManager();

        WarmStartTreatments warmStartTreatments;

        try {
            warmStartTreatments = (WarmStartTreatments)
		dm.getBinding(WarmStartInfo.DS_WARM_START_TREATMENTS);
        } catch (NameNotBoundException e) {
            try {
                warmStartTreatments = new WarmStartTreatments();
                dm.setBinding(WarmStartInfo.DS_WARM_START_TREATMENTS, warmStartTreatments);
            }  catch (RuntimeException re) {
                logger.log(Level.WARNING, "failed to bind map for warm starting treatments " 
		    + re.getMessage());
		throw new IOException("failed to bind map for warm starting treatments " 
                    + re.getMessage());
            }
        }

        warmStartTreatments.put(id, new WarmStartTreatmentInfo(setup));
	return new TreatmentImpl(id, setup);
    }

    public Treatment getTreatment(String id) {
	return VoiceImpl.getInstance().getTreatment(id);
    }

    /*
     * Recorder control
     */
    public Recorder createRecorder(String id, RecorderSetup setup) throws IOException {
        DataManager dm = AppContext.getDataManager();

        WarmStartRecorders warmStartRecorders;

        try {
            warmStartRecorders = (WarmStartRecorders)
                dm.getBinding(WarmStartInfo.DS_WARM_START_RECORDERS);
        } catch (NameNotBoundException e) {
            try {
                warmStartRecorders = new WarmStartRecorders();
                dm.setBinding(WarmStartInfo.DS_WARM_START_RECORDERS, warmStartRecorders);
            }  catch (RuntimeException re) {
                logger.log(Level.WARNING, "failed to bind map for warm starting Recorders "
                    + re.getMessage());
                throw new IOException("failed to bind map for warm starting Recorders "
                    + re.getMessage());
            }
        }

        warmStartRecorders.put(id, setup);

	return new RecorderImpl(id, setup);
    }

    public Recorder getRecorder(String id) {
	return VoiceImpl.getInstance().getRecorder(id);
    }

    /*
     * Spatial audio Control
     */
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

    public void addWall(double startX, double startY, double endX, double endY,
	    double endZ) {

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

	try {
	    transactionScheduler.runTask(new ReadyNotifier(), taskOwner);
	} catch (Exception e) {
	    System.out.println("Exception trying to warm start:  " + e.getMessage());
	}
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
            boolean warmStart = false;

	    DataManager dm = AppContext.getDataManager();

            try {
                dm.getBinding(WarmStartInfo.DS_WARM_START_CALLS);
	        warmStart = true;
            } catch (NameNotBoundException e) {
	    }

            try {
                dm.getBinding(WarmStartInfo.DS_WARM_START_TREATMENTGROUPS);
	        warmStart = true;
            } catch (NameNotBoundException e) {
	    }

            try {
                dm.getBinding(WarmStartInfo.DS_WARM_START_TREATMENTS);
	        warmStart = true;
            } catch (NameNotBoundException e) {
	    }

            try {
                dm.getBinding(WarmStartInfo.DS_WARM_START_RECORDERS);
	        warmStart = true;
            } catch (NameNotBoundException e) {
	    }

	    if (warmStart) {
                new WarmStart(VoiceImpl.getInstance());
	    } else {
		System.out.println("There is nothing to warm start");
		//VoiceImpl.getInstance().foo();
	    }
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

	//System.out.println("VS:  Commit");

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
	//System.out.println("VS: WORK TO DO SIZE " + workToDo.size());

	for (int i = 0; i < workToDo.size(); i++) {
	    Work work = workToDo.get(i);

	    if (work instanceof RecorderWork) {
		RecorderWork w = (RecorderWork) work;
		RecorderImpl r = (RecorderImpl) w.recorder;
		r.commit(w);
	    } else if (work instanceof CallWork) {
		CallWork w = (CallWork) work;
		CallImpl c = (CallImpl) w.call;
		c.commit(w);
	    } else if (work instanceof PlayerWork) {
		PlayerWork w = (PlayerWork) work;
		PlayerImpl p = (PlayerImpl) w.player;
		p.commit(w);
	    } else if (work instanceof AudioGroupWork) {
		AudioGroupWork w = (AudioGroupWork) work;
		AudioGroupImpl a = (AudioGroupImpl) w.audioGroup;
		a.commit(w);
	    } else if (work instanceof TreatmentGroupWork) {
		TreatmentGroupWork w = (TreatmentGroupWork) work;
		TreatmentGroupImpl t = (TreatmentGroupImpl) w.treatmentGroup;
		t.commit(w);
	    } else if (work instanceof TreatmentWork) {
		TreatmentWork w = (TreatmentWork) work;
		TreatmentImpl t = (TreatmentImpl) w.treatment;
		t.commit(w);
	    } else if (work instanceof ListenerWork) {
		ListenerWork w = (ListenerWork) work;
		VoiceImpl.getInstance().commit(w);
	    } else {
		 logger.log(Level.WARNING, "Unknown work to do:  " + work);
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
	//System.out.println("VS:  Abort " + txn.getAbortCause().getMessage());

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

    public boolean inTransaction() {
	try {
            return txnProxy.getCurrentTransaction() != null;
	} catch (Exception e) {
	    return false;
	}
    }

    public String dump(String command) {
	return VoiceImpl.getInstance().dump(command);
    }

    public void testUDPPort(String host, int port, int duration) throws IOException {
	BridgeConnection bc = bridgeManager.getBridgeConnection();

	bc.sendMessage("testUDP=" + host + ":" + port + ":" + duration + "\n");
    }

    /**
     * Inner class that is used to track state associated with a single
     * transaction. This is indexed in the local transaction map.
     */
    private static final class TxnState {
	public boolean prepared = false;
    }

    public void scheduleTask(KernelRunnable runnable) {
	scheduleTask(runnable, 0);
    }

    public void scheduleTask(KernelRunnable runnable, long startTime) {
	if (startTime == 0) {
            transactionScheduler.scheduleTask(runnable, taskOwner); 
	} else {
            transactionScheduler.scheduleTask(runnable, taskOwner, startTime); 
	}
    }

    public void joinTransaction(NonDurableTransactionParticipant participant) {
	txnProxy.getCurrentTransaction().join(participant);
    }

    private class CallStatusNotifier implements KernelRunnable, NonDurableTransactionParticipant {

	private final CallStatus status;

	public CallStatusNotifier(CallStatus status) {
	    this.status = status;
	}

	public String getBaseTaskType() {
	    return CallStatusNotifier.class.getName();
	}

	public void run() throws Exception {
            txnProxy.getCurrentTransaction().join(this);

	    /*
	     * This runs in a transaction and the txnProxy
	     * is usable at this point.  It's okay to get a manager
	     * or another service.
	     *
	     * This method could get called multiple times if
	     * ExceptionRetryStatus is thrown.
	     */
	    VoiceImpl.getInstance().callStatusChanged(status);
        }

        public boolean prepare(Transaction txn) throws Exception {
            return false;
	}

        public void abort(Transaction t) {
	}

	public void prepareAndCommit(Transaction txn) throws Exception {
            prepare(txn);
            commit(txn);
	}

	public void commit(Transaction t) {
	    VoiceImpl.getInstance().callStatusChanged(status);
	}

        public String getTypeName() {
	    return "CallStatusNotifier";
	}

    }

}
