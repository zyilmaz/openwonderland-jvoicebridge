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

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import com.sun.mc.softphone.SipCommunicator;
import com.sun.mc.softphone.common.*;
import com.sun.mc.softphone.gui.config.*;
import com.sun.mc.softphone.gui.event.*;
import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.sip.Call;

import com.sun.voip.Logger;

import javax.swing.plaf.metal.MetalLookAndFeel;
import com.sun.mc.softphone.gui.plaf.SipCommunicatorColorTheme;
import java.awt.event.KeyEvent;
import java.io.IOException;
//import com.sun.mc.softphone.media.JMFRegistry;

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
public class NewGuiManager implements GuiManagerUI, GuiCallback,
	WindowListener {

    private static Console console = Console.getConsole(NewGuiManager.class);
    //Global status codes
    public static final String NOT_REGISTERED = "Not Registered";
    public static final String UNREGISTERING = "Unregistering, please wait!";
    public static final String REGISTERING = "Trying to register as:";
    public static final String REGISTERED = "Registered as ";

    private NewPhoneFrame   phoneFrame   = null;
    private ConfigFrame     configFrame  = null;
    private LineTestConfig  lineTestConfig = null;
    private NetworkInterfaceConfig  networkInterfaceConfig = null;
    private ArrayList       listeners    = null;
    private AlertManager    alertManager = null;
    private Properties      properties;
    private JLabel          logoLabel    = null;
    private Lines           interlocutors= null;

    //Set default colors
    // static Color defaultBackground = new Color(255, 255, 255);

    private AuthenticationSplash authenticationSplash = null;

    private HistorySplash historySplash = null;

    private RecordDialog recordDialog = null;

    private PlaybackDialog playbackDialog = null;

    private Process audioControlProcess = null;

    private VolumeControl volumeControl = null;

    private LpfControl lpfControl = null;

    private LpfSliderControl lpfSliderControl = null;

    /*
     * Controls for tweaking the speech detector parameters
     */
    private ThreshControl threshControl = null;
    private OnControl onControl = null;

    public NewGuiManager(Boolean visibleUI) {
	//if (visibleUI.booleanValue()) {
            initLookAndFeel();
	//}

        //create actions first for they are used by others

	phoneFrame    = new NewPhoneFrame(this);
	configFrame   = new ConfigFrame();

	listeners     = new ArrayList();
	alertManager  = new AlertManager();

        logoLabel     = new JLabel();
        interlocutors = new Lines();
        // interlocutors = new InterlocutorsTableModel();

        initActionListeners();
 	( (MenuBar) phoneFrame.jMenuBar1).addConfigCallAction(new ConfigAction());
 	( (MenuBar) phoneFrame.jMenuBar1).addAudioQualityAction(new AudioQualityAction());
 	( (MenuBar) phoneFrame.jMenuBar1).addPhoneAction(new PhoneAction());
 	( (MenuBar) phoneFrame.jMenuBar1).addVPNAction(new VPNAction());
 	( (MenuBar) phoneFrame.jMenuBar1).addCDAction(new CDAction());
 	( (MenuBar) phoneFrame.jMenuBar1).addCustomAction(new CustomAction());
 	( (MenuBar) phoneFrame.jMenuBar1).addVolumeCallAction(new VolumeAction());
 	( (MenuBar) phoneFrame.jMenuBar1).addAudioCallAction(new AudioAction());
 	//( (MenuBar) phoneFrame.jMenuBar1).addOnAction(new OnAction());
 	//( (MenuBar) phoneFrame.jMenuBar1).addThreshAction(new ThreshAction());
 	//( (MenuBar) phoneFrame.jMenuBar1).addLpfAction(new LpfAction());
 	//( (MenuBar) phoneFrame.jMenuBar1).addLpfSliderAction(new LpfSliderAction());
	( (MenuBar) phoneFrame.jMenuBar1).addHistoryAction(new HistoryAction());
	( (MenuBar) phoneFrame.jMenuBar1).addRecordAction(new RecordAction());
	( (MenuBar) phoneFrame.jMenuBar1).addPlaybackAction(new PlaybackAction());
	( (MenuBar) phoneFrame.jMenuBar1).addExitCallAction(new ExitAction());
	configFrame.setLocationRelativeTo(phoneFrame);
        //phoneFrame.participantsTable.setModel(interlocutors);
        phoneFrame.setIconImage(new ImageIcon(Utils.getResource("sip-communicator-16x16.jpg")).getImage());
        logoLabel.setIcon(new ImageIcon(Utils.getResource("sip-communicator.jpg")));
        setGlobalStatus(NOT_REGISTERED, "");
        JOptionPane.setRootFrame(phoneFrame);
    }
    
    public void addConfigMenuItem(Action action) {
        ((MenuBar)phoneFrame.jMenuBar1).settingsMenu.add(action);
    }

    private static void initLookAndFeel() {
        MetalLookAndFeel mlf = new MetalLookAndFeel();
        mlf.setCurrentTheme( new SipCommunicatorColorTheme());

        try {
            UIManager.setLookAndFeel(mlf);
        }
        catch (UnsupportedLookAndFeelException ex) {
            console.error("Failed to se custom look and feel", ex);
        }
    }

    public void showConfigFrame() {
        new ConfigurationFrame();
        //configFrame.pack();
	//configFrame.setVisible(true);
    }

    public void showPhoneFrame(boolean show) {
	if (show == true) {
            phoneFrame.setVisible(true);
	    phoneFrame.toFront();

	    if (Utils.isJdk14() == false) {
	        phoneFrame.setAlwaysOnTop(true);
	        phoneFrame.setAlwaysOnTop(false);
	    }

            phoneFrame.contactBox.grabFocus();
	} else {
            phoneFrame.setVisible(false);
	}
    }

    public void addVisualComponent(Component vComp) {
        if (vComp == null) {
            return;
        }
        else {
        }
    }

    public void addControlComponent(Component cComp) {
        if (cComp == null) {
            return;
        }
    }

    public void removePlayerComponents() {
    }

    public void addInterlocutor(InterlocutorUI interlocutor) {

//System.out.println("NewGuiManager.addInterlocutor: "+interlocutor.getID());
        interlocutor.setCallback(this);
        int inter=interlocutors.addInterlocutor(interlocutor);
        if(inter>=0) {
            update(interlocutor);
        } else {
            // no available lines
            UserCallControlEvent commEvt = new UserCallControlEvent(interlocutor);
            for (int i = listeners.size() - 1; i >= 0; i--) {
                ( (UserActionListener) listeners.get(i)).handleHangupRequest(
                    commEvt);
            }
        }
    }

    public void setCommunicationActionsEnabled(boolean enabled) {
        phoneFrame.dialButton.setEnabled(enabled);
        phoneFrame.hangupButton.setEnabled(enabled);
        phoneFrame.answerButton.setEnabled(enabled);
        phoneFrame.muteButton.setEnabled(enabled);
    }

    public void addUserActionListener(UserActionListener l) {
        listeners.add(l);
    }

    public void removeUserActionListener(UserActionListener l) {
        listeners.remove(l);
    }

    public void setPhoneNumber(String phoneNumber) {
	phoneFrame.contactBox.setText(phoneNumber);
        phoneFrame.contactBox.setToolTipText(phoneNumber);
    }

//-------------------------- GuiCallback --------------------
    public void update(InterlocutorUI interlocutorUI) {
        String state=interlocutorUI.getCallState();
        String error=interlocutorUI.getCallError();
        int index=interlocutors.findIndex(interlocutorUI.getID());
        if(index==-1) {
            return;
        }

        if(Call.DISCONNECTED.equals(state)) {
            if(!phoneFrame.isLineConnected(index)) {
                if(historySplash==null) {
                    historySplash=new HistorySplash(phoneFrame,true);
                }
                historySplash.addMissedNumber(interlocutorUI.getAddress());
            }
            phoneFrame.setLineConnected(index,false);
            phoneFrame.muted(false);
        } if(Call.DIALING.equals(state)) {
            phoneFrame.setLineConnected(index,true);
        } if(Call.RINGING.equals(state)) {
            phoneFrame.setLineConnected(index,true);
        } if(Call.BUSY.equals(state)) {
            phoneFrame.setLineConnected(index,false);
        } if(Call.FAILED.equals(state)) {
            phoneFrame.setLineConnected(index,false);
        } if(Call.ALERTING.equals(state)) {
            phoneFrame.setLineAlerting(index);
            if(phoneFrame.getSelectedLine()==index) {
                phoneFrame.contactBox.setText(interlocutorUI.getAddress());
                phoneFrame.contactBox.setToolTipText(interlocutorUI.getAddress());
            }
        } if(Call.CONNECTED.equals(state)) {
            phoneFrame.setLineConnected(index,true);
            if(phoneFrame.getSelectedLine()==index) {
                phoneFrame.contactBox.setText(interlocutorUI.getAddress());
                phoneFrame.contactBox.setToolTipText(interlocutorUI.getAddress());
            }
        }
        if(Call.FAILED.equals(state)) {
            setCallStatus(state+": "+error);
        } else {
            setCallStatus(state);
        }
        interlocutors.update(interlocutorUI);
        //phoneFrame.contactBox.grabFocus();
    }

    public void remove(InterlocutorUI interlocutorUI) {
        interlocutors.remove(interlocutorUI);
    }

    //----------- Alerts
    public void startAlert(String alertResourceName) {
        try {
            alertManager.startAlert(alertResourceName);
        }
        catch (Throwable ex) {
            //OK, no one cares really
            console.warn("Couldn't play alert", ex);
        }
    }

    public void stopAlert(String alertResourceName) {
        try {
            alertManager.stopAlert(alertResourceName);
        }
        catch (Throwable ex) {
            //OK, no one cares really
            console.warn("Couldn't stop alert", ex);
        }
    }

    //----------- DTMF
    public boolean startDtmf(String key) {
        //System.out.println("GuiManager.startDtmf");
        int selectedRow=phoneFrame.getSelectedLine();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            return false;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        if(inter==null) {
            return false;
        }
        UserDtmfEvent dtmfEvt = new UserDtmfEvent(inter,true,key);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleDtmfRequest(dtmfEvt);
        }
        phoneFrame.contactBox.grabFocus();
        return true;
    }

    public boolean stopDtmf(String key) {
        //System.out.println("GuiManager.stopDtmf");
        int selectedRow=phoneFrame.getSelectedLine();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            return false;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        if(inter==null) {
            return false;
        }
        UserDtmfEvent dtmfEvt = new UserDtmfEvent(inter,false,key);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleDtmfRequest(dtmfEvt);
        }
        phoneFrame.contactBox.grabFocus();
        return true;
    }

    public void muted(boolean isMuted) {
        phoneFrame.muted(isMuted);
    }

