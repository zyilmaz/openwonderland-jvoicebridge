package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

import com.sun.voip.CallParticipant;

import com.sun.voip.client.connector.CallStatusListener;

public class CallSetup implements Serializable {

    public CallParticipant cp;
    public BridgeInfo bridgeInfo;
    public CallStatusListener listener;

}
