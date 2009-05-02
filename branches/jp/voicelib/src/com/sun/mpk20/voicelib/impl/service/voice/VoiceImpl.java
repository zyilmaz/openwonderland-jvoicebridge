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

import com.sun.mpk20.voicelib.impl.service.voice.work.Work;
import com.sun.mpk20.voicelib.impl.service.voice.work.listener.*;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.service.NonDurableTransactionParticipant;

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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.prefs.Preferences;

public class VoiceImpl implements Serializable {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;

    private static final Logger logger =
        Logger.getLogger(VoiceImpl.class.getName());

    private static final String NAME = VoiceImpl.class.getName();
    private static final String DS_PREFIX = NAME + ".";

    /* The name of the list for call status listeners */
    private static final String DS_MANAGED_ALL_CALL_LISTENERS =
        DS_PREFIX + "AllCallListeners";

    /* The name of the list for call status listeners */
    private static final String DS_MANAGED_CALL_STATUS_LISTENERS =
        DS_PREFIX + "CallStatusListeners";

    /* The name of the list for call begin/end listeners */
    private static final String DS_MANAGED_CALL_BEGIN_END_LISTENERS =
        DS_PREFIX + "CallBeginEndListeners";

    private static final String VOICEIMPL_PREFIX = NAME + ".";

    private final String SCALE_PROPERTY =
        VOICEIMPL_PREFIX + ".VoiceManagerImpl.SCALE";

    private double DEFAULT_SCALE = 1.0;

    private final String CONFERENCE_PROPERTY = 
	VOICEIMPL_PREFIX + ".DEFAULT_CONFERENCE";

    private final String DEFAULT_CONFERENCE="Test:PCM/16000/2";

    private final String LIVE_PLAYER_MAXIMUM_VOLUME_PREFERENCE =
	VOICEIMPL_PREFIX + ".LIVE_PLAYER_MAXIMUM_VOLUME";

    private final double DEFAULT_LIVE_PLAYER_MAXIMUM_VOLUME = .8;

    private final String LIVE_PLAYER_ZERO_VOLUME_RADIUS_PREFERENCE =
	VOICEIMPL_PREFIX + ".LIVE_PLAYER_ZERO_VOLUME_RADIUS";

    private final double DEFAULT_LIVE_PLAYER_ZERO_VOLUME_RADIUS = 22;

    private final String LIVE_PLAYER_FULL_VOLUME_RADIUS_PREFERENCE =
	VOICEIMPL_PREFIX + ".LIVE_PLAYER_FULL_VOLUME_RADIUS";

    private final double DEFAULT_LIVE_PLAYER_FULL_VOLUME_RADIUS = 8;

    private final String LIVE_PLAYER_FALLOFF_PREFERENCE =
	VOICEIMPL_PREFIX + ".LIVE_PLAYER_FALLOFF";

    private final double DEFAULT_LIVE_PLAYER_FALLOFF = .95;

    private final String STATIONARY_PLAYER_MAXIMUM_VOLUME_PREFERENCE =
	VOICEIMPL_PREFIX + ".STATIONARY_PLAYER_MAXIMUM_VOLUME";

    private final double DEFAULT_STATIONARY_PLAYER_MAXIMUM_VOLUME = .6;

    private final String STATIONARY_PLAYER_ZERO_VOLUME_RADIUS_PREFERENCE =
	VOICEIMPL_PREFIX + ".STATIONARY_PLAYER_ZERO_VOLUME_RADIUS";

    private final double DEFAULT_STATIONARY_PLAYER_ZERO_VOLUME_RADIUS = 16;

    private final String STATIONARY_PLAYER_FULL_VOLUME_RADIUS_PREFERENCE =
	VOICEIMPL_PREFIX + ".STATIONARY_PLAYER_FULL_VOLUME_RADIUS";

    private final double DEFAULT_STATIONARY_PLAYER_FULL_VOLUME_RADIUS = 6;

    private final String STATIONARY_PLAYER_FALLOFF_PREFERENCE =
	VOICEIMPL_PREFIX + ".STATIONARY_PLAYER_FALLOFF";

    private final double DEFAULT_STATIONARY_PLAYER_FALLOFF = .94;

    private final String OUTWORLDER_MAXIMUM_VOLUME_PREFERENCE =
	VOICEIMPL_PREFIX + ".OUTWORLDER_MAXIMUM_VOLUME";

