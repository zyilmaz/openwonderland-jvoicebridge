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

import com.sun.mpk20.voicelib.impl.service.voice.work.player.*;

import java.math.BigInteger;

import java.io.IOException;
import java.io.Serializable;

import java.lang.Integer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupPlayerInfo;
import com.sun.mpk20.voicelib.app.AudioGroupSetup;
import com.sun.mpk20.voicelib.app.AudioSink;
import com.sun.mpk20.voicelib.app.AudioSource;
import com.sun.mpk20.voicelib.app.BridgeInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.DefaultSpatializer;
import com.sun.mpk20.voicelib.app.DefaultSpatializers;
import com.sun.mpk20.voicelib.app.FullVolumeSpatializer;
import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerInRangeListener;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Spatializer;
import com.sun.mpk20.voicelib.app.Util;
import com.sun.mpk20.voicelib.app.VirtualPlayer;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceService;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;
import com.sun.mpk20.voicelib.app.ZeroVolumeSpatializer;
import com.sun.mpk20.voicelib.app.Wall;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;

import java.awt.geom.Line2D;

public class PlayerImpl implements Player, CallStatusListener, Serializable {

    private static final Logger logger =
        Logger.getLogger(PlayerImpl.class.getName());

    private static final double ZERO_VOLUME = .009;

    private PlayerSetup setup;

    private String id;

    private Call call;

    private AudioSink audioSink;

    private AudioSource audioSource;

    /*
     * We would like to use the Cartesian Coordinate system.
     * However, with Darkstar, x is positive to the left in stead of the right.
     * Also, y and z are reversed.
     */
    private double x;
 
    private double y;

    private double z;

    private double orientation;  

    private boolean isRecording;

    private double masterVolume = 1.0;

    private Spatializer publicSpatializer;

    private ArrayList<AudioGroup> audioGroups = new ArrayList();

    private CopyOnWriteArrayList<VirtualPlayer> virtualPlayers = new CopyOnWriteArrayList();

    private ConcurrentHashMap<String, Spatializer> privateSpatializers =
	new ConcurrentHashMap<String, Spatializer>();

    private CopyOnWriteArrayList<Player> playersInRange = new CopyOnWriteArrayList<Player>();

    private static double scale;

    static {
        scale =  VoiceImpl.getInstance().getVoiceManagerParameters().scale;
    }

    public PlayerImpl(String id, PlayerSetup setup) {
	this.id = id;
	this.setup = setup;

	setup.x = -setup.x;

	publicSpatializer = setup.publicSpatializer;

	logger.info("creating player for " + id
            + " at (" + setup.x + ", " + setup.y + ", " + setup.z + ": "
	    + setup.orientation + ")" + " setup.isOutworlder " + setup.isOutworlder);

	if (VoiceImpl.getInstance().addWork(new CreatePlayerWork(this)) == false) {
	    playerImplCommit();
	}
    }

    private void playerImplCommit() {
	setPosition(setup.x, setup.y, setup.z);
	setOrientation(setup.orientation);

	VoiceImpl.getInstance().addCallStatusListener(this, id);
	VoiceImpl.getInstance().putPlayer(this);
    }

    public String getId() {
	return id;
    }

    public PlayerSetup getSetup() {
	return setup;
    }

    private void removePlayerCommit() {
        AudioGroup[] audioGroups = VoiceImpl.getInstance().getAudioGroups();

	for (int i = 0; i < audioGroups.length; i++) {
	    audioGroups[i].removePlayer(this);
	}

	for (VirtualPlayer vp : virtualPlayers) {
              VoiceImpl.getInstance().removePlayer(vp.player);
        }

        virtualPlayers.clear();

        VoiceImpl.getInstance().removeCallStatusListener(this, id);
	VoiceImpl.getInstance().removePlayer(this);
    }

    public void setCall(Call call) {
	if (VoiceImpl.getInstance().addWork(new SetCallWork(this, call)) == false) {
	    setCallCommit(call);
	}
    }

    private void setCallCommit(Call call) {
	this.call = call;

	setPrivateMixesCommit(true);
    }

    public Call getCall() {
	return call;
    }

    public void addVirtualPlayer(VirtualPlayer vp) {
        if (VoiceImpl.getInstance().addWork(new AddVirtualPlayerWork(this, vp)) == false) {
	    addVirtualPlayerCommit(vp);
	}
    }

