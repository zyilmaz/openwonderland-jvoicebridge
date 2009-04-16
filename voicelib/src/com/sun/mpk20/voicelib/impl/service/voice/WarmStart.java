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
import com.sun.sgs.app.NameNotBoundException;

import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentGroup;
import com.sun.mpk20.voicelib.app.TreatmentSetup;

import java.io.IOException;

import java.util.HashMap;
import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.Logger;

public class WarmStart {

    private static final Logger logger =
        Logger.getLogger(WarmStart.class.getName());

    private boolean treatmentGroupsRestarted;
    private boolean treatmentsRestarted;
    private boolean recordersRestarted = true;

    VoiceImpl voiceImpl;

    private HashMap<String, TreatmentGroup> treatmentGroups = new HashMap();

    public WarmStart(VoiceImpl voiceImpl) {
	this.voiceImpl = voiceImpl;

	logger.info("WARM START");

	if (treatmentGroupsRestarted == false) {
	    restartTreatmentGroups();
	}

	if (treatmentsRestarted == false) {
	    restartTreatments();
	}

	restartRecorders();
    }

    private void restartTreatmentGroups() {
	logger.fine("Restarting treatmentGroups...");

        WarmStartTreatmentGroups warmStartTreatmentGroups;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartTreatmentGroups = (WarmStartTreatmentGroups) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTGROUPS);
        } catch (NameNotBoundException e) {
	    logger.fine("There are no treatment groups to restart...");
	    return;
	}

	for (String groupId : warmStartTreatmentGroups) {
	    if (voiceImpl.getTreatmentGroup(groupId) == null) {
		treatmentGroups.put(groupId, voiceImpl.createTreatmentGroup(groupId));
		logger.fine("Restarted treatment group " + groupId);
	    } else {
		logger.fine("Treatment group is already started:  " + groupId);
	    }
	}

	treatmentGroupsRestarted = true;
    }

    private void restartTreatments() {
	logger.fine("Restarting treatments...");

        WarmStartTreatments warmStartTreatments;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartTreatments = (WarmStartTreatments) dm.getBinding(
		WarmStartInfo.DS_WARM_START_TREATMENTS);
        } catch (NameNotBoundException e) {
	    logger.fine("There are no treatments to restart...");
	    return;
	}

	logger.fine("Treatments to restart:  " + warmStartTreatments.size());

	Enumeration<String> keys = warmStartTreatments.keys();

	while (keys.hasMoreElements()) {
	    String treatmentId = keys.nextElement();

	    if (voiceImpl.getTreatment(treatmentId) == null) {
		WarmStartTreatmentInfo info = warmStartTreatments.get(treatmentId);
                try {
                    Treatment treatment = voiceImpl.createTreatment(treatmentId, info.setup);

                    if (info.groupId != null) {
                        logger.fine("Looking for group " + info.groupId);
                        TreatmentGroup group = treatmentGroups.get(info.groupId);

			if (group == null) {
			    logger.warning("Unable to find treatmentGroup " + info.groupId);
			} else {
                            group.addTreatment(treatment);
			}
                    }
		    logger.fine("Created treatment " + treatmentId);
                } catch (IOException e) {
                    logger.warning("Unable to create treatment " + treatmentId + " "
                    + e.getMessage());
                }
	    } else {
		logger.fine("Treatment is already started:  " + treatmentId);
	    }
	}

	treatmentsRestarted = true;
    }

    private void restartRecorders() {
	logger.fine("Restarting recorders...");

        WarmStartRecorders warmStartRecorders;

	DataManager dm = AppContext.getDataManager();

        try {
            warmStartRecorders = (WarmStartRecorders) dm.getBinding(
		WarmStartInfo.DS_WARM_START_RECORDERS);
        } catch (NameNotBoundException e) {
	    logger.fine("There are no recorders to restart...");
	    return;
	}

	Enumeration<String> keys = warmStartRecorders.keys();

	while (keys.hasMoreElements()) {
	    String recorderId = keys.nextElement();

	    if (voiceImpl.getRecorder(recorderId) == null) {
		try {
		    voiceImpl.createRecorder(recorderId, warmStartRecorders.get(recorderId));
		    logger.fine("Restarted recorder " + recorderId);
            	} catch (IOException e) {
                    logger.warning("Unable to restart recorder:  " + recorderId);
                }
	    } else {
		logger.fine("Recorder is already started:  " + recorderId);
	    }
	}

	recordersRestarted = true;
    }

}
