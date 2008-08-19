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

import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.Spatializer;
import com.sun.mpk20.voicelib.app.DefaultSpatializer;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;

import java.awt.geom.Line2D;

import java.io.IOException;
import java.io.Serializable;

import java.lang.reflect.Constructor;

import java.util.concurrent.ConcurrentHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.sun.voip.CallParticipant;

/**
 * This is an implementation of <code>VoiceManager</code>. 
 * It simply calls its backing manager for each manager.
 *
 * @since 1.0
 * @author Joe Provino
 */
public class VoiceManagerImpl implements VoiceManager {

    /** a logger */
    private static final Logger logger =
        Logger.getLogger(VoiceManagerImpl.class.getName());

    // the voice manager that this manager calls through to
    private final VoiceManager backingManager;

    private DefaultSpatializer livePlayerSpatializer;

    private DefaultSpatializer stationarySpatializer;

    private DefaultSpatializer outworlderSpatializer;

    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<String, Player>();

    private static final double LIVE_PLAYER_MAXIMUM_VOLUME = .8;
    private static final double LIVE_PLAYER_ZERO_VOLUME_RADIUS = .22;
    private static final double LIVE_PLAYER_FULL_VOLUME_RADIUS = .08;
    private static final double LIVE_PLAYER_FALLOFF = .95;

    private static final double STATIONARY_MAXIMUM_VOLUME = .2;
    private static final double STATIONARY_ZERO_VOLUME_RADIUS = .16;
    private static final double STATIONARY_FULL_VOLUME_RADIUS = .06;
    private static final double STATIONARY_FALLOFF = .94;

    private static final double OUTWORLDER_MAXIMUM_VOLUME = .4;
    private static final double OUTWORLDER_ZERO_VOLUME_RADIUS = .26;
    private static final double OUTWORLDER_FULL_VOLUME_RADIUS = .1;
    private static final double OUTWORLDER_FALLOFF = .94;

    private static final double ZERO_VOLUME = .009;

    private static Object lock = new Object();

    /**
     * @param backingManager the <code>VoiceManager</code> to call through to
     */
    public VoiceManagerImpl(VoiceManager backingManager) {
        this.backingManager = backingManager;

	livePlayerSpatializer = new DefaultSpatializer();
	livePlayerSpatializer.setMaximumVolume(
	    getPreference("LIVE_PLAYER_MAXIMUM_VOLUME", LIVE_PLAYER_MAXIMUM_VOLUME));

	livePlayerSpatializer.setZeroVolumeRadius(
	    getPreference("LIVE_PLAYER_ZERO_VOLUME_RADIUS", LIVE_PLAYER_ZERO_VOLUME_RADIUS));

	livePlayerSpatializer.setFullVolumeRadius(
	    getPreference("LIVE_PLAYER_FULL_VOLUME_RADIUS", LIVE_PLAYER_FULL_VOLUME_RADIUS));

	livePlayerSpatializer.setFalloff(
	    getPreference("LIVE_PLAYER_FALLOFF", LIVE_PLAYER_FALLOFF));

	stationarySpatializer = new DefaultSpatializer();
	stationarySpatializer.setMaximumVolume(
	    getPreference("STATIONARY_MAXIMUM_VOLUME", STATIONARY_MAXIMUM_VOLUME));
	stationarySpatializer.setZeroVolumeRadius(
	    getPreference("STATIONARY_ZERO_VOLUME_RADIUS", STATIONARY_ZERO_VOLUME_RADIUS));
	stationarySpatializer.setFullVolumeRadius(
	    getPreference("STATIONARY_FULL_VOLUME_RADIUS", STATIONARY_FULL_VOLUME_RADIUS));
	stationarySpatializer.setFalloff(
	    getPreference("STATIONARY_FALLOFF", STATIONARY_FALLOFF));

  	outworlderSpatializer = new DefaultSpatializer();
	outworlderSpatializer.setMaximumVolume(
	    getPreference("OUTWORLDER_MAXIMUM_VOLUME", OUTWORLDER_MAXIMUM_VOLUME));
	outworlderSpatializer.setZeroVolumeRadius(
	    getPreference("OUTWORLDER_ZERO_VOLUME_RADIUS", OUTWORLDER_ZERO_VOLUME_RADIUS));
	outworlderSpatializer.setFullVolumeRadius(
	    getPreference("OUTWORLDER_FULL_VOLUME_RADIUS", OUTWORLDER_FULL_VOLUME_RADIUS));
	outworlderSpatializer.setFalloff(
	    getPreference("OUTWORLDER_FALLOFF", OUTWORLDER_FALLOFF));
    }

    private String VOICEMANAGER_PREFIX = "COM.SUN.MPK20.VOICELIB.IMPL.APP.VOICEMANAGERIMPL.";

