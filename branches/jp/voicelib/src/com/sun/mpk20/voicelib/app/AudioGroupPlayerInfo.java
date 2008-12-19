package com.sun.mpk20.voicelib.app;

public class AudioGroupPlayerInfo {

    public double defaultSpeakingAttenuation = 
	AudioGroup.DEFAULT_SPEAKING_ATTENUATION;

    public double speakingAttenuation = 
	AudioGroup.DEFAULT_SPEAKING_ATTENUATION;

    public double defaultListenAttenuation = 
	AudioGroup.DEFAULT_LISTEN_ATTENUATION;

    public double listenAttenuation = AudioGroup.DEFAULT_LISTEN_ATTENUATION;
    public boolean isSpeaking;

    public enum ChatType {
	EXCLUSIVE,
	SECRET,
	PRIVATE,
	PUBLIC
    };

    public ChatType chatType;

    public AudioGroupPlayerInfo(boolean isSpeaking, ChatType chatType) {
	this.isSpeaking = isSpeaking;
	this.chatType = chatType;
    }

    public String toString() {
	return "speakingAttenuation=" + speakingAttenuation
	    + ", listenAttenuation=" + listenAttenuation
	    + ", isSpeaking=" + isSpeaking + " " + chatType;
    }
	
}
