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

import java.io.IOException;

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

    private ConcurrentLinkedQueue<ConcurrentHashMap<String, Work>> workToDo =
	new ConcurrentLinkedQueue<ConcurrentHashMap<String, Work>>();

    private CountDownLatch doneSignal;

    private ConcurrentHashMap<String, ConcurrentHashMap<String, Work>> 
	privateMixMap = new ConcurrentHashMap<String, 
	ConcurrentHashMap<String, Work>>();

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
		Work>> (privateMixMap.values());

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
        	    ConcurrentHashMap<String, Work> pm = 
		    (ConcurrentHashMap<String, Work>) workToDo.remove();

	    	    long start = System.nanoTime();
 
		    processPrivateMixes(pm);

		    if (logger.isLoggable(Level.FINE)) {
	    	        elapsed += (System.nanoTime() - start);

	    	        if (++n == 500) {
			    double seconds = elapsed / 1000000000.;

			    logger.info(this + ": elapsed " + seconds 
				+ " seconds " + ", n " + n + ", avg " 
				+ (seconds / n) + " seconds");

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

	private void processPrivateMixes(ConcurrentHashMap<String, Work> pm) {
	    ArrayList<Work> mixList = new ArrayList<Work>(pm.values());

	    pm.clear();

	    //logger.fine(getName() + ":  Mixes to process:  " + mixList.size());

	    for (Work work : mixList) {
		BridgeConnection fromBridgeConnection;

		BridgeConnection toBridgeConnection;

		String fromCallId = work.targetCallId;

		String toCallId = work.sourceCallId;

		double[] privateMixParameters = work.privateMixParameters;

	        //logger.fine("setting private mix for"
	        //    + " source " + toCallId
    	        //    + " target " + fromCallId
		//    + " privateMixParameters " 
		//    + privateMixParameters[0] + ":"
    		//    + privateMixParameters[1] + ":"
		//    + privateMixParameters[2] + ":"
		//    + privateMixParameters[3]);

		try {
		    fromBridgeConnection = 
			bridgeManager.getBridgeConnection(fromCallId);

		    if (fromBridgeConnection == null) {
			logger.info("Unable to get bridge connection "
			    + " for fromCallId " + fromCallId);

			continue;
		    }

		    if (fromBridgeConnection.isConnected() == false) {
			logger.info("fromBridgeConnection is not connected: "
			    + fromBridgeConnection);
			continue;
		    } 

		    toBridgeConnection = 
			bridgeManager.getBridgeConnection(toCallId);

		    if (toBridgeConnection == null) {
			logger.info("Unable to get bridge connection "
			    + " for toCallId " + toCallId);

			continue;
		    }

		    if (toBridgeConnection.isConnected() == false) {
			logger.info("toBridgeConnection is not connected: "
			    + toBridgeConnection);
			continue;
		    } 

		    if (toBridgeConnection == fromBridgeConnection) {
			logger.fine("both calls are on same machine "
			    + toBridgeConnection);

		        setPrivateMix(fromBridgeConnection,
			    toCallId, fromCallId, privateMixParameters);
		    } else {
			/*
			 * The calls are on different bridges.
			 */
			if (work.privateMixParameters[3] != 0) {
			    addPrivateMixes(fromBridgeConnection,
				toBridgeConnection, fromCallId,
				toCallId, privateMixParameters);
			} else {
			    removePrivateMixes(fromBridgeConnection,
				toBridgeConnection, fromCallId, toCallId,
				privateMixParameters);
			}
		    }
		} catch (IOException e) {
		    logger.warning("Unable to set private volume for"
			+ " source " + fromCallId 
			+ " target " + toCallId
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

	private void setPrivateMix(BridgeConnection bc, String toCallId,
	        String fromCallId, double[] privateMixParameters) 
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
                + fromCallId + ":" + toCallId;

	    commands.add(s);
	}

        /*
	 * Setup a connection such that data flows from fromCallId to toCallId.
 	 */
	private void addPrivateMixes(BridgeConnection fromBridgeConnection,
	        BridgeConnection toBridgeConnection, String fromCallId,
	        String toCallId, double[] privateMixParameters) 
		throws IOException {

	    String vFromCallId = "V-" + fromCallId + "_To_" 
	    	+ toBridgeConnection.getPrivateHost() + "_"
	    	+ toBridgeConnection.getPrivateSipPort();

	    String vToCallId = "V-" + fromCallId + "_From_"
 	    	+ fromBridgeConnection.getPrivateHost() + "_"
	    	+ fromBridgeConnection.getPrivateSipPort();

	    CallInfo vToCallInfo = bridgeManager.getCallConnection(vToCallId);

	    if (vToCallInfo != null) {
		/*
		 * Let's make sure things are consistent.
		 */
		CallInfo vFromCallInfo = bridgeManager.getCallConnection(
		    vFromCallId);
		
		if (vToCallInfo.bridgeConnection.isConnected() == false) {
		    logger.info("Unusal:  " + vToCallId
			+ " is on disconnected bridge");

		    vToCallInfo = null;
		} else if (vFromCallInfo == null) {
		    logger.info("Unusal:  vToCallId " + vFromCallId
			+ " but vFromCallId doesn't " + vFromCallId);

		    vToCallInfo = null;
		} else if (vFromCallInfo.bridgeConnection.isConnected() == false) {
		    logger.info("Unusal:  " + vFromCallId
			+ " is on disconnected bridge");

		    vToCallInfo = null;
		} 

		callEnder.removeCallToEnd(vFromCallId, vToCallId);

		logger.fine("already have connection for " + vToCallId);

		logger.fine("on " + toBridgeConnection
		    + " set pm " + privateMixParameters[0]
		    + ":" + privateMixParameters[1] + ":" 
		    + privateMixParameters[2]
		    + ":" + privateMixParameters[3]
		    + " for data to " + toCallId + " from " + vToCallId);

		try {
		    setPrivateMix(toBridgeConnection, toCallId,
		        vToCallId, privateMixParameters);

		    synchronized (vToCallInfo) {
		        vToCallInfo.privateMixes.put(vFromCallId, toCallId);
		    }
		} catch (IOException e) {
		    logger.info("Unable to set pm on " + toBridgeConnection 
			+ " for " + toCallId + " from " + vToCallId
			+ " " + e.getMessage());
		}

		return;
	    }

	    logger.fine("adding pm for data to " + toCallId 
		+ " on " + toBridgeConnection.getPrivateHost()
		+ ":" + toBridgeConnection.getPrivateSipPort()
		+ ":" + toBridgeConnection.getPrivateControlPort()
		+ ", fromCallId " + fromCallId + " toCAllId " + toCallId
		+ " on " + fromBridgeConnection.getPrivateHost()
		+ ":" + fromBridgeConnection.getPrivateSipPort()
		+ ":" + fromBridgeConnection.getPrivateControlPort()
		+ " vFromCallId " + vFromCallId + " vToCallId "
		+ vToCallId);

	    CallInfo toCallInfo = bridgeManager.getCallConnection(toCallId);

	    if (toCallInfo == null) {
		logger.info("Can't find CallInfo for " + toCallId);
		return;
	    }

	    /*
	     * We don't need to setup a connection to receive data if
	     * the toCall is an input treatment.
	     */
	    if (toCallInfo.cp.getInputTreatment() != null) {
		logger.info("Shouldn't get here!");
		return;
	    }

	    /*
	     * setup a new call to send data from fromCall to tocall.
	     */
	    CallParticipant cp = new CallParticipant();

	    cp.setConferenceId(toCallInfo.cp.getConferenceId());

	    /*
	     * The id on the fromBridgeConnection side has have
	     * the toBridgeConnection address to make it unique
	     * in case there are other bridges getting data from the
	     * same call.
	     */
	    cp.setCallId(vFromCallId);

	    String phoneNumber = "6666@"
		+ toBridgeConnection.getPrivateHost() + ":"
		+ toBridgeConnection.getPrivateSipPort();

            cp.setPhoneNumber(phoneNumber);
	    cp.setName(vFromCallId);
	    cp.setDisplayName(vToCallId);   // this will be remote short name
	    cp.setRemoteCallId(vToCallId);  // this will be remote id 
	    cp.setForwardingCallId(fromCallId);
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

	    synchronized (toCallInfo) {
	        toCallInfo.privateMixes.put(vFromCallId, toCallId);
	    }

	    CallInfo fromCallInfo = new CallInfo(cp, fromBridgeConnection);

	    bridgeManager.putCallConnection(vFromCallId, fromCallInfo);

	    cp = new CallParticipant();

	    cp.setCallId(vToCallId);
	    cp.setPhoneNumber(phoneNumber);
	    cp.setName(vToCallId);

	    toBridgeConnection.addCall(cp);

	    toCallInfo = new CallInfo(cp, toBridgeConnection);

	    bridgeManager.putCallConnection(vToCallId, toCallInfo);

	    ArrayList<String> virtualCalls = virtualCallMap.get(fromCallId);

	    if (virtualCalls == null) {
		virtualCalls = new ArrayList<String>();
	        virtualCallMap.put(fromCallId, virtualCalls);
	    }

	    virtualCalls.add(vFromCallId);

	    virtualCalls = virtualCallMap.get(toCallId);

	    if (virtualCalls == null) {
	    	virtualCalls = new ArrayList<String>();
	        virtualCallMap.put(toCallId, virtualCalls);
	    }

	    virtualCalls.add(vToCallId);

	    /*
	     * Set a pm for toCall from callId using privateMixParameters. 
	     */
	    logger.fine("on " + toBridgeConnection
		+ " set pm " + privateMixParameters[0]
		+ ":" + privateMixParameters[1] + ":" + privateMixParameters[2]
		+ ":" + privateMixParameters[3]
		+ " for data to " + toCallId + " from " + vFromCallId);

	    /*
	     * I think there's a window where callId might not yet be valid
	     * on the toBridge.  When we get established status for vToCallId,
	     * we'll set the pm then.
	     */
	    try {
	        setPrivateMix(toBridgeConnection, toCallId, vToCallId,
		    privateMixParameters);
	    } catch (IOException e) {
		logger.info("Unable to set pm on " + toBridgeConnection
		    + " for data from " + vToCallId + " to " + toCallId
		    + " deferring... ");

		synchronized (deferredPrivateMixes) {
		    deferredPrivateMixes.put(vToCallId, new DeferredPrivateMix(
			toBridgeConnection, toCallId, privateMixParameters));
		    deferredPrivateMixes.put(toCallId, new DeferredPrivateMix(
			toBridgeConnection, vToCallId, privateMixParameters));
		}
	    }

	    //dump("After setup of virtual call");
	}

	private void removePrivateMixes(BridgeConnection fromBridgeConnection,
	        BridgeConnection toBridgeConnection, String fromCallId,
	        String toCallId, double[] privateMixParameters) 
		throws IOException {

	    String vFromCallId = "V-" + fromCallId + "_To_" 
	    	+ toBridgeConnection.getPrivateHost() + "_"
	        + toBridgeConnection.getPrivateSipPort();

	    String vToCallId = "V-" + fromCallId + "_From_" 
	    	+ fromBridgeConnection.getPrivateHost() + "_"
	        + fromBridgeConnection.getPrivateSipPort();

	    logger.fine("removing pm for data to " + toCallId 
		+ " on " + toBridgeConnection.getPrivateHost()
		+ ":" + toBridgeConnection.getPrivateSipPort()
		+ ":" + toBridgeConnection.getPrivateControlPort()
		+ ", from " + vFromCallId
		+ " on " + fromBridgeConnection.getPrivateHost()
		+ ":" + fromBridgeConnection.getPrivateSipPort()
		+ ":" + fromBridgeConnection.getPrivateControlPort()
		+ " vFromCallId " + vFromCallId + " vToCallId "
		+ vToCallId);

	    CallInfo toCallInfo = bridgeManager.getCallConnection(vToCallId);

            try {
                setPrivateMix(toBridgeConnection, toCallId, vToCallId,
                    privateMixParameters);
            } catch (IOException e) {
                 logger.info("Unable to remove pm on " + toBridgeConnection
                     + " for " + toCallId + " from " + vToCallId
                     + " " + e.getMessage());
            }

	    ConcurrentHashMap<String, String> privateMixes = null;

	    if (toCallInfo != null) {
	        privateMixes = toCallInfo.privateMixes;

	        synchronized (toCallInfo) {
	            privateMixes.remove(vFromCallId, toCallId);
	        }
	    } else {
		logger.warning("No CallInfo for " + toCallId);
	    }

	    if (privateMixes == null || privateMixes.size() == 0) {
	        callEnder.addCallToEnd(vFromCallId, vToCallId);
	    } else {
		logger.fine("Not ending call " + vToCallId + " remote " 
		    + vFromCallId
		    + " because there are still " + privateMixes.size()
		    + " private mixes for " + vToCallId);    
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

        public void addCallToEnd(String fromCallId, String toCallId) {
	    synchronized (callsToEnd) {
	        callsToEnd.put(fromCallId, new EndCallInfo(fromCallId));
	        callsToEnd.put(toCallId, new EndCallInfo(toCallId));
	        logger.fine("CallEnder: " + fromCallId + " scheduled to end");
	        logger.fine("CallEnder: " + toCallId + " scheduled to end");
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
        public String toCallId;
        public double[] privateMixParameters;

        public DeferredPrivateMix(BridgeConnection bc, String toCallId,
                double[] privateMixParameters) {

            this.bc = bc;
            this.toCallId = toCallId;
            this.privateMixParameters = privateMixParameters;
        }

        public String toString() {
            return bc + " toCallId " + toCallId;
        }
    }

    private ConcurrentHashMap<String, DeferredPrivateMix> deferredPrivateMixes =
        new ConcurrentHashMap<String, DeferredPrivateMix>();
   
    public void callEstablished(String callId) {
	logger.finer("established " + callId);

	privateMixMap.put(callId, new ConcurrentHashMap<String, Work>());

	DeferredPrivateMix dpm = deferredPrivateMixes.remove(callId);

	if (dpm != null && deferredPrivateMixes.size() > 0) {
            logger.info("deferred pm for " + callId + " is " + dpm
                + " " + deferredPrivateMixes.size());
        }

        if (dpm != null) {
            try {
                logger.info("setting deferred pm on "
                    + dpm.bc + " for data from " + callId + " to "
                    + dpm.toCallId);

                dpm.bc.setPrivateMix(
                dpm.toCallId, callId, dpm.privateMixParameters);
            } catch (IOException e) {
                logger.info("Unable to set deferred pm on "
                    + dpm.bc + " for data from " + callId + " to "
                    + dpm.toCallId);
            }
        }
    }

    private int replaced;
    private int count;

    private boolean privateMixSet;

    public void setPrivateMix(Work work) {
	ConcurrentHashMap<String, Work> mixMap = 
	    privateMixMap.get(work.sourceCallId);

        if (mixMap == null) {
            logger.fine("No mixMap for " + work.sourceCallId);
	    return;
	}

	Work w = mixMap.put(work.targetCallId, work);

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
	        logger.fine("Replaced " + replaced);
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

	ConcurrentHashMap<String, Work> mixMap = privateMixMap.get(callId);

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
