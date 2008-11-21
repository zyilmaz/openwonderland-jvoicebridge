package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

public interface Treatment {

    public String getId();

    public TreatmentSetup getSetup();
  
    public Call getCall();

    public void setTreatment(String treatment);

    public void stop();

}