    private void addVirtualPlayerCommit(VirtualPlayer vp) {
	virtualPlayers.add(vp);
    }

    public void removeVirtualPlayer(VirtualPlayer vp) {
        if (VoiceImpl.getInstance().addWork(new RemoveVirtualPlayerWork(this, vp)) == false) {
	    removeVirtualPlayerCommit(vp);
	}
    }

    private void removeVirtualPlayerCommit(VirtualPlayer vp) {
	virtualPlayers.remove(vp);
    }

    public VirtualPlayer[] getVirtualPlayers() {
	return virtualPlayers.toArray(new VirtualPlayer[0]);
    }

    public AudioSink getAudioSink() {
	return audioSink;
    }

    public void setAudioSink(AudioSink AudioSink) {
	this.audioSink = audioSink;
    }

    public AudioSource getAudioSource() {
	return audioSource;
    }

    public void setAudioSource(AudioSource AudioSource) {
	this.audioSource = audioSource;
    }

    public double getX() {
	return x;
    }

    public double getY() {
	return y;
    }

    public double getZ() {
	return z;
    }

    public double getOrientation() {
	return orientation;
    }

    public void moved(double x, double y, double z, double orientation) {
        if (VoiceImpl.getInstance().addWork(new MovedWork(this, x, y, z, orientation)) == false) {
	    movedCommit(x, y, z, orientation);
	}
    }

    private void movedCommit(double x, double y, double z, double orientation) {
	boolean positionChanged;

	x = -x;

	double prevX = getX();
	double prevY = getY();
	double prevZ = getZ();

	setPosition(x, y, z);
	setOrientation(orientation);

	positionChanged = getX() != prevX || getY() != prevY || getZ() != prevZ;

	logger.finest("Player " + this + " moved to " + x + ":" + y + ":" + z
	    + " orientation " + orientation + " positionChanged = " + positionChanged);

	setPrivateMixes(positionChanged);
    }

    private void setPosition(double x, double y, double z) {
	this.x = Util.round100(x / scale);
	this.y = Util.round100(y / scale);
	this.z = Util.round100(z / scale);
    }

    public boolean samePosition(double x, double y, double z) {
    	return getX() == Util.round100(x) && getY() == Util.round100(y) &&
	    getZ() == Util.round100(z);
    }
	
    private void setOrientation(double orientation) {
	orientation = Util.round100(Math.toRadians(orientation));

	this.orientation = orientation;
    }

    public boolean sameOrientation(double orientation) {
	orientation = Util.round100(Math.toRadians(orientation));

	return getOrientation() == orientation;
    }

    public void setRecording(boolean isRecording) {
        if (VoiceImpl.getInstance().addWork(new SetRecordingWork(this, isRecording)) == false) {
	    setRecordingCommit(isRecording);
	}
    }

    private void setRecordingCommit(boolean isRecording) {
	this.isRecording = isRecording;
    }

    public boolean isRecording() {
	return isRecording;
    }

    public void setPublicSpatializer(Spatializer spatializer) {
        if (VoiceImpl.getInstance().addWork(
	        new SetPublicSpatializerWork(this, spatializer)) == false) {

	    setPublicSpatializerCommit(spatializer);
	}
    }

    private void setPublicSpatializerCommit(Spatializer spatializer) {
	this.publicSpatializer = publicSpatializer;
    }

    public Spatializer getPublicSpatializer() {
	return publicSpatializer;
    }

    public void setPrivateSpatializer(Player player, Spatializer spatializer) {
        if (VoiceImpl.getInstance().addWork(
	        new SetPrivateSpatializerWork(this, player, spatializer)) == false) {

	    setPrivateSpatializerCommit(player, spatializer);
	}
    }

    private void setPrivateSpatializerCommit(Player player, Spatializer spatializer) {
	if (spatializer == null) {
	    privateSpatializers.remove(player.getId());
	    return;
	}

	privateSpatializers.put(player.getId(), spatializer);
	setPrivateMixes(true);
    }

    public Spatializer getPrivateSpatializer(Player player) {
	return privateSpatializers.get(player.getId());
    }

    public void removePrivateSpatializer(Player player) {
        if (VoiceImpl.getInstance().addWork(new RemovePrivateSpatializerWork(this, player)) == false) {
	    removePrivateSpatializerCommit(player);
	}
    }