    private double getPreference(String preference, double defaultValue) {
        Preferences prefs = Preferences.userNodeForPackage(VoiceManagerImpl.class);

	String s = prefs.get(VOICEMANAGER_PREFIX + preference, null);

	if (s == null) {
	    return defaultValue;
	}

	try {
	    return Double.parseDouble(s);    
	} catch (NumberFormatException e) {
	    logger.warning("Invalid value '" + s + "' for " + preference
		+ ".   Using " + defaultValue);
	    return defaultValue;
	}
    }

    private void setPreference(String preference, double value) {
        Preferences prefs = Preferences.userNodeForPackage(VoiceManagerImpl.class);
	prefs.put(VOICEMANAGER_PREFIX + preference, String.valueOf(value));
    }

    public void monitorConference(String conferenceId) throws IOException {
	backingManager.monitorConference(conferenceId);
    }

    public String getVoiceBridge() {
	return backingManager.getVoiceBridge();
    }

    public void setupCall(CallParticipant cp, double x, double y, double z,
	    double orientation, Spatializer spatializer,
	    String bridge) throws IOException {

	/*
	 * This doesn't complete until commit!
	 * XXX What happens if the call setup fails during commit?
	 */
	backingManager.setupCall(cp, x, y, z, orientation, spatializer,
	    bridge);

	String callId = cp.getCallId();

	Player p = getPlayer(callId);

	if (p != null) {
	    logger.info("Call " + callId + " exists, reset privateMixes.");

	    setPrivateMixes(p);
	    return;
	}

	logger.finer("setupcall putting " + callId);

        p = new Player(callId, x, y, z, orientation);

	if (cp.getInputTreatment() == null) {
	    /*
	     * If there is an input treatment, this is not a live player.
	     * We only set private mixes for live players.
	     */
	    p.setLivePerson();
	}

	if (spatializer == null) {
	    if (cp.getInputTreatment() == null) {
	        p.setPublicSpatializer(livePlayerSpatializer);
		logger.fine(callId + " setting pub spatializer to live " 
		    + livePlayerSpatializer);
	    } else {
	        p.setPublicSpatializer(stationarySpatializer);
		logger.fine(p + " setting pub spatializer to stationary" 
		    + stationarySpatializer);
	    }
	} else {
	    logger.fine(p + " setting pub spatializer to " + spatializer);
	    p.setPublicSpatializer(spatializer);
	}

	if (players.put(callId, p) != null) {
	    logger.info("Player for " + callId + " already existed");
	}

	logger.info("Created player for " + p + " number of players "
	    + players.size());
    }

    public void createPlayer(String callId, double x, double y, double z,
	    double orientation, boolean isOutworlder) {

	Player p;

	if ((p = findPlayer(callId)) != null) {
	    logger.info("player already exists for " + callId);
	} else {
	    logger.info("Creating player for " + callId + " at "
	        + " " + x + ":" + y + ":" + z + ":" + orientation
	        + " isOutworlder " + isOutworlder);

	    p = new Player(callId, x, y, z, orientation);

	    players.put(callId, p);
	}

	p.setLivePerson();

	if (isOutworlder) {
	    p.setIncomingSpatializer(outworlderSpatializer);
	    p.setPublicSpatializer(outworlderSpatializer);
	    logger.fine(callId + " setting pub spatializer to outworlder " 
		+ outworlderSpatializer);
	} else {
	    p.setPublicSpatializer(livePlayerSpatializer);
	    logger.fine(callId + " setting pub spatializer to live " 
		+ livePlayerSpatializer);
	}

	setPrivateMixes(p);
    }

    public void removePlayer(String callId) {
	logger.info("removed player " + callId);

	players.remove(callId);
    }

    public void transferCall(String callId, String conferenceId) 
	    throws IOException {

	backingManager.transferCall(callId, conferenceId);
    }

    public Player getPlayer(String callId) {
	return findPlayer(callId);
    }

