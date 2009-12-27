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
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.AudioGroupListener;
import com.sun.mpk20.voicelib.app.AudioGroupSetup;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Util;
import com.sun.mpk20.voicelib.app.VirtualPlayerListener;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.logging.Logger;

public class AudioGroupImpl implements AudioGroup {

    private static final Logger logger =
        Logger.getLogger(PlayerImpl.class.getName());

    private AudioGroupSetup setup;

    private String id;

    private ConcurrentHashMap<String, AudioGroupPlayerInfo> players = new ConcurrentHashMap();

    private CopyOnWriteArrayList<AudioGroupListener> listeners = new CopyOnWriteArrayList();

    private VirtualPlayerHandler virtualPlayerHandler;

    public AudioGroupImpl(String id, AudioGroupSetup setup) {
	this.id = Util.generateUniqueId(id);
	this.setup = setup;

	if (VoiceImpl.getInstance().addWork(new CreateAudioGroupWork(this)) == false) {
	    audioGroupImplCommit();
	}
    }

    private void audioGroupImplCommit() {
	VoiceImpl.getInstance().putAudioGroup(this);

	if (setup.audioGroupListener != null) {
	    listeners.add(setup.audioGroupListener);
	}

	if (setup.virtualPlayerListener != null) {
	    virtualPlayerHandler = new VirtualPlayerHandler(this, setup.virtualPlayerListener);
	}
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
	if (players.get(player.getId()) != null) {
	    players.put(player.getId(), info);
	    return;
	}

	players.put(player.getId(), info);

	Player p = VoiceImpl.getInstance().getPlayer(player.getId());

	if (p == null) {
	    logger.warning("player null for " + player.getId());
	} else {
	    if (p.toString().equals(player.toString()) == false) {
	       System.out.println("DIFFERENT OBJECTS:  player " + player + " p " + p);
	    }

	    player = p;
	}

	((PlayerImpl) player).addAudioGroupCommit(this);

	for (AudioGroupListener listener : listeners) {
	    VoiceImpl.getInstance().scheduleTask(new Notifier(listener, this, player, info, true));
	}

	if (virtualPlayerHandler != null) {
	    virtualPlayerHandler.playerAdded(player, info);
	}

	player.setPrivateMixes(true);

	//System.out.println("AudioGroup Added " + player.getId() + " to " + this + " call info " + info);
    }

    public void removeAudioGroupListener(AudioGroupListener listener) {
	listeners.remove(listener);
    }

    public void removePlayer(Player player) {
	//System.out.println("Audiogroup removed player " + player + " from " + this);
	//new Exception("removed player " + player).printStackTrace();

	if (VoiceImpl.getInstance().addWork(new RemovePlayerAudioGroupWork(this, player)) == false) {
	    removePlayerCommit(player);
	}
    }

    private void removePlayerCommit(Player player) {
	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	player.removeAudioGroup(this);

	AudioGroupPlayerInfo info = players.remove(player.getId());

	for (AudioGroupListener listener : listeners) {
	    voiceImpl.scheduleTask(new Notifier(listener, this, player, info, false));
	}

	if (virtualPlayerHandler != null) {
	    virtualPlayerHandler.playerRemoved(player, info);
	}

	//System.out.println("AudioGroup removed " + player.getId() + " from " + this);

	if (setup.removeWhenLastPlayerRemoved && players.size() == 0) {
	    VoiceManagerParameters parameters = voiceImpl.getVoiceManagerParameters();

	    if (this.equals(parameters.livePlayerAudioGroup) == false &&
		    this.equals(parameters.stationaryPlayerAudioGroup) == false) {

	        voiceImpl.removeAudioGroup(this);
	    }
	}
    }

