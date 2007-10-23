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

    private DefaultSpatializer defaultSpatializer;

    private DefaultSpatializer livePlayerSpatializer;

    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<String, Player>();

    private static final double ZERO_VOLUME = .009;

    private static final double LIVE_PLAYER_FALLOFF = .94;

    /**
     * @param backingManager the <code>VoiceManager</code> to call through to
     */
    public VoiceManagerImpl(VoiceManager backingManager) {
        this.backingManager = backingManager;

	defaultSpatializer = new DefaultSpatializer();

	livePlayerSpatializer = new DefaultSpatializer();

	livePlayerSpatializer.setFallOff(LIVE_PLAYER_FALLOFF);

	//String s = System.getProperty("voicelib.tunerEnabled");
	//
	//if (s != null && s.equalsIgnoreCase("true")) {
	//    try {
	//        new VoiceManagerDialog(this);
	//    } catch (Throwable t) {
	//	logger.info("Unable to start VoiceManagerDialog: "
	//	    + t.getMessage());
	//    }
	//}
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

	p.setSpatializer(spatializer);

	if (cp.getInputTreatment() == null) {
	    /*
	     * If there is an input treatment, this is not a live player.
	     * We only set private mixes for live players.
	     */
	    p.setLivePerson();
	}

	if (p.spatializer == null) {
	    if (cp.getInputTreatment() == null) {
	        p.spatializer = livePlayerSpatializer;
	    } else {
	        p.spatializer = defaultSpatializer;
	    }
	}

	players.put(callId, p);
    }

    public Player getPlayer(String callId) {
	return findPlayer(callId);
    }

    public void setSpatializer(String callId, Spatializer spatializer) {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.info("setSpatializer:  no Player for " + callId);
            return;
	}

	player.setSpatializer(spatializer);

	/*
	 * Update everything
	 */
	try {
	    setPrivateMixes();
	} catch (IOException e) {
	    logger.info("Unable to update private mixes");
	}
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

		    if (player.isLivePerson == false ||
			    player.callId.equals(sourceCallId)) {

			continue;
		    }

		    setPrivateSpatializer(player.callId, sourceCallId, 
			spatializer);
		}
	    }
	}

        Player targetPlayer = findPlayer(targetCallId);

        if (targetPlayer == null) {
            logger.info("setPrivateSpatializer:  no targetPlayer for " 
		+ targetCallId);
            return;
        }

        Player sourcePlayer = findPlayer(sourceCallId);

        if (sourcePlayer == null) {
            logger.info("setPrivateSpatializer:  no sourcePlayer for " 
		+ sourceCallId);
            return;
        }

        targetPlayer.setPrivateSpatializer(sourceCallId, spatializer);

	/*
	 * Update everything
	 */
	try {
	    setPrivateMixes();
	} catch (IOException e) {
	    logger.info("Unable to update private mixes");
	}
    }

    public void callEstablished(String callId) throws IOException {
	logger.finer("call established: " + callId);

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

	setPrivateMixes(findPlayer(callId));
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

    public void endCall(String callId) throws IOException {
	endCall(callId, true);
    }

    public void endCall(String callId, boolean tellBackingManager) 
	    throws IOException {

	logger.info("call ending:  " + callId);

	players.remove(callId);

	if (tellBackingManager) {
	    backingManager.endCall(callId);
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
            logger.info("no Player for " + callId);
            return;
	}

	boolean setPrivateMixes = false;

	if (player.samePosition(x, y, z)) {
	    //logger.info("same position:  " + player);
	} else {
	    player.setPosition(x, y, z);
	    setPrivateMixes = true;
	}

	if (player.sameOrientation(orientation)) {
	    //logger.info("same orientation:  " + player);
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
            logger.info("no Player for " + callId);
            return;
	}

	if (player.samePosition(x, y, z)) {
	    //logger.info("same position:  " + player);
	    return;
	}

	player.setPosition(x, y, z);

	setPrivateMixes(player);
    }

    public void setOrientation(String callId, double orientation) throws IOException {
	Player player = findPlayer(callId);

        if (player == null) {
            logger.info("no Player for " + callId);
            return;
	}

	if (player.sameOrientation(orientation)) {
	    //logger.info("same orientation:  " + player);
	    return;
	}

	player.setOrientation(orientation);

	setPrivateMixes(player);
    }

    public void setAttenuationRadius(String callId, double attenuationRadius) 
	    throws IOException {

	Player player = findPlayer(callId);

        if (player == null) {
            logger.info("no Player for " + callId);
            return;
	}

	player.setAttenuationRadius(attenuationRadius);
        setPrivateMixes();
    }
	
    public void setAttenuationVolume(String callId, double attenuationVolume) 
            throws IOException {

	Player player = findPlayer(callId);

        if (player == null) {
            logger.info("no Player for " + callId);
            return;
	}

	player.setAttenuationVolume(attenuationVolume);
        setPrivateMixes();
    }
	
    public void setPrivateMix(String sourceCallId, String targetCallId,
            double[] privateMixParameters) throws IOException {

	if (backingManager != null) {

	    if (privateMixParameters[3] <= ZERO_VOLUME) {
		privateMixParameters[3] = 0;
	    }

	    backingManager.setPrivateMix(sourceCallId, targetCallId,
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

    public void setSpatialFallOff(double spatialFallOff) throws IOException {
	if (backingManager != null) {
	    backingManager.setSpatialFallOff(spatialFallOff);
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

    public DefaultSpatializer getDefaultSpatializer() {
	return defaultSpatializer;
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

    private int n;

    private int skipped;

    private long startTime;

    private long timeToSpatialize;

    private void setPrivateMixes() throws IOException {
	setPrivateMixes(null);
    }

    private void setPrivateMixes(Player changedPlayer) throws IOException {
	//Adjust private mixes for all calls
	//For each call, we have to determine where it is
	// in 3-space relative to each other call
	// and set the private mix accordingly.

	synchronized (this) {
	    if (startTime == 0) {
	        startTime = System.nanoTime();
	    }
	}

	Player[] playersArray = players.values().toArray(new Player[0]);

	long start = System.nanoTime();

	if (changedPlayer == null) {
	    for (int i = 0; i < playersArray.length; i++) {
	        Player p1 = playersArray[i];

	        for (int j = 0; j < playersArray.length; j++) {
		    Player p2 = playersArray[j];

		    if (p1 == p2 ) {
		        skipped++;
		        continue;
		    }
   
		    /*
         	     * Set the private mix p1 has for p2
	             */
        	    if (p1.isLivePerson == false) {
		        /*
             	         * We only set private mixes for live players
             	         * and not for audio sources.
             	         */
		        skipped++;
	  	        continue;
        	    }

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
         	 * Set the private mix p1 has for p2
	         */
        	if (p1.isLivePerson == false) {
		    /*
             	     * We only set private mixes for live players
             	     * and not for audio sources.
             	     */
		    skipped++;
        	} else {
		    setPrivateMix(p1, changedPlayer);
		}

		if (changedPlayer.isLivePerson == false) {
		    skipped++;
		} else {
		    setPrivateMix(changedPlayer, p1);
		}
	    }
	}

	long now = System.nanoTime();

	synchronized (this) {
	    timeToSetMixes += (now - start);

	    if (n++ != 100) {
		return;
	    }

	    if (false && numberOfPrivateMixesSet > 0) {
	        long elapsed = (now - startTime) * 1000 / 1000000000;

	        timeToSetMixes = timeToSetMixes * 1000 / 1000000000;

	        double avg = (double) timeToSetMixes / numberOfPrivateMixesSet;
		    
	        logger.info("elapsed ms " + elapsed + ", time to set "
		    + numberOfPrivateMixesSet + " mixes " 
		    + timeToSetMixes + ", avg time to set a pm " + avg
		    + ", number of players " + playersArray.length 
		    + ", avg time to spatialize " 
		    + (((double) timeToSpatialize) / 
		      (numberOfPrivateMixesSet + skipped))
		    + ", out of range " + skipped);
	    }

	    startTime = 0;
	    numberOfPrivateMixesSet = 0;
	    timeToSetMixes = 0;
	    timeToSpatialize = 0;
	    skipped = 0;
	    n = 0;
	}
    }

    private static int count = -1;

    private void setPrivateMix(Player p1, Player p2) throws IOException {
	if (p1.callId == null || p1.callId.length() == 0 ||
	        p2.callId == null || p2.callId.length() == 0) {

	    logger.warning("setPrivateMix bad callId:  callId1='" 
		+ p1.callId + "' callId2='" + p2.callId + "'");
	    return;
	}

	Spatializer spatializer = p1.getPrivateSpatializer(p2.callId);

	if (spatializer == null) {
	    spatializer = p2.getSpatializer();
	}

	if (spatializer == null) {
	    spatializer = defaultSpatializer;
	}

	long start = System.currentTimeMillis();

	double[] privateMixParameters = spatializer.spatialize(
	    p2.x, p2.y, p2.z, p2.orientation, p1.x, p1.y, p1.z, p1.orientation);

	timeToSpatialize += (System.currentTimeMillis() - start);

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
	if (p2.isLivePerson == false) {
	    privateMixParameters[3] *= p1.attenuationVolume;
	    privateMixParameters[3] *= p2.attenuationVolume;
	    logger.finest("p1.AttenuationVolume " + p1.attenuationVolume 
		+  " volume " + round(privateMixParameters[3]));
	}

	double wallAttenuation = getWallAttenuation(p1, p2);

	logger.finest("volume before wall attenuation: " 
	    + round(privateMixParameters[3]));

	if (wallAttenuation != 1.0) {
	    privateMixParameters[3] *= wallAttenuation;
	}

	logger.finest("volume after wall attenuation: " 
	    + round(privateMixParameters[3]));

	count++;

	if (backingManager != null) {
	    if (privateMixParameters[3] <= ZERO_VOLUME) {
		privateMixParameters[3] = 0;
	    }

	    logger.fine("pmx for " + p1.callId + ": "
	        + p2.callId + " vol " 
		+ privateMixParameters[3]);
 
	    if (privateMixParameters[3] == 0) {
		if (p1.isInRange(p2) == false) {
		    if ((count % 1000) == 0 || logger.isLoggable(Level.FINE)) {
	    	        logger.info("pmx for " + p1 + ": " + p2 
			    + " already out of range."); 
		    }

		    /*
		     * This is an optimization.  p2 was not in range
		     * and we already knew that.
		     */
		    skipped++;
		    return;
	        }

	    	logger.info("pmx for " + p1 + ": "
	            + p2 + " no longer in range."); 

		p1.removePlayerInRange(p2);   // p2 is not in range any more
	    } else {
		if ((count % 1000) == 0 || logger.isLoggable(Level.FINE)) {
	    	    logger.info("pmx for " + p1 + ": "
	                + p2 + " is in range."); 
		}

		if (p1.isInRange(p2) == false) {
	    	    logger.info("pmx for " + p1 + ": "
	                + p2 + " setting in range."); 

		    p1.addPlayerInRange(p2);  // p2 is in range now
		}
	    }

	    numberOfPrivateMixesSet++;

            backingManager.setPrivateMix(p1.callId, p2.callId, 
		privateMixParameters);
	}
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
	logger.info("logLevel set to " + parameters.logLevel
	    + " liveFalloff set to " + parameters.liveFalloff
	    + " defaultFalloff set to " + parameters.defaultFalloff
	    + " liveFullVolRadius set to " 
	    + parameters.liveFullVolRadius 
	    + " liveZeroVolRadius set to "
	    + parameters.liveZeroVolRadius
	    + " defaultFullVolRadius set to " 
	    + parameters.defaultFullVolRadius 
	    + " defaultZeroVolRadius set to "
	    + parameters.defaultZeroVolRadius);

	livePlayerSpatializer.setFallOff(parameters.liveFalloff);
	defaultSpatializer.setFallOff(parameters.defaultFalloff);
	livePlayerSpatializer.setFullVolumeRadius(
	    parameters.liveFullVolRadius);
	livePlayerSpatializer.setZeroVolumeRadius(
	    parameters.liveZeroVolRadius);
	defaultSpatializer.setFullVolumeRadius(
	    parameters.defaultFullVolRadius);
	defaultSpatializer.setZeroVolumeRadius(
	    parameters.defaultZeroVolRadius);

	/*
	 * Reset all private mixes
	 */
	try {
	    setPrivateMixes();
	} catch (IOException e) {
	    logger.info("Unable to setPrivateMixes!  " + e.getMessage());
	}
    }

    public VoiceManagerParameters getParameters() {
	VoiceManagerParameters parameters = new VoiceManagerParameters(
	    0,
	    livePlayerSpatializer.getFallOff(),
	    defaultSpatializer.getFallOff(),
	    livePlayerSpatializer.getFullVolumeRadius(),
	    livePlayerSpatializer.getZeroVolumeRadius(),
	    defaultSpatializer.getFullVolumeRadius(),
	    defaultSpatializer.getZeroVolumeRadius());

	return parameters;
    }

    public void setLogLevel(Level level) {
	logger.setLevel(level);
	logger.info("set log level to " + logger.getLevel()
	    + " int " + logger.getLevel().intValue());

	if (backingManager != null) {
	    backingManager.setLogLevel(level);
	}
    }

    public Level getLogLevel() {
	return logger.getLevel();
    }

    public double round(double v) {
	return Math.round(v * 1000) / (double) 1000;
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