    private final double DEFAULT_OUTWORLDER_MAXIMUM_VOLUME = .4;

    private final String OUTWORLDER_ZERO_VOLUME_RADIUS_PREFERENCE =
	VOICEIMPL_PREFIX + ".OUTWORLDER_ZERO_VOLUME_RADIUS";

    private final double DEFAULT_OUTWORLDER_ZERO_VOLUME_RADIUS = 26;

    private final String OUTWORLDER_FULL_VOLUME_RADIUS_PREFERENCE =
	VOICEIMPL_PREFIX + ".OUTWORLDER_FULL_VOLUME_RADIUS";

    private final double DEFAULT_OUTWORLDER_FULL_VOLUME_RADIUS = 10;

    private final String OUTWORLDER_FALLOFF_PREFERENCE =
	VOICEIMPL_PREFIX + ".OUTWORLDER_FALLOFF";

    private final double DEFAULT_OUTWORLDER_FALLOFF = .94;

    private final String DEFAULT_LIVE_PLAYER_AUDIO_GROUP_ID =
        "DefaultLivePlayerAudioGroup";

    private final String DEFAULT_STATIONARY_PLAYER_AUDIO_GROUP_ID =
        "DefaultStationaryPlayerAudioGroup";

    private final String AUDIO_DIR_PROPERTY = VOICEIMPL_PREFIX + ".AUDIO_DIR";

    private final String DEFAULT_AUDIO_DIR = ".";

    private VoiceManagerParameters voiceManagerParameters;

    private ConcurrentHashMap<String, AudioGroup> audioGroups = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Call> calls = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap();
    private ConcurrentHashMap<String, RecorderInfo> recorders = new ConcurrentHashMap();
    private ConcurrentHashMap<String, TreatmentGroup> treatmentGroups = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Treatment> treatments = new ConcurrentHashMap();

    private ConcurrentHashMap<String, RecorderInfo> recorderInfoMap = new ConcurrentHashMap();
    
    private static VoiceImpl voiceImpl;

    public static VoiceImpl getInstance() {
	if (voiceImpl == null) {
	    voiceImpl = new VoiceImpl();
	}

	return voiceImpl;
    }

    private VoiceImpl() {
    }