//----------------- Event dispatching------------------------
    void dialButton_actionPerformed(EventObject evt) {
        //TODO temporarily close alerts from here.
        alertManager.stopAllAlerts();
        String callee = phoneFrame.contactBox.getText();
        if (callee == null || callee.trim().length() < 1) {
            phoneFrame.contactBox.grabFocus();
            return;
        }

        int selectedRow=phoneFrame.getSelectedLine();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            phoneFrame.contactBox.grabFocus();
            return;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        if(inter!=null) {
            // line in use
            phoneFrame.contactBox.grabFocus();
            return;
        }
	
        if(historySplash==null) {
            historySplash=new HistorySplash(phoneFrame,true);
        }
        historySplash.addDialedNumber(callee);

	callee = format(callee);

        UserCallInitiationEvent commEvt = new UserCallInitiationEvent(callee);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleDialRequest(commEvt);
        }

        phoneFrame.contactBox.grabFocus();
    }

    void hangupButton_actionPerformed(ActionEvent evt) {
        //TODO temporarily close alerts from here.
        alertManager.stopAllAlerts();
        int selectedRow=phoneFrame.getSelectedLine();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            phoneFrame.contactBox.grabFocus();
            return;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        if(inter!=null) {
            UserCallControlEvent commEvt = new UserCallControlEvent(inter);
            for (int i = listeners.size() - 1; i >= 0; i--) {
                ( (UserActionListener) listeners.get(i)).handleHangupRequest(
                    commEvt);
            }
        }
        //phoneFrame.contactBox.setText("");
        //phoneFrame.contactBox.setToolTipText("");
        phoneFrame.contactBox.grabFocus();
    }

    void answerButton_actionPerformed(ActionEvent evt) {
        //TODO temporarily close alerts from here.

        alertManager.stopAllAlerts();

        int selectedRow=phoneFrame.getSelectedLine();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            phoneFrame.contactBox.grabFocus();
            return;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        if(inter==null) {
            phoneFrame.contactBox.grabFocus();
            return;
        }


        if(historySplash==null) {
            historySplash=new HistorySplash(phoneFrame,true);
        }
        historySplash.addReceivedNumber(inter.getAddress());
        
        UserCallControlEvent commEvt = new UserCallControlEvent(inter);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleAnswerRequest(
                commEvt);
        }
        phoneFrame.contactBox.grabFocus();
    }

    void muteButton_actionPerformed(ActionEvent evt) {
        int selectedRow=phoneFrame.getSelectedLine();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            phoneFrame.contactBox.grabFocus();
            return;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        if(inter==null) {
            phoneFrame.contactBox.grabFocus();
            return;
        }

        UserCallControlEvent commEvt = new UserCallControlEvent(inter);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleMuteRequest(
                commEvt);
        }
        phoneFrame.contactBox.grabFocus();
    }

    void fireExitRequest() {
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleExitRequest();
        }
    }

    void fireDebugToolLaunchRequest() {
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleDebugToolLaunch();
        }
    }


    /*
     * Add prefixes to phone number, strip out extraneous characters
     */
    private String format(String callee) {
	/*
	 * It's a softphone number.  Leave it as is.
	 */
	if (callee.indexOf("sip:") == 0) {
	    return callee;
	}

	if (callee.indexOf("@") > 0) {
	    /*
	     * It must be a sip number but the user left off sip:
	     */
	    return "sip:" + callee;
	}

        /*
         * Get rid of white space in the phone number
         */ 
        callee = callee.replaceAll("\\s", "");

        /*
         * Get rid of "-" in the phone number
         */
        callee = callee.replaceAll("-", "");

	if (callee.length() == 0) {
	    return null;
	}

	/*
	 * If the communicator is running standalone, leave the phone number as is.
	 */
	if (!SipCommunicator.fromMC()) {
	    return callee;
	}
	
        /*
	 * Sip communicator is running with Meeting Central.
	 * Prepend the phone number with the right prefixes.
	 *
         * Replace leading "+" (from namefinder) with appropriate numbers.
         * +1 is a US number and becomes 91.
         * +<anything else> is considered to be an international number and 
	 * becomes 9011.
         */
        if (callee.charAt(0) == '+') {
            if (callee.charAt(1) == '1') {
                callee = "9" + callee.substring(1);
            } else {
                callee = "9011" + callee.substring(1);
	    }
        } else if (callee.charAt(0) == 'x' ||
	        callee.charAt(0) == 'X') {

	    callee = callee.substring(1);
	}

        if (callee.length() > 7) {
            /*
             * it's an outside number
             */
            if (callee.length() == 10) {
		callee = "91-" + callee;
            } else if (callee.charAt(0) != '9') {
                if (callee.length() <= 11) {
        	    /*
                     * It's US or Canada, number needs 91.
                     */ 
                    if (callee.charAt(0) == '1') {
                        callee = "9-" + callee;
                    } else {
                        callee = "91-" + callee;
		    }
                } else {
                    /*
                     * It's international, number needs 9011
                     */ 
		    if (callee.substring(0,3).equals("011")) {
			/*
                         * 011 is already there, just prepend 9
                         */
                        callee = "9-" + callee;
		    } else {
                        callee = "9011-" + callee;
		    }
                }
            }
        }

        return callee;
    }

