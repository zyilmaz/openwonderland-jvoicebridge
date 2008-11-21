package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

public class BridgeInfo implements Serializable {

    public String bridgeId;
    public String privateHostName;
    public int privateControlPort;
    public int privateSipPort;
    public String publicHostName;
    public int publicControlPort;
    public int publicSipPort;

    public String toString() {
	return bridgeId + "::" + privateHostName
	    + ":" + privateControlPort 
	    + ":" + privateSipPort
	    + ":" + publicHostName
	    + ":" + publicControlPort
	    + ":" + publicSipPort;
    }

}
