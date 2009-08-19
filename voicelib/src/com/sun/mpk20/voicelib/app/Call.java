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
package com.sun.mpk20.voicelib.app;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatusListener;

import java.io.IOException;
import java.io.Serializable;

public interface Call extends CallStatusListener {

    public String getId();

    public CallSetup getSetup();

    public void setPlayer(Player player);

    public Player getPlayer();
	
    public void mute(boolean isMuted) throws IOException;

    public boolean isMuted();

    public void transfer(CallParticipant cp, boolean cancel) throws IOException;

    public void transferToConference(String conferenceId) throws IOException;

    public void newInputTreatment(String treatmentgroup) throws IOException;

    public void restartInputTreatment() throws IOException;

    public void stopInputTreatment() throws IOException;

    public void playTreatment(String treatment) throws IOException;

    public void pauseTreatment(String treatment, boolean isPaused) 
	throws IOException;

    public void stopTreatment(String treatment) throws IOException;

    public void record(String path, boolean isRecording) throws IOException;

    public void end(boolean removePlayer) throws IOException;

    public String dump();

}
