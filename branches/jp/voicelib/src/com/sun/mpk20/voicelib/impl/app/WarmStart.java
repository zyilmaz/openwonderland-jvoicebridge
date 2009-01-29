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

package com.sun.mpk20.voicelib.impl.app;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentGroup;
import com.sun.mpk20.voicelib.app.TreatmentSetup;
import com.sun.mpk20.voicelib.app.VoiceManager;

import java.io.IOException;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class WarmStart implements Serializable {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;

    private static final Logger logger =
        Logger.getLogger(WarmStart.class.getName());

    private boolean treatmentGroupsRestarted;
    private boolean treatmentsRestarted;
    private boolean recordersRestarted = true;

    private ArrayList<Task> taskList = new ArrayList();

    /*
     * TODO
     *
     * This needs to be broken up into individual transactions.
     */
    public WarmStart(VoiceManager vm) {
	if (treatmentGroupsRestarted == false) {
	    restartTreatmentGroups(vm);
	}

	if (treatmentsRestarted == false) {
	    restartTreatments(vm);
	}

	restartRecorders(vm);

	if (taskList.size() > 0) {
	    System.out.println("Warm start sheduling tasks...");
	}

	scheduleNextTask();
    }

    private void restartTreatmentGroups(VoiceManager vm) {
	//System.out.println("Restarting treatmentGroups...");

        WarmStartTreatments warmStartTreatments = null;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartTreatments = (WarmStartTreatments) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTS);
        } catch (NameNotBoundException e) {
	}

        WarmStartTreatmentGroups warmStartTreatmentGroups;

        try {
            warmStartTreatmentGroups = (WarmStartTreatmentGroups) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTGROUPS);
        } catch (NameNotBoundException e) {
	    //System.out.println("There are no treatment groups to restart...");
	    return;
	}

	Enumeration<String> keys = warmStartTreatmentGroups.keys();

	ArrayList<String> keyList = new ArrayList();

	while (keys.hasMoreElements()) {
	    String groupId = keys.nextElement();

	    if (vm.getTreatmentGroup(groupId) == null) {
	        keyList.add(groupId);
	    } else {
		//System.out.println("Treatment group is already started:  " + groupId);
	    }
	}

	for (String groupId : keyList) {
	    taskList.add(new TreatmentGroupTask(groupId));

	    keys = warmStartTreatmentGroups.get(groupId).keys();

	    while (keys.hasMoreElements()) {
		String treatmentId = keys.nextElement();

	        if (vm.getTreatment(treatmentId) == null) {
		    taskList.add(new TreatmentTask(groupId, treatmentId,
			warmStartTreatments.get(treatmentId)));
		} else {
		    //System.out.println("Treatment is already started:  " + treatmentId);
		}
	    }
	}

	treatmentGroupsRestarted = true;
    }

    private void restartTreatments(VoiceManager vm) {
	//System.out.println("Restarting treatments...");

        WarmStartTreatments warmStartTreatments;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartTreatments = (WarmStartTreatments) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTS);
        } catch (NameNotBoundException e) {
	    //System.out.println("There are no treatments to restart...");
	    return;
	}

	Enumeration<String> keys = warmStartTreatments.keys();

	while (keys.hasMoreElements()) {
	    String treatmentId = keys.nextElement();

	    if (vm.getTreatment(treatmentId) == null) {
	        taskList.add(new TreatmentTask(treatmentId,  warmStartTreatments.get(treatmentId)));
	    } else {
		//System.out.println("Treatment is already started:  " + treatmentId);
	    }
	}

	treatmentsRestarted = true;
    }

    private void restartRecorders(VoiceManager vm) {
	//System.out.println("Restarting recorders...");

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

	    if (vm.getRecorder(recorderId) == null) {
	        taskList.add(new RecorderTask(recorderId, warmStartRecorders.get(recorderId)));
	    } else {
		//System.out.println("Recorder is already started:  " + recorderId);
	    }
	}

	recordersRestarted = true;
    }

    private void scheduleNextTask() {
	if (taskList.size() > 0) {
	    AppContext.getTaskManager().scheduleTask(taskList.remove(0));
	}
    }

    class TreatmentGroupTask implements Task, Serializable {

	private String groupId;

	public TreatmentGroupTask(String groupId) {
	    this.groupId = groupId;
 	}

	public void run() {
	    AppContext.getManager(VoiceManager.class).createTreatmentGroup(groupId);

	    scheduleNextTask();
	}

    }

    class TreatmentTask implements Task, Serializable {

	private String groupId;
	private String treatmentId;
	private TreatmentSetup setup;

	public TreatmentTask(String treatmentId, TreatmentSetup setup) {
	    this(null, treatmentId, setup);
	}

	public TreatmentTask(String groupId, String treatmentId, TreatmentSetup setup) {
	    this.groupId = groupId;
	    this.treatmentId = treatmentId;
	    this.setup = setup;
	}

 	public void run() {
	    //System.out.println("Creating treatment " + treatmentId);

	    VoiceManager vm = AppContext.getManager(VoiceManager.class);

	    if (vm.getTreatment(treatmentId) == null) {
	        try {
		    vm.createTreatment(treatmentId, setup);
	        } catch (IOException e) {
		    logger.warning("Unable to create treatment " + treatmentId + " " 
		        + e.getMessage());
	        }
	    } else {
		//System.out.println("Treatment is already started:  " + treatmentId);
	    }

	    scheduleNextTask();
	}

    }

    class RecorderTask implements Task, Serializable {

	private String recorderId;
	private RecorderSetup setup;

	public RecorderTask(String recorderId, RecorderSetup setup) {
	    this.recorderId = recorderId;
	    this.setup = setup;
	}

	public void run() {
	    //System.out.println("Creating recorder " + recorderId);

	    try {
	        AppContext.getManager(VoiceManager.class).createRecorder(recorderId, setup);
	    } catch (IOException e) {
		logger.warning("Unable to restart recorder:  " + recorderId + " " + setup);
	    }

	    scheduleNextTask();
	}

    }

}
