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

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo.ChatType;
import com.sun.mpk20.voicelib.app.AudioGroupListener;
import com.sun.mpk20.voicelib.app.AudioGroupSetup;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Util;
import com.sun.mpk20.voicelib.app.VirtualPlayer;
import com.sun.mpk20.voicelib.app.VirtualPlayerListener;
import com.sun.mpk20.voicelib.app.VoiceManager;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

import java.io.Serializable;

import java.util.ArrayList;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.logging.Logger;

public class VirtualPlayerHandler implements Serializable {

    private static final Logger logger =
        Logger.getLogger(VirtualPlayerHandler.class.getName());

    private AudioGroup audioGroup;

    private VirtualPlayerListener listener;

    public VirtualPlayerHandler(AudioGroup audioGroup, VirtualPlayerListener listener) {
	this.audioGroup = audioGroup;
	this.listener = listener;
    }

    public void playerAdded(Player player, AudioGroupPlayerInfo playerInfo) {
	if (playerInfo.isTransientMember) {
	    return;
	}

	Player[] players = audioGroup.getPlayers();

	for (int i = 0; i < players.length; i++) {
	    Player p = players[i];

	    if (p.equals(player) || p.getSetup().isLivePlayer == false) {
		continue;
	    }
		
	    AudioGroupPlayerInfo info = audioGroup.getPlayerInfo(p);

	    if (info.isTransientMember) {
		continue;
	    }

	    if (info.chatType == AudioGroupPlayerInfo.ChatType.PUBLIC) {
	        logger.info("Creating virtual player for " + p + ": " + player);
	        createVirtualPlayer(p, player);
	    }

	    if (playerInfo.chatType == AudioGroupPlayerInfo.ChatType.PUBLIC) {
	        logger.info("Creating virtual player for " + player + ": " + p);
	        createVirtualPlayer(player, p);
	    }
	}

	if (playerInfo.chatType != AudioGroupPlayerInfo.ChatType.PUBLIC) {
	    removeVirtualPlayers(player);
	}
    }

    private void createVirtualPlayer(Player playerWithVp, Player player) {
	String id = player.getId() + "-to-" + playerWithVp.getId();

	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	if (voiceImpl.getPlayer(id) != null) {
	    logger.fine("Player " + id + " already exists");
	    return;
	}

	Call call = player.getCall();

	if (call.getSetup().externalOutgoingCall == true ||
		call.getSetup().incomingCall == true) {

	    logger.fine("Don't create a virtual player attached to " + playerWithVp);
	    return;
	}

	PlayerSetup setup = new PlayerSetup();
	double scale = voiceImpl.getVoiceManagerParameters().scale;
	setup.x = -playerWithVp.getX() * scale;
	setup.y = playerWithVp.getY() * scale;
	setup.z = playerWithVp.getZ() * scale;
	setup.orientation = Math.toDegrees(playerWithVp.getOrientation());
	setup.isLivePlayer = true;
	setup.isVirtualPlayer = true;

	Player vp = voiceImpl.createPlayer(id, setup);

	//System.out.println("CREATE VP:  player " + player + " p with vp " 
	//    + playerWithVp + " vp " + vp);

	vp.setCall(call);

	voiceImpl.getVoiceManagerParameters().livePlayerAudioGroup.addPlayer(vp, 
	    new AudioGroupPlayerInfo(true, AudioGroupPlayerInfo.ChatType.PUBLIC));

	VirtualPlayer virtualPlayer = new VirtualPlayer(audioGroup, vp, player, playerWithVp);

	playerWithVp.addVirtualPlayer(virtualPlayer);

	voiceImpl.scheduleTask(new Notifier(virtualPlayer));
    }

    public void playerRemoved(Player player, AudioGroupPlayerInfo info) {
	removeVirtualPlayers(player);

	/* 
	 * Now remove virtual players that other players have for us.
	 */
	VoiceImpl voiceImpl = VoiceImpl.getInstance();
	
	ArrayList<VirtualPlayer> othersToRemove = new ArrayList();

	Player[] players = audioGroup.getPlayers();

	for (int i = 0; i < players.length; i++) {
	    Player p = players[i];

	    if (p.equals(player)) {
		continue;
	    }

	    VirtualPlayer[] virtualPlayers = p.getVirtualPlayers();

	    for (int j = 0; j < virtualPlayers.length; j++) {
		VirtualPlayer vp = virtualPlayers[j];

		if (vp.realPlayer.equals(player)) {
		    othersToRemove.add(vp);
		    p.removeVirtualPlayer(vp);
		    voiceImpl.removePlayer(vp.player);
	    	    voiceImpl.getVoiceManagerParameters().livePlayerAudioGroup.removePlayer(
			vp.player);
		}
	    }
	}

	logger.fine("othersToRemoveSize " + othersToRemove.size());

	voiceImpl.scheduleTask(new Notifier(othersToRemove.toArray(new VirtualPlayer[0])));
    }

    private void removeVirtualPlayers(Player player) {
	VirtualPlayer[] virtualPlayersToRemove = player.getVirtualPlayers();

	VoiceImpl voiceImpl = VoiceImpl.getInstance();

	voiceImpl.scheduleTask(new Notifier(virtualPlayersToRemove));
	
	for (int i = 0; i < virtualPlayersToRemove.length; i++) {
	    VirtualPlayer vp = virtualPlayersToRemove[i];

	    voiceImpl.removePlayer(vp.player);
	    voiceImpl.getVoiceManagerParameters().livePlayerAudioGroup.removePlayer(vp.player);
	    player.removeVirtualPlayer(vp);
	}
    }

    private class Notifier implements KernelRunnable, NonDurableTransactionParticipant {

	private VirtualPlayer[] virtualPlayers;

	private VirtualPlayer virtualPlayer;

	public Notifier(VirtualPlayer[] virtualPlayers) {
	    this.virtualPlayers = virtualPlayers;
	}

	public Notifier(VirtualPlayer virtualPlayer) {
	    this.virtualPlayer = virtualPlayer;
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
	    if (virtualPlayer != null) {
		listener.virtualPlayerAdded(virtualPlayer);
	    } else {
	        listener.virtualPlayersRemoved(virtualPlayers);
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
	    return "VirtualPlayerNotifier";
	}

    }
}
