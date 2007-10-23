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

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;

import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.Spatializer;
import com.sun.mpk20.voicelib.app.DefaultSpatializer;

import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.io.IOException;
import java.io.Serializable;

import java.text.ParseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;
import com.sun.voip.client.connector.impl.VoiceBridgeConnection;

/**
 * This is an implementation of <code>VoiceService</code> that works on a
 * single node. 
 *
 * @since 1.0
 * @author Joe Provino
 */
public class VoiceServiceImpl implements VoiceManager, Service,
	CallStatusListener, BridgeOfflineListener, NonDurableTransactionParticipant {

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
    private static final Logger logger = Logger.getLogger(NAME);

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
    private TaskScheduler taskScheduler = null;

    // a proxy providing access to the transaction state
    private static TransactionProxy transactionProxy = null;

    // the data service used in the same context
    private DataService dataService = null;

    // the task serviced using in the same context
    private TaskService taskService = null;

    // the state map for each active transaction
    private ConcurrentHashMap<Transaction,TxnState> txnMap;

    // the transient map for all recurring tasks' handles
    private ConcurrentHashMap<String, RecurringTaskHandle> recurringMap;

    private ArrayList<BridgeConnection> bridgeConnections = 
	new ArrayList<BridgeConnection>();

    private ConcurrentHashMap<String, CallParticipant> recoveryList =
	new ConcurrentHashMap<String, CallParticipant>();

    /*
     * Map of callId's to callInfo
     */
    private ConcurrentHashMap<String, CallInfo> callConnectionMap =
	new ConcurrentHashMap<String, CallInfo>();

    private Properties properties;

    private TaskOwner defaultOwner;

    private PrivateMixHandler privateMixHandler;

    private BridgeOnlineWatcher bridgeOnlineWatcher;

    private Reconnector reconnector;

    /**
     * Creates an instance of <code>VoiceServiceImpl</code>. 
     *
     * @param properties startup properties
     * @param systemRegistry the registry of system components
     */
    public VoiceServiceImpl(Properties properties,
                            ComponentRegistry systemRegistry) {

	this.properties = properties;

        txnMap = new ConcurrentHashMap<Transaction,TxnState>();
        recurringMap = new ConcurrentHashMap<String,RecurringTaskHandle>();

        // the scheduler is the only system component that we use
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

	privateMixHandler = new PrivateMixHandler(this, callConnectionMap);

	bridgeOnlineWatcher = new BridgeOnlineWatcher(this);

	reconnector = new Reconnector(this);
    }

    public ConcurrentHashMap<String, CallParticipant> getRecoveryList() {
	return recoveryList;
    }

    public void monitorConference(String conferenceId) throws IOException {
	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
	        bc.monitorConference(conferenceId);
	    }
	}
    }

    public String getVoiceBridge() {
	BridgeConnection bc;

	try {
	    bc = getBridgeConnection();
	} catch (IOException e) {
	    return "";
	}

	return bc.getPublicAddress();
    }

    public void setupCall(CallParticipant cp, double x, double y, double z,
	    double orientation, Spatializer spatializer, String bridgeInfo) 
	    throws IOException {

	getTxnState();

        localWorkToDo.get().add(new Work(cp, bridgeInfo));

	//dump("Setup call to " + cp.toString());
    }

    public void setSpatializer(String callId, Spatializer spatializer) {
    }

    public void setPrivateSpatializer(String sourceCallId, String targetCallId,
	    Spatializer spatializer) {
    }

    public void callEstablished(String callId) throws IOException {
	logger.fine("call established:  " + callId);
    }

    public void newInputTreatment(String callId, String treatment) 
	    throws IOException {

	getTxnState();

	localWorkToDo.get().add(new Work(Work.NEWINPUTTREATMENT, callId, 
	    treatment));
    }

    public void stopInputTreatment(String callId) throws IOException {
	getTxnState();

	localWorkToDo.get().add(new Work(Work.STOPINPUTTREATMENT, callId, 
	    null));
    }

    public void restartInputTreatment(String callId) throws IOException {
	getTxnState();

	localWorkToDo.get().add(new Work(Work.RESTARTINPUTTREATMENT, callId, 
	    null));
    }

    public void restorePrivateMixes() throws IOException {
    }

    public void playTreatmentToCall(String callId, String treatment) 
	    throws IOException {

	BridgeConnection bc = getBridgeConnection(callId);

	bc.playTreatmentToCall(callId, treatment);
    }

    public void pauseTreatmentToCall(String callId, String treatment)
	     throws IOException {

	BridgeConnection bc = getBridgeConnection(callId);

	bc.pauseTreatmentToCall(callId, treatment);
    }

    public void stopTreatmentToCall(String callId, String treatment)
	     throws IOException {

	BridgeConnection bc = getBridgeConnection(callId);

	bc.stopTreatmentToCall(callId, treatment);
    }

    public void disconnectCall(String callId) throws IOException {
	endCall(callId);
    }

    public void endCall(String callId) throws IOException {
	endCall(null, callId, true);
    }

    public void endCall(String callId, boolean tellBridge) throws IOException {
	endCall(null, callId, tellBridge);
    }

    private void endCall(BridgeConnection bc, String callId, boolean tellBridge) 
	    throws IOException {

	logger.info("call ended:  " + callId + " on " + bc);

	if (tellBridge) {
	    if (bc == null) {
                bc = getBridgeConnection(callId);
	    }

	    try {
 	        bc.endCall(callId);
	    } catch (IOException e) {
	        logger.info("Unable to end call:  " + e.getMessage());
	    }
	}

	callConnectionMap.remove(callId);

	privateMixHandler.endCall(callId);
    }

    public void muteCall(String callId, boolean isMuted) throws IOException {
        BridgeConnection bc = getBridgeConnection(callId);
 	bc.muteCall(callId, isMuted);
    }

    public void setSpatialAudio(boolean enabled) throws IOException {
	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
	        bc.setSpatialAudio(enabled);
	    }
	}
    }

    public void setSpatialMinVolume(double spatialMinVolume) 
	    throws IOException {

	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
	        bc.setSpatialMinVolume(spatialMinVolume);
	    }
	}
    }

    public void setSpatialFallOff(double spatialFallOff) throws IOException {
	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
	        bc.setSpatialFallOff(spatialFallOff);
	    }
	}
    }

    public void setSpatialEchoDelay(double spatialEchoDelay) throws IOException {
	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
	        bc.setSpatialEchoDelay(spatialEchoDelay);
	    }
	}
    }

    public void setSpatialEchoVolume(double spatialEchoVolume) throws IOException {
	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
	        bc.setSpatialEchoVolume(spatialEchoVolume);
	    }
	}
    }

    public void setSpatialBehindVolume(double spatialBehindVolume) 
	    throws IOException {

	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
	        bc.setSpatialBehindVolume(spatialBehindVolume);
	    }
	}
    }

    public void setPrivateMix(String sourceCallId, String targetCallId,
	    double[] privateMixParameters) throws IOException {

	getTxnState();

	if (sourceCallId == null) {
	    throw new IOException("Invalid sourceCallId " + sourceCallId);
	}

	if (targetCallId == null) {
	    throw new IOException("Invalid targetCallId " + targetCallId);
	}

	logger.finest("setPrivateMix for source " 
	    + sourceCallId + " target " + targetCallId
	    + " privateMixParameters: " + privateMixParameters[0] 
	    + "," + privateMixParameters[1] + "," + privateMixParameters[2]
	    + "," + privateMixParameters[3]);

	localWorkToDo.get().add(
	    new Work(sourceCallId, targetCallId, privateMixParameters));
    }

    public void setPositionAndOrientation(String callId, double x, double y, 
	    double z, double orientation) throws IOException {

    }

    public void setPosition(String callId, double x, double y, double z)
	    throws IOException {

    }
	
    public void setOrientation(String callId, double orientation)
	    throws IOException {

    }

    public void setAttenuationRadius(String callId, double attenuationRadius)
	    throws IOException {
    
    }
  
    public void setAttenuationVolume(String callId, double attenuationVolume)
	    throws IOException {
    
    }

    public void addWall(double startX, double startY, 
	    double endX, double endY, double characteristic) throws IOException{
    }

    public DefaultSpatializer getDefaultSpatializer() {
	return null;
    }

    public void setParameters(VoiceManagerParameters parameters) {
    }

    public VoiceManagerParameters getParameters() {
	return new VoiceManagerParameters();
    }

    public void setLogLevel(Level level) {
	logger.info("Setting log level to " + level);
	logger.setLevel(level);
    }

    public void addCallStatusListener(ManagedCallStatusListener mcsl) {
	logger.finest("VS:  addCallStatusListener " + mcsl);

        CallStatusListeners listeners =
            dataService.getServiceBinding(DS_CALL_STATUS_LISTENERS,
		    CallStatusListeners.class);

	/*
	 * Create a reference to mcsl and keep that.
	 */
        synchronized (listeners) {
	    listeners.add(dataService.createReference(mcsl));
	    logger.finest("VS:  listeners size " + listeners.size());
	}
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    public void configure(ComponentRegistry serviceRegistry,
                          TransactionProxy transactionProxy) {
        if (isConfigured) {
            throw new IllegalStateException("Voice Service already configured");
	}

        isConfiguring = true;

        logger.finer("configure");

        if (transactionProxy == null) {
            throw new NullPointerException("null proxy not allowed");
	}

        // keep track of the proxy, the data service, and the task service
        this.transactionProxy = transactionProxy;
        dataService = serviceRegistry.getComponent(DataService.class);
        taskService = serviceRegistry.getComponent(TaskService.class);

	defaultOwner = transactionProxy.getCurrentOwner();

	logger.info("Default owner is " + defaultOwner);

        String s = properties.getProperty(
            "com.sun.server.impl.services.voice.BRIDGE_SERVERS");

        if (s != null) {
            logger.info("Using specified bridge servers:  " + s);

            String[] bridgeServers = s.split(",");

            for (int i = 0; i < bridgeServers.length; i++) {
                try {
                    connect(bridgeServers[i]);
                } catch (IOException e) {
                    logger.info(e.getMessage());
                }
            }
        }

	boolean firstTime = true;

	if (bridgeConnections.size() == 0) {
	    logger.info("There are currently no voice bridges available");
	}

	CallStatusListeners listeners = null;

	try {
	    listeners = dataService.getServiceBinding(DS_CALL_STATUS_LISTENERS,
		CallStatusListeners.class);
	} catch (NameNotBoundException e) {
	    listeners = new CallStatusListeners();

	    try {
		dataService.setServiceBinding(DS_CALL_STATUS_LISTENERS, 
		    listeners);
	    }  catch (RuntimeException re) {
                logger.warning("failed to bind pending map " + re.getMessage());
                throw re;
            }
	}

	logger.fine("Done configuring voice.");
    }

    public void connect(String bridgeServer) throws IOException {
	String[] tokens = bridgeServer.split(":");

        String privateHost = tokens[0];

	try {
            privateHost =
                InetAddress.getByName(tokens[0]).getHostAddress();
	} catch (UnknownHostException e) {
            throw new IOException("Unknown host " + tokens[0]);
        }

	bridgeServer = privateHost;

	int sipPort = 5060;
	int controlPort = 6666;

	if (tokens.length >= 2) {
	    try {
		sipPort = Integer.parseInt(tokens[1]);
	    } catch (NumberFormatException e) {
		logger.warning("Invalid sip port:  " + bridgeServer
		    + ".  Defaulting to " + sipPort);
	    }
	}

	bridgeServer += ":" + sipPort;

	if (tokens.length >= 3) {
	    try {
		controlPort = Integer.parseInt(tokens[2]);
	    } catch (NumberFormatException e) {
		logger.warning("Invalid control port:  " + bridgeServer
		    + ".  Defaulting to " + controlPort);
	    }
	}

	bridgeServer += ":" + controlPort;

	BridgeConnection bc = findBridge(bridgeServer);

	if (bc != null) {
	    /*
	     * This is a voice bridge we already knew about.
	     */
	    if (bc.isConnected()) {
		/*
		 * It's already connected.  Tell the bridge connector
		 * we got a ping from the bridge.
		 */
		logger.finest("got ping from " + bc);
		bc.gotBridgePing();
		return;
	    }

	    /*
	     * This happens if the watchdog isn't running.
	     */
	    bridgeOffline(bc);

	    logger.info("Disconnected bridge is now back online: " 
		+ bridgeServer);
	} else {
	    logger.info("Bridge " + (bridgeConnections.size() + 1) 
	        + " came online:  '" + bridgeServer + "'");
	}

	try {
	    /*
	     * This connection is used to send commands to the bridge
	     * and get status
	     */
	    bc = new BridgeConnection(privateHost, sipPort, controlPort, true);

	    bc.addCallStatusListener(this);

	    bc.addBridgeOfflineListener(this);

	    synchronized (bridgeConnections) {
		for (BridgeConnection bridgeConnection : bridgeConnections) {
		    bc.monitorConferences(bridgeConnection.getConferences());
		}
	    }
	} catch (IOException e) {
	    throw new IOException("Unable to connect to bridge "
		+ privateHost + ":" + sipPort + ":" + controlPort 
		+ " " + e.getMessage());
	}

	synchronized (bridgeConnections) {
	    if (bc.isConnected() == false) {
		logger.info("New bridge " + bc + " is no longer connected!");
		return; 
	    }

	    bridgeConnections.add(bc);

	    if (bridgeConnections.size() == 1) {
		/*
		 * If there are calls to reconnect, do it now.
		 */
		synchronized (recoveryList) {
		    if (recoveryList.size() > 0) {
			logger.info("A bridge is now online, recover "
			    + recoveryList.size() + " calls");
		        recoveryList.notifyAll();
		    }
		}
	    }

	    bridgeConnections.notifyAll();
	}

	//dump("bridge online " + bc);
    }

    public ArrayList<BridgeConnection> getBridgeConnections() {
	return bridgeConnections;
    }

    private BridgeConnection findBridge(String bridgeInfo) {
	String[] tokens = bridgeInfo.split(":");

	synchronized (bridgeConnections) {
	    for (BridgeConnection bc : bridgeConnections) {
		String s = bc.toString();

		String[] t = s.split(":");

		if (tokens[0].equals(t[0]) && tokens[1].equals(t[1])) {
		    return bc;
		}	
	    }
	}

	return null;
    }

    private BridgeConnection getBridgeConnection() throws IOException {
	BridgeConnection bridgeConnection = null;

	synchronized (bridgeConnections) {
	    if (bridgeConnections.size() == 0) {
	        throw new IOException("There are no voice bridges available!");
	    }

	    for (BridgeConnection bc : bridgeConnections) {
	        if (bc.isConnected() == false) {
	            logger.warning("Skipping Voice bridge " + bc 
		        + " which is DOWN!");

		    continue;
	        }

	        if (bridgeConnection == null) {
		    bridgeConnection = bc;
	        } else {
		    /*
		     * Choose bridge with least number of calls
		     */
	            if (bc.getNumberOfCalls() < bridgeConnection.getNumberOfCalls()) {
		        bridgeConnection = bc;
	            }
	    	}
	    } 
	}

	if (bridgeConnection == null) {
	    throw new IOException("There are no voice bridges available!");
	}

	return bridgeConnection;
    }

    public BridgeConnection getBridgeConnection(String callId) throws IOException {

	return getBridgeConnection(callId, false);
    }

    private BridgeConnection getBridgeConnection(String callId, boolean allocate) 
	    throws IOException {

	CallInfo callInfo = callConnectionMap.get(callId);

	if (callInfo == null) {
	    if (allocate == false) {
	        throw new IOException("Cannot find bridgeConnection for " + callId);
	    }

	    return getBridgeConnection();
	}

	return callInfo.bridgeConnection;
    }

    /*
     * Get voice bridge notification and pass it along.
     * When called, this is not in a transaction or one of the
     * DarkStar threads.  In the future, we should use DarkStar threads.
     *
     * The work here must be done in a transaction.
     */
    public void callStatusChanged(CallStatus status) {
	logger.fine("VS:  Call status changed:  " + status + " " + this);

	if (status == null) {
	    return;
	}

	String callId = status.getCallId();
	
	if (callId == null) {
	    return;
	}

	if (status.getCode() == CallStatus.ESTABLISHED) {
	    privateMixHandler.callEstablished(callId);
	} else if (status.getCode() == CallStatus.ENDED) {
	    synchronized (bridgeConnections) {
		synchronized (callConnectionMap) {
		    try {
			endCall(callId, false);
		    } catch (IOException e) {
			logger.warning(e.getMessage());
		    }
		}
	    }
	} 

	/*
	 * Treat bridge shutdown as a failed bridge which
	 * the watchdog time will detect.
	 */
	if (status.toString().indexOf("System shutdown") < 0) {
	    sendStatus(null, callId, status);
	}
    }

    public void sendStatus(CallParticipant cp, String callId, CallStatus status) {
        CallStatusNotifier notifier = new CallStatusNotifier(cp, status);
        taskScheduler.scheduleTask(new TransactionRunner(notifier), defaultOwner);
    }

    /**
     * {@inheritDoc}
     */
    public boolean prepare(Transaction txn) throws Exception {
        logger.finest("prepare");

        // resolve the current transaction and the local state
        TxnState txnState = txnMap.get(txn);

        // make sure that we're still actively participating
        if (txnState == null) {
            logger.finer("not participating in txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + "is no " +
                                            "longer participating in this " +
                                            "transaction");
        }

        // make sure that we haven't already been called to prepare
        if (txnState.prepared) {
            logger.finer("already prepared for txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + " has " +
                                            "already been prepared");
        }

        // mark ourselves as being prepared
        txnState.prepared = true;

        logger.finest("prepare txn succeeded " + txn);
        
        // if we joined the transaction it's because we have reserved some
        // task(s) or have to cancel some task(s) so always return false

        return false;
    }

    public void bridgeOffline(BridgeConnection bc) {
        synchronized (bridgeConnections) {
            logger.info("Removing bridge connection for offline bridge " + bc);

            if (bridgeConnections.contains(bc) == false) {
		/*
		 * Another voice service is already handling this.
		 */
		return;
	    }

	    bridgeConnections.remove(bc);
	}

	ArrayList<CallParticipant> calls = bc.getCallParticipantArray();

	bc.disconnect();

	/*
	 * Notify other bridges that a bridge went down
	 */
	synchronized (bridgeConnections) {
            for (BridgeConnection bridgeConnection: bridgeConnections) {
		bridgeConnection.bridgeOffline(bc);
	    }
	}

	if (calls.size() == 0) {
	    logger.info("No calls on " + bc + " to reconnect");
	    return;
	}

	for (int i = 0; i < calls.size(); i++) {
	    CallParticipant cp = calls.get(i);

	    String callId = cp.getCallId();

	    try {
		//dump("Before ending " + callId);
	        endCall(bc, callId, true);
		//dump("After ending " + callId);
	    } catch (IOException e) {
		logger.info("Unable to end call " + callId);
	    }
	}

	/*
	 * Schedule calls to be reconnected when there's a bridge available
	 */
	synchronized (recoveryList) {
	    logger.info("Adding " + bc + " calls to recovery list so " 
		+ calls.size() + " calls will be reconnected later");

	    for (CallParticipant c : calls) {
	        recoveryList.put(c.getCallId(), c);
	    }

	    recoveryList.notifyAll();
	}
    }

    private void dump(String msg) {
	logger.info(msg);

	synchronized (bridgeConnections) {
	    synchronized (callConnectionMap) {
		doDump(msg);
	    }
	}
    }

    private void doDump(String msg) {
	logger.info("Bridge connections " + bridgeConnections.size());

	for (BridgeConnection bc : bridgeConnections) {
	    dumpBridgeConnection(bc);
	}

	privateMixHandler.dumpVirtualCallMap();

	Enumeration<String> keys = callConnectionMap.keys();

	logger.info("Call Connection Map " + callConnectionMap.size());

	while (keys.hasMoreElements()) {
            String callId = keys.nextElement();

	    CallInfo callInfo = callConnectionMap.get(callId);

	    if (callInfo == null) {
		logger.info("callInfo is null for " + callId + "!");
	    } else {
	        logger.info("  " + callInfo);
	    }
	}

	logger.info("Calls waiting to be reconnected");

	Collection<CallParticipant> values = recoveryList.values();

        Iterator<CallParticipant> iterator = values.iterator();

        while (iterator.hasNext()) {
	    CallParticipant cp = iterator.next();
	    logger.info("  " + cp);
	}
    }

    private void dumpBridgeConnection(BridgeConnection bc) {
        ArrayList<CallParticipant> cpArray = bc.getCallParticipantArray();

        logger.info("  " + bc.toString()
            + (bc.isConnected() ? " Connected " : " NOT Connected ")
            + cpArray.size());

        for (int i = 0; i < cpArray.size(); i++) {
            logger.info("    " + cpArray.get(i));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void commit(Transaction txn) {
        logger.finest("committing txn: " + txn);

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
            logger.warning("not participating in txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + "is no " +
                                            "longer participating in this " +
                                            "transaction");
        }

        // make sure that we were already called to prepare
        if (! txnState.prepared) {
            logger.warning("were not prepared for txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + " has " +
                                            "not been prepared");
        }

        ArrayList<Work> workToDo = localWorkToDo.get();

	for (int i = 0; i < workToDo.size(); i++) {
	    Work work = workToDo.get(i);

	    switch (work.command) {
	    case Work.SETUPCALL:
		if (work.cp.getPhoneNumber() != null) {
		    logger.finer("VS:  Setting up call to "
			+ work.cp.getPhoneNumber() + " on bridge " 
			+ work.bridgeInfo);
		} else {
		    logger.finer("VS:  Setting up call to "
			+ work.cp.getInputTreatment());
		}

		String callId = work.cp.getCallId();

		try {
		    initiateCall(work.cp, work.bridgeInfo);
		} catch (IOException e) {
		    logger.info(e.getMessage());
		    logger.info("Adding " + work.cp + " to recovery list");

	    	    synchronized (recoveryList) {
			recoveryList.put(work.cp.getCallId(), work.cp);
			recoveryList.notifyAll();
	    	    }
		} catch (ParseException e) {
		    logger.info("Unable to setup call:  " + e.getMessage()
			+ " " + work.cp);
		}
		break;

	    case Work.SETPRIVATEMIX:
                logger.finest("commit setting private mix for"
                    + " source " + work.sourceCallId
                    + " target " + work.targetCallId
                    + " privateMixParameters "
                    +  work.privateMixParameters[0] + ":"
                    +  work.privateMixParameters[1] + ":"
                    +  work.privateMixParameters[2] + ":"
                    +  work.privateMixParameters[3]);

		privateMixHandler.setPrivateMix(work);
		break;

	    case Work.NEWINPUTTREATMENT:
		try {
		    BridgeConnection bc = getBridgeConnection(
			work.targetCallId, true);

		    bc.newInputTreatment(work.targetCallId,
			work.treatment);
		} catch (IOException e) {
		    logger.warning("Unable to start input treatment "
			+ work.treatment + " for " + work.targetCallId
			+ " " + e.getMessage());
	        }
		break;

	    case Work.STOPINPUTTREATMENT:
		try {
		    BridgeConnection bc = getBridgeConnection(
			work.targetCallId);

		    bc.stopInputTreatment(work.targetCallId);
		} catch (IOException e) {
		    logger.warning("Unable to stop input treatment for "
			+ work.targetCallId);
		}
		break;

	    case Work.RESTARTINPUTTREATMENT:
		try {
		    BridgeConnection bc = getBridgeConnection(
			work.targetCallId, true);

		    bc.restartInputTreatment(work.targetCallId);
		} catch (IOException e) {
		    logger.warning("Unable to restart input treatment for "
			+ work.targetCallId + " " + e.getMessage());
		}
		break;

	    default:
		logger.warning("Unknown work command " + work.command);
	    }
	}

	localWorkToDo.get().clear();

        logger.finest("commit txn succeeded " + txn);
    }

    /**
     * {@inheritDoc}
     */
    public void prepareAndCommit(Transaction txn) throws Exception {
        logger.finest("prepareAndCommit on txn: " + txn);

        prepare(txn);
        commit(txn);
    }

    /**
     * {@inheritDoc}
     */
    public void abort(Transaction txn) {
	logger.info(txn.getAbortCause().getMessage());

        localWorkToDo.get().clear();

        // resolve the current transaction and the local state, removing the
        // state so we can't accidentally use it further in the future
        TxnState txnState = txnMap.remove(txn);

        // make sure that we were participating
        if (txnState == null) {
            logger.warning("not participating txn: " + txn);

            throw new IllegalStateException("VoiceService " + NAME + "is " +
                                            "not participating in this " +
                                            "transaction");
        }
    }

    public void initiateCall(CallParticipant cp) throws IOException, 
	    ParseException {

	initiateCall(cp, null);
    }

    private void initiateCall(CallParticipant cp, String bridgeInfo) 
	    throws IOException, ParseException {

	String callId = cp.getCallId();

	BridgeConnection bc = null;

	if (bridgeInfo == null) {
	    try {
	        bc = getBridgeConnection();
	    } catch (IOException e) {
	        throw new IOException(
		    "No voice bridge available, adding to recovery list: "
		    + cp + " " + e.getMessage());
	    }
	} else {
	    synchronized (bridgeConnections) {
		String[] tokens = bridgeInfo.split(":");

	        for (BridgeConnection c : bridgeConnections) {
		    String[] t = c.toString().split(":");

		    if (tokens[0].equals(t[0]) && tokens[1].equals(t[1])) {
			bc = c;
			break;
		    }
		}

		if (bc == null) {
		    throw new IOException("Unable to find bridge for '"
			+ bridgeInfo + "'");
		}
	    }

	    /*
	     * Make sure a call with the same id is ended before starting a new call.
	     */
	    try {
    		endCall(bc, callId, true);
	    } catch (IOException e) {
		logger.info("Unable to end call " + callId);
	    }
	}

	logger.info("Setting up call " + cp + " on " + bc);

	if (bc.isConnected() == false) {
	    bridgeOffline(bc);	// mark bridge offline, so we stop using it

	    throw new IOException("Unable to setup call on disconnect bridge" 
		+ bc);
	}

	/*
	 * We have to put this in the map now because we
	 * could get call status before setupCall() returns.
	 */
	callConnectionMap.put(callId, new CallInfo(cp, bc));

	try {
	    bc.setupCall(cp);
	} catch (IOException e) {
	    callConnectionMap.remove(callId);

	    /*
	     * There needs to be a better way to do this!
	     * XXX We can't do this because when a bridge
	     * goes down we get an IOException because the
	     * socket got closed.  In that case we want to restart
	     * the call.
	     */
	    if (false && e.getMessage().indexOf("CallId " + callId
		    + " is already in use") < 0) {

		throw new ParseException(
		    "Invalid call setup Parameters", 0);
	    }

	    if (e.getMessage().indexOf("CallId " + callId
		    + " is already in use") < 0) {

		/*
		 * It's not a duplicate callId so the bridge is most likely dead.
		 * XXX Not necesesarily!  For example, a bad treatment...
		 */
		//logger.info("Marking bridge offline " + bc 
		//    + " isConnected=" + bc.isConnected());

		//bridgeOffline(bc); // mark bridge offline so we stop using it
	    }

	    throw new IOException("Failed to setup call " + cp + " " 
		+ e.getMessage());
	}
    }

    private boolean done;

    public boolean shutdown() {
	logger.info("Shutdown Voice service");
	done = true;
	return true;
    }

    /**
     * Private helper that gets the transaction state, or creates it (and
     * joins to the transaction) if the state doesn't exist.
     */
    private TxnState getTxnState() {
        // resolve the current transaction and the local state
        Transaction txn = transactionProxy.getCurrentTransaction();
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
                logger.warning("already prepared txn: " + txn);

                throw new IllegalStateException("Trying to access prepared " +
                                                "transaction for scheduling");
            }
        }

        return txnState;
    }

    /**
     * Inner class that is used to track state associated with a single
     * transaction. This is indexed in the local transaction map.
     */
    private static final class TxnState {
	public boolean prepared = false;
        public HashSet<TaskReservation> reservationSet = null;
        @SuppressWarnings("hiding")
        public HashMap<String,RecurringTaskHandle> recurringMap = null;
        public HashSet<String> cancelledSet = null;
    }

    private static final class CallStatusListeners extends 
	    ArrayList<ManagedReference> implements ManagedObject,
	    Serializable {

	 private static final long serialVersionUID = 1;
    }

    private class CallStatusNotifier implements KernelRunnable {

	private final CallParticipant cp;
	private final CallStatus status;

	public CallStatusNotifier(CallParticipant cp, CallStatus status) {
	    this.cp = cp;
	    this.status = status;
	}

	public String getBaseTaskType() {
	    return CallStatusNotifier.class.getName();
	}

	public void run() throws Exception {
	    /*
	     * This runs in a transaction and the transactionProxy
	     * is usable at this point.  It's okay to get a manager
	     * or another service.
	     *
	     * This method could get called multiple times if
	     * ExceptionRetryStatus is thrown.
	     */
	    CallStatusListeners listeners = 
		dataService.getServiceBinding(DS_CALL_STATUS_LISTENERS,
		    CallStatusListeners.class);

	    ManagedReference[] listenerList;

	    synchronized (listeners) {
		listenerList = listeners.toArray(new ManagedReference[0]);
	    }

	    logger.finest("CallStatusNotifier:  " + status);

	    for (int i = 0; i < listenerList.length; i++) {
		ManagedCallStatusListener mcsl = 
		    listenerList[i].get(ManagedCallStatusListener.class);

		logger.finest("Notifying listener " + i + " status " + status);

		try {
		    mcsl.callStatusChanged(status);
		} catch (IllegalStateException e) {
		    logger.info("Can't send status:  " + status 
			+ " " + e.getMessage());
		    
		    //synchronized (recoveryList) {
		    //	logger.info("Adding " + cp + " to recovery list");
		    //
		    //	recoveryList.put(cp.getCallId(), cp);
            	    //	recoveryList.notifyAll();
        	    //}
		}
	    }
	}

    }

}
