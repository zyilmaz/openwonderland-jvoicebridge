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
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.mpk20.voicelib.app.AudioGroup;
import com.sun.mpk20.voicelib.app.AudioGroupSetup;
import com.sun.mpk20.voicelib.app.BridgeInfo;
import com.sun.mpk20.voicelib.app.Call;
import com.sun.mpk20.voicelib.app.CallSetup;
import com.sun.mpk20.voicelib.app.DefaultSpatializer;
import com.sun.mpk20.voicelib.app.DefaultSpatializers;
import com.sun.mpk20.voicelib.app.CallBeginEndListener;
import com.sun.mpk20.voicelib.app.ManagedCallStatusListener;
import com.sun.mpk20.voicelib.app.ManagedCallBeginEndListener;
import com.sun.mpk20.voicelib.app.Player;
import com.sun.mpk20.voicelib.app.PlayerSetup;
import com.sun.mpk20.voicelib.app.Recorder;
import com.sun.mpk20.voicelib.app.RecorderSetup;
import com.sun.mpk20.voicelib.app.Treatment;
import com.sun.mpk20.voicelib.app.TreatmentGroup;
import com.sun.mpk20.voicelib.app.TreatmentSetup;
import com.sun.mpk20.voicelib.app.VirtualPlayerListener;
import com.sun.mpk20.voicelib.app.VoiceManager;
import com.sun.mpk20.voicelib.app.VoiceService;
import com.sun.mpk20.voicelib.app.VoiceBridgeParameters;
import com.sun.mpk20.voicelib.app.VoiceManagerParameters;

import com.sun.voip.client.connector.CallStatus;
import com.sun.voip.client.connector.CallStatusListener;

