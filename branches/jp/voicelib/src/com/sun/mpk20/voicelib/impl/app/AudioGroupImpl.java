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

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.AudioGroupSetup;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.VirtualPlayer;
import com.sun.mpk20.voicelib.app.Util;
import com.sun.mpk20.voicelib.app.VoiceManager;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Enumeration;

import java.util.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class AudioGroupImpl implements AudioGroup, Serializable {

    private static final Logger logger =
        Logger.getLogger(PlayerImpl.class.getName());

    private AudioGroupSetup setup;

    private String id;

    private ConcurrentHashMap<Player, AudioGroupPlayerInfo> players = 
	new ConcurrentHashMap();

    public AudioGroupImpl(String id, AudioGroupSetup setup) {
	this.id = Util.generateUniqueId(id);
	this.setup = setup;
    }

    public String getId() {
	return id;
    }

    public AudioGroupSetup getSetup() {
	return setup;
    }

    public void addPlayer(Player player, AudioGroupPlayerInfo playerInfo) {
	players.put(player, playerInfo);
	player.addAudioGroup(this);

	logger.finer("Adding " + player + " to " + this + " call info " + playerInfo);
    }

    public void removePlayer(Player player) {
	player.removeAudioGroup(this);

	if (players.remove(player) != null) {
	    logger.finer("Removed " + player + " from " + this);
	}
    }

    public ConcurrentHashMap<Player, AudioGroupPlayerInfo> getPlayers() {
	return players;
    }

    public void removePlayers() {
	Enumeration<Player> pk = players.keys();
	
	while (pk.hasMoreElements()) {
	    Player player = pk.nextElement();
	    removePlayer(player);
	}
    }

    public void setSpeakingAttenuation(Player player, double speakingAttenuation) {
	AudioGroupPlayerInfo playerInfo = players.get(player);

	playerInfo.speakingAttenuation = speakingAttenuation;
    }

    public void setListenAttenuation(Player player, double listenAttenuation) {
	AudioGroupPlayerInfo playerInfo = players.get(player);

	playerInfo.listenAttenuation = listenAttenuation;
    }

    public void setSpeaking(Player player, boolean isSpeaking) {
	AudioGroupPlayerInfo playerInfo = players.get(player);

	playerInfo.isSpeaking = isSpeaking;
    }

    public AudioGroupPlayerInfo getPlayerInfo(Player player) {
	return players.get(player);
    }

    public boolean equals(AudioGroup audioGroup) {
	return this.id.equals(audioGroup.getId());
    }

    public String dump() {
	String s = toString();

	if (players.size() == 0) {
	    return s;
	}

	Enumeration<Player> pk = players.keys();
	
	while (pk.hasMoreElements()) {
	    Player player = pk.nextElement();

	    AudioGroupPlayerInfo info = players.get(player);
	    
            s += "\n  " + player.getId() + " " + info;
	}
	
	return s;
    }

    public String toString() {
	return id + ":  " + setup.spatializer;
    }

}
