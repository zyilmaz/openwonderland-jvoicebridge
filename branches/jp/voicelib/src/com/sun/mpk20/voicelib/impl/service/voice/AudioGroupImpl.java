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

import com.sun.mpk20.voicelib.impl.service.voice.work.audiogroup.*;

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
import java.util.Iterator;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Logger;

public class AudioGroupImpl implements AudioGroup, Serializable {

    private static final Logger logger =
        Logger.getLogger(PlayerImpl.class.getName());

    private AudioGroupSetup setup;

    private String id;

    private ConcurrentHashMap<Player, AudioGroupPlayerInfo> players = new ConcurrentHashMap();

    public AudioGroupImpl(String id, AudioGroupSetup setup) {
	this.id = Util.generateUniqueId(id);
	this.setup = setup;

	if (VoiceImpl.getInstance().addWork(new CreateAudioGroupWork(this)) == false) {
	    audioGroupImplCommit();
	}
    }

    private void audioGroupImplCommit() {
	VoiceImpl.getInstance().putAudioGroup(this);
    }

    private void removeAudioGroupCommit() {
	removePlayersCommit();
	VoiceImpl.getInstance().removeAudioGroup(this);
    }

    public String getId() {
	return id;
    }

    public AudioGroupSetup getSetup() {
	return setup;
    }

    public void addPlayer(Player player, AudioGroupPlayerInfo info) {
	if (VoiceImpl.getInstance().addWork(new AddPlayerWork(this, player, info)) == false) {
	    addPlayerCommit(player, info);
	}
    }

    private void addPlayerCommit(Player player, AudioGroupPlayerInfo info) {
	players.put(player, info);

	((PlayerImpl) player).addAudioGroupCommit(this);

	logger.finer("Adding " + player + " to " + this + " call info " + info);
    }

    public void removePlayer(Player player) {
	if (VoiceImpl.getInstance().addWork(new RemovePlayerAudioGroupWork(this, player)) == false) {
	    removePlayerCommit(player);
	}
    }

    private void removePlayerCommit(Player player) {
	player.removeAudioGroup(this);

	if (players.remove(player) != null) {
	    logger.finer("Removed " + player + " from " + this);
	}
    }

    public Player[] getPlayers() {
	return players.keySet().toArray(new Player[0]);
    }

    public int getNumberOfPlayers() {
	return players.size();
    }

    public void removePlayers() {
	if (VoiceImpl.getInstance().addWork(new RemovePlayersWork(this)) == false) {
	    removePlayersCommit();
	}
    }

    private void removePlayersCommit() {
	Player[] players = getPlayers();

	for (int i = 0; i < players.length; i++) {
	    removePlayer(players[i]);
	}
    }

    public void setSpeakingAttenuation(Player player, double speakingAttenuation) {
	if (VoiceImpl.getInstance().addWork(
	        new SetSpeakingAttenuationWork(this, player, speakingAttenuation)) == false) {

	    setSpeakingAttenuationCommit(player, speakingAttenuation);
	}
    }

    private void setSpeakingAttenuationCommit(Player player, double speakingAttenuation) {
	AudioGroupPlayerInfo info = players.get(player);

	info.speakingAttenuation = speakingAttenuation;
    }

    public void setListenAttenuation(Player player, double listenAttenuation) {
	if (VoiceImpl.getInstance().addWork(
	        new SetListenAttenuationWork(this, player, listenAttenuation)) == false) {

	    setListenAttenuationCommit(player, listenAttenuation);
	}
    }

    private void setListenAttenuationCommit(Player player, double listenAttenuation) {
	AudioGroupPlayerInfo info = players.get(player);

	info.listenAttenuation = listenAttenuation;
    }

    public void setSpeaking(Player player, boolean isSpeaking) {
	if (VoiceImpl.getInstance().addWork(new SetSpeakingWork(this, player, isSpeaking)) == false) {
	    setSpeakingCommit(player, isSpeaking);
	}
    }

    private void setSpeakingCommit(Player player, boolean isSpeaking) {
	AudioGroupPlayerInfo info = players.get(player);

	info.isSpeaking = isSpeaking;
    }

    public AudioGroupPlayerInfo getPlayerInfo(Player player) {
	return players.get(player);
    }

    public boolean equals(AudioGroup audioGroup) {
	return this.id.equals(audioGroup.getId());
    }

    public void commit(AudioGroupWork work) {
	AudioGroup audioGroup = work.audioGroup;

	if (work instanceof CreateAudioGroupWork) {
	    audioGroupImplCommit();
	    return;
	} 

	if (work instanceof RemoveAudioGroupWork) {
	    removeAudioGroupCommit();
	    return;
	} 

	if (work instanceof AddPlayerWork) {
	    AddPlayerWork w = (AddPlayerWork) work;
	    addPlayerCommit(w.player, w.playerInfo);
	    return;
	} 

	if (work instanceof RemovePlayerAudioGroupWork) {
	    removePlayerCommit(((RemovePlayerAudioGroupWork) work).player);
	    return;
	} 

	if (work instanceof RemovePlayersWork) {
	    removePlayersCommit();
	    return;
	} 

	if (work instanceof SetSpeakingAttenuationWork) {
	    SetSpeakingAttenuationWork w = (SetSpeakingAttenuationWork) work;

	    setSpeakingAttenuationCommit(w.player, w.speakingAttenuation);
	    return;
	} 

	if (work instanceof SetListenAttenuationWork) {
	    SetListenAttenuationWork w = (SetListenAttenuationWork) work;

	    setListenAttenuationCommit(w.player, w.listenAttenuation);
	    return;
	} 

	if (work instanceof SetSpeakingWork) {
	    SetSpeakingWork w = (SetSpeakingWork) work;
	    setSpeakingCommit(w.player, w.isSpeaking);
	    return;
	} 

	logger.warning("Unknown AudioGroupWork:  " + work);
    }

    public String dump() {
	String s = toString();

	if (players.size() == 0) {
	    return s;
	}

	Iterator<Player> it = players.keySet().iterator();
	
	while (it.hasNext()) {
	    Player player = it.next();

	    AudioGroupPlayerInfo info = players.get(player);
	    
            s += "\n  " + player.getId() + " " + info;
	}
	
	return s;
    }

    public String toString() {
	return id + ":  " + setup.spatializer;
    }

}