import java.io.IOException;
import java.io.Serializable;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.prefs.Preferences;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VoiceManagerImpl implements VoiceManager {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;

    private static final Logger logger =
        Logger.getLogger(VoiceManagerImpl.class.getName());

    // the voice manager that this manager calls through to
    private VoiceService backingManager;

    private static final String SCALE = 
	"org.jdesktop.lg3d.wonderland.darkstar.server.VoiceManagerImpl.SCALE";

    private static final double LIVE_PLAYER_MAXIMUM_VOLUME = .8;
    private static final double LIVE_PLAYER_ZERO_VOLUME_RADIUS = 22;
    private static final double LIVE_PLAYER_FULL_VOLUME_RADIUS = 8;
    private static final double LIVE_PLAYER_FALLOFF = .95;

    private static final double STATIONARY_MAXIMUM_VOLUME = .25;
    private static final double STATIONARY_ZERO_VOLUME_RADIUS = 16;
    private static final double STATIONARY_FULL_VOLUME_RADIUS = 6;
    private static final double STATIONARY_FALLOFF = .94;

    private static final double OUTWORLDER_MAXIMUM_VOLUME = .4;
    private static final double OUTWORLDER_ZERO_VOLUME_RADIUS = 26;
    private static final double OUTWORLDER_FULL_VOLUME_RADIUS = 10;
    private static final double OUTWORLDER_FALLOFF = .94;

    private double scale = 1.;

    private String conferenceId;

    private VoiceManagerParameters voiceManagerParameters;

    private ConcurrentHashMap<String, AudioGroup> audioGroups = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Call> calls = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Recorder> recorders = new ConcurrentHashMap();
    private ConcurrentHashMap<String, TreatmentGroup> treatmentGroups = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Treatment> treatments = new ConcurrentHashMap();

    public VoiceManagerImpl(VoiceService backingManager) {
	this.backingManager = backingManager;

	String s = System.getProperty(SCALE);

	if (s != null) {
	    try {
		scale = Double.parseDouble(s);
	    } catch (NumberFormatException e) {
		logger.info("Invalid scale factor:  " + s);
	    }
	}

	try {
	    conferenceId = System.getProperty(
	        "com.sun.sgs.impl.app.voice.DEFAULT_CONFERENCE");

	    if (conferenceId == null || conferenceId.length() == 0) {
		conferenceId = VoiceManager.DEFAULT_CONFERENCE;
	    }

	    backingManager.monitorConference(conferenceId);
	} catch (IOException e) {
	    logger.severe("Unable to communicate with voice bridge:  " 
		+ e.getMessage());
	    return;
	} 

	DefaultSpatializer livePlayerSpatializer = new DefaultSpatializer();
	livePlayerSpatializer.setMaximumVolume(
	    getPreference("LIVE_PLAYER_MAXIMUM_VOLUME", 
	    LIVE_PLAYER_MAXIMUM_VOLUME));

	livePlayerSpatializer.setZeroVolumeRadius(
	    getPreference("LIVE_PLAYER_ZERO_VOLUME_RADIUS", 
	    LIVE_PLAYER_ZERO_VOLUME_RADIUS / scale));

	livePlayerSpatializer.setFullVolumeRadius(
	    getPreference("LIVE_PLAYER_FULL_VOLUME_RADIUS", 
	    LIVE_PLAYER_FULL_VOLUME_RADIUS / scale));

	livePlayerSpatializer.setFalloff(
	    getPreference("LIVE_PLAYER_FALLOFF", LIVE_PLAYER_FALLOFF));

	DefaultSpatializer stationarySpatializer = new DefaultSpatializer();
	stationarySpatializer.setMaximumVolume(
	    getPreference("STATIONARY_MAXIMUM_VOLUME", STATIONARY_MAXIMUM_VOLUME));
	stationarySpatializer.setZeroVolumeRadius(
	    getPreference("STATIONARY_ZERO_VOLUME_RADIUS", 
	    STATIONARY_ZERO_VOLUME_RADIUS / scale));
	stationarySpatializer.setFullVolumeRadius(
	    getPreference("STATIONARY_FULL_VOLUME_RADIUS", 
	    STATIONARY_FULL_VOLUME_RADIUS / scale));
	stationarySpatializer.setFalloff(
	    getPreference("STATIONARY_FALLOFF", STATIONARY_FALLOFF));

	voiceManagerParameters = new VoiceManagerParameters(getLogLevel().intValue(), 
	    livePlayerSpatializer, stationarySpatializer);

        AudioGroupSetup setup = new AudioGroupSetup();

        setup.spatializer = getVoiceManagerParameters().livePlayerSpatializer;

        createAudioGroup(DEFAULT_LIVE_PLAYER_AUDIO_GROUP_ID, setup);

	setup = new AudioGroupSetup();
        setup.spatializer = getVoiceManagerParameters().stationarySpatializer;

        createAudioGroup(DEFAULT_STATIONARY_PLAYER_AUDIO_GROUP_ID, setup);

        logger.warning("Created default audio groups...");
    }

    public double getScale() {
	return scale;
    }

    public String getConferenceId() {
	return conferenceId;
    }

    public VoiceService getBackingManager() {
	return backingManager;
    }

    /*
     * Call Setup
     */

    /**
     * Get the next available voice bridge.
     */
    public BridgeInfo getVoiceBridge() throws IOException {
	return backingManager.getVoiceBridge();
    }

    /**
     * Initiate a call
     */
    public Call createCall(String id, CallSetup setup) throws IOException {
	Call call = new CallImpl(id, setup);

	logger.warning("Created call " + call);
	return call;
    }

    public Call getCall(String id) {
	return calls.get(id);
    }

    public void endCall(Call call, boolean removePlayer) throws IOException {
	dump("all");

 	call.end(removePlayer);	

	if (calls.remove(call.getId()) == null) {
	    logger.warning("Call " + call + " not in list of calls");
	} 

	dump("all");
    }

    public ConcurrentHashMap<String, Call> getCalls() {
	return calls;    
    }

    /*
     * Group management
     */
    public AudioGroup createAudioGroup(String id, AudioGroupSetup setup) {
	AudioGroup audioGroup = audioGroups.get(id);

	if (audioGroup != null) {
	    return audioGroup;
	}

	audioGroup = new AudioGroupImpl(id, setup);
	audioGroups.put(id, audioGroup);
	return audioGroup;
    }

    public AudioGroup getAudioGroup(String id) {
	return audioGroups.get(id);
    }

    public AudioGroup getDefaultLivePlayerAudioGroup() {
        return getAudioGroup(DEFAULT_LIVE_PLAYER_AUDIO_GROUP_ID);
    }

    public AudioGroup getDefaultStationaryPlayerAudioGroup() {
	return getAudioGroup(DEFAULT_STATIONARY_PLAYER_AUDIO_GROUP_ID);
    }

    public ConcurrentHashMap<String, AudioGroup> getAudioGroups() {
	return audioGroups;
    }

    public void removeAudioGroup(String id) {
	AudioGroup audioGroup = audioGroups.get(id);

	if (audioGroup == null) {
	    logger.warning("Nonexistent audio group:  " + id);
	    return;
	}

	audioGroup.removePlayers();
	audioGroups.remove(id);
    }

    /*
     * Treatments
     */
    public TreatmentGroup createTreatmentGroup(String id) {
	TreatmentGroup group = treatmentGroups.get(id);

	if (group != null) {
	    return group;
	}

	group = new TreatmentGroupImpl(id);

	treatmentGroups.put(id, group);

	return group;
    }

    public void removeTreatmentGroup(TreatmentGroup group) {
	treatmentGroups.remove(group);
    }
	
    public TreatmentGroup getTreatmentGroup(String id) {
	return treatmentGroups.get(id);
    }

    public Treatment createTreatment(String id, TreatmentSetup setup) throws IOException {
	return new TreatmentImpl(id, setup);
    }

    public Treatment getTreatment(String id) {
	return treatments.get(id);
    }

    public ConcurrentHashMap<String, Treatment> getTreatments() {
	return treatments;
    }

    /*
     * Recording setup and control
     */
    public Recorder createRecorder(String id, RecorderSetup setup) throws IOException {
  	return new RecorderImpl(id, setup);
    }

    public Recorder getRecorder(String id) {
	return recorders.get(id);
    }

    public ConcurrentHashMap<String, Recorder> getRecorders() {
	return recorders;
    }

    /*
     * VoiceManager
     */
    public Player createPlayer(String id, PlayerSetup setup) {
	Player p = players.get(id);

	if (p == null) {
	    p = new PlayerImpl(id, setup);

            players.put(id, p);

            logger.info("Created player for " + p + " number of players "
                + players.size());
	} else {
	    p.moved(setup.x, setup.y, setup.z, setup.orientation);
	}

	return p;
    }

    public Player getPlayer(String id) {
	return players.get(id);
    }

    public void removePlayer(String id) {
	Player player = players.remove(id);

	if (player != null) {
	    removeCallStatusListener(player);
	    player.removePlayer();
	    logger.warning("Removed Player " + id);
	} else {
	    logger.warning("Player " + id + " not found");
	}
    }

    private ArrayList<VirtualPlayerListener> virtualPlayerListeners = new ArrayList();

    public void addVirtualPlayerListener(VirtualPlayerListener listener) {
	virtualPlayerListeners.add(listener);
    }

    public void removeVirtualPlayerListener(VirtualPlayerListener listener) {
	virtualPlayerListeners.remove(listener);
    }

    private void notifyPlayerChangeListeners(Player player, boolean created) {
	for (VirtualPlayerListener listener : virtualPlayerListeners) {
	    if (created) {
		listener.virtualPlayerCreated(player);
	    } else {
		listener.virtualPlayerRemoved(player);
	    }
	}
    }

    public ConcurrentHashMap<String, Player> getPlayers() {
	return players;
    }

    public void addWall(double startX, double startY, double endX, double endY,
            double characteristic) {
    }

    public void setDefaultSpatializers(DefaultSpatializers defaultSpatializers) {
    }

    public DefaultSpatializers getDefaultSpatializers() {
	return null;
    }

    public void setDefaultSpatializers(DefaultSpatializers defaultSpatializers,
        double startX, double startY, double endX, double endY) {
    }

    public DefaultSpatializers getDefaultSpatializers(
        double startX, double startY, double endX, double endY) {

	return null;
    }

    public int getNumberOfPlayersInRange(double x, double y, double z) {
	/*
	 * Create a player at the specified location so we can easily
	 * determine the other players we can hear.
	 */
	PlayerSetup setup = new PlayerSetup();
        setup.x = x / scale;
        setup.y = y / scale;
        setup.z = z / scale;
        setup.isLivePlayer = true;

	Player p1 = new PlayerImpl("NoCallID", setup);

	int n = 0;

	logger.finest("location " + x + ":" + y + ":" + z);

	//synchronized (players) {
	    Collection<Player> values = players.values();

	    Iterator<Player> iterator = values.iterator();

	    while (iterator.hasNext()) {
		Player p2 = iterator.next();

		if (p2.getSetup().isLivePlayer == false) {
		    continue;  // skip recordings
		}

		double volume = p1.spatialize(p2);

		if (volume == 0) {
		    continue;
		}

		logger.finest("volume for " + p2 + " " + volume);

	        if (volume > 0) {
		    logger.finest(p2 + " is in range");
		    n++;
		}
	    }
	//}

	return n;
    }

    /*
     * Voice bridge parameters.
     */
    public void setVoiceBridgeParameters(VoiceBridgeParameters parameters) {
    }
	
    public void setVoiceManagerParameters(VoiceManagerParameters parameters) {
	//setLogLevel(parameters.logLevel);
	
	logger.fine("logLevel set to " + parameters.logLevel);

	voiceManagerParameters = parameters;
    }

    public VoiceManagerParameters getVoiceManagerParameters() {
	return voiceManagerParameters;
    }

    private CopyOnWriteArrayList<CallStatusListener> allCallListeners = new CopyOnWriteArrayList();

    private ConcurrentHashMap<String, CopyOnWriteArrayList<CallStatusListener>> callStatusListeners =
	new ConcurrentHashMap();

    private CopyOnWriteArrayList<CallBeginEndListener> callBeginEndListeners =
	new CopyOnWriteArrayList();

    public CopyOnWriteArrayList<CallStatusListener> getAllCallListeners() {
	return allCallListeners;
    }

    public ConcurrentHashMap<String, CopyOnWriteArrayList<CallStatusListener>> getCallStatusListeners() {
	return callStatusListeners;
    }

    public CopyOnWriteArrayList<CallBeginEndListener> getCallBeginEndListeners() {
	return callBeginEndListeners;
    }

    private void initializeBindings() {
        DataManager dm = AppContext.getDataManager();

        try {
            dm.getBinding(DS_MANAGED_ALL_CALL_LISTENERS);
        } catch (NameNotBoundException e) {
            try {
                dm.setBinding(DS_MANAGED_ALL_CALL_LISTENERS, 
		    new ManagedCallStatusListeners());
            }  catch (RuntimeException re) {
                logger.warning("failed to bind pending map " + re.getMessage());
                throw re;
            }
        }

        try {
            dm.getBinding(DS_MANAGED_CALL_STATUS_LISTENERS);
        } catch (NameNotBoundException e) {
            try {
                dm.setBinding(DS_MANAGED_CALL_STATUS_LISTENERS, 
		    new ManagedCallStatusListeners());
            }  catch (RuntimeException re) {
                logger.warning("failed to bind pending map " + re.getMessage());
                throw re;
            }
        }

        try {
            dm.getBinding(DS_MANAGED_CALL_BEGIN_END_LISTENERS);
        } catch (NameNotBoundException e) {
            try {
                dm.setBinding(DS_MANAGED_CALL_BEGIN_END_LISTENERS, 
		    new ManagedCallBeginEndListeners());
            }  catch (RuntimeException re) {
                logger.warning("failed to bind pending map " + re.getMessage());
                throw re;
            }
        }
    }

    private boolean listenerAdded = false;

    public void addCallStatusListener(CallStatusListener listener) {
	addCallStatusListener(listener, null);
    }

    public void addCallStatusListener(CallStatusListener listener, String callId) {
	if (listenerAdded == false) {
	    listenerAdded = true;

	    initializeBindings();

	    backingManager.addCallStatusListener(new CallStatusNotifier(callId));
	}

	if (listener instanceof ManagedCallStatusListener) {
	    addManagedCallStatusListener((ManagedCallStatusListener) listener, callId);
	    return;
	}

	if (callId == null) {
	    if (allCallListeners.contains(listener)) {
		logger.warning("listener " + listener + " is already in the list.");
		return;
	    }

	    allCallListeners.add(listener);
	    return;
	}

	CopyOnWriteArrayList<CallStatusListener> listeners = callStatusListeners.get(callId);

	if (listeners == null) {
	    listeners = new CopyOnWriteArrayList<CallStatusListener>();
	    callStatusListeners.put(callId, listeners);
	} else if (listeners.contains(listener)) {
	    logger.warning("listener " + listener + " is already in the list.");
	    return;
	}

	listeners.add(listener);
    }
    
    private void addManagedCallStatusListener(ManagedCallStatusListener listener, String callId) {
	DataManager dm = AppContext.getDataManager();

        ManagedReference<ManagedCallStatusListener> mr = dm.createReference(listener);

	if (callId == null) {
            ManagedAllCallListeners managedListeners = (ManagedAllCallListeners) dm.getBinding(
	        DS_MANAGED_ALL_CALL_LISTENERS);

	    if (managedListeners.contains(mr)) {
		logger.warning("listener " + listener + " is already in the list.");
		return;
	    }

	    managedListeners.add(mr);
	    return;
	}

        ManagedCallStatusListeners managedListeners = (ManagedCallStatusListeners) dm.getBinding(
	        DS_MANAGED_CALL_STATUS_LISTENERS);

	CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>> listeners = managedListeners.get(callId);

	if (listeners == null) {
	    listeners = new CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>>();
	    managedListeners.put(callId, listeners);
	} else if (listeners.contains(listener)) {
	    logger.warning("listener " + listener + " is already in the list.");
	    return;
	}

	listeners.add(mr);
    }

    public void removeCallStatusListener(CallStatusListener listener) {
	removeCallStatusListener(listener, null);
    }

    public void removeCallStatusListener(CallStatusListener listener, String callId) {
	if (listener instanceof ManagedCallStatusListener) {
	    removeManagedCallStatusListener((ManagedCallStatusListener) listener, callId);
	    return;
	}

	if (callId == null) {
            callStatusListeners.remove(listener);
	} else {
	    CopyOnWriteArrayList<CallStatusListener> listeners = callStatusListeners.get(callId);

	    if (listeners == null) {
	        logger.warning("Can't find listener for " + callId);
	        return;
	    }

	    if (listeners.contains(listener)) {
	    	listeners.remove(listener);

		if (listeners.isEmpty()) {
		    callStatusListeners.remove(callId);
		}
	    }
	}
    }
  
    private void removeManagedCallStatusListener(ManagedCallStatusListener listener,
	    String callId) {

	DataManager dm = AppContext.getDataManager();

	if (callId == null) {
            ManagedCallStatusListeners allCallManagedListeners =
                (ManagedCallStatusListeners) dm.getBinding(
		DS_MANAGED_ALL_CALL_LISTENERS);
	
            ManagedReference<ManagedCallStatusListener> mr = dm.createReference(listener);

	    allCallManagedListeners.remove(mr);
	    return;
	}

        ManagedCallStatusListeners managedListeners =
            (ManagedCallStatusListeners) dm.getBinding(
	    DS_MANAGED_CALL_STATUS_LISTENERS);
	
	ManagedCallStatusListener ml = (ManagedCallStatusListener) listener;

        ManagedReference<ManagedCallStatusListener> mr = dm.createReference(ml);

	CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>> listeners = managedListeners.get(callId);

	if (listeners == null) {
	    logger.warning("Can't find listener for " + callId);
	    return;
	}

	if (listeners.contains(mr)) {
	    listeners.remove(mr);

	    if (listeners.isEmpty()) {
		managedListeners.remove(callId);
	    }
	}
    }

    public void addCallBeginEndListener(CallBeginEndListener listener) {
	if (listener instanceof ManagedCallBeginEndListener) {
            DataManager dm = AppContext.getDataManager();

            ManagedCallBeginEndListeners managedListeners =
                (ManagedCallBeginEndListeners) dm.getBinding(
		DS_MANAGED_CALL_BEGIN_END_LISTENERS);

            /*
             * Create a reference to listener and keep that.
             */
	    ManagedCallBeginEndListener ml = (ManagedCallBeginEndListener) listener;

            ManagedReference<ManagedCallBeginEndListener> mr = dm.createReference(ml);

	    if (managedListeners.contains(mr) == false) {
                managedListeners.add(mr);
	    }
	    return;
	}

	if (callBeginEndListeners.contains(listener) == false) {
            callBeginEndListeners.add(listener);
	}
    }

    public void removeCallBeginEndListener(CallBeginEndListener listener) {
	if (listener instanceof ManagedCallBeginEndListener) {
            DataManager dm = AppContext.getDataManager();

            ManagedCallBeginEndListeners managedListeners =
                (ManagedCallBeginEndListeners) dm.getBinding(
		DS_MANAGED_CALL_BEGIN_END_LISTENERS);

	    ManagedCallBeginEndListener ml = (ManagedCallBeginEndListener) listener;

            ManagedReference<ManagedCallBeginEndListener> mr = dm.createReference(ml);

            managedListeners.remove(mr);
	    return;
	}

        callBeginEndListeners.remove(listener);
    }

    static class CallStatusNotifier implements ManagedCallStatusListener {

	String callId;

	public CallStatusNotifier(String callId) {
	    this.callId = callId;
	}

        public void callStatusChanged(CallStatus callStatus) {
            int code = callStatus.getCode();

            String callId = callStatus.getCallId();

	    logger.fine("Got status " + callStatus);

	    if (code == CallStatus.ESTABLISHED ||
		    code == CallStatus.MIGRATED ||
		    code == CallStatus.ENDED) {

	        notifyCallBeginEndListeners(callStatus);
	        notifyManagedCallBeginEndListeners(callStatus);
	    } else if (code == CallStatus.INFO) {
	        VoiceManagerImpl vm = AppContext.getManager(VoiceManagerImpl.class);
	        vm.dump(callStatus.getOption("Info"));
		return;
	    }
	
	    if (callId != null && this.callId != null && callId.equals(this.callId) == false) {
		return;
	    }

	    notifyCallStatusListeners(callStatus);
	    notifyManagedCallStatusListeners(callStatus);
        }

        private void notifyCallStatusListeners(CallStatus status) {
	    VoiceManagerImpl vm = AppContext.getManager(VoiceManagerImpl.class);

	    CopyOnWriteArrayList<CallStatusListener> listeners = vm.getAllCallListeners();

	    for (CallStatusListener listener : listeners) {
                listener.callStatusChanged(status);
	    }
	
	    String callId = status.getCallId();

	    if (callId == null) {
	 	return;
	    }

	    listeners = vm.getCallStatusListeners().get(callId);

	    if (listeners == null) {
		return;
	    }

	    for (CallStatusListener listener : listeners) {
                listener.callStatusChanged(status);
            }
        }

        private void notifyManagedCallStatusListeners(CallStatus status) {
	}
 
        private void notifyCallBeginEndListeners(CallStatus status) {
	    VoiceManagerImpl vm = AppContext.getManager(VoiceManagerImpl.class);

            CopyOnWriteArrayList<CallBeginEndListener> listenerList = 
	        vm.getCallBeginEndListeners();

	    for (CallBeginEndListener listener : listenerList) {
                listener.callBeginEndNotification(status);
            }
        }

        private void notifyManagedCallBeginEndListeners(CallStatus status) {
            DataManager dm = AppContext.getDataManager();

            ManagedCallBeginEndListeners managedCallBeginEndListeners =
                (ManagedCallBeginEndListeners) dm.getBinding(
		DS_MANAGED_CALL_BEGIN_END_LISTENERS);

            CopyOnWriteArrayList<ManagedReference<ManagedCallBeginEndListener>> listenerList = 
    	        managedCallBeginEndListeners;

	    for (ManagedReference<ManagedCallBeginEndListener> listener : listenerList) {
                listener.get().callBeginEndNotification(status);
            }
        }
    }

    public static final class CallStatusListeners extends ConcurrentHashMap
	    <CallStatusListener, CopyOnWriteArrayList> {

         private static final long serialVersionUID = 1;
    }

    private static final class CallBeginEndListeners extends CopyOnWriteArrayList
	    <CallBeginEndListener> {

         private static final long serialVersionUID = 1;
    }

    public static final class ManagedAllCallListeners extends CopyOnWriteArrayList
	    <ManagedReference<ManagedCallStatusListener>> implements ManagedObject {

         private static final long serialVersionUID = 1;
    }

    public static final class ManagedCallStatusListeners extends ConcurrentHashMap
	    <String, CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>>> implements ManagedObject {

         private static final long serialVersionUID = 1;
    }

    private static final class ManagedCallBeginEndListeners extends CopyOnWriteArrayList
	    <ManagedReference<ManagedCallBeginEndListener>> implements ManagedObject {

         private static final long serialVersionUID = 1;
    }

    public Level getLogLevel() {
	Level logLevel;

	Logger l = logger;

	while ((logLevel = l.getLevel()) == null) {
	    l = l.getParent();
	}

	return logLevel;
    }

    public void setLogLevel(Level level) {
	logger.setLevel(level);

        logger.info("level " + level + " set log level to "
            + logger.getLevel() + " int " + logger.getLevel().intValue());

        if (backingManager != null) {
	    backingManager.setLogLevel(level);
	}
    }

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

    private static void setPreference(String preference, double value) {
        Preferences prefs = Preferences.userNodeForPackage(VoiceManagerImpl.class);
	prefs.put(VOICEMANAGER_PREFIX + preference, String.valueOf(value));
    }

    public void dump(String command) {
	String[] tokens = command.split("[+]");

	for (int i = 0; i < tokens.length; i++) {
	    if (tokens[i].equalsIgnoreCase("all")) {
		dumpAudioGroups();
		dumpCalls();
		dumpPlayers();
	    } else if (tokens[i].equalsIgnoreCase("audioGroups")) {
		dumpAudioGroups();
	    } else if (tokens[i].equalsIgnoreCase("calls")) {
		dumpCalls();
	    } else if (tokens[i].equalsIgnoreCase("players")) {
		dumpPlayers();
	    } else {
		System.out.println("Unrecognized object to dump:  " 
		   + tokens[i]);
	    }
	}

	System.out.println("");
    }

    private void dumpAudioGroups() {
	System.out.println("");
	System.out.println("Audio Groups");
	System.out.println("------------");

	if (audioGroups.size() > 0) {
            Enumeration<AudioGroup> ae = audioGroups.elements();

	    while (ae.hasMoreElements()) {
		System.out.println(ae.nextElement().dump());
	    }
	} else {
	    System.out.println("There are no audio groups!");
	}
    }
	
    private void dumpCalls() {
	System.out.println("");
	System.out.println("Calls");
	System.out.println("-----");

	if (calls.size() > 0) {
	    Enumeration<Call> ce = calls.elements();

	    while (ce.hasMoreElements()) {
		System.out.println(ce.nextElement().dump());
	    }
	} else {
	    System.out.println("There are no calls!");
	}
    }

    private void dumpPlayers() {
	System.out.println("");
	System.out.println("Players");
	System.out.println("-------");

	if (players.size() > 0) {
	    Enumeration<Player> pe = players.elements();

	    while (pe.hasMoreElements()) {
		System.out.println(pe.nextElement().dump());
	    }
	} else {
	    System.out.println("There are no players!");
	}
    }

}
