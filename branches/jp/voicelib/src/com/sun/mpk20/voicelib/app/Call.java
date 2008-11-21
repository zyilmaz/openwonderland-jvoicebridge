package com.sun.mpk20.voicelib.app;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatusListener;

import java.io.IOException;
import java.io.Serializable;

public interface Call extends CallStatusListener {

    public String getId();

    public CallSetup getSetup();

    public void setPlayer(Player player);

    public Player getPlayer();
	
    public void mute(boolean isMuted) throws IOException;

    public boolean isMuted();

    public void transfer(CallParticipant cp) throws IOException;

    public void transferToConference(String conferenceId) throws IOException;

    public void playTreatment(String treatment) throws IOException;

    public void pauseTreatment(String treatment) throws IOException;

    public void stopTreatment(String treatment) throws IOException;

    public void end(boolean removePlayer) throws IOException;

    public String dump();

}
