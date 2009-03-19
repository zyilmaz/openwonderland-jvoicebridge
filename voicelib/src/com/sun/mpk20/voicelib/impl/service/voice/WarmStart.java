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
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.NonDurableTransactionParticipant;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupSetup;
import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentGroup;
import com.sun.mpk20.voicelib.app.TreatmentSetup;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class WarmStart extends Thread {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;

    private static final Logger logger =
        Logger.getLogger(WarmStart.class.getName());

    private static boolean started;

    private ArrayList<String> treatmentGroups = new ArrayList();

    private HashMap<String, WarmStartTreatmentInfo> treatments = new HashMap();

    private HashMap<String, RecorderSetup> recorders = new HashMap();

    /*
     * TODO
     *
     * This needs to be broken up into individual transactions.
     */
    public WarmStart() {
	System.out.println("WARM START");

	getTreatments();

	getRecorders();

	start();

	System.out.println("WARM START FINISHED SCHEDULING WORK...");

	started = true;
    }

    private void getTreatments() {
	//System.out.println("getting treatments...");

        WarmStartTreatments warmStartTreatments;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartTreatments = (WarmStartTreatments) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTS);
        } catch (NameNotBoundException e) {
	    //System.out.println("There are no treatments to restart...");
	    return;
	}

	//System.out.println("Treatments to restart:  " + warmStartTreatments.size());

	Enumeration<String> keys = warmStartTreatments.keys();

	while (keys.hasMoreElements()) {
	    String treatmentId = keys.nextElement();

	    if (VoiceImpl.getInstance().getTreatment(treatmentId) == null) {
		treatments.put(treatmentId, warmStartTreatments.get(treatmentId));
	    } else {
		//System.out.println("Treatment is already started:  " + treatmentId);
	    }
	}
    }

    private void getRecorders() {
	//System.out.println("getting recorders...");

        WarmStartRecorders warmStartRecorders;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartRecorders = (WarmStartRecorders) dm.getBinding(
		WarmStartInfo.DS_WARM_START_RECORDERS);
        } catch (NameNotBoundException e) {
	    //System.out.println("There are no recorders to restart...");
	    return;
	}

	Enumeration<String> keys = warmStartRecorders.keys();

	while (keys.hasMoreElements()) {
	    String recorderId = keys.nextElement();

	    if (VoiceImpl.getInstance().getRecorder(recorderId) == null) {
		recorders.put(recorderId, warmStartRecorders.get(recorderId));
	    } else {
		//System.out.println("Recorder is already started:  " + recorderId);
	    }
	}
    }

    public void run() {
	restartTreatments();
	restartRecorders();
    }

    private void restartTreatments() {
	Iterator<String> it = treatments.keySet().iterator();

	while (it.hasNext()) {
	    String treatmentId = it.next();

	    WarmStartTreatmentInfo info = treatments.get(treatmentId);

	    if (VoiceImpl.getInstance().getTreatment(treatmentId) != null) {
		System.out.println("Treatment is already started:  " + treatmentId);
		continue;
	    }

	    System.out.println("Restarting treatment:  " + treatmentId + " group " + info.groupId);

	    Treatment treatment;

	    try {
	        treatment = new TreatmentImpl(treatmentId, info.setup);
	    } catch (IOException e) {
		System.out.println("Unable to create treatment " + treatmentId + " " 
		    + e.getMessage());
		continue;
	    }

	    if (info.groupId == null) {
		continue;
	    }

	    TreatmentGroup group = VoiceImpl.getInstance().getTreatmentGroup(info.groupId);
			
	    if (group == null) {
		group = new TreatmentGroupImpl(info.groupId);
	    }

	    group.addTreatment(treatment);
	}
    }

    private void restartRecorders() {
	Iterator<String> it = recorders.keySet().iterator();
	
	while (it.hasNext()) {
	    String recorderId = it.next();

	    System.out.println("Restarting recorder " + recorderId);

	    try {
	        new RecorderImpl(recorderId, recorders.get(recorderId));
	    } catch (IOException e) {
		logger.warning("Unable to restart recorder:  " + recorderId);
	    }
	}
    }

}
