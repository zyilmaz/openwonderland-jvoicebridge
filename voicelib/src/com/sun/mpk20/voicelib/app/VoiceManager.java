package com.sun.mpk20.voicelib.app;

import com.sun.voip.client.connector.CallStatusListener;

import java.math.BigInteger;

import java.io.IOException;
import java.io.Serializable;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;

//add javadoc

/**
 * The VoiceManager interface is an API providing call setup and control.
 */
public interface VoiceManager extends Serializable {

    public static final String VOICEMANAGER_PREFIX = "COM.SUN.MPK20.VOICELIB.APP.VOICEMANAGER.";

    public static final String NAME = VoiceManager.class.getName();
    public static final String DS_PREFIX = NAME + ".";

    /* The name of the list for call status listeners */
    public static final String DS_MANAGED_ALL_CALL_LISTENERS =
        DS_PREFIX + "AllCallListeners";

    /* The name of the list for call status listeners */
    public static final String DS_MANAGED_CALL_STATUS_LISTENERS =
        DS_PREFIX + "CallStatusListeners";

    /* The name of the list for call begin/end listeners */
    public static final String DS_MANAGED_CALL_BEGIN_END_LISTENERS =
        DS_PREFIX + "CallBeginEndListeners";

    public static final String DEFAULT_CONFERENCE="Test:PCM/16000/2";

    public static final String DEFAULT_LIVE_PLAYER_AUDIO_GROUP_ID =
        "DefaultLivePlayerAudioGroup";

    public static final String DEFAULT_STATIONARY_PLAYER_AUDIO_GROUP_ID =
        "DefaultStationaryPlayerAudioGroup";

    public static final String AUDIO_DIR = "com.sun.sgs.impl.app.voice.AUDIO_DIR";

    public static final String DEFAULT_AUDIO_DIR = ".";

    //
    // Call setup 
    // 

    /**
     * Get the next available voice bridge.
     */
    public BridgeInfo getVoiceBridge() throws IOException;

    /**
     * Initiate a call
     */
    public Call createCall(String id, CallSetup setup) throws IOException;

    public Call getCall(String id);

    public ConcurrentHashMap<String, Call> getCalls();

    public void endCall(Call call, boolean removePlayer) throws IOException;

    /*
     * Group management
     */
    public AudioGroup createAudioGroup(String id, AudioGroupSetup setup);

    public AudioGroup getAudioGroup(String id);

    public AudioGroup getDefaultLivePlayerAudioGroup();

    public AudioGroup getDefaultStationaryPlayerAudioGroup();

    public ConcurrentHashMap<String, AudioGroup> getAudioGroups();

    public void removeAudioGroup(String id);

    /*
     * Treatments
     */
    public TreatmentGroup createTreatmentGroup(String id);

    public void removeTreatmentGroup(TreatmentGroup group);

    public TreatmentGroup getTreatmentGroup(String id);

    public Treatment createTreatment(String id, TreatmentSetup setup) throws IOException;

    public Treatment getTreatment(String id);

    public ConcurrentHashMap<String, Treatment> getTreatments();

    /*
     * Recording setup and control
     */
    public Recorder createRecorder(String id, RecorderSetup setup) throws IOException;

    public Recorder getRecorder(String id);
    
    public ConcurrentHashMap<String, Recorder> getRecorders();

    /*
     * Voice bridge parameters.
     */
    public void setVoiceBridgeParameters(VoiceBridgeParameters parameters);
	
    /*
     * VoiceManager
     */
    public VoiceService getBackingManager();

    public double getScale();

    public String getConferenceId();

    public String getAudioDirectory();

    public void setVoiceManagerParameters(VoiceManagerParameters parameters);

    public VoiceManagerParameters getVoiceManagerParameters();

    public Player createPlayer(String id, PlayerSetup setup);

    public void removePlayer(String id);
    
    public void addVirtualPlayerListener(VirtualPlayerListener listener);

    public void removeVirtualPlayerListener(VirtualPlayerListener listener);

    public Player getPlayer(String id);

    public ConcurrentHashMap<String, Player> getPlayers();

    public void addWall(double startX, double startY, double endX, double endY,
        double characteristic);

    public void setDefaultSpatializers(DefaultSpatializers defaultSpatializers);

    public DefaultSpatializers getDefaultSpatializers();

    public void setDefaultSpatializers(DefaultSpatializers defaultSpatializers,
	double startX, double startY, double endX, double endY);

    public DefaultSpatializers getDefaultSpatializers(
	double startX, double startY, double endX, double endY);

    public void setLogLevel(Level level);

    public Level getLogLevel();

    public int getNumberOfPlayersInRange(double x, double y, double z);

    public void addCallStatusListener(CallStatusListener listener);

    public void addCallStatusListener(CallStatusListener listener, String callID);

    public void removeCallStatusListener(CallStatusListener listener);

    public void removeCallStatusListener(CallStatusListener listener, String callID);

    public void addCallBeginEndListener(CallBeginEndListener listener);

    public void removeCallBeginEndListener(CallBeginEndListener listener);

    public void dump(String command);

}
