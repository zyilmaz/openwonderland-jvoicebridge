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

import com.sun.mpk20.voicelib.impl.service.voice.work.player.SetPrivateMixWork;

import java.io.IOException;
import java.io.Serializable;

import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.voip.CallParticipant;

public class PrivateMixManager extends Thread {

    private static final Logger logger = Logger.getLogger(
	PrivateMixManager.class.getName());

    /*
     * Send private mix commands to the bridge.
     */
    private ArrayList<WorkerThread> workerThreads = 
	new ArrayList<WorkerThread>();

    private ConcurrentLinkedQueue<ConcurrentHashMap<String, SetPrivateMixWork>> workToDo =
	new ConcurrentLinkedQueue<ConcurrentHashMap<String, SetPrivateMixWork>>();

    private CountDownLatch doneSignal;

    private ConcurrentHashMap<String, ConcurrentHashMap<String, SetPrivateMixWork>> 
	privateMixMap = new ConcurrentHashMap<String, 
	ConcurrentHashMap<String, SetPrivateMixWork>>();

    private ConcurrentHashMap<String, ArrayList<String>> virtualCallMap =
        new ConcurrentHashMap<String, ArrayList<String>>();

    private CallEnder callEnder;

    private BridgeManager bridgeManager;
  
    public PrivateMixManager(BridgeManager bridgeManager) {

	this.bridgeManager = bridgeManager;

	callEnder = new CallEnder();

	int n = Runtime.getRuntime().availableProcessors(); 

	doneSignal = new CountDownLatch(n);

	for (int i = 0; i < n; i++) {   
	    workerThreads.add(new WorkerThread(i, doneSignal));
        }

	start();
    }

    public void run() {
        while (true) {
	    synchronized (privateMixMap) {
		try {
		    privateMixMap.wait();
		} catch (InterruptedException e) {
		}
	    }

	    workToDo = new ConcurrentLinkedQueue<ConcurrentHashMap<String, 
		SetPrivateMixWork>> (privateMixMap.values());

	    /*
             * Start all of the worker threads
             */
            for (int i = 0; i < workerThreads.size(); i++) {
                WorkerThread workerThread = (WorkerThread) workerThreads.get(i);

                workerThread.setLatch(doneSignal);

                synchronized (workerThread) {
                    workerThread.notify();
                }
            }
	}
    }

    class WorkerThread extends Thread {
	private boolean done;
	private CountDownLatch doneSignal;

	private long elapsed;
	private long n;

	public WorkerThread(int i, CountDownLatch doneSignal) {
	    this.doneSignal = doneSignal;

	    setName("PrivateMixManager-WorkerThread-" + i);

	    start();
	}

	public void setLatch(CountDownLatch doneSignal) {
	    this.doneSignal = doneSignal;
	}

	public void done() {
	    done = true;
	    interrupt();
	}
		
