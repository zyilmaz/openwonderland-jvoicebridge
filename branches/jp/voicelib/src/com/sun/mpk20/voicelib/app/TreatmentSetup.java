package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

import com.sun.voip.client.connector.CallStatusListener;

public class TreatmentSetup implements Serializable {

    public String treatment;
    public double lowerLeftX;
    public double lowerLeftY;
    public double lowerLeftZ;
    public double upperRightX;
    public double upperRightY;
    public double upperRightZ;

    public CallStatusListener listener;

}
