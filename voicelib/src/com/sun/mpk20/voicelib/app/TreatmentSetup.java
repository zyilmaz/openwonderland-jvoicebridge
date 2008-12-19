package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

import com.sun.voip.client.connector.CallStatusListener;

public class TreatmentSetup implements Serializable {

    public String treatment;
    public double x;
    public double y;
    public double z;

    public CallStatusListener listener;

}
