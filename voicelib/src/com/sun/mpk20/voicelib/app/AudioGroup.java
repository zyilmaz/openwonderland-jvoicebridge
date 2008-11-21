package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

import java.util.concurrent.ConcurrentHashMap;

public interface AudioGroup extends Serializable {

    public final double FULL_VOLUME = 1.0;

    public final double DEFAULT_SPEAKING_ATTENUATION = 1.0;

    public final double DEFAULT_LISTEN_ATTENUATION = 1.0;

    public final double MINIMAL_LISTEN_ATTENUATION = .2;

    public String getId();

    public AudioGroupSetup getSetup();

    public void addPlayer(Player player, AudioGroupPlayerInfo info);

    public void removePlayer(Player player);

    public ConcurrentHashMap<Player, AudioGroupPlayerInfo> getPlayers();

    public void removePlayers();

    public void setSpeakingAttenuation(Player player, 
	double speakingAttenuation);

    public void setListenAttenuation(Player player, double listenAttenuation);

    public void setSpeaking(Player player, boolean isSpeaking);

    public AudioGroupPlayerInfo getPlayerInfo(Player player);

    public boolean equals(Object audioGroup);

    public String dump();

}
