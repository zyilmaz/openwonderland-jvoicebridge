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

import java.util.ArrayList;

import com.sun.voip.client.connector.CallStatusListener;

public interface Player extends CallStatusListener {

    public String getId();

    public PlayerSetup getSetup();

    public AudioSink getAudioSink();
    public void setAudioSink(AudioSink sink); 

    public AudioSource getAudioSource();
    public void setAudioSource(AudioSource source);

    public double getX();

    public double getY();

    public double getZ();

    public double getOrientation();

    public void moved(double x, double y, double z, double orientation);

    public boolean samePosition(double x, double y, double z);
	
    public boolean sameOrientation(double orientation);

    public void setRecording(boolean isRecording);

    public boolean isRecording();

    public void setPrivateMixes(boolean positionChanged);

    public double spatialize(Player player);

    public void setPublicSpatializer(Spatializer spatializer);

    public Spatializer getPublicSpatializer();

    public void setPrivateSpatializer(Player player, Spatializer spatializer); 

    public Spatializer getPrivateSpatializer(Player player);

    public void removePrivateSpatializer(Player player);

    public void setMasterVolume(double masterVolume);

    public double getMasterVolume();

    public boolean isInRange(Player p);

    public void addPlayerInRange(Player p);

    public void removePlayerInRange(Player p);

    public void addAudioGroup(AudioGroup group);

    public void removeAudioGroup(AudioGroup group);

    public ArrayList<AudioGroup> getAudioGroups();

    public void addVirtualPlayer(VirtualPlayer p);

    public void removeVirtualPlayer(VirtualPlayer p);

    public VirtualPlayer[] getVirtualPlayers();

    public int getNumberOfPlayersInRange();

    public void setCall(Call call);

    public Call getCall();

    public void attenuateOtherGroups(AudioGroup audioGroup, 
	double speakingAttenuation, double listenAttenuation);

    public String dump();

}