    private void removePrivateSpatializerCommit(Player player) {
	privateSpatializers.remove(player.getId());
    }

    public void setMasterVolume(double masterVolume) {
        if (VoiceImpl.getInstance().addWork(new SetMasterVolumeWork(this, masterVolume)) == false) {
	    setMasterVolumeCommit(masterVolume);
	}
    }

    private void setMasterVolumeCommit(double masterVolume) {
	this.masterVolume = masterVolume;
	setPrivateMixes(false);
    }

    public double getMasterVolume() {
	return masterVolume;
    }

    public void addAudioGroup(AudioGroup audioGroup) {
        if (VoiceImpl.getInstance().addWork(new AddAudioGroupWork(this, audioGroup)) == false) {
	    addAudioGroupCommit(audioGroup);
	}
    }

    public void addAudioGroupCommit(AudioGroup audioGroup) {
	if (audioGroups.contains(audioGroup) == false) {
	    audioGroups.add(audioGroup);
	} else {
	    logger.warning(this + " is aleady in audio group " + audioGroup);
	}

	setPrivateMixes(true);
    }

    public void removeAudioGroup(AudioGroup audioGroup) {
        if (VoiceImpl.getInstance().addWork(new RemoveAudioGroupPlayerWork(this, audioGroup)) == false) {
	    removeAudioGroupCommit(audioGroup);
	}
    }

    private void removeAudioGroupCommit(AudioGroup audioGroup) {
	if (audioGroups.contains(audioGroup)) {
	    audioGroups.remove(audioGroup);
	    logger.warning("removed " + this + " from " + audioGroup);
	}

	setPrivateMixes(true);
    }

    public void attenuateOtherGroups(AudioGroup audioGroup, 
	    double speakingAttenuation,  double listenAttenuation) {

        if (VoiceImpl.getInstance().addWork(
	        new AttenuateOtherGroupsWork(this, audioGroup, speakingAttenuation,
	        listenAttenuation)) == false) {

	    attenuateOtherGroupsCommit(audioGroup, speakingAttenuation, listenAttenuation);
	}
    }

    private void attenuateOtherGroupsCommit(AudioGroup audioGroup, 
	    double speakingAttenuation, double listenAttenuation) {

	AudioGroup[] audioGroups = getAudioGroups();

	for (int i = 0; i < audioGroups.length; i++) {
	    AudioGroup ag = audioGroups[i];

	    if (ag.equals(audioGroup)) {
		continue;
	    }

	    AudioGroupPlayerInfo info = ag.getPlayerInfo(this);

	    if (info == null) {
	  	logger.warning("info for " + this + " is null for audio group " + ag); 
		continue;
	    }

	    info.speakingAttenuation = speakingAttenuation;
	    info.listenAttenuation = listenAttenuation;

	    logger.finest("group " + audioGroup + " " + this 
		+ " setting listen attenuation to " 
		+ listenAttenuation + " " + ag + " info " + info);
	}

	setPrivateMixes(true);
    }

    public AudioGroup[] getAudioGroups() {
	return audioGroups.toArray(new AudioGroup[0]);
    }

    private CopyOnWriteArrayList<PlayerInRangeListener> playersInRangeListeners =
	new CopyOnWriteArrayList();

    public void addPlayerInRangeListener(PlayerInRangeListener listener) {
	if (playersInRangeListeners.contains(listener)) {
	    return;
	}

	for (Player playerInRange : playersInRange) {
	    listener.playerInRange(this, playerInRange, true);
	}

	playersInRangeListeners.add(listener);
    }

    public void removePlayerInRangeListener(PlayerInRangeListener listener) {
	playersInRangeListeners.remove(listener);
    }

    public boolean isInRange(Player p) {
	return playersInRange.contains(p);
    }

    public void addPlayerInRange(Player p) {
	if (playersInRange.contains(p)) {
	    logger.warning("playersInRange already contains " + p);
	    return;
	}
	playersInRange.add(p);

	notifyPlayerInRangeListeners(this, p, true);
    }

    public void removePlayerInRange(Player p) {
	if (playersInRange.contains(p) == false) {
	    logger.warning("playersInRange doesn't contain " + p);
	    return;
	}
	playersInRange.remove(p);

	notifyPlayerInRangeListeners(this, p, false);
    }