    public void setPublicSpatializer(String callId, Spatializer publicSpatializer) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.warning("no Player for " + callId);
            return;
	}

	logger.fine(callId + " setting public spatialializer " + publicSpatializer);

	player.setPublicSpatializer(publicSpatializer);

	/*
	 * Update everything
	 */
	setPrivateMixes();
    }

    public Spatializer getPublicSpatializer(String targetCallId) {
        Player targetPlayer = findPlayer(targetCallId);

        if (targetPlayer == null) {
            logger.info("no targetPlayer for " + targetCallId);
            return null;
	}

	return targetPlayer.getPublicSpatializer();
    }

    public void setPrivateSpatializer(String targetCallId, String sourceCallId,
	    Spatializer spatializer) {

	if (targetCallId == null) {
	    /*
	     * Set a private spatializer for all live Players for sourceCallId
	     */
	    synchronized (players) {
		Collection<Player> values = players.values();

		Iterator<Player> iterator = values.iterator();

		while (iterator.hasNext()) {
		    Player player = iterator.next();

		    if (player.isLivePerson() == false ||
			    player.callId.equals(sourceCallId)) {

			continue;
		    }

		    setPrivateSpatializer(player.callId, sourceCallId, 
			spatializer);
		}
		return;
	    }
	}

        Player targetPlayer = findPlayer(targetCallId);

        if (targetPlayer == null) {
            logger.info("no targetPlayer for " + targetCallId);
            return;
        }

        Player sourcePlayer = findPlayer(sourceCallId);

        if (sourcePlayer == null) {
            logger.info("no sourcePlayer for " + sourceCallId);
            return;
        }

	if (spatializer != null) {
	    double attenuator =  spatializer.getAttenuator();

	    logger.finest(targetCallId + " Setting private spatializer for "
	        + sourceCallId + " att " + attenuator + " target att "
	        + targetPlayer.getListenAttenuator()
		+ " source att "
		+ sourcePlayer.getTalkAttenuator());

	    attenuator *= targetPlayer.getListenAttenuator() *
		sourcePlayer.getTalkAttenuator();

	    spatializer.setAttenuator(attenuator);
	}

	logger.finer(targetPlayer.callId + " set private spatializer for " 
	    + sourceCallId);

        targetPlayer.setPrivateSpatializer(sourceCallId, spatializer);

	/*
	 * Update everything
	 */
	setPrivateMixes();
    }

    public void setIncomingSpatializer(String targetCallId,
	    Spatializer spatializer) {

        Player targetPlayer = findPlayer(targetCallId);

        if (targetPlayer == null) {
            logger.info("no targetPlayer for " + targetCallId);
            return;
        }

        targetPlayer.setIncomingSpatializer(spatializer);

	/*
	 * Update everything
	 */
	setPrivateMixes();
    }

    public Spatializer getIncomingSpatializer(String targetCallId) {
        Player targetPlayer = findPlayer(targetCallId);

        if (targetPlayer == null) {
            logger.info("no targetPlayer for " + targetCallId);
            return null;
	}

	return targetPlayer.getIncomingSpatializer();
    }

    public void setTalkAttenuator(String callId, double talkAttenuator) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	player.setTalkAttenuator(talkAttenuator);

	logger.finest("set talk attenuator to " + talkAttenuator);
	setPrivateMixes(player);
    }

    public double getTalkAttenuator(String callId) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return 0;
	}

	return player.getTalkAttenuator();
    }

    public void setListenAttenuator(String callId, double listenAttenuator) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	player.setListenAttenuator(listenAttenuator);

	setPrivateMixes(player);
    }    
 
    public double getListenAttenuator(String callId) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return 0;
	}

	return player.getListenAttenuator();
    }

    public void callEstablished(String callId) throws IOException {
	logger.fine("call established: " + callId);

	/*
	 * XXX We don't need to do this any more.  Audio treatments are already
	 * added as special Players by the server.
	 */
	if (false && players.putIfAbsent(callId, new Player(callId, .5, .5, 0, 0)) == null) {
	    /*
	     * When a call is started by us, the player is created
	     * during call setup.  Calls started from outside are assumed
	     * to be audio sources using input treatments
	     * and are not considered live players.
	     */
	    logger.finer(
		"Call Established, Creating new Player for " + callId
		+ " players size " + players.size());
	} else {
	    logger.finer("Call Established:  Player already exists");
	}

	setPrivateMixes();
    }

    public void newInputTreatment(String callId, String treatment) 
	    throws IOException {

	backingManager.newInputTreatment(callId, treatment);
    }

    public void stopInputTreatment(String callId) throws IOException {
	backingManager.stopInputTreatment(callId);
    }

    public void restartInputTreatment(String callId) throws IOException {
	backingManager.restartInputTreatment(callId);
    }

    public void restorePrivateMixes() throws IOException {
	setPrivateMixes();
    }

    public void playTreatmentToCall(String callId, String treatment) 
	    throws IOException {

	backingManager.playTreatmentToCall(callId, treatment);
    }

    public void pauseTreatmentToCall(String callId, String treatment) 
	    throws IOException {

	backingManager.pauseTreatmentToCall(callId, treatment);
    }

    public void stopTreatmentToCall(String callId, String treatment) 
	    throws IOException {

	backingManager.stopTreatmentToCall(callId, treatment);
    }

    public void migrateCall(CallParticipant cp) throws IOException {
	String callId = cp.getCallId();

	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);

	    throw new IOException("No player for " + callId);
	}

	cp.setName(player.callId);

	backingManager.migrateCall(cp);
    }

    public void endCall(String callId) throws IOException {
	endCall(callId, true);
    }

    public void endCall(String callId, boolean tellBackingManager) 
	    throws IOException {

	logger.fine("call ending:  " + callId + " players " + players.size());

	removePlayerFromLists(callId);

	/*
	 * There is a timing problem when the softphone is reconnected
	 * so we can't do this here.
	 */
	//players.remove(callId);

	if (tellBackingManager) {
	    backingManager.endCall(callId);
	}
    }

    public void setGroupId(String callId, String groupId) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.info("no Player for " + callId);
            return;
	}

	logger.warning("Setting group Id for " + callId + " to " + groupId);

	player.setGroupId(groupId);

	setPrivateMixes();
    }

    public String getGroupId(String callId) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.info("no Player for " + callId);
            return null;
	}

	return player.getGroupId();
    }

    private void removePlayerFromLists(String callId) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	synchronized (players) {
	    Collection<Player> values = players.values();

	    Iterator<Player> iterator = values.iterator();

	    while (iterator.hasNext()) {
		Player p = iterator.next();

		p.removePrivateSpatializer(callId);

		if ((p.isLivePerson() == false && p.isRecording() == false) || 
			p.isInRange(player) == false) {

		    continue;
		}

		p.removePlayerInRange(player);    
	    }
	}
    }

    public void disconnectCall(String callId) throws IOException {
	logger.finer("call disconnecting:  " + callId);

	backingManager.disconnectCall(callId);
    }

    public void muteCall(String callId, boolean isMuted) throws IOException {
	logger.finer("mute call:  " + callId + " " + isMuted);

	backingManager.muteCall(callId, isMuted);
    }

    /*
     * In 3-d graphics, y and z are switched around which is why
     * the second argument is z and the third y.
     */
    public void setPositionAndOrientation(String callId, double x, double z, 
	    double y, double orientation) throws IOException {

	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	boolean setPrivateMixes = false;

	if (player.samePosition(x, y, z)) {
	    logger.finest("same position:  " + player);
	} else {
	    player.setPosition(x, y, z);
	    setPrivateMixes = true;
	}

	logger.finest("setting pos and orient for " + player);

	if (player.sameOrientation(orientation)) {
	    logger.finest("same orientation:  " + player);
	} else {
	    player.setOrientation(orientation);
	    setPrivateMixes = true;
	}

	if (setPrivateMixes == true) {
	    setPrivateMixes(player);
	}
    }

    /*
     * In 3-d graphics, y and z are switched around which is why
     * the second argument is z and the third y.
     */
    public void setPosition(String callId, double x, double z, double y) 
	    throws IOException {

	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	if (player.samePosition(x, y, z)) {
	    logger.finest("same position:  " + player);
	    return;
	}

	player.setPosition(x, y, z);

	logger.finest("setting position for " + player);

	setPrivateMixes(player);
    }

    public void setOrientation(String callId, double orientation) throws IOException {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	if (player.sameOrientation(orientation)) {
	    logger.finest("same orientation:  " + player);
	    return;
	}

	player.setOrientation(orientation);

	setPrivateMixes(player);
    }

    public void setAttenuationRadius(String callId, double attenuationRadius) 
	    throws IOException {

	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	player.setAttenuationRadius(attenuationRadius);
        setPrivateMixes();
    }
	
    public void setAttenuationVolume(String callId, double attenuationVolume) 
            throws IOException {

	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	player.setAttenuationVolume(attenuationVolume);
        setPrivateMixes();
    }
	
    public void setMasterVolume(String callId, double masterVolume) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return;
	}

	logger.fine("Setting master volume for " + callId
	    + " to " + masterVolume);

	player.setMasterVolume(masterVolume);

	setPrivateMixes();
    }

    public double getMasterVolume(String callId) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.fine("no Player for " + callId);
            return 0;
	}

	return player.getMasterVolume();
    }

    public void setPrivateMix(String targetCallId, String fromCallId,
            double[] privateMixParameters) throws IOException {

	if (backingManager != null) {

	    if (privateMixParameters[3] <= ZERO_VOLUME) {
		privateMixParameters[3] = 0;
	    } else {
	        privateMixParameters[3] = round(privateMixParameters[3]);
	    }

	    backingManager.setPrivateMix(targetCallId, fromCallId,
		privateMixParameters);
	}
    }

    public void setSpatialAudio(boolean enabled) throws IOException {
	if (backingManager != null) {
	    backingManager.setSpatialAudio(enabled);
	}
    }

    public void setSpatialMinVolume(double spatialMinVolume) throws IOException {
	if (backingManager != null) {
	    backingManager.setSpatialMinVolume(spatialMinVolume);
	}
    }

    public void setSpatialFalloff(double spatialFalloff) throws IOException {
	if (backingManager != null) {
	    backingManager.setSpatialFalloff(spatialFalloff);
	}
    }

    public void setSpatialEchoDelay(double spatialEchoDelay) throws IOException {
	if (backingManager != null) {
	    backingManager.setSpatialEchoDelay(spatialEchoDelay);
	}
    }

    public void setSpatialEchoVolume(double spatialEchoVolume) throws IOException {
	if (backingManager != null) {
	    backingManager.setSpatialEchoVolume(spatialEchoVolume);
	}
    }

    public void setSpatialBehindVolume(double spatialBehindVolume) throws IOException {
	if (backingManager != null) {
	    backingManager.setSpatialBehindVolume(spatialBehindVolume);
	}
    }

    public Spatializer getLivePlayerSpatializer() {
	return livePlayerSpatializer;
    }

    public Spatializer getStationarySpatializer() {
	return stationarySpatializer;
    }

    public Spatializer getOutworlderSpatializer() {
	return outworlderSpatializer;
    }

    private ArrayList<Wall> walls = new ArrayList<Wall>();

    public void addWall(double startX, double startY, double endX,
	    double endY, double characteristic) throws IOException {

	synchronized (walls) {
	    walls.add(new Wall(startX, startY, endX, endY, characteristic));
	}

	setPrivateMixes();
    }

    private Player findPlayer(String callId) {
	if (callId == null || callId.length() == 0) {
            logger.info("callId is null or 0 length");
            return null;
        }

        return players.get(callId);
    }

    private int numberOfPrivateMixesSet;

    private long timeToSetMixes;

    private int skipped;

    private long timeToSpatialize;

    private void setPrivateMixes() {
	setPrivateMixes(null);
    }

    private void setPrivateMixes(Player changedPlayer) {
	//Adjust private mixes for all calls
	//For each call, we have to determine where it is
	// in 3-space relative to each other call
	// and set the private mix accordingly.

	long startTime = System.nanoTime();

	Player[] playersArray = players.values().toArray(new Player[0]);

	if (changedPlayer == null) {
	    for (int i = 0; i < playersArray.length; i++) {
	        Player p1 = playersArray[i];

	        for (int j = 0; j < playersArray.length; j++) {
		    Player p2 = playersArray[j];

		    if (p1 == p2 ) {
		        skipped++;
		        continue;
		    }
   
        	    if (p1.isLivePerson() == false && p1.isRecording() == false) {
		        /*
             	         * We only set private mixes for live players
             	         * and recorders.
             	         */
		        skipped++;
	  	        continue;
        	    }

		    /*
         	     * Set the private mix p1 has for p2
	             */
	            setPrivateMix(p1, p2);
	        }
	    }
	} else {
	    /*
	     * We only need to adjust the private mixes between the
	     * changed player and other calls.
	     */
	    for (int i = 0; i < playersArray.length; i++) {
	        Player p1 = playersArray[i];

		if (p1 == changedPlayer) {
		    skipped++;
		    continue;
		}

		/*
		 * If the changed player didn't change position,
		 * then the mix for other players won't be affected
		 * because the changed player's orientation has
	 	 * no effect on the direction other player's hear
		 * the changed player's sound coming from.
		 */
		if (changedPlayer.positionChanged == true &&
		        (p1.isLivePerson() == true || p1.isRecording())) {

		    /*
		     * Set the private mix p1 has for the changed player
		     */
		    setPrivateMix(p1, changedPlayer);
		} else {
		    skipped++;
		}

		if (changedPlayer.isLivePerson() == false && 
			changedPlayer.isRecording() == false) {

		    /*
		     * Only live players have private mixes
		     */
		    skipped++;
		} else {
		    logger.finest("Setting pm for " + changedPlayer
			+ " target " + p1);

		    /*
		     * Set the private mix changedPlayer has for p1
		     */
		    setPrivateMix(changedPlayer, p1);
		}
	    }
	}

	if (logger.isLoggable(Level.FINE) == false) {
	    return;
	}

	long now = System.nanoTime();

	synchronized (lock) {
	    timeToSetMixes += (now - startTime);

	    if (numberOfPrivateMixesSet < 1000) {
		return;
	    }

	    double elapsed = timeToSetMixes / 1000000000.;

	    double avg = elapsed / numberOfPrivateMixesSet;

	    logger.info("elapsed " + elapsed + " avg time to set " 
		+ numberOfPrivateMixesSet + " mixes "
		+ avg + ", number of players " + playersArray.length 
		+ ", avg time to spatialize " 
		+ (timeToSpatialize / 1000000000. / 
		  (numberOfPrivateMixesSet + skipped))
		+ ", out of range " + skipped);

	    numberOfPrivateMixesSet = 0;
	    timeToSetMixes = 0;
	    timeToSpatialize = 0;
	    skipped = 0;
	}
    }

    /*
     * Set the private mix p1 has for p2
     */
    private void setPrivateMix(Player p1, Player p2) {
	double[] privateMixParameters = getPrivateMixParameters(p1, p2);

	if (privateMixParameters == null) {
	    return;
	}

	if (privateMixParameters[3] == 0) {
	    if (p1.isInRange(p2) == false) {
		/*
		 * This is an optimization.  p2 was not in range
		 * and we already knew that.
		 */
		skipped++;
		return;
	    }

	    logger.fine("pmx for " + p1 + ": "
	        + p2 + " no longer in range."); 

	    p1.removePlayerInRange(p2);   // p2 is not in range any more
	} else {
	    if (p1.isInRange(p2) == false) {
	    	logger.fine("pmx for " + p1 + ": "
	            + p2 + " setting in range."); 

		p1.addPlayerInRange(p2);  // p2 is in range now
	    }
	}

	numberOfPrivateMixesSet++;

	if (backingManager == null) {
	    return;
	}

	try {
            backingManager.setPrivateMix(p1.callId, p2.callId, 
	        privateMixParameters);
	} catch (IOException e) {
	    logger.info("Unable to set private mix " + p1
		+ " has for " + p2 + " " + e.getMessage());
	}
    }

    private double[] getPrivateMixParameters(Player p1, Player p2) {
	if (p1.callId == null || p1.callId.length() == 0 ||
	        p2.callId == null || p2.callId.length() == 0) {

	    logger.warning("bad callId:  callId1='" 
		+ p1.callId + "' callId2='" + p2.callId + "'");

	    return null;
	}

	if (inSameGroup(p1.getGroupId(), p2.getGroupId()) == false) {
	     /*
	      * Players are in different groups.  They can't hear each other.
	      */
	     logger.finest("different groups! p1 g " + p1.getGroupId()
		 + " p2 g " + p2.getGroupId());

	     return new double[4];
	}

	double attenuator = 1.0;

	Spatializer spatializer = p1.getPrivateSpatializer(p2.callId);

	if (spatializer == null) {
	    attenuator = p1.getListenAttenuator() * p2.getTalkAttenuator();

	    /*
	     * If p1 has an incoming spatializer, use that.
	     */
	    spatializer = p1.getIncomingSpatializer();

	    if (spatializer == null) {
	        /*
	         * If there is a public spatializer, use that.
	         */
	        spatializer = p2.getPublicSpatializer();

	        if (spatializer == null) {
		    /*
		     * Just use the default spatializer.
		     */
		    if (p2.isLivePerson() == true) {
		        spatializer = livePlayerSpatializer;
		    } else {
	                spatializer = stationarySpatializer;
		    }
		} else {
	            logger.finest(p1 + " using public spatializer for " + p2
		        + " " + spatializer);
		}
	    } else {
	        logger.finest(p1 + " using incoming spatializer for " + p2
		    + " " + spatializer);
	    }
	} else {
	    logger.finest(p1 + " using private spatializer for " + p2
		+ " " + spatializer);
	}

	double[] privateMixParameters = spatializer.spatialize(
	    p2.x, p2.y, p2.z, p2.orientation, p1.x, p1.y, p1.z, p1.orientation);

	if (privateMixParameters[3] > .1) {
          logger.finest("p1=" + p1 + " p2=" + p2 + " mix " 
	    + round(privateMixParameters[0]) + ", " 
	    + round(privateMixParameters[1]) + ", "
	    + round(privateMixParameters[2]) + ", "
	    + round(privateMixParameters[3]));
	}

	/*
	 * If we are setting a private mix for a placeable, the
	 * p1's attenuationVolume is used to attenuate the final volume.
	 */
	if (p2.isLivePerson() == false && p2.isRecording() == false) {
	    privateMixParameters[3] *= p1.attenuationVolume;
	    privateMixParameters[3] *= p2.attenuationVolume;
	}

	double v = privateMixParameters[3];

        privateMixParameters[3] *= (attenuator * p1.getMasterVolume());

	double wallAttenuation = getWallAttenuation(p1, p2);

	logger.finest("volume before wall attenuation: " 
	    + round(privateMixParameters[3]));

	if (wallAttenuation != 1.0) {
	    privateMixParameters[3] *= wallAttenuation;
	}

	logger.finest("volume after wall attenuation: " 
	    + round(privateMixParameters[3]));

	if (privateMixParameters[3] <= ZERO_VOLUME) {
	    privateMixParameters[3] = 0;
	} else {
	    privateMixParameters[3] = round(privateMixParameters[3]);
	}

	logger.finest("pmx for " + p1.callId + ": "
	    + p2.callId + " vol " 
	    + privateMixParameters[3]);

	return privateMixParameters;
    }
 
    private boolean inSameGroup(String p1GroupId, String p2GroupId) {
	if (p1GroupId != null && p2GroupId != null) {
	    return p1GroupId.equals(p2GroupId);
	}

	return p1GroupId == p2GroupId;
    }

    private double getWallAttenuation(Player p1, Player p2) {
	double wallAttenuation = 1.0;

	for (Wall wall : walls) {
	    if (Line2D.linesIntersect(p1.x, p1.y, p2.x, p2.y, 
		wall.startX, wall.startY, wall.endX, wall.endY)) {

		logger.finest(wall + " between " + p1 + " and " + p2);

		return wall.characteristic;
	    }
	}

	return wallAttenuation;
    }

    public void addCallStatusListener(ManagedCallStatusListener mcsl) {
	logger.finest("VoiceManager:  callStatusListener added");

	backingManager.addCallStatusListener(mcsl);
    }

    public void setParameters(VoiceManagerParameters parameters) {
	logger.fine("logLevel set to " + parameters.logLevel);

	logger.fine("live: max v "
	    + parameters.liveMaxVolume
	    + " zero radius "
	    + parameters.liveZeroVolRadius
	    + " full radius "
	    + parameters.liveFullVolRadius
	    + " falloff "
	    + parameters.liveFalloff);

	logger.fine("stationary: max v "
	    + parameters.stationaryMaxVolume
	    + " zero radius "
	    + parameters.stationaryZeroVolRadius
	    + " full radius "
	    + parameters.stationaryFullVolRadius
	    + " falloff "
	    + parameters.stationaryFalloff);

	logger.fine("outworlder: max v "
	    + parameters.outworlderMaxVolume
	    + " zero radius "
	    + parameters.outworlderZeroVolRadius
	    + " full radius "
	    + parameters.outworlderFullVolRadius
	    + " falloff "
	    + parameters.outworlderFalloff);

	livePlayerSpatializer.setMaximumVolume(
	    parameters.liveMaxVolume);
	setPreference("LIVE_PLAYER_MAXIMUM_VOLUME", 
	    parameters.liveMaxVolume);

	livePlayerSpatializer.setZeroVolumeRadius(
	    parameters.liveZeroVolRadius);
	setPreference("LIVE_PLAYER_ZERO_VOLUME_RADIUS", 
	    parameters.liveZeroVolRadius);

	livePlayerSpatializer.setFullVolumeRadius(
	    parameters.liveFullVolRadius);
	setPreference("LIVE_PLAYER_FULL_VOLUME_RADIUS", 
	    parameters.liveFullVolRadius);

	livePlayerSpatializer.setFalloff(parameters.liveFalloff);
	setPreference("LIVE_PLAYER_FALLOFF", parameters.liveFalloff);

	stationarySpatializer.setMaximumVolume(
	    parameters.stationaryMaxVolume);
	setPreference("STATIONARY_MAXIMUM_VOLUME", 
	    parameters.stationaryMaxVolume);

	stationarySpatializer.setZeroVolumeRadius(
	    parameters.stationaryZeroVolRadius);
	setPreference("STATIONARY_ZERO_VOLUME_RADIUS", 
	    parameters.stationaryZeroVolRadius);

	stationarySpatializer.setFullVolumeRadius(
	    parameters.stationaryFullVolRadius);
	setPreference("STATIONARY_FULL_VOLUME_RADIUS", 
	    parameters.stationaryFullVolRadius);

	stationarySpatializer.setFalloff(parameters.stationaryFalloff);
	setPreference("STATIONARY_FALLOFF", parameters.stationaryFalloff);

	outworlderSpatializer.setMaximumVolume(
	    parameters.outworlderMaxVolume);
	setPreference("OUTWORLDER_MAXIMUM_VOLUME", 
	    parameters.outworlderMaxVolume);

	outworlderSpatializer.setZeroVolumeRadius(
	    parameters.outworlderZeroVolRadius);
	setPreference("OUTWORLDER_ZERO_VOLUME_RADIUS", 
	    parameters.outworlderZeroVolRadius);

	outworlderSpatializer.setFullVolumeRadius(
	    parameters.outworlderFullVolRadius);
	setPreference("OUTWORLDER_FULL_VOLUME_RADIUS", 
	    parameters.outworlderFullVolRadius);

	outworlderSpatializer.setFalloff(parameters.outworlderFalloff);
	setPreference("OUTWORLDER_FALLOFF", parameters.outworlderFalloff);

	/*
	 * Reset all private mixes
	 */
	setPrivateMixes();
    }

    public VoiceManagerParameters getParameters() {
	return new VoiceManagerParameters(
	    0, 
	    livePlayerSpatializer.getMaximumVolume(),
	    livePlayerSpatializer.getZeroVolumeRadius(),
	    livePlayerSpatializer.getFullVolumeRadius(),
	    livePlayerSpatializer.getFalloff(),
	    stationarySpatializer.getMaximumVolume(),
	    stationarySpatializer.getZeroVolumeRadius(),
	    stationarySpatializer.getFullVolumeRadius(),
	    stationarySpatializer.getFalloff(),
	    outworlderSpatializer.getMaximumVolume(),
	    outworlderSpatializer.getZeroVolumeRadius(),
	    outworlderSpatializer.getFullVolumeRadius(),
	    outworlderSpatializer.getFalloff());
    }

    public void setLogLevel(Level level) {

	logger.setLevel(level);

	logger.info("level " + level + " set log level to " 
	    + logger.getLevel() + " int " + logger.getLevel().intValue());

	if (backingManager == null) {
	    return;
	}

	backingManager.setLogLevel(level);
    }

    public Level getLogLevel() {
	return logger.getLevel();
    }

    public int getNumberOfPlayersInRange(double x, double y, double z) {
	/*
	 * Create a player at the specified location so we can easily
	 * determine the other players we can hear.
	 */
	Player p1 = new Player("NoCallID", x, y, z, 0);
	p1.setLivePerson();

	int n = 0;

	logger.finest("location " + x + ":" + y + ":" + z);

	synchronized (players) {
	    Collection<Player> values = players.values();

	    Iterator<Player> iterator = values.iterator();

	    while (iterator.hasNext()) {
		Player p2 = iterator.next();

		if (p2.isLivePerson() == false) {
		    continue;  // skip recordings
		}

		double[] privateMixParameters = getPrivateMixParameters(p1, p2);

		if (privateMixParameters == null) {
		    logger.info("No private mix parameters for " + p2);
		    continue;
		}

		logger.finest("volume for " + p2 + " " + privateMixParameters[3]);

	        if (privateMixParameters[3] > 0) {
		    logger.finest(p2 + " is in range");
		    n++;
		}
	    }
	}

	return n;
    }

    public int getNumberOfPlayersInRange(String callId) {
	Player p = findPlayer(callId);

	if (p == null) {
	    logger.info("No player for " + callId);
	    return 0;
	}

	/*
	 * Don't count ourself.
	 */
	return getNumberOfPlayersInRange(p.x, p.y, p.z) - 1;
    }

    public void startRecording(String callId, String recordingFile) 
	    throws IOException {

	Player p = findPlayer(callId);

	if (p == null) {
	    logger.info("No player for " + callId);
	    return;
	}

	if (backingManager == null) {
	    return;
	}

	p.setRecording(true);

	setPrivateMixes();

	backingManager.startRecording(callId, recordingFile);
    }

    public void stopRecording(String callId) 
	    throws IOException {

	Player p = findPlayer(callId);

	if (p == null) {
	    logger.info("No player for " + callId);
	    return;
	}

	if (backingManager == null) {
	    return;
	}

	p.setRecording(false);

	backingManager.stopRecording(callId);
    }

    public void playRecording(String callId, String recordingFile) 
	    throws IOException {

	Player p = findPlayer(callId);

	if (p == null) {
	    logger.info("No player for " + callId);
	    return;
	}

	if (backingManager == null) {
	    return;
	}

	backingManager.playRecording(callId, recordingFile);
    }

    public void stopPlayingRecording(String callId, String recordingFile) 
	throws IOException {
    }

    public double round(double v) {
	return Math.round(v * 100) / (double) 100;
    }

    private void test() throws IOException {
	Player p1 = new Player("1", 5, 5, 0, 0);
	Player p2 = new Player("2", 6, 6, 0, 0);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", -5, 5, 0, 0);
	p2 = new Player("2", -6, 6, 0, 0);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", -5, -5, 0, 0);
	p2 = new Player("2", -6, -6, 0, 0);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 5, -5, 0, 0);
	p2 = new Player("2", 6, -6, 0, 0);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 5, -5, 0, 0);
	p2 = new Player("2", 6, -6, 0, 0);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 5, -5, 0, 0);
	p2 = new Player("2", 6, -6, 0, 0);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 5, -5, 0, 0);
	p2 = new Player("2", 6, -6, 0, 180);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 1, 1, 0, 0);
	p2 = new Player("2", -1, 1, 0, 180);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 1, -1, 0, 0);
	p2 = new Player("2", -1, -1, 0, 180);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 1, 1, 0, 0);
	p2 = new Player("2", 1, -1, 0, 180);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", -1, 1, 0, 0);
	p2 = new Player("2", -1, -1, 0, 180);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", 5, 5, 0, 0);
	p2 = new Player("2", -5, -5, 0, 180);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");

	p1 = new Player("1", -5, 5, 0, 0);
	p2 = new Player("2", 5, -5, 0, 180);
	setPrivateMix(p1, p2);
	setPrivateMix(p2, p1);
	System.out.println("");
    }

    public static void main(String[] args) {
	VoiceManagerImpl vm = new VoiceManagerImpl(null);

	try {
	    vm.test();
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

}