    public Player[] getPlayers() {
        VoiceImpl voiceImpl = VoiceImpl.getInstance();

	String[] keys = players.keySet().toArray(new String[0]);

	ArrayList<Player> players = new ArrayList();

	for (int i = 0; i < keys.length; i++) {
	    Player player = voiceImpl.getPlayer(keys[i]);

	    if (player == null) {
		logger.warning("Can't find player for " + keys[i]);
		players.remove(keys[i]);
		continue;
	    }
	
	    players.add(player);
	}

	return players.toArray(new Player[0]);
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
	AudioGroupPlayerInfo info = players.get(player.getId());

	if (info != null) {
	    info.speakingAttenuation = speakingAttenuation;
	} else {
	    logger.warning("No playerInfo for " + player);
	}

	player.setPrivateMixes(true);
    }

    public void setListenAttenuation(Player player, double listenAttenuation) {
	if (VoiceImpl.getInstance().addWork(
	        new SetListenAttenuationWork(this, player, listenAttenuation)) == false) {

	    setListenAttenuationCommit(player, listenAttenuation);
	}
    }

    private void setListenAttenuationCommit(Player player, double listenAttenuation) {
	AudioGroupPlayerInfo info = players.get(player.getId());

	if (info == null) {
	    logger.warning("Unable to set listen attenuation for " + player);
	    return;
	}

	info.listenAttenuation = listenAttenuation;
	player.setPrivateMixes(true);
    }

    public void setSpeaking(Player player, boolean isSpeaking) {
	if (VoiceImpl.getInstance().addWork(new SetSpeakingWork(this, player, isSpeaking)) == false) {
	    setSpeakingCommit(player, isSpeaking);
	}
    }

    private void setSpeakingCommit(Player player, boolean isSpeaking) {
	AudioGroupPlayerInfo info = players.get(player.getId());

	if (info == null) {
	    return;
	}

	info.isSpeaking = isSpeaking;
    }

    public AudioGroupPlayerInfo getPlayerInfo(Player player) {
	return players.get(player.getId());
    }

    public void commit(AudioGroupWork work) {
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

    private class Notifier implements KernelRunnable, NonDurableTransactionParticipant {

	private AudioGroupListener listener;
	private AudioGroup audioGroup;
	private Player player;
	private AudioGroupPlayerInfo info;
	private boolean add;

	public Notifier(AudioGroupListener listener, AudioGroup audioGroup, Player player, 
	 	AudioGroupPlayerInfo info, boolean add) {

	    this.listener = listener;
	    this.audioGroup = audioGroup;
	    this.player = player;
	    this.info = info;
	    this.add = add;
	}

	public String getBaseTaskType() {
	    return Notifier.class.getName();
	}

	public void run() throws Exception {
            VoiceImpl.getInstance().joinTransaction(this);

	    /*
	     * This runs in a transaction and the txnProxy
	     * is usable at this point.  It's okay to get a manager
	     * or another service.
	     *
	     * This method could get called multiple times if
	     * ExceptionRetryStatus is thrown.
	     */
	    if (add == true) {
		listener.playerAdded(audioGroup, player, info);
	    } else {
		listener.playerRemoved(audioGroup, player, info);
	    }
        }

        public boolean prepare(Transaction txn) throws Exception {
            return false;
	}

        public void abort(Transaction t) {
	}

	public void prepareAndCommit(Transaction txn) throws Exception {
            prepare(txn);
            commit(txn);
	}

	public void commit(Transaction t) {
	}

        public String getTypeName() {
	    return "AudioGroupNotifier";
	}

    }

    public String dump() {
	String s = toString();

	if (players.size() == 0) {
	    return s;
	}

	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	Iterator<String> it = players.keySet().iterator();
	
	while (it.hasNext()) {
	    String key = it.next();

	    AudioGroupPlayerInfo info = players.get(key);
	    
	    Player player = voiceImpl.getPlayer(key);

            s += "\n  " + player + " " + info;
	}
	
	return s;
    }

    public boolean equals(Object o) {
	if (o instanceof AudioGroup == false) {
	    return false;
	}

	AudioGroup audioGroup = (AudioGroup) o;

	return id.equals(audioGroup.getId());
    }

    public String toString() {
	return id + ":  " + setup.spatializer;
    }

}