    private void notifyPlayerInRangeListeners(Player p, Player playerInRange, boolean isInRange) {
	for (PlayerInRangeListener listener : playersInRangeListeners) {
	     new Notifier(listener, p, playerInRange, isInRange);
	}
    }

    private long timeToSpatialize;

    private long timeToSetMixes;
 
    private Integer lock = new Integer(0);

    public void setPrivateMixes(boolean positionChanged) {
        if (VoiceImpl.getInstance().addWork(new SetPrivateMixesWork(
	        this, positionChanged)) == false) {

	    setPrivateMixesCommit(positionChanged);
	}
    }

    private void setPrivateMixesCommit(boolean positionChanged) {
	//Adjust private mixes for all calls
	//For each call, we have to determine where it is
	// in 3-space relative to each other call
	// and set the private mix accordingly.

        Player[] playersArray = VoiceImpl.getInstance().getPlayers();

	logger.finer("Players " + playersArray.length + " changed " + this);

	/*
	 * We only need to adjust the private mixes between the
	 * changed player and other calls.
	 */
	for (int i = 0; i < playersArray.length; i++) {
	    Player p1 = playersArray[i];

	    if (p1 == this) {
		continue;
	    }

	    /*
	     * If we didn't change position,
	     * then the mix for other players won't be affected
	     * because our player's orientation has
	     * no effect on the direction other player's hear
	     * the our player's sound coming from.
	     */
	    if (positionChanged == true &&
		    (p1.getSetup().isLivePlayer == true || p1.isRecording())) {

		/*
		 * Set the private mix p1 has for us
		 */
		setPrivateMix(p1, this);
	    } 

	    if (getSetup().isLivePlayer == true || isRecording() == false) {
		/*
		 * Only live players have private mixes
		 * Set the private mix we have for p1
		 */
		setPrivateMix(this, p1);
	    }
	}

	if (logger.isLoggable(Level.FINE) == false) {
	    return;
	}
    }

    /*
     * Set the private mix p1 has for p2
     */
    private void setPrivateMix(Player p1, Player p2) {
	logger.finest("setting pm which " + p1 + " has for " + p2);

	if (p1.getCall() == null || p2.getCall() == null) {
	    logger.warning("Can't set pm " + p1 + " has for " + p2
		+ " p1 call " + p1.getCall() + " p2 call " + p2.getCall());
	    return;
	}

	if (p1.getCall().getId().equals(p2.getCall().getId())) {
	    return;
	}

	double volume = p1.spatialize(p2);

	boolean inRange = p1.isInRange(p2);

	if (volume == 0) {
	    if (inRange == false) {
		/*
		 * This is an optimization.  p2 was not in range
		 * and we already knew that.
		 */
	        logger.finest("pmx for " + p1 + ": "
	            + p2 + " is not in range."); 

		return;
	    }

	    logger.finest("pmx for " + p1 + ": "
	        + p2 + " no longer in range."); 

	    p1.removePlayerInRange(p2);   // p2 is not in range any more
	} else {
	    if (inRange == false) {
	    	logger.finest("pmx for " + p1 + ": "
	            + p2 + " setting in range."); 

		p1.addPlayerInRange(p2);  // p2 is in range now
	    }
	}
    }