    public void initialize() {
	String s = System.getProperty(SCALE_PROPERTY);

	double scale = DEFAULT_SCALE;

	if (s != null) {
	    try {
		scale = Double.parseDouble(s);
	    } catch (NumberFormatException e) {
		logger.info("Invalid scale factor:  " + s);
	    }
	}

	DefaultSpatializer livePlayerSpatializer = new DefaultSpatializer();
	livePlayerSpatializer.setMaximumVolume(
	    getPreference(LIVE_PLAYER_MAXIMUM_VOLUME_PREFERENCE, 
	    DEFAULT_LIVE_PLAYER_MAXIMUM_VOLUME));

	livePlayerSpatializer.setZeroVolumeRadius(
	    getPreference(LIVE_PLAYER_ZERO_VOLUME_RADIUS_PREFERENCE, 
	    DEFAULT_LIVE_PLAYER_ZERO_VOLUME_RADIUS / scale));

	livePlayerSpatializer.setFullVolumeRadius(
	    getPreference(LIVE_PLAYER_FULL_VOLUME_RADIUS_PREFERENCE, 
	    DEFAULT_LIVE_PLAYER_FULL_VOLUME_RADIUS / scale));

	livePlayerSpatializer.setFalloff(
	    getPreference(LIVE_PLAYER_FALLOFF_PREFERENCE, 
	    DEFAULT_LIVE_PLAYER_FALLOFF));

	DefaultSpatializer stationarySpatializer = new DefaultSpatializer();
	stationarySpatializer.setMaximumVolume(
	    getPreference(STATIONARY_PLAYER_MAXIMUM_VOLUME_PREFERENCE, 
	    DEFAULT_STATIONARY_PLAYER_MAXIMUM_VOLUME));
	stationarySpatializer.setZeroVolumeRadius(
	    getPreference(STATIONARY_PLAYER_ZERO_VOLUME_RADIUS_PREFERENCE, 
	    DEFAULT_STATIONARY_PLAYER_ZERO_VOLUME_RADIUS / scale));
	stationarySpatializer.setFullVolumeRadius(
	    getPreference(STATIONARY_PLAYER_FULL_VOLUME_RADIUS_PREFERENCE, 
	    DEFAULT_STATIONARY_PLAYER_FULL_VOLUME_RADIUS / scale));
	stationarySpatializer.setFalloff(
	    getPreference(STATIONARY_PLAYER_FALLOFF_PREFERENCE, 
	    DEFAULT_STATIONARY_PLAYER_FALLOFF));

	DefaultSpatializer outworlderSpatializer = new DefaultSpatializer();
	outworlderSpatializer.setMaximumVolume(
	    getPreference(OUTWORLDER_MAXIMUM_VOLUME_PREFERENCE, 
	    DEFAULT_OUTWORLDER_MAXIMUM_VOLUME));
	outworlderSpatializer.setZeroVolumeRadius(
	    getPreference(OUTWORLDER_ZERO_VOLUME_RADIUS_PREFERENCE, 
	    DEFAULT_OUTWORLDER_ZERO_VOLUME_RADIUS / scale));
	outworlderSpatializer.setFullVolumeRadius(
	    getPreference(OUTWORLDER_FULL_VOLUME_RADIUS_PREFERENCE, 
	    DEFAULT_OUTWORLDER_FULL_VOLUME_RADIUS / scale));
	outworlderSpatializer.setFalloff(
	    getPreference(OUTWORLDER_FALLOFF_PREFERENCE, 
	    DEFAULT_OUTWORLDER_FALLOFF));

        AudioGroupSetup setup = new AudioGroupSetup();
        setup.spatializer = livePlayerSpatializer;

        AudioGroupImpl livePlayerAudioGroup = 
	    new AudioGroupImpl(DEFAULT_LIVE_PLAYER_AUDIO_GROUP_ID, setup);

	setup = new AudioGroupSetup();
        setup.spatializer = stationarySpatializer;

        AudioGroupImpl stationaryPlayerAudioGroup = 
	    new AudioGroupImpl(DEFAULT_STATIONARY_PLAYER_AUDIO_GROUP_ID, setup);

	voiceManagerParameters = new VoiceManagerParameters(
	    getLogLevel().intValue(), scale,
	    getConferenceId(), livePlayerSpatializer, stationarySpatializer, 
	    outworlderSpatializer, livePlayerAudioGroup,
	    stationaryPlayerAudioGroup);
    }

    public BridgeManager getBridgeManager() {
	return VoiceServiceImpl.getInstance().getBridgeManager();
    }

    /*
     * Called by the VoiceService
     */
    private String conferenceId;

    public String getConferenceId() {
	if (conferenceId == null) {
	    conferenceId = System.getProperty(CONFERENCE_PROPERTY);

	    if (conferenceId == null || conferenceId.length() == 0) {
	        conferenceId = DEFAULT_CONFERENCE;
	    }
	}

	return conferenceId;
    }

