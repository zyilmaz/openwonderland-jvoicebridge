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

import java.io.Serializable;

public interface AudioGroup extends Serializable {

    public final double FULL_VOLUME = 1.0;

    public final double DEFAULT_SPEAKING_ATTENUATION = 1.0;

    public final double DEFAULT_LISTEN_ATTENUATION = 1.0;

    public final double MINIMAL_LISTEN_ATTENUATION = .2;

    public String getId();

    public AudioGroupSetup getSetup();

    public void addPlayer(Player player, AudioGroupPlayerInfo info);

    public void removePlayer(Player player);

    public Player[] getPlayers();

    public int getNumberOfPlayers();

    public void removePlayers();

    public void removeAudioGroupListener(AudioGroupListener listener);

    public void setSpeakingAttenuation(Player player, 
	double speakingAttenuation);

    public void setListenAttenuation(Player player, double listenAttenuation);

    public void setSpeaking(Player player, boolean isSpeaking);

    public AudioGroupPlayerInfo getPlayerInfo(Player player);

    public boolean equals(Object audioGroup);

    public String dump();

}