        public void run() {
	    while (!done) {
		try {
        	    ConcurrentHashMap<String, SetPrivateMixWork> pm = 
		    (ConcurrentHashMap<String, SetPrivateMixWork>) workToDo.remove();

	    	    long start = System.nanoTime();
 
		    processPrivateMixes(pm);

		    if (logger.isLoggable(Level.FINE)) {
	    	        elapsed += (System.nanoTime() - start);

	    	        if (++n == 500) {
			    double seconds = elapsed / 1000000000.;

			    logger.info(this + ": elapsed " + seconds 
				+ " seconds " + ", n " + n + ", avg " 
				+ (seconds / n) + " seconds, "
				+ (500. / elapsed) + " pm's / second");

		            elapsed = 0;
		            n = 0;
			}
	    	    }
		} catch (NoSuchElementException e) {
                    synchronized (this) {
                        doneSignal.countDown();

                        if (done) {
                            break;  // done
                        }

                        try {
                            wait();
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }
	    }
	}

	HashMap<BridgeConnection, ArrayList<String>>  bridgeMap =
	    new HashMap<BridgeConnection, ArrayList<String>>();

	private void processPrivateMixes(ConcurrentHashMap<String, SetPrivateMixWork> pm) {
	    ArrayList<SetPrivateMixWork> mixList = new ArrayList<SetPrivateMixWork>(pm.values());

	    pm.clear();

	    //logger.fine(getName() + ":  Mixes to process:  " + mixList.size());

	    for (SetPrivateMixWork work : mixList) {
		BridgeConnection fromBridgeConnection;

		BridgeConnection targetBridgeConnection;

		String targetCallId = work.targetCallId;

		String sourceCallId = work.sourceCallId;

		double[] privateMixParameters = work.privateMixParameters;

	        //logger.fine("setting private mix for"
	        //    + targetCallId " from " + sourceCallId
		//    + " privateMixParameters " 
		//    + privateMixParameters[0] + ":"
    		//    + privateMixParameters[1] + ":"
		//    + privateMixParameters[2] + ":"
		//    + privateMixParameters[3]);

		try {
		    fromBridgeConnection = 
			bridgeManager.getBridgeConnection(sourceCallId);

		    if (fromBridgeConnection.isConnected() == false) {
			logger.info("fromBridgeConnection is not connected: "
			    + fromBridgeConnection);
			continue;
		    } 

		    targetBridgeConnection = 
			bridgeManager.getBridgeConnection(targetCallId);

		    if (targetBridgeConnection.isConnected() == false) {
			logger.info("targetBridgeConnection is not connected: "
			    + targetBridgeConnection);
			continue;
		    } 

		    if (targetBridgeConnection == fromBridgeConnection) {
			logger.fine("both calls are on same machine "
			    + targetBridgeConnection);

		        setPrivateMix(fromBridgeConnection,
			    targetCallId, sourceCallId, privateMixParameters);
		    } else {
			/*
			 * The calls are on different bridges.
			 */
			if (work.privateMixParameters[3] != 0) {
			    addPrivateMixes(targetBridgeConnection, targetCallId,
				fromBridgeConnection, sourceCallId, 
				privateMixParameters);
			} else {
			    removePrivateMixes(targetBridgeConnection,
				targetCallId, fromBridgeConnection, sourceCallId,
				privateMixParameters);
			}
		    }
		} catch (IOException e) {
		    logger.fine("Unable to set private volume for "
			+ targetCallId + " from " + sourceCallId
		    	+ " privateMixParameters " 
			+  privateMixParameters[0] + ":"
			+  privateMixParameters[1] + ":"
			+  privateMixParameters[2] + ":"
			+  privateMixParameters[3] + " " + e.getMessage());
		}
	    }

	    Set<BridgeConnection> keys = bridgeMap.keySet();

	    Iterator<BridgeConnection> it = keys.iterator();

            while (it.hasNext()) {
                BridgeConnection bc = it.next();

                ArrayList<String> commands = bridgeMap.get(bc);

		String s = "";

		for (int i = 0; i < commands.size(); i++) {
		    s += commands.get(i) + "\n";
		}

		try {
		    bc.setPrivateMix(s);
		} catch (IOException e) {
		    logger.info("Unable to send private mix command to "
			+ bc + " " + e.getMessage());
		}
	    }
	    
	    bridgeMap.clear();
	}

	private void setPrivateMix(BridgeConnection bc, String targetCallId,
	        String sourceCallId, double[] privateMixParameters) 
		throws IOException {

	    ArrayList<String> commands = bridgeMap.get(bc);

	    if (commands == null) {
		commands = new ArrayList<String>();

		bridgeMap.put(bc, commands);
	    }

	    String s = "pmx=" +  privateMixParameters[0] + ":"
                +  privateMixParameters[1] + ":"
                +  privateMixParameters[2] + ":"
                +  privateMixParameters[3] + ":"
                + sourceCallId + ":" + targetCallId;

	    commands.add(s);
	}

        /*
	 * Setup a connection such that data flows from sourceCallId to targetCallId.
 	 */
	private void addPrivateMixes(BridgeConnection targetBridgeConnection,
		String targetCallId, BridgeConnection fromBridgeConnection, 
		String sourceCallId, double[] privateMixParameters) 
		throws IOException {

	    String vSourceCallId = "V-" + sourceCallId + "_To_" 
	    	+ targetBridgeConnection.getPrivateHost() + "_"
	    	+ targetBridgeConnection.getPublicSipPort();

	    String vTargetCallId = "V-" + sourceCallId + "_From_"
 	    	+ fromBridgeConnection.getPrivateHost() + "_"
	    	+ fromBridgeConnection.getPublicSipPort();

	    CallInfo vTargetCallInfo = bridgeManager.getCallConnection(vTargetCallId);

	    if (vTargetCallInfo != null) {
		/*
		 * Let's make sure things are consistent.
		 */
		CallInfo vSourceCallInfo = bridgeManager.getCallConnection(
		    vSourceCallId);
		
		if (vTargetCallInfo.bridgeConnection.isConnected() == false) {
		    logger.info("Unusal:  " + vTargetCallId
			+ " is on disconnected bridge");

		    vTargetCallInfo = null;
		} else if (vSourceCallInfo == null) {
		    logger.info("Unusal:  vTargetCallId " + vSourceCallId
			+ " but vSourceCallId doesn't " + vSourceCallId);

		    vTargetCallInfo = null;
		} else if (vSourceCallInfo.bridgeConnection.isConnected() == false) {
		    logger.info("Unusal:  " + vSourceCallId
			+ " is on disconnected bridge");

		    vTargetCallInfo = null;
		} 

		callEnder.removeCallToEnd(vSourceCallId, vTargetCallId);

		logger.fine("already have connection for " + vTargetCallId);

		logger.fine("on " + targetBridgeConnection
		    + " set pm " + privateMixParameters[0]
		    + ":" + privateMixParameters[1] + ":" 
		    + privateMixParameters[2]
		    + ":" + privateMixParameters[3]
		    + " for data to " + targetCallId + " from " + vTargetCallId);

		try {
		    setPrivateMix(targetBridgeConnection, targetCallId,
		        vTargetCallId, privateMixParameters);

		    synchronized (vTargetCallInfo) {
		        vTargetCallInfo.privateMixes.put(vSourceCallId, targetCallId);
		    }
		} catch (IOException e) {
		    logger.info("Unable to set pm on " + targetBridgeConnection 
			+ " for " + targetCallId + " from " + vTargetCallId
			+ " " + e.getMessage());
		}

		return;
	    }

	    logger.fine("adding pm for data to " + targetCallId 
		+ " on " + targetBridgeConnection.getPrivateHost()
		+ ":" + targetBridgeConnection.getPublicSipPort()
		+ ":" + targetBridgeConnection.getPrivateControlPort()
		+ ", sourceCallId " + sourceCallId + " targetCallId " + targetCallId
		+ " on " + fromBridgeConnection.getPrivateHost()
		+ ":" + fromBridgeConnection.getPublicSipPort()
		+ ":" + fromBridgeConnection.getPrivateControlPort()
		+ " vSourceCallId " + vSourceCallId + " vTargetCallId "
		+ vTargetCallId);

	    CallInfo targetCallInfo = bridgeManager.getCallConnection(targetCallId);

	    if (targetCallInfo == null) {
		logger.info("Can't find CallInfo for " + targetCallId);
		return;
	    }

	    /*
	     * We don't need to setup a connection to receive data if
	     * the targetCall is an input treatment.
	     */
	    if (targetCallInfo.cp.getInputTreatment() != null) {
		logger.info("Shouldn't get here!");
		return;
	    }

	    /*
	     * setup a new call to send data from sourceCall to targetcall.
	     */
	    CallParticipant cp = new CallParticipant();

	    cp.setConferenceId(targetCallInfo.cp.getConferenceId());

	    /*
	     * The id on the fromBridgeConnection side has have
	     * the targetBridgeConnection address to make it unique
	     * in case there are other bridges getting data from the
	     * same call.
	     */
	    cp.setCallId(vSourceCallId);

	    String phoneNumber = "6666@"
		+ targetBridgeConnection.getPrivateHost() + ":"
		+ targetBridgeConnection.getPublicSipPort();

            cp.setPhoneNumber(phoneNumber);
	    cp.setName(vSourceCallId);
	    cp.setDisplayName(vTargetCallId);   // this will be remote short name
	    cp.setRemoteCallId(vTargetCallId);  // this will be remote id 
	    cp.setForwardingCallId(sourceCallId);
	    cp.setVoiceDetection(true);

	    logger.fine("setup virtual call on " + fromBridgeConnection 
		+ " " + cp);

	    try {
    	        fromBridgeConnection.setupCall(cp);
	    } catch (IOException e) {
		logger.info("Unable to setup call on " + fromBridgeConnection
		    + " " + cp);
		
		return;
	    }

	    synchronized (targetCallInfo) {
	        targetCallInfo.privateMixes.put(vSourceCallId, targetCallId);
	    }

	    CallInfo sourceCallInfo = new CallInfo(cp, fromBridgeConnection);

	    bridgeManager.putCallConnection(vSourceCallId, sourceCallInfo);

	    cp = new CallParticipant();

	    cp.setCallId(vTargetCallId);
	    cp.setPhoneNumber(phoneNumber);
	    cp.setName(vTargetCallId);

	    targetBridgeConnection.addCall(cp);

	    targetCallInfo = new CallInfo(cp, targetBridgeConnection);

	    bridgeManager.putCallConnection(vTargetCallId, targetCallInfo);

	    ArrayList<String> virtualCalls = virtualCallMap.get(sourceCallId);

	    if (virtualCalls == null) {
		virtualCalls = new ArrayList<String>();
	        virtualCallMap.put(sourceCallId, virtualCalls);
	    }

	    virtualCalls.add(vSourceCallId);

	    virtualCalls = virtualCallMap.get(targetCallId);

	    if (virtualCalls == null) {
	    	virtualCalls = new ArrayList<String>();
	        virtualCallMap.put(targetCallId, virtualCalls);
	    }

	    virtualCalls.add(vTargetCallId);

	    /*
	     * Set a pm for targetCall from callId using privateMixParameters. 
	     */
	    logger.fine("on " + targetBridgeConnection
		+ " set pm " + privateMixParameters[0]
		+ ":" + privateMixParameters[1] + ":" + privateMixParameters[2]
		+ ":" + privateMixParameters[3]
		+ " for data to " + targetCallId + " from " + vSourceCallId);

	    /*
	     * I think there's a window where callId might not yet be valid
	     * on the targetBridge.  When we get established status for 
	     * vTargetCallId, we'll set the pm then.
	     */
	    try {
	        setPrivateMix(targetBridgeConnection, targetCallId, vTargetCallId,
		    privateMixParameters);
	    } catch (IOException e) {
		logger.info("Unable to set pm on " + targetBridgeConnection
		    + " for data from " + vTargetCallId + " to " + targetCallId
		    + " deferring... ");

		synchronized (deferredPrivateMixes) {
		    deferredPrivateMixes.put(vTargetCallId, new DeferredPrivateMix(
			targetBridgeConnection, targetCallId, privateMixParameters));
		    deferredPrivateMixes.put(targetCallId, new DeferredPrivateMix(
			targetBridgeConnection, vTargetCallId, privateMixParameters));
		}
	    }

	    //dump("After setup of virtual call");
	}

	private void removePrivateMixes(BridgeConnection targetBridgeConnection,
	        String targetCallId, BridgeConnection fromBridgeConnection, 
		String sourceCallId, double[] privateMixParameters) 
		throws IOException {

	    String vSourceCallId = "V-" + sourceCallId + "_To_" 
	    	+ targetBridgeConnection.getPrivateHost() + "_"
	        + targetBridgeConnection.getPublicSipPort();

	    String vTargetCallId = "V-" + sourceCallId + "_From_" 
	    	+ fromBridgeConnection.getPrivateHost() + "_"
	        + fromBridgeConnection.getPublicSipPort();

	    logger.fine("removing pm for data to " + targetCallId 
		+ " on " + targetBridgeConnection.getPrivateHost()
		+ ":" + targetBridgeConnection.getPublicSipPort()
		+ ":" + targetBridgeConnection.getPrivateControlPort()
		+ ", from " + vSourceCallId
		+ " on " + fromBridgeConnection.getPrivateHost()
		+ ":" + fromBridgeConnection.getPublicSipPort()
		+ ":" + fromBridgeConnection.getPrivateControlPort()
		+ " vSourceCallId " + vSourceCallId + " vTargetCallId "
		+ vTargetCallId);

	    CallInfo targetCallInfo = bridgeManager.getCallConnection(vTargetCallId);

            try {
                setPrivateMix(targetBridgeConnection, targetCallId, vTargetCallId,
                    privateMixParameters);
            } catch (IOException e) {
                 logger.info("Unable to remove pm on " + targetBridgeConnection
                     + " for " + targetCallId + " from " + vTargetCallId
                     + " " + e.getMessage());
            }

	    ConcurrentHashMap<String, String> privateMixes = null;

	    if (targetCallInfo != null) {
	        privateMixes = targetCallInfo.privateMixes;

	        synchronized (targetCallInfo) {
	            privateMixes.remove(vSourceCallId, targetCallId);
	        }
	    } else {
		logger.warning("No CallInfo for " + targetCallId);
	    }

	    if (privateMixes == null || privateMixes.size() == 0) {
	        callEnder.addCallToEnd(vSourceCallId, vTargetCallId);
	    } else {
		logger.fine("Not ending call " + vTargetCallId + " remote " 
		    + vSourceCallId
		    + " because there are still " + privateMixes.size()
		    + " private mixes for " + vTargetCallId);    
	    }

	    //dump("After removing private mix and virtual call");
	}
    }

    class CallEnder extends Thread {
        class EndCallInfo {
	    String callId;

	    public int timeoutSeconds = 10;

	    public EndCallInfo(String callId) {
	        this.callId = callId;
	    }
        }

        private ConcurrentHashMap<String, EndCallInfo> callsToEnd =
	    new ConcurrentHashMap<String, EndCallInfo>();

        public CallEnder() {
	    start();
        }

        public void addCallToEnd(String sourceCallId, String targetCallId) {
	    synchronized (callsToEnd) {
	        callsToEnd.put(sourceCallId, new EndCallInfo(sourceCallId));
	        callsToEnd.put(targetCallId, new EndCallInfo(targetCallId));
	        logger.fine("CallEnder: " + sourceCallId + " scheduled to end");
	        logger.fine("CallEnder: " + targetCallId + " scheduled to end");
	    }
        }

        public void removeCallToEnd(String callId, String remoteCallId) {
	    synchronized (callsToEnd) {
	        if (callsToEnd.remove(callId) != null) {
		    logger.fine("CallEnder:  Reprieve given to " + callId);
		    callsToEnd.remove(remoteCallId);
	        } 
	    }
        }

        public void run() {
	    while (true) {
	        try {
		    Thread.sleep(1000);
	        } catch (InterruptedException e) {
	        }

	        ArrayList<String> callList = new ArrayList<String>();

	        synchronized (callsToEnd) {
		    Collection<EndCallInfo> v = callsToEnd.values();

		    Iterator<EndCallInfo> it = v.iterator();

		    while (it.hasNext()) {
		        EndCallInfo callToEnd = it.next();

		        if (--callToEnd.timeoutSeconds <= 0) {
			    callList.add(callToEnd.callId);
		        }
		    }
	        }

	        for (int i = 0; i < callList.size(); i++) {
		    String callId = callList.get(i);

		    callsToEnd.remove(callId);

		    try {
			logger.fine("End call:  " + callId);
		        bridgeManager.endCall(callId);
			endCall(callId);
		    } catch (IOException e) {
		        logger.info("Unable to end call " + callId
			    + " " + e.getMessage());
		    }
	        }
	    }
        }
    }

    class DeferredPrivateMix {
        public BridgeConnection bc;
        public String targetCallId;
        public double[] privateMixParameters;

        public DeferredPrivateMix(BridgeConnection bc, String targetCallId,
                double[] privateMixParameters) {

            this.bc = bc;
            this.targetCallId = targetCallId;
            this.privateMixParameters = privateMixParameters;
        }

        public String toString() {
            return bc + " targetCallId " + targetCallId;
        }
    }

    private ConcurrentHashMap<String, DeferredPrivateMix> deferredPrivateMixes =
        new ConcurrentHashMap<String, DeferredPrivateMix>();
   
    public void callEstablished(String callId) {
	logger.finer("established " + callId);

	privateMixMap.put(callId, new ConcurrentHashMap<String, SetPrivateMixWork>());

	DeferredPrivateMix dpm = deferredPrivateMixes.remove(callId);

	if (dpm != null && deferredPrivateMixes.size() > 0) {
            logger.info("deferred pm for " + callId + " is " + dpm
                + " " + deferredPrivateMixes.size());
        }

        if (dpm != null) {
            try {
                logger.info("setting deferred pm on "
                    + dpm.bc + " for data from " + callId + " to "
                    + dpm.targetCallId);

                dpm.bc.setPrivateMix(
                dpm.targetCallId, callId, dpm.privateMixParameters);
            } catch (IOException e) {
                logger.info("Unable to set deferred pm on "
                    + dpm.bc + " for data from " + callId + " to "
                    + dpm.targetCallId);
            }
        }
    }

    private int replaced;
    private int count;

    private boolean privateMixSet;

    public void setPrivateMix(SetPrivateMixWork work) {
	ConcurrentHashMap<String, SetPrivateMixWork> mixMap = 
	    privateMixMap.get(work.sourceCallId);

        if (mixMap == null) {
            logger.fine("No mixMap for " + work.sourceCallId);
	    return;
	}

	SetPrivateMixWork w = mixMap.put(work.targetCallId, work);

	if (work.privateMixParameters[3] == 0) {
	    logger.finer("Zero volume for source " + work.sourceCallId
		+ " target " + work.targetCallId);
	}

	synchronized (privateMixMap) {
            if (w != null) {
                replaced++;

		if (w.privateMixParameters[3] == 0) {
	    	    logger.finer("Replacing 0 volume " + w.sourceCallId
			+ " target " + w.targetCallId
			+ " new volume " + work.privateMixParameters[3]);
		}

                logger.finer(w.sourceCallId + " Replacing pm for "
                    + w.targetCallId + " old "
                    + w.privateMixParameters[0] + ":"
                    + w.privateMixParameters[1] + ":"
                    + w.privateMixParameters[2] + ":"
                    + w.privateMixParameters[3]
                    + " new "
                    + work.privateMixParameters[0] + ":"
                    + work.privateMixParameters[1] + ":"
                    + work.privateMixParameters[2] + ":"
                    + work.privateMixParameters[3]);
	    } 

	    if (++count == 1000) {
	        logger.info("Replaced " + replaced);
	        count = 0;
	        replaced = 0;
	    }

	    //privateMixMap.notifyAll();

	    privateMixSet = true;
	}
    }

    public void commit() {
	synchronized (privateMixMap) {
	    if (privateMixSet == false) {
		return;
	    }

	    privateMixSet = false;

	    privateMixMap.notifyAll();
	}
    }

    public void endCall(String callId) {
	logger.finer("endCall " + callId);

	ConcurrentHashMap<String, SetPrivateMixWork> mixMap = privateMixMap.get(callId);

        if (mixMap != null) {
            privateMixMap.remove(mixMap);
        }
        
	deferredPrivateMixes.remove(callId);

	/*
	 * There could be calls on many machines starting
	 * with V-<callId>.  We need to end all of them because
	 * the main call has ended.
	 */
	endVirtualCalls(callId);
	removeFromVirtualCallMap(callId);
    }

    private void endVirtualCalls(String callId) {
	ArrayList<String> virtualCalls = virtualCallMap.get(callId);

	if (virtualCalls == null) {
	    logger.fine("No virtual calls for " + callId);
	    return;
	}

	logger.fine("vm size for " + callId + " " + virtualCalls.size());

	virtualCallMap.remove(callId);

	for (String id : virtualCalls) {
	    CallInfo callInfo =  bridgeManager.getCallConnection(id);

	    if (callInfo == null) {
		logger.fine("No call info for " + id);
		return;
	    }

            ConcurrentHashMap<String, String> privateMixes = 
		callInfo.privateMixes;

	    if (privateMixes.size() <= 1) {
	        try {
		    logger.fine("Ending virtual call " + id);
		    bridgeManager.endCall(id);
		    endCall(callId);
	        } catch (IOException e) {
		    logger.info("Unable to end virtual call :  " + id
		        + " " + e.getMessage());
	        }
	    } else {
		logger.fine("Not ending " + id + " because it still has "
		    + privateMixes.size() + " privateMixes");
	    }
	}
    }

    private void removeFromVirtualCallMap(String callId) {
	logger.fine("trying to remove v call " + callId);

        Enumeration<String> keys = virtualCallMap.keys();

        while (keys.hasMoreElements()) {
            String key = keys.nextElement();

            ArrayList<String> virtualCalls = virtualCallMap.get(key);

	    for (String id : virtualCalls) {
		if (callId.equals(id)) {
		    logger.fine("Removed virtual call " + id
			+ " from list for " + callId);

		    virtualCalls.remove(id);

		    if (virtualCalls.size() == 0) {
			logger.fine("Removed virtual call array for "
			    + callId);

			virtualCallMap.remove(virtualCalls);
		    }
		    return;
                }
            }
	}
    }

    public void dump() {
	logger.info("Virtual call map " + virtualCallMap.size());

	Enumeration<String> keys = virtualCallMap.keys();

	while (keys.hasMoreElements()) {
	    String callId = keys.nextElement();

	    ArrayList<String> vCalls = virtualCallMap.get(callId);

	    if (vCalls != null) {
	    	logger.info("Virtual calls for " + callId + " " 
		    + vCalls.size());

		for (String vc : vCalls) {
		    logger.info("  " + vc);
		}
	    }
  	}
    }

}