    private double getPreference(String preference, double defaultValue) {
        Preferences prefs = Preferences.userNodeForPackage(VoiceImpl.class);

	String s = prefs.get(VOICEIMPL_PREFIX + preference, null);

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

    public boolean addWork(Work work) {
	return VoiceServiceImpl.getInstance().addWork(work);
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
    }

    public void setVoiceBridgeParameters(
	    VoiceBridgeParameters voiceBridgeParameters) {
    }

    public void setVoiceManagerParameters(
	    VoiceManagerParameters voiceManagerParameters) {

	this.voiceManagerParameters = voiceManagerParameters;
    }

    public VoiceManagerParameters getVoiceManagerParameters() {
	return voiceManagerParameters;
    }

    /**
     * Initiate a call
     */
    public Call createCall(String id, CallSetup setup) throws IOException {
	logger.warning("Create call " + id);

	return new CallImpl(id, setup);
    }

    public Call getCall(String id) {
	return calls.get(id);
    }

    public Call[] getCalls() {
	if (calls.size() == 0) {
	    return new Call[0];
	}

	return calls.values().toArray(new Call[0]);
    }

    public void endCall(Call call, boolean removePlayer) throws IOException {
	if (logger.isLoggable(Level.FINE)) {
	    System.out.println(dump("all"));
	}

 	call.end(removePlayer);	

	if (calls.remove(call.getId()) == null) {
	    logger.warning("Call " + call + " not in list of calls");
	} 

	if (logger.isLoggable(Level.FINE)) {
	    System.out.println(dump("all"));
	}
    }

    public void putCall(Call call) {
	calls.put(call.getId(), call);
    }

    public void removeCall(Call call) {
	calls.remove(call.getId());
	VoiceServiceImpl.getInstance().endCall(call);
    }

    public void putPlayer(Player player) {
	players.put(player.getId(), player);
    }

    public void removePlayer(Player player) {
	players.remove(player.getId());
    }

    public AudioGroup getAudioGroup(String id) {
	return audioGroups.get(id);
    }

    public void putAudioGroup(AudioGroup audioGroup) {
	audioGroups.put(audioGroup.getId(), audioGroup);
    }

    public void removeAudioGroup(AudioGroup audioGroup) {
	audioGroups.remove(audioGroup.getId());
    }

    public AudioGroup[] getAudioGroups() {
	if (audioGroups.size() == 0) {
	    return new AudioGroup[0];
	}

    	return audioGroups.values().toArray(new AudioGroup[0]);
    }

    /*
     * Treatments
     */
    public TreatmentGroup createTreatmentGroup(String id) {
	TreatmentGroup group = treatmentGroups.get(id);

	if (group != null) {
	    return group;
	}

	return new TreatmentGroupImpl(id);
    }

    public void putTreatmentGroup(TreatmentGroup group) {
	treatmentGroups.put(group.getId(), group);
    }

    public void removeTreatmentGroup(TreatmentGroup group) {
	treatmentGroups.remove(group);
    }
	
    public TreatmentGroup getTreatmentGroup(String id) {
	return treatmentGroups.get(id);
    }

    public Treatment createTreatment(String id, TreatmentSetup setup) 
	    throws IOException {

	return new TreatmentImpl(id, setup);
    }

    public void putTreatment(Treatment treatment) {
	treatments.put(treatment.getId(), treatment);
    }

    public void removeTreatment(Treatment treatment) {
	treatments.remove(treatment);
    }

    public Treatment getTreatment(String id) {
	return treatments.get(id);
    }

    /*
     * Recording setup and control
     */
    public Recorder createRecorder(String id, RecorderSetup setup) 
	    throws IOException {

  	return new RecorderImpl(id, setup);
    }

    public void putRecorder(String id, RecorderInfo info) {
	recorders.put(id, info);
    }

    public void removeRecorder(String id) {
	recorders.remove(id);
    }

    public Recorder getRecorder(String id) {
	RecorderInfo info = recorders.get(id);

	if (info == null) {
	    return null;
	}

	return info.recorder;
    }

    public RecorderInfo getRecorderInfo(String id) {
	return recorders.get(id);
    }

    public Player createPlayer(String id, PlayerSetup setup) {
	Player player = new PlayerImpl(id, setup);

	logger.info("Created player for " + player + " number of players "
            + players.size());

	return player;
    }

    public Player getPlayer(String id) {
	return players.get(id);
    }

    public Player[] getPlayers() {
	if (players.size() == 0) {
	    return new Player[0];
	}

	return players.values().toArray(new Player[0]);
    }

    public int getNumberOfPlayersInRange(double x, double y, double z) {
	/*
	 * Create a player at the specified location so we can easily
	 * determine the other players we can hear.
	 */
	PlayerSetup setup = new PlayerSetup();
	double scale = voiceManagerParameters.scale;
        setup.x = x / scale;
        setup.y = y / scale;
        setup.z = z / scale;
        setup.isLivePlayer = true;

	Player p1 = new PlayerImpl("NoCallID", setup);

	int n = 0;

	logger.finest("location " + x + ":" + y + ":" + z);

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

	return n;
    }

    public void addWall(double startX, double startY, double endX, double endY,
            double characteristic) {
    }

    private CopyOnWriteArrayList<CallStatusListener> allCallListeners = new CopyOnWriteArrayList();

    private ConcurrentHashMap<String, CopyOnWriteArrayList<CallStatusListener>> callStatusListeners =
	new ConcurrentHashMap();

    private CopyOnWriteArrayList<CallBeginEndListener> callBeginEndListeners =
	new CopyOnWriteArrayList();

    public CopyOnWriteArrayList<CallStatusListener> getAllCallListeners() {
	return allCallListeners;
    }

    public void addCallStatusListener(CallStatusListener listener, String callId) {
        if (listener instanceof ManagedCallStatusListener) {
            addManagedCallStatusListener((ManagedCallStatusListener) listener, callId);
            return;
        }

	if (addWork(new AddCallStatusListenerWork(callId, listener)) == false) {
	    addCallStatusListenerCommit(listener, callId);
	}
    }

    private void addCallStatusListenerCommit(CallStatusListener listener, String callId) {
        if (listener instanceof ManagedCallStatusListener) {
            addManagedCallStatusListener((ManagedCallStatusListener) listener, callId);
            return;
	}

	if (callId == null) {
	    synchronized (allCallListeners) {
	        if (allCallListeners.contains(listener)) {
		    logger.warning("listener " + listener + " is already in the list.");
		    return;
	        }

	        allCallListeners.add(listener);
	        return;
	    }
	}

	synchronized (callStatusListeners) {
	    CopyOnWriteArrayList<CallStatusListener> listeners = callStatusListeners.get(callId);

	    if (listeners == null) {
	        listeners = new CopyOnWriteArrayList<CallStatusListener>();
	        callStatusListeners.put(callId, listeners);
	    } else if (listeners.contains(listener)) {
	        logger.fine("listener " + listener + " is already in the list.");
	        return;
	    }

	    synchronized (listeners) {
	        listeners.add(listener);
	    }
	}
    }
    
    private boolean bindingsInitialized;

    private void initializeBindings() {
        DataManager dm = AppContext.getDataManager();

        try {
            dm.getBinding(DS_MANAGED_ALL_CALL_LISTENERS);
        } catch (NameNotBoundException e) {
            try {
                dm.setBinding(DS_MANAGED_ALL_CALL_LISTENERS, 
		    new ManagedAllCallListeners());
            }  catch (RuntimeException re) {
                logger.warning("failed to bind all call listeners map " + re.getMessage());
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
                logger.warning("failed to bind call listeners map " + re.getMessage());
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
                logger.warning("failed to bind begin / end listeners map " + re.getMessage());
                throw re;
            }
        }
    }

    private void addManagedCallStatusListener(
	    ManagedCallStatusListener listener, String callId) {

	if (bindingsInitialized == false) {
	    initializeBindings();
	}

	DataManager dm = AppContext.getDataManager();

        ManagedReference<ManagedCallStatusListener> mr = 
	    dm.createReference(listener);

	if (callId == null) {
            ManagedAllCallListeners managedListeners = 
		(ManagedAllCallListeners) dm.getBinding(
	        DS_MANAGED_ALL_CALL_LISTENERS);

	    if (managedListeners.contains(mr)) {
		logger.warning("listener " + listener 
		    + " is already in the list.");
		return;
	    }

	    managedListeners.add(mr);
	    return;
	}

        ManagedCallStatusListeners managedListeners = 
	    (ManagedCallStatusListeners) dm.getBinding(
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

    public void removeCallStatusListener(CallStatusListener listener, String callId) {
	if (listener instanceof ManagedCallStatusListener) {
            removeManagedCallStatusListener((ManagedCallStatusListener) listener, callId);
            return;
        }

	if (addWork(new RemoveCallStatusListenerWork(callId, listener)) == false) {
	    removeCallStatusListenerCommit(listener, callId);
	}
    }

    private void removeCallStatusListenerCommit(CallStatusListener listener, String callId) {
	if (listener instanceof ManagedCallStatusListener) {
            removeManagedCallStatusListener((ManagedCallStatusListener) listener, callId);
            return;
        }

	synchronized (callStatusListeners) {
	    if (callId == null) {
                callStatusListeners.remove(listener);
	    } else {
	        CopyOnWriteArrayList<CallStatusListener> listeners = 
		    callStatusListeners.get(callId);

	        if (listeners == null) {
	            logger.warning("Can't find listener for " + callId);
	            return;
	        }

	        synchronized (listeners) {
	            if (listeners.contains(listener)) {
	    	        listeners.remove(listener);

		        if (listeners.isEmpty()) {
		            callStatusListeners.remove(callId);
		        }
		    }
	        }
	    }
	}
    }
  
    private void removeManagedCallStatusListener(
	    ManagedCallStatusListener listener, String callId) {

	DataManager dm = AppContext.getDataManager();

	if (callId == null) {
            ManagedAllCallListeners allCallManagedListeners =
                (ManagedAllCallListeners) dm.getBinding(
		DS_MANAGED_ALL_CALL_LISTENERS);
	
            ManagedReference<ManagedCallStatusListener> mr = 
		dm.createReference(listener);

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
	    addManagedCallBeginEndListener((ManagedCallBeginEndListener) listener);
	    return;
	}

	if (addWork(new AddCallBeginEndListenerWork(listener)) == false) {
	    addCallBeginEndListenerCommit(listener);
	}
    }

    public void addCallBeginEndListenerCommit(CallBeginEndListener listener) {
	synchronized (callBeginEndListeners) {
	    if (callBeginEndListeners.contains(listener) == false) {
                callBeginEndListeners.add(listener);
	    }
	}
    }

    private void addManagedCallBeginEndListener(ManagedCallBeginEndListener listener) {
	if (bindingsInitialized == false) {
	    initializeBindings();
	}

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
    }

    public void removeCallBeginEndListener(CallBeginEndListener listener) {
	if (listener instanceof ManagedCallBeginEndListener) {
	    removeManagedCallBeginEndListener((ManagedCallBeginEndListener) listener);
	    return;
	}

	if (addWork(new RemoveCallBeginEndListenerWork(listener)) == false) {
	    removeCallBeginEndListenerCommit(listener);
	}
    }

    public void removeCallBeginEndListenerCommit(CallBeginEndListener listener) {
	synchronized (callBeginEndListeners) {
            callBeginEndListeners.remove(listener);
	}
    }

    public void removeManagedCallBeginEndListener(ManagedCallBeginEndListener listener) {
        DataManager dm = AppContext.getDataManager();

        ManagedCallBeginEndListeners managedListeners =
            (ManagedCallBeginEndListeners) dm.getBinding(
	    DS_MANAGED_CALL_BEGIN_END_LISTENERS);

	ManagedCallBeginEndListener ml = (ManagedCallBeginEndListener) listener;

        ManagedReference<ManagedCallBeginEndListener> mr = dm.createReference(ml);

        managedListeners.remove(mr);
    }

    public void callStatusChanged(CallStatus callStatus) {
        int code = callStatus.getCode();

        String callId = callStatus.getCallId();

	logger.finer("Got status " + callStatus);

	if (code == CallStatus.INFO) {
	    System.out.println(dump(callStatus.getOption("Info")));
	    return;
	}

	boolean inTransaction = VoiceServiceImpl.getInstance().inTransaction();

	if (code == CallStatus.ESTABLISHED || code == CallStatus.MIGRATED ||
		code == CallStatus.ENDED) {

	    if (inTransaction == false) {
	        notifyCallBeginEndListeners(callStatus);
	    } else {
	        notifyManagedCallBeginEndListeners(callStatus);
	    }
	}
	
	if (inTransaction == false) {
	    notifyCallStatusListeners(callStatus);
	} else {
	    notifyManagedCallStatusListeners(callStatus);
	}
    }

    private void notifyCallStatusListeners(CallStatus status) {
	synchronized (allCallListeners) {
	    for (CallStatusListener listener : allCallListeners) {
                listener.callStatusChanged(status);
	    }
	}
	
	if (status.getCode() == CallStatus.BRIDGE_OFFLINE) {
	    /*
	     * We need to notify all call status listeners
	     */
	    synchronized (callStatusListeners) {
	        Iterator<CopyOnWriteArrayList<CallStatusListener>> iterator = 
		    callStatusListeners.values().iterator();

                while (iterator.hasNext()) {
                    CopyOnWriteArrayList<CallStatusListener> listenerList = iterator.next();

		    synchronized (listenerList) {
		        for (CallStatusListener l : listenerList) {
		            l.callStatusChanged(status);
		        }
		    }
	        }
	    }
	    return;
	}

	String callId = status.getCallId();

	if (callId == null || callId.length() == 0) {
	    logger.warning("No callID:  '" + callId + "'");
	     return;
	}

	CallStatusListener[] listeners;

	synchronized (callStatusListeners) {
	    CopyOnWriteArrayList<CallStatusListener> listenerList = callStatusListeners.get(callId);

	    if (listenerList == null) {
	        logger.finer("No listeners for " + callId);
		return;
	    }
	
	    listeners = listenerList.toArray(new CallStatusListener[0]);
	}

	if (listeners.length == 0) {
	    logger.finer("No listeners for " + callId);
	    return;
	}

	for (int i = 0; i < listeners.length; i++) {
	    logger.finer("Notifiying " + listeners[i] + " callId " + callId);
            listeners[i].callStatusChanged(status);
	}
    }

    public void notifyManagedCallStatusListeners(CallStatus status) {
	if (bindingsInitialized == false) {
	    initializeBindings();
	}

        DataManager dm = AppContext.getDataManager();

        ManagedAllCallListeners managedAllListeners = (ManagedAllCallListeners) dm.getBinding(
	    DS_MANAGED_ALL_CALL_LISTENERS);

	for (ManagedReference<ManagedCallStatusListener> managedListener : managedAllListeners) {
            managedListener.get().callStatusChanged(status);
        }

	String callId = status.getCallId();

	ManagedCallStatusListeners managedListeners =
            (ManagedCallStatusListeners) dm.getBinding(DS_MANAGED_CALL_STATUS_LISTENERS);

	if (status.getCode() == CallStatus.BRIDGE_OFFLINE) {
	    /*
	     * We need to notify all call status listeners
	     */
	    Collection<CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>>> values = 
		managedListeners.values();

	    Iterator<CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>>> iterator = 
		values.iterator();

            while (iterator.hasNext()) {
                CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>> managedListenerList = 
		    iterator.next();

		for (ManagedReference<ManagedCallStatusListener> l : managedListenerList) {
		    l.get().callStatusChanged(status);
		}
	    }

	    return;
	}

	if (callId == null || callId.length() == 0) {
	    logger.warning("No callID:  '" + callId + "'");
	    return;
	}

	CopyOnWriteArrayList<ManagedReference<ManagedCallStatusListener>> listenerList =
	    managedListeners.get(callId);

	if (listenerList == null) {
	    logger.finer("No listeners for " + callId);
	    return;
	}

	for (ManagedReference<ManagedCallStatusListener> listener : listenerList) {
            listener.get().callStatusChanged(status);
        }
    }
 
    private void notifyCallBeginEndListeners(CallStatus status) {
	synchronized (callBeginEndListeners) {
	    for (CallBeginEndListener listener : callBeginEndListeners) {
                listener.callBeginEndNotification(status);
	    }
        }
    }

    private void notifyManagedCallBeginEndListeners(CallStatus status) {
	if (bindingsInitialized == false) {
	    initializeBindings();
	}

        DataManager dm = AppContext.getDataManager();

        ManagedCallBeginEndListeners managedCallBeginEndListeners =
            (ManagedCallBeginEndListeners) dm.getBinding(
	    DS_MANAGED_CALL_BEGIN_END_LISTENERS);

        CopyOnWriteArrayList<ManagedReference<ManagedCallBeginEndListener>> listenerList = 
    	    managedCallBeginEndListeners;

	if (status.getCode() == CallStatus.ESTABLISHED && listenerList.size() == 0) {
	    endIncomingCall(status);
	    return;
	}
	
	for (ManagedReference<ManagedCallBeginEndListener> listener : listenerList) {
            listener.get().callBeginEndNotification(status);
        }
    }

    private void endIncomingCall(CallStatus status) {
	String  incomingCall = status.getOption("IncomingCall");

        if (incomingCall == null || incomingCall.equals("true") == false) {
	    return;
	}

	String callId = status.getCallId();

	BridgeManager bm = getBridgeManager();

	try {
	    bm.playTreatmentToCall(callId,
	        "tts:There are no phones to answer your call.  Good Bye.");
	} catch (IOException e) {
	    logger.warning("Unable to play message to call " + callId
		+ ": " + e.getMessage());
	}

	try {
	    bm.endCall(callId);
	} catch (IOException e) {
	    logger.warning("Unable to end call " + callId
		+ ": " + e.getMessage());
	}

	logger.warning("There are no incoming call handlers.");
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

    public void commit(ListenerWork work) {
	bindingsInitialized = true;

	if (work instanceof AddCallBeginEndListenerWork) {
	    addCallBeginEndListenerCommit(((AddCallBeginEndListenerWork) work).listener);
	    return;
	} 

	if (work instanceof RemoveCallBeginEndListenerWork) {
	    removeCallBeginEndListenerCommit(((RemoveCallBeginEndListenerWork) work).listener);
	    return;
	} 

	if (work instanceof AddCallStatusListenerWork) {
	    AddCallStatusListenerWork w = (AddCallStatusListenerWork) work;
	    addCallStatusListenerCommit(w.listener, w.callId);
	    return;
	} 

	if (work instanceof RemoveCallStatusListenerWork) {
	    RemoveCallStatusListenerWork w = (RemoveCallStatusListenerWork) work;
	    removeCallStatusListenerCommit(w.listener, w.callId);
	    return;
	} 

	logger.warning("Unknown ListenerWork:  " + work);
    }

    public void scheduleTask(KernelRunnable runnable) {
	VoiceServiceImpl.getInstance().scheduleTask(runnable);
    }

    public void joinTransaction(NonDurableTransactionParticipant participant) {
	VoiceServiceImpl.getInstance().joinTransaction(participant);
    }

    public String dump(String command) {
	String[] tokens = command.split(":");

	if (tokens.length > 1) {
	    System.out.println("DUMP:  " + tokens[1]);
	}

	tokens = tokens[0].split("[+]");

	String s = "";

	for (int i = 0; i < tokens.length; i++) {
	    if (tokens[i].equalsIgnoreCase("all")) {
		s += dumpAudioGroups();
		s += dumpCalls();
		s += dumpPlayers();
		s += dumpTreatmentGroups();
		s += dumpTreatments();
	    } else if (tokens[i].equalsIgnoreCase("audioGroups")) {
		s += dumpAudioGroups();
	    } else if (tokens[i].equalsIgnoreCase("calls")) {
		s += dumpCalls();
	    } else if (tokens[i].equalsIgnoreCase("players")) {
		s += dumpPlayers();
	    } else if (tokens[i].equalsIgnoreCase("treatmentGroups")) {
	    } else if (tokens[i].equalsIgnoreCase("treatments")) {
	    } else {
		logger.warning("Unrecognized object to dump:  " + tokens[i]);
	    }
	}

	if (s.length() == 0) {
	    return "";
	}

	s += "\n";

	return s;
    }

    private String dumpAudioGroups() {
	String s = "\n";
	s += "Audio Groups\n";
	s += "------------\n";

	if (audioGroups.size() > 0) {
	    Iterator<AudioGroup> it = audioGroups.values().iterator();

	    while (it.hasNext()) {
		s += it.next().dump() + "\n";
	    }
	} else {
	    s += "There are no audio groups!\n";
	}

	return s;
    }
	
    private String dumpCalls() {
	String s = "\n";
	s += "Calls\n";
	s += "-----\n";

	if (calls.size() > 0) {
	    Iterator<Call> it = calls.values().iterator();

	    while (it.hasNext()) {
		s += it.next().dump() + "\n";
	    }
	} else {
	    s += "There are no calls!\n";
	}

	return s;
    }

    private String dumpPlayers() {
	String s = "\n";
	s += "Players\n";
	s += "-------\n";

	if (players.size() > 0) {
	    Iterator<Player> it = players.values().iterator();

	    while (it.hasNext()) {
		s += it.next().dump() + "\n";
	    }
	} else {
	    s += "There are no players!\n";
	}

	return s;
    }

    private String dumpTreatmentGroups() {
	String s = "\n";
	s += "TreatmentGroups\n";
	s += "---------------\n";

	if (treatmentGroups.size() > 0) {
	    Iterator<TreatmentGroup> it = treatmentGroups.values().iterator();

	    while (it.hasNext()) {
		s += it.next().dump() + "\n";
	    }
	} else {
	    s += "There are no treatment groups!\n";
	}

	return s;
    }

    private String dumpTreatments() {
	String s = "\n";
	s += "Treatments\n";
	s += "----------\n";

	if (treatments.size() > 0) {
	    Iterator<Treatment> it = treatments.values().iterator();

	    while (it.hasNext()) {
		s += it.next().dump() + "\n";
	    }
	} else {
	    s += "There are no treatments!\n";
	}

	return s;
    }

}