    /*
     * For each AudioGroup in which this Player is a member,
     * determine if we need to set a private mix for other
     * players in the AudioGroup.
     */
    public double spatialize(Player p) {
	AudioGroup[] audioGroups = p.getAudioGroups();

	//logger.warning("groups size " + getAudioGroups().length + " for " + this);
	//logger.warning("groups size " + p.getAudioGroups().length + " for " + p);

	double[] privateMixParameters = new double[4];

	if (p.getCall() == null) {
	    return 0;
	}

	if (p.getCall().isMuted() == false) {
	    for (int i = 0; i < audioGroups.length; i++) {
		AudioGroup audioGroup = audioGroups[i];

	        //logger.warning("ag " + audioGroup);

                if (this.audioGroups.contains(audioGroup) == false) {
                    logger.finest(p + " not in audio group " + audioGroup + " of " + this);
                    continue;
                }

	        AudioGroupPlayerInfo info = audioGroup.getPlayerInfo(p);

		if (info == null) {
		    System.out.println("this " + this + " p null " + p + " group " + audioGroup);
		}

	        if (info.isSpeaking == false) {
		    logger.fine(this + "::: " + p + " not speaking in " + audioGroup);
		    continue;  // p is not speaking in the group
	        }

		Spatializer spatializer = audioGroup.getSetup().spatializer;

		if (p.getPublicSpatializer() != null) {
		    spatializer = p.getPublicSpatializer();
		}

	        double[] pmp = spatializer.spatialize(
	            p.getX(), p.getY(), p.getZ(), p.getOrientation(), 
		    getX(), getY(), getZ(), getOrientation());

	        if (pmp[3] > privateMixParameters[3]) {
	            logger.finest("group " + audioGroup + " " + this + " has pm for " + p 
		        + " vol " + privateMixParameters[3] + " ag " + audioGroup
		        + " la " + audioGroup.getPlayerInfo(this).listenAttenuation + " sap "
		        + info.speakingAttenuation + " pmp[3] " + pmp[3]);

		    privateMixParameters = pmp;
	        }

	        if (privateMixParameters[3] != 0) {
	            privateMixParameters[3] *= 
		        audioGroup.getPlayerInfo(this).listenAttenuation * info.speakingAttenuation;
	        }
	    }
	} else {
	    logger.finest(p + " is Muted");
	}

	logger.finest("pm vol is " + privateMixParameters[3]);

	if (privateMixParameters[3] != 0) {
	    /*
	     * Only use a private spatializer if we would have heard something
	     * from p.
	     */
	    Spatializer spatializer = getPrivateSpatializer(p);

	    if (spatializer != null) {
	        privateMixParameters = spatializer.spatialize(
	            p.getX(), p.getY(), p.getZ(), p.getOrientation(), 
		    getX(), getY(), getZ(), getOrientation());
	    } else {
		privateMixParameters[3] *= getWallAttenuation(p);
	    }

	    privateMixParameters[3] *= masterVolume;
	} else {
	    if (isInRange(p) == false) {
		return 0;	// it's not in range and we already knew it
	    }
	}

	/*
	 * Debug
	 */
	//if (privateMixParameters[0] == 0 && privateMixParameters[1] == 0 &&
	//        privateMixParameters[2] == 0 && privateMixParameters[3] == 0) {

	//     dumpEverything(p);
	//}

        if (privateMixParameters[3] > .1) {
            logger.finest("this=" + this + " p=" + p + " mix "
                + Util.round100(privateMixParameters[0]) + ", "
                + Util.round100(privateMixParameters[1]) + ", "
                + Util.round100(privateMixParameters[2]) + ", "
                + Util.round100(privateMixParameters[3]));
        }

        VoiceImpl.getInstance().getBridgeManager().setPrivateMix(
	    getCall().getId(), p.getCall().getId(), privateMixParameters);

	return privateMixParameters[3];
    }

    private void dumpEverything(Player p) {
	System.out.println("=======");
	     
	System.out.println(VoiceImpl.getInstance().dump("all"));

	spatializeDebug(p);

	System.out.println("=======");
    }

