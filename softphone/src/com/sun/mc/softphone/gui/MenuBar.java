/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2000 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * Portions of this software are based upon public domain software
 * originally written at the National Center for Supercomputing Applications,
 * University of Illinois, Urbana-Champaign.
 *
 * Copyright 2007 Sun Microsystems, Inc.
 */
package com.sun.mc.softphone.gui;

import java.awt.event.*;
import javax.swing.*;
import javax.swing.JMenuItem;
import com.sun.mc.softphone.common.Utils;
import com.sun.mc.softphone.Version;
import java.awt.event.KeyEvent;

/**
 * <p>Title: SIP COMMUNICATOR</p>
 * <p>Description:JAIN-SIP Audio/Video phone application</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Organisation: LSIIT laboratory (http://lsiit.u-strasbg.fr) </p>
 * <p>Network Research Team (http://www-r2.u-strasbg.fr))</p>
 * <p>Louis Pasteur University - Strasbourg - France</p>
 * @author Emil Ivov (http://www.emcho.com)
 * @version 1.1
 *
 */
class MenuBar extends JMenuBar {
    JMenu callMenu = new JMenu("Call");
    JMenu settingsMenu = new JMenu("Settings");
    JMenu helpMenu = new JMenu("Help");
    Action exitAction;

    private ConfigFrame configFrame;

    MenuBar() {
        callMenu.setMnemonic('C');
        settingsMenu.setMnemonic('S');
        helpMenu.setMnemonic('H');
        helpMenu.add(new JMenuItem( new AboutAction()));
        add(callMenu);
        add(settingsMenu);
        add(helpMenu);
    }

    public void setConfigFrame(ConfigFrame configFrame) {
        this.configFrame = configFrame;
    }

    private class AboutAction extends AbstractAction {
        public AboutAction() {
            super("About ...");
        }

        public void actionPerformed(ActionEvent evt) {
	  /*
            JOptionPane.showMessageDialog(
                null,
                "Network Research Team ( http://www-r2.u-strasbg.fr )\n" +
                "LSIIT Laboratory ( http://lsiit.u-strasbg.fr )\n" +
                "Louis Pasteur University ( http://www-ulp.u-strasbg.fr )\n" +
                "Division Chief: Thomas Noel ( Thomas.Noel@dpt-info.u-strasbg.fr )\n\n" +
                "Author: Emil Ivov\n" +
                "http://www.emcho.com\n" +
                "e-mail: emil_ivov@yahoo.com"
                );
          */
             JOptionPane.showMessageDialog(null,
                 new JLabel("Built on: " + Version.getVersion(),
		 new ImageIcon(Utils.getResource("sip-communicator.about.jpg")),
		 SwingConstants.TRAILING), 
		 "Sun Softphone (powered by SIP Communicator)",
                 JOptionPane.PLAIN_MESSAGE);
        }
    }

    void addConfigCallAction(Action action) {
        JMenuItem config = new JMenuItem(action);
        config.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
        settingsMenu.add(config);
    }

    JMenu audioQualityMenu = new JMenu("Audio Quality");
    JRadioButtonMenuItem rbPhone;
    JRadioButtonMenuItem rbVPN;
    JRadioButtonMenuItem rbCD;
    JRadioButtonMenuItem none;

    ButtonGroup buttonGroup;

    void addAudioQualityAction(Action action) {
	settingsMenu.add(audioQualityMenu);

	buttonGroup = new ButtonGroup();

	none = new JRadioButtonMenuItem("none");
	none.setVisible(false);

	buttonGroup.add(none);
    }

    void addPhoneAction(Action action) {
        rbPhone = new JRadioButtonMenuItem(action);
        audioQualityMenu.add(rbPhone);

	buttonGroup.add(rbPhone);

	if (isPhoneSetting()) {
	   rbPhone.setSelected(true);
	}
    }

    void addVPNAction(Action action) {
        rbVPN = new JRadioButtonMenuItem(action);
        audioQualityMenu.add(rbVPN);

	buttonGroup.add(rbVPN);

	if (isVPNSetting()) {
	    rbVPN.setSelected(true);
	}
    }

    void addCDAction(Action action) {
        rbCD = new JRadioButtonMenuItem(action);
        audioQualityMenu.add(rbCD);

	buttonGroup.add(rbCD);

	if (isCDSetting()) {
	    rbCD.setSelected(true);
	}
    }

    void addCustomAction(Action action) {
	JMenuItem custom = new JMenuItem(action);

	audioQualityMenu.add(custom);
    }

    boolean isPhoneSetting() {
	return getSampleRate() == 8000 && getChannels() == 1 &&
		getEncoding().equals("PCMU") &&
		getTransmitSampleRate() == 8000 && getTransmitChannels() == 1 &&
		getTransmitEncoding().equals("PCMU");
    }

    boolean isVPNSetting() {
	return getSampleRate() == 16000 && getChannels() == 2 &&
		getEncoding().equals("PCM") &&
		getTransmitSampleRate() == 16000 && 
		getTransmitChannels() == 2 &&
		getTransmitEncoding().equals("PCM");
    }