//============================== Configuration ==============================
    public void setProperties(Properties properties) {
        configFrame.properties.setProperties(properties);
        this.properties = properties;
    }

    private void configFrame_savePerformed(ActionEvent evt) {
        //check if properties are still being edited
        if (configFrame.propertiesTable.isEditing()) {
            configFrame.propertiesTable.getCellEditor().stopCellEditing();
        }
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).
                handlePropertiesSaveRequest();
        }
        configFrame.dispose();
    }

    public void setGlobalStatus(String statusCode, String reason) {
        if (statusCode == REGISTERED) {
            phoneFrame.registrationStatus.setForeground(SipCommunicatorColorTheme.REGISTERED);
	    int ix;
	    if ((ix = reason.indexOf(";")) != -1) {
		reason = reason.substring(0, ix) + ">";
	    }
            //phoneFrame.registrationStatus.setText(statusCode+" "+reason);
            //phoneFrame.registrationStatus.setToolTipText(statusCode+" "+reason);
	    if ((ix = reason.indexOf("<")) != -1) {
		reason = reason.substring(ix + 1);

	        if ((ix = reason.indexOf(">")) != -1) {
		    reason = reason.substring(0, ix);
		}
	    }
		
            phoneFrame.registrationStatus.setText(reason);
            phoneFrame.registrationStatus.setToolTipText(reason);
        }
        else if (statusCode == REGISTERING) {
            phoneFrame.registrationStatus.setForeground(SipCommunicatorColorTheme.REGISTERING);
            phoneFrame.registrationStatus.setText(statusCode+" "+reason);
            phoneFrame.registrationStatus.setToolTipText(statusCode+" "+reason);
        }
        else if (statusCode == NOT_REGISTERED) {
            phoneFrame.registrationStatus.setForeground(SipCommunicatorColorTheme.NOT_REGISTERED);
            phoneFrame.registrationStatus.setText(reason);
            phoneFrame.registrationStatus.setToolTipText(reason);
        }
        else if (statusCode == UNREGISTERING)
        {
            phoneFrame.registrationStatus.setForeground(SipCommunicatorColorTheme.NOT_REGISTERED);
            phoneFrame.registrationStatus.setText(statusCode + " " + reason);
            phoneFrame.registrationStatus.setToolTipText(statusCode + " " + reason);
        }
        else {
            phoneFrame.registrationStatus.setForeground(Color.red);
            phoneFrame.registrationStatus.setText(statusCode);
            phoneFrame.registrationStatus.setToolTipText(statusCode);
        }
    }

    public void setCallStatus(String statusCode) {
        phoneFrame.callStatus.setText(statusCode);
    }