    /*
     * For each AudioGroup in which this Player is a member,
     * determine if we need to set a private mix for other
     * players in the AudioGroup.
     */
    public void spatializeDebug(Player p) {
	AudioGroup[] audioGroups = p.getAudioGroups();

	System.out.println("groups size " + getAudioGroups().length + " for " + this);
	System.out.println("groups size " + p.getAudioGroups().length + " for " + p);

	double[] privateMixParameters = new double[4];

	if (p.getCall() == null) {
	    return;
	}

	if (p.getCall().isMuted() == false) {
	    System.out.println("audioGroups length " + audioGroups.length);

	    for (int i = 0; i < audioGroups.length; i++) {
		AudioGroup audioGroup = audioGroups[i];

	        System.out.println("ag " + audioGroup);

                if (this.audioGroups.contains(audioGroup) == false) {
                    System.out.println(p + " not in audio group " + audioGroup + " of " + this);
                    continue;
                }

	        AudioGroupPlayerInfo info = audioGroup.getPlayerInfo(p);

		if (info == null) {
		    System.out.println("this " + this + " p null " + p + " group " + audioGroup);
		}

	        if (info.isSpeaking == false) {
		    System.out.println(this + "::: " + p + " not speaking in " + audioGroup);
		    continue;  // p is not speaking in the group
	        }

		Spatializer spatializer = audioGroup.getSetup().spatializer;

		if (p.getPublicSpatializer() != null) {
		    spatializer = p.getPublicSpatializer();
		}

	        double[] pmp = spatializer.spatialize(
	            p.getX(), p.getY(), p.getZ(), p.getOrientation(), 
		    getX(), getY(), getZ(), getOrientation());

	        if (pmp[3] > privateMixParameters[3]) {
	            System.out.println("group " + audioGroup + " " + this + " has pm for " + p 
		        + " vol " + privateMixParameters[3] + " ag " + audioGroup
		        + " la " + audioGroup.getPlayerInfo(this).listenAttenuation + " sap "
		        + info.speakingAttenuation + " pmp[3] " + pmp[3]);

		    privateMixParameters = pmp;
	        }

	        if (privateMixParameters[3] != 0) {
	            privateMixParameters[3] *= 
		        audioGroup.getPlayerInfo(this).listenAttenuation * info.speakingAttenuation;
	        }
	    }
	} else {
	    System.out.println(p + " is Muted");
	}

	System.out.println ("pm vol is " + privateMixParameters[3]);

	if (privateMixParameters[3] != 0) {
	    /*
	     * Only use a private spatializer if we would have heard something
	     * from p.
	     */
	    Spatializer spatializer = getPrivateSpatializer(p);

	    if (spatializer != null) {
		System.out.println("Using private spatializer");
	        privateMixParameters = spatializer.spatialize(
	            p.getX(), p.getY(), p.getZ(), p.getOrientation(), 
		    getX(), getY(), getZ(), getOrientation());
	    } else {
		privateMixParameters[3] *= getWallAttenuation(p);
	    }

	    privateMixParameters[3] *= masterVolume;
	} else {
	    if (isInRange(p) == false) {
		System.out.println("Not in range and we knew it");
		return;	// it's not in range and we already knew it
	    }
	}

        if (privateMixParameters[3] > .1) {
            System.out.println("this=" + this + " p=" + p + " mix "
                + Util.round100(privateMixParameters[0]) + ", "
                + Util.round100(privateMixParameters[1]) + ", "
                + Util.round100(privateMixParameters[2]) + ", "
                + Util.round100(privateMixParameters[3]));
        }
    }

    private double getWallAttenuation(Player p2) {
	double wallAttenuation = 1.0;

	for (Wall wall : walls) {
	    if (Line2D.linesIntersect(x, y, p2.getX(), p2.getY(), 
		wall.startX, wall.startY, wall.endX, wall.endY)) {

		logger.finest(wall + " between " + this + " and " + p2);

		return wall.characteristic;
	    }
	}

	return wallAttenuation;
    }

    private static ArrayList<Wall> walls = new ArrayList<Wall>();

    public void addWall(double startX, double startY, double endX, double endY,
        double characteristic) {

        //synchronized (walls) {
            walls.add(new Wall(startX, startY, endX, endY, characteristic));
        //}

        setPrivateMixes(true);
    }

    public int getNumberOfPlayersInRange() {
	return VoiceImpl.getInstance().getNumberOfPlayersInRange(x, y, z);
    }

    public void callStatusChanged(CallStatus callStatus) {
	int code = callStatus.getCode();

	String callId = callStatus.getCallId();

	switch (code) {
        case CallStatus.ESTABLISHED:
        case CallStatus.MIGRATED:
            logger.fine("callEstablished: " + callId);
 
 	    setPrivateMixes(true);
            break;

	case CallStatus.ENDED:
	    if (callId == null || id.equals(callId) == false) {
		return;
	    }

	    //removePlayer();
	    break;
        }
    }

    public String dump() {
	String s = toString() + " masterVolume " + masterVolume;
	
	s += "\n  Audio Groups: ";

	for (AudioGroup audioGroup : audioGroups) {
	    s += " " + audioGroup.getId();
	}

	if (privateSpatializers.size() > 0) {
	    s += "\n  Private Spatializers:";

	    Set<String> keys = privateSpatializers.keySet();

	    Iterator<String> it = keys.iterator();

	    String space = " ";

	    int i = 0;

	    while (it.hasNext()) {
		if (i > 0) {
		    space = "\n                          ";
		}
		String key = it.next();

		s += space + key + " " + privateSpatializers.get(key);
	    }
	}
	
	if (virtualPlayers.size() > 0) {
	    s += "\n  Virtual Players:  ";

	    for (VirtualPlayer vp : virtualPlayers) {
		s += " " + vp.player.getId();
	    }
	}

	if (playersInRange.size() == 0) {
	    s += "\n  There are no players in range.";
	} else {
	    s += "\n  Players in Range: ";

	    for (Player p : playersInRange) {
	        s += p.getId() + " ";
	    }
	}
	
	return s;
    }

