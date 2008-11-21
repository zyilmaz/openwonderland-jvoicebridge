package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

import java.util.concurrent.ConcurrentHashMap;

public interface TreatmentGroup {

    public String getId();

    public void addTreatment(Treatment treatment);

    public void removeTreatment(Treatment treatment);

    public ConcurrentHashMap<String, Treatment> getTreatments();

}