//===================================== Action classes ===============================

    private class HistoryAction extends AbstractAction {


        public HistoryAction() {
            super("History");
        }

        public void actionPerformed(ActionEvent evt) {
            if(historySplash==null) {
                historySplash=new HistorySplash(phoneFrame,true);
            }

            historySplash.setVisible(true);

            if(historySplash.getNumber()!=null) {
                phoneFrame.contactBox.setText(historySplash.getNumber());
                phoneFrame.contactBox.setToolTipText(historySplash.getNumber());
            }

        }
    }

    private class RecordAction extends AbstractAction {
        public RecordAction() {
            super("Record");
        }

        public void actionPerformed(ActionEvent evt) {
            if(recordDialog == null) {
                recordDialog = new RecordDialog(phoneFrame, true);
            }

            recordDialog.setVisible(true);
        }
    }

    private class PlaybackAction extends AbstractAction {
        public PlaybackAction() {
            super("Playback");
        }

        public void actionPerformed(ActionEvent evt) {
            if (playbackDialog == null) {
                playbackDialog = new PlaybackDialog(phoneFrame, true);
            }

            playbackDialog.setVisible(true);
        }
    }

    private class ExitAction extends AbstractAction {
        public ExitAction() {
            super("Exit");
        }

        public void actionPerformed(ActionEvent evt) {
            fireExitRequest();
        }
    }

    private class ConfigAction extends AbstractAction {
        public ConfigAction() {
            super("Configure");
        }

        public void actionPerformed(ActionEvent evt) {
            new ConfigurationFrame();
            //configFrame.show();
        }
    }

    private class AudioQualityAction extends AbstractAction {
        public AudioQualityAction() {
            super("Audio Quality");
        }

        public void actionPerformed(ActionEvent evt) {
        }
    }

    private class PhoneAction extends AbstractAction {
        public PhoneAction() {
            super("Phone (8k/mono)");
        }

        public void actionPerformed(ActionEvent evt) {
            ( (MenuBar) phoneFrame.jMenuBar1).setAudioQuality(
		"8000", "1", "PCMU");
        }
    }

    private class VPNAction extends AbstractAction {
        public VPNAction() {
            super("VPN (16k/stereo)");
        }

        public void actionPerformed(ActionEvent evt) {
            ( (MenuBar) phoneFrame.jMenuBar1).setAudioQuality(
		"16000", "2", "PCM");
        }
    }

    private class CDAction extends AbstractAction {
        public CDAction() {
            super("CD (44.1k/stereo)");
        }

        public void actionPerformed(ActionEvent evt) {
            ( (MenuBar) phoneFrame.jMenuBar1).setAudioQuality(
		"44100", "2", "PCM");
        }
    }

    private class CustomAction extends AbstractAction implements 
	    MediaConfigDoneListener {

        public CustomAction() {
            super("Custom");
        }

        public void actionPerformed(ActionEvent evt) {
	    MediaConfigurationFrame frame = new MediaConfigurationFrame();
	   
	    frame.addListener(this);
        }

        public void mediaConfigDone(boolean isCancelled) {
	    ( (MenuBar) phoneFrame.jMenuBar1).selectCustom();
        }
    }


    private class AudioAction extends AbstractAction {
        public AudioAction() {
            super("Audio Control");
        }

        public void actionPerformed(ActionEvent evt) {
            try {
		if (LineTestConfig.startAudioConfigTool() == false) {
		    Logger.println("Unable to start audio config tool for os");
		}
            } catch (IOException e) {
		Logger.println("Error trying to start audio config program:  "
		    + e.getMessage());
            }
        }
    }

    private class VolumeAction extends AbstractAction {
        public VolumeAction() {
            super("Volume Control");
        }

        public void actionPerformed(ActionEvent evt) {
            if(volumeControl==null) {
                volumeControl=new VolumeControl();
            }
         
            if(volumeControl!=null) {
                volumeControl.setVisible(true);
            }
        }
    }

    private class ThreshAction extends AbstractAction {
        public ThreshAction() {
            super("Control offThresh and powerThresholdLimit");
        }

        public void actionPerformed(ActionEvent evt) {
            if (threshControl == null) {
                threshControl = new ThreshControl(
		    "Control offThresh and powerThresholdLimit");
            } 

	    threshControl.setVisible(true);	
        }
    }

    private class LpfAction extends AbstractAction {
        public LpfAction() {
            super("Low Pass Filter Control");
        }

        public void actionPerformed(ActionEvent evt) {
            if (lpfControl != null) {
		lpfControl.setVisible(false);
	    }

            lpfControl = new LpfControl(
		"Control Low Pass Filter and Volume");
         
            lpfControl.setVisible(true);
        }
    }

    private class LpfSliderAction extends AbstractAction {
        public LpfSliderAction() {
            super("Low Pass Filter Slider Control");
        }

        public void actionPerformed(ActionEvent evt) {
            if (lpfSliderControl != null) {
		lpfSliderControl.setVisible(false);
	    }

            lpfSliderControl = new LpfSliderControl();
            lpfSliderControl.setVisible(true);
        }
    }
    
    private class OnAction extends AbstractAction {
        public OnAction() {
            super("Control onCount and onThresh");
        }

        public void actionPerformed(ActionEvent evt) {
            if (onControl == null) {
                onControl = new OnControl(
		    "Control onThresh and cnThresh");
            }

	    onControl.setVisible(true);
        }
    }

    private class ShowTracesAction extends AbstractAction {
        public ShowTracesAction() {
            super("View Traces");
        }

        public void actionPerformed(ActionEvent action) {
            fireDebugToolLaunchRequest();
        }
    }

    private void initActionListeners() {
        ActionListener dialListener = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                dialButton_actionPerformed(evt);
            }
        };
        phoneFrame.dialButton.addActionListener(dialListener);


        MouseAdapter mouseListener = new MouseAdapter() {

            public void mouseEntered(MouseEvent event) {
                phoneFrame.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            public void mouseExited(MouseEvent event) {
                phoneFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }

            public void mousePressed(MouseEvent event) {
                JButton source=(JButton)event.getSource();
                String button=getButton(source);
                if(!startDtmf(button)) {
                    phoneFrame.contactBox.setText(phoneFrame.contactBox.getText()+button);
                    phoneFrame.contactBox.setToolTipText(phoneFrame.contactBox.getText());
                }
            }

            public void mouseReleased(MouseEvent event) {
                JButton source=(JButton)event.getSource();
                String button=getButton(source);
                if(!stopDtmf(button)) {
                }
                phoneFrame.contactBox.grabFocus();
            }

            private String getButton(JButton source) {
                String result="";
                if(source==phoneFrame.oneButton) {
                    result="1";
                } else if(source==phoneFrame.twoButton) {
                    result="2";
                } else if(source==phoneFrame.threeButton) {
                    result="3";
                } else if(source==phoneFrame.fourButton) {
                    result="4";
                } else if(source==phoneFrame.fiveButton) {
                    result="5";
                } else if(source==phoneFrame.sixButton) {
                    result="6";
                } else if(source==phoneFrame.sevenButton) {
                    result="7";
                } else if(source==phoneFrame.eightButton) {
                    result="8";
                } else if(source==phoneFrame.nineButton) {
                    result="9";
                } else if(source==phoneFrame.starButton) {
                    result="*";
                } else if(source==phoneFrame.zeroButton) {
                    result="0";
                } else if(source==phoneFrame.hashButton) {
                    result="#";
                }
                return result;
            }
	};

        phoneFrame.oneButton.addMouseListener(mouseListener);
        phoneFrame.twoButton.addMouseListener(mouseListener);
        phoneFrame.threeButton.addMouseListener(mouseListener);
        phoneFrame.fourButton.addMouseListener(mouseListener);
        phoneFrame.fiveButton.addMouseListener(mouseListener);
        phoneFrame.sixButton.addMouseListener(mouseListener);
        phoneFrame.sevenButton.addMouseListener(mouseListener);
        phoneFrame.eightButton.addMouseListener(mouseListener);
        phoneFrame.nineButton.addMouseListener(mouseListener);
        phoneFrame.starButton.addMouseListener(mouseListener);
        phoneFrame.zeroButton.addMouseListener(mouseListener);
        phoneFrame.hashButton.addMouseListener(mouseListener);

        KeyAdapter keyListener = new KeyAdapter() {
            public void keyPressed(KeyEvent event) {
                if(event.getKeyChar()!=KeyEvent.VK_ENTER) {
                    Character c=new Character(event.getKeyChar());
                    if(Character.isLetterOrDigit(c.charValue()) || c.toString().equals("#") || c.toString().equals("*")) {
                        if(startDtmf(c.toString())) {
                        }
                    }
                }
            }

            public void keyReleased(KeyEvent event) {
                if(event.getKeyChar()==KeyEvent.VK_ENTER) {
                    dialButton_actionPerformed(event);
                } else {
                    Character c=new Character(event.getKeyChar());
                    if(Character.isLetterOrDigit(c.charValue()) || c.toString().equals("#") || c.toString().equals("*")) {
                        if(stopDtmf(c.toString())) {
                        }
                    }
                }
            }

            public void keyTyped(KeyEvent event) {
                // if we have a connection the ignore the key (do not want DTMF to display)
                int selectedRow=phoneFrame.getSelectedLine();
                if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
                    return;
                }
                InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
                if(inter==null) {
                    return;
                }
                event.consume();
            }
        };

        phoneFrame.contactBox.addKeyListener(keyListener);

        ActionListener lineListener = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                JButton button=(JButton)(evt.getSource());
                if(button==phoneFrame.lineOneButton) {
                    if(phoneFrame.getSelectedLine()!=0) {
                        phoneFrame.setLineSelected(phoneFrame.getSelectedLine(),false);
                        phoneFrame.setLineSelected(0,true);
                        if(interlocutors.getInterlocutorAt(0)!=null) {
                            phoneFrame.contactBox.setText(interlocutors.getValueAt(0,1).toString());
                            phoneFrame.contactBox.setToolTipText(interlocutors.getValueAt(0,1).toString());
                            if("FAILED".equals(interlocutors.getValueAt(0,2).toString())) {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(0,2).toString()+": "+interlocutors.getValueAt(0,3).toString());
                            } else {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(0,2).toString());
                            }
                        } else {
                            phoneFrame.contactBox.setText("");
                            phoneFrame.contactBox.setToolTipText("");
                            phoneFrame.callStatus.setText("");
                        }
                    }
                } else if(button==phoneFrame.lineTwoButton) {
                    if(phoneFrame.getSelectedLine()!=1) {
                        phoneFrame.setLineSelected(phoneFrame.getSelectedLine(),false);
                        phoneFrame.setLineSelected(1,true);
                        if(interlocutors.getInterlocutorAt(1)!=null) {
                            phoneFrame.contactBox.setText(interlocutors.getValueAt(1,1).toString());
                            phoneFrame.contactBox.setToolTipText(interlocutors.getValueAt(1,1).toString());
                            if("FAILED".equals(interlocutors.getValueAt(0,2).toString())) {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(1,2).toString()+": "+interlocutors.getValueAt(1,3).toString());
                            } else {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(1,2).toString());
                            }
                        } else {
                            phoneFrame.contactBox.setText("");
                            phoneFrame.contactBox.setToolTipText("");
                            phoneFrame.callStatus.setText("");
                        }
                    }
                } else if(button==phoneFrame.lineThreeButton) {
                    if(phoneFrame.getSelectedLine()!=2) {
                        phoneFrame.setLineSelected(phoneFrame.getSelectedLine(),false);
                        phoneFrame.setLineSelected(2,true);
                        if(interlocutors.getInterlocutorAt(2)!=null) {
                            phoneFrame.contactBox.setText(interlocutors.getValueAt(2,1).toString());
                            phoneFrame.contactBox.setToolTipText(interlocutors.getValueAt(2,1).toString());
                            if("FAILED".equals(interlocutors.getValueAt(0,2).toString())) {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(2,2).toString()+": "+interlocutors.getValueAt(2,3).toString());
                            } else {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(2,2).toString());
                            }
                        } else {
                            phoneFrame.contactBox.setText("");
                            phoneFrame.contactBox.setToolTipText("");
                            phoneFrame.callStatus.setText("");
                        }
                    }
                } else if(button==phoneFrame.lineFourButton) {
                    if(phoneFrame.getSelectedLine()!=3) {
                        phoneFrame.setLineSelected(phoneFrame.getSelectedLine(),false);
                        phoneFrame.setLineSelected(3,true);
                        if(interlocutors.getInterlocutorAt(3)!=null) {
                            phoneFrame.contactBox.setText(interlocutors.getValueAt(3,1).toString());
                            phoneFrame.contactBox.setToolTipText(interlocutors.getValueAt(3,1).toString());
                            if("FAILED".equals(interlocutors.getValueAt(0,2).toString())) {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(3,2).toString()+": "+interlocutors.getValueAt(3,3).toString());
                            } else {
                                phoneFrame.callStatus.setText(interlocutors.getValueAt(3,2).toString());
                            }
                        } else {
                            phoneFrame.contactBox.setText("");
                            phoneFrame.contactBox.setToolTipText("");
                            phoneFrame.callStatus.setText("");
                        }
                    }
                }
                phoneFrame.contactBox.grabFocus();
            }
        };

        phoneFrame.lineOneButton.addActionListener(lineListener);
        phoneFrame.lineTwoButton.addActionListener(lineListener);
        phoneFrame.lineThreeButton.addActionListener(lineListener);
        phoneFrame.lineFourButton.addActionListener(lineListener);

        phoneFrame.answerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt)
            {
                answerButton_actionPerformed(evt);
            }
        });
        phoneFrame.hangupButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt)
            {
                hangupButton_actionPerformed(evt);
            }
        });

        phoneFrame.muteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt)
            {
                muteButton_actionPerformed(evt);
            }
        });

        phoneFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt)
            {
                fireExitRequest();
            }
        });
        configFrame.saveButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent evt)
            {
                configFrame_savePerformed(evt);
            }
        });
    }

    public void requestAuthentication(String realm,
                                      String userName,
                                      String authenticationUserName,
                                      char[] password)
    {
        if (authenticationSplash != null)
            authenticationSplash.dispose();
        authenticationSplash = new AuthenticationSplash(phoneFrame, true);
        if(userName != null) {
            authenticationSplash.userNameTextField.setText(userName);
	}
        if(authenticationUserName != null) {
            authenticationSplash.authenticationUserNameTextField.setText(authenticationUserName);
	}
        if(password != null)
            authenticationSplash.passwordTextField.setText(new String(password));
        //Set a relevant realm value
        //Bug report by Steven Lass (sltemp at comcast.net)
        if(realm != null)
            authenticationSplash.realmValueLabel.setText(new String(realm));
        authenticationSplash.setVisible(true);
    }

    public String getUserName()
    {
        return authenticationSplash.userName;
    }

    public String getAuthenticationUserName()
    {
        return authenticationSplash.authenticationUserName;
    }

    public char[] getAuthenticationPassword()
    {
        return authenticationSplash.password;
    }

    public void windowActivated(WindowEvent e) {
    }

    public void windowClosed(WindowEvent e) {
	System.out.println("Softphone is hidden");
        phoneFrame.setVisible(false);
    }

    public void windowClosing(WindowEvent e) {
    }

    public void windowDeactivated(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowOpened(WindowEvent e) {
    }

    public void showLineTest(MediaManager mediaManager) {
        if (lineTestConfig != null) {
            lineTestConfig.setVisible(true);
	    lineTestConfig.refreshDevices();
        } else {
            lineTestConfig = new LineTestConfig(mediaManager);
            lineTestConfig.addComponentListener(new ComponentAdapter() {
                public void componentHidden(ComponentEvent e) {
                    
                }
            });
        }
    }

    public void showNetworkInterfaceConfig() {
        if (networkInterfaceConfig != null) {
            networkInterfaceConfig.setVisible(true);
        } else {
            networkInterfaceConfig = new NetworkInterfaceConfig();
            networkInterfaceConfig.addComponentListener(new ComponentAdapter() {
                public void componentHidden(ComponentEvent e) {

                }
            });
        }
    }


}