    public void commit(PlayerWork work) {
	VoiceImpl voiceImpl = VoiceImpl.getInstance();

        if (work instanceof CreatePlayerWork) {
	    playerImplCommit();
	    return;
	}

        if (work instanceof RemovePlayerWork) {
	    removePlayerCommit();
	    return;
        } 

	if (work instanceof MovedWork) {
	    MovedWork w = (MovedWork) work;
	    movedCommit(w.x, w.y, w.z, w.orientation);
	    return;
        } 

	if (work instanceof SetRecordingWork) {
	    setRecordingCommit(((SetRecordingWork) work).isRecording);
	    return;
        } 

	if (work instanceof SetPrivateMixesWork) {
	    setPrivateMixesCommit(((SetPrivateMixesWork) work).positionChanged);
	    return;
        } 

	if (work instanceof SetPrivateSpatializerWork) {
	    SetPrivateSpatializerWork w = (SetPrivateSpatializerWork) work;

	    setPrivateSpatializerCommit(w.targetPlayer, w.spatializer);
	}

	if (work instanceof RemovePrivateSpatializerWork) {
	    removePrivateSpatializerCommit(
		((RemovePrivateSpatializerWork) work).targetPlayer);
	    return;
        } 

	if (work instanceof SetPublicSpatializerWork) {
	    setPublicSpatializerCommit(
		((SetPublicSpatializerWork) work).spatializer);
	    return;
        } 

	if (work instanceof SetMasterVolumeWork) {
	    setMasterVolumeCommit(((SetMasterVolumeWork) work).masterVolume);
	    return;
        } 

	if (work instanceof AddAudioGroupWork) {
	    addAudioGroupCommit(((AddAudioGroupWork) work).audioGroup);
	    return;
        } 

	if (work instanceof RemoveAudioGroupPlayerWork) {
	    removeAudioGroupCommit(((RemoveAudioGroupPlayerWork) work).audioGroup);
	    return;
        } 

	if (work instanceof AddVirtualPlayerWork) {
	    addVirtualPlayerCommit(((AddVirtualPlayerWork) work).virtualPlayer);
	    return;
        } 

	if (work instanceof RemoveVirtualPlayerWork) {
	    removeVirtualPlayerCommit(
		((RemoveVirtualPlayerWork) work).virtualPlayer);
	    return;
        }

	if (work instanceof SetCallWork) {
	    setCallCommit(((SetCallWork) work).call);
	    return;
        }

	if (work instanceof AttenuateOtherGroupsWork) {
	    AttenuateOtherGroupsWork w = (AttenuateOtherGroupsWork) work;

	    attenuateOtherGroupsCommit(w.audioGroup, w.speakingAttenuation,
	 	w.listenAttenuation);
	    return;
        }

	logger.warning("Unknown PlayerWork:  " + work);
    }

    public boolean equals(Object o) {
	if (o instanceof Player == false) {
	    return false;
	}

	Player p = (Player) o;

	return id.equals(p.getId());
    }

    public String toString() {
	double xR = Util.round100(x);
	double yR = Util.round100(y);
	double zR = Util.round100(z);

	int a = ((int) Math.toDegrees(orientation)) % 360;

	if (a < 0) {
	    a += 360;
	}

	return getId() + ":(" + xR + "," + yR + "," + zR + "," + a + ")" 
	    + " " + (setup.isLivePlayer ? "LivePlayer" : "") 
	    + (publicSpatializer != null ? publicSpatializer.toString() : "");
    }

    private class Notifier implements KernelRunnable, NonDurableTransactionParticipant {

	private PlayerInRangeListener listener;
	private Player player;
	private Player playerInRange;
	private boolean isInRange;

	public Notifier(PlayerInRangeListener listener, Player player, 
	 	Player playerInRange, boolean isInRange) {

	    this.listener = listener;
	    this.player = player;
	    this.playerInRange = playerInRange;
	    this.isInRange = isInRange;
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
	    listener.playerInRange(player, playerInRange, isInRange);
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
	    return "PlayerInRangeNotifier";
	}

    }
}