    boolean isCDSetting() {
	return getSampleRate() == 44100 && getChannels() == 2 &&
		getEncoding().equals("PCM") &&
		getTransmitSampleRate() == 44100 && 
		getTransmitChannels() == 2 &&
		getTransmitEncoding().equals("PCM");
    }

    void selectCustom() {
	if (isPhoneSetting()) {
	    rbPhone.setSelected(true);
	} else if (isVPNSetting()) {
	    rbVPN.setSelected(true);
	} else if (isCDSetting()) {
	    rbCD.setSelected(true);
	} else {
	    none.setSelected(true);
	}
    }

    public void setAudioQuality(String sampleRate, String channels,
	    String encoding) {

	Utils.setPreference("com.sun.mc.softphone.media.SAMPLE_RATE",
                sampleRate);
	Utils.setPreference("com.sun.mc.softphone.media.CHANNELS", channels);
        Utils.setPreference("com.sun.mc.softphone.media.ENCODING", encoding);

	Utils.setPreference("com.sun.mc.softphone.media.TRANSMIT_SAMPLE_RATE",
            sampleRate);
	Utils.setPreference("com.sun.mc.softphone.media.TRANSMIT_CHANNELS", 
	    channels);
        Utils.setPreference("com.sun.mc.softphone.media.TRANSMIT_ENCODING", 
	    encoding);

	selectCustom();
    }

    private int getSampleRate() {
        return Utils.getIntPreference(
            "com.sun.mc.softphone.media.SAMPLE_RATE", 16000);
    }

    private int getChannels() {
	return Utils.getIntPreference("com.sun.mc.softphone.media.CHANNELS", 2);
    }

    private String getEncoding() {
        String s = Utils.getPreference("com.sun.mc.softphone.media.ENCODING");

	if (s != null) { 
	    if (s.equalsIgnoreCase("PCMU")) {
	        return "PCMU";
	    }

	    if (s.equalsIgnoreCase("SPEEX")) {
		return "SPEEX";
	    }
	}

	return "PCM";
    }
	
    private int getTransmitSampleRate() {
        return Utils.getIntPreference(
            "com.sun.mc.softphone.media.TRANSMIT_SAMPLE_RATE", 16000);
    }

    private int getTransmitChannels() {
        return Utils.getIntPreference(
	    "com.sun.mc.softphone.media.TRANSMIT_CHANNELS", 2);
    }

    private String getTransmitEncoding() {
        String s = Utils.getPreference(
	    "com.sun.mc.softphone.media.TRANSMIT_ENCODING");

        if (s != null) {
	    if (s.equalsIgnoreCase("PCMU")) {
                return "PCMU";
	    }

	    if (s.equalsIgnoreCase("SPEEX")) {
                return "SPEEX";
	    }
        }

        return "PCM";
    }

    void addVolumeCallAction(Action action) {
        JMenuItem volume = new JMenuItem(action);
        volume.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        settingsMenu.add(volume);
    }

    void addAudioCallAction(Action action) {
        JMenuItem audio = new JMenuItem(action);
        audio.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        settingsMenu.add(audio);
    }

    void addDtmfCallAction(Action action)
    {
        JMenuItem dtmf = new JMenuItem(action);
        dtmf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        settingsMenu.add(dtmf);
    }

    void addThreshAction(Action action) {
        JMenuItem thresh = new JMenuItem(action);
        settingsMenu.add(thresh);
    }

    void addLpfAction(Action action) {
        JMenuItem lpf = new JMenuItem(action);
        settingsMenu.add(lpf);
    }

    void addLpfSliderAction(Action action) {
        JMenuItem lpf = new JMenuItem(action);
        settingsMenu.add(lpf);
    }

    void addVuMeterAction(Action action) {
        JMenuItem vuMeter = new JMenuItem(action);
        settingsMenu.add(vuMeter);
    }

    void addOnAction(Action action) {
        JMenuItem on = new JMenuItem(action);
        settingsMenu.add(on);
    }

    void addCallAction(Action action) {
        addCallAction(action, -1);
    }

    void addCallAction(Action action, int accelerator) {
        JMenuItem voiceMail = new JMenuItem(action);
        if(accelerator != -1)
            voiceMail.setAccelerator(KeyStroke.getKeyStroke(accelerator, 0));
        callMenu.add(voiceMail);
    }

    void addHistoryAction(Action action) {
        JMenuItem history = new JMenuItem(action);
        history.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        callMenu.add(history);
    }

    void addRecordAction(Action action) {
        JMenuItem record = new JMenuItem(action);
        //record.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        callMenu.add(record);
    }

    void addPlaybackAction(Action action) {
        JMenuItem playback = new JMenuItem(action);
        //playback.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        callMenu.add(playback);
    }

    void addReceiveMonAction(Action action) {
        JMenuItem receiveMon = new JMenuItem(action);
        callMenu.add(receiveMon);
    }

    void addExitCallAction(Action action) {
        callMenu.addSeparator();
        callMenu.add(action);
    }

}
