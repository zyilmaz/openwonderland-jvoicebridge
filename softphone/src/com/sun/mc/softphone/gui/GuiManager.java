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
import com.sun.mc.softphone.gui.event.*;
import com.sun.mc.softphone.media.MediaManager;
import java.awt.SystemColor;
import javax.swing.plaf.metal.MetalLookAndFeel;
import com.sun.mc.softphone.gui.plaf.SipCommunicatorColorTheme;
import java.awt.event.KeyEvent;
//import java.io.*;
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
public class GuiManager implements GuiManagerUI, GuiCallback {

    private static Console console = Console.getConsole(GuiManager.class);
    //Global status codes
    public static final String NOT_REGISTERED = "Not Registered";
    public static final String UNREGISTERING = "Unregistering, please wait!";
    public static final String REGISTERING = "Trying to register as:";
    public static final String REGISTERED = "Registered as ";

    private PhoneFrame      phoneFrame   = null;
    private ConfigFrame     configFrame  = null;
    private DtmfPad         dtmfPad      = null;
    private ArrayList       listeners    = null;
    private AlertManager    alertManager = null;
    private Properties      properties;
    private JLabel          logoLabel    = null;
    private InterlocutorsTableModel interlocutors = null;
    //Set default colors
    static Color defaultBackground = new Color(255, 255, 255);

    private AuthenticationSplash authenticationSplash = null;

    public GuiManager(Boolean visibleUI) {
	if (visibleUI.booleanValue()) {
            initLookAndFeel();
	}

        //create actions first for they are used by others

	phoneFrame    = new PhoneFrame(this);
	configFrame   = new ConfigFrame();
        dtmfPad       = new DtmfPad();

	listeners     = new ArrayList();
	alertManager  = new AlertManager();

        logoLabel     = new JLabel();
        interlocutors = new InterlocutorsTableModel();

        initActionListeners();
        phoneFrame.contactBox.setModel(new ContactsComboBoxModel());
 	( (MenuBar) phoneFrame.jMenuBar1).addConfigCallAction(new ConfigAction());
        ( (MenuBar) phoneFrame.jMenuBar1).addDtmfCallAction(new DtmfAction());
        ( (MenuBar) phoneFrame.jMenuBar1).addExitCallAction(new ExitAction());
	configFrame.setLocationRelativeTo(phoneFrame);
        dtmfPad.setGuiCallback(this);
        dtmfPad.setLocationRelativeTo(phoneFrame);
        phoneFrame.participantsTable.setModel(interlocutors);
        phoneFrame.setIconImage(new ImageIcon(Utils.getResource("sip-communicator-16x16.jpg")).getImage());
        logoLabel.setIcon(new ImageIcon(Utils.getResource("sip-communicator.jpg")));
        phoneFrame.videoPane.setBackground(Color.white);
        phoneFrame.videoPane.add(logoLabel);
        setGlobalStatus(NOT_REGISTERED, "");
        JOptionPane.setRootFrame(phoneFrame);
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
        new com.sun.mc.softphone.gui.config.ConfigurationFrame();
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
	} else {
            phoneFrame.setVisible(false);
	}
    }

    public void addVisualComponent(Component vComp) {
        if (vComp == null) {
            return;
        }
        else {
            phoneFrame.videoPane.remove(logoLabel);
            phoneFrame.videoPane.add(vComp);
        }
        phoneFrame.videoPane.updateUI();
    }

    public void addControlComponent(Component cComp) {
        if (cComp == null) {
            return;
        }

	phoneFrame.videoPane.remove(logoLabel);
        phoneFrame.videoPane.add(cComp);
        phoneFrame.videoPane.updateUI();
    }

    public void removePlayerComponents() {
        phoneFrame.videoPane.removeAll();
        phoneFrame.videoPane.add(logoLabel);
        phoneFrame.videoPane.updateUI();
    }

    public void addInterlocutor(InterlocutorUI interlocutor) {
        interlocutor.setCallback(this);
        interlocutors.addInterlocutor(interlocutor);
        phoneFrame.participantsTable.
            setRowSelectionInterval(interlocutors.findIndex(interlocutor.getID()),
                                    interlocutors.findIndex(interlocutor.getID()));
    }

    public void setCommunicationActionsEnabled(boolean enabled) {
        phoneFrame.dialButton.setEnabled(enabled);
        phoneFrame.hangupButton.setEnabled(enabled);
        phoneFrame.muteButton.setEnabled(enabled);
        phoneFrame.answerButton.setEnabled(enabled);
    }

    public void addUserActionListener(UserActionListener l) {
        listeners.add(l);
    }

    public void removeUserActionListener(UserActionListener l) {
        listeners.remove(l);
    }

    public void setPhoneNumber(String phoneNumber) {
    }

//-------------------------- GuiCallback --------------------
    public void update(InterlocutorUI interlocutorUI) {
        interlocutors.update(interlocutorUI);
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
            console.warn("Couldn't sotp alert", ex);
        }
    }

    //----------- DTMF
    public boolean startDtmf(String key) {
        //System.out.println("GuiManager.startDtmf");
        if (interlocutors.getRowCount() < 1) {
            return false;
        }
        int selectedRow = phoneFrame.participantsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            return false;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        UserDtmfEvent dtmfEvt = new UserDtmfEvent(inter,true,key);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleDtmfRequest(dtmfEvt);
        }
        return true;
    }

    public boolean stopDtmf(String key) {
        //System.out.println("GuiManager.stopDtmf");
        if (interlocutors.getRowCount() < 1) {
            return false;
        }
        int selectedRow = phoneFrame.participantsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            return false;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        UserDtmfEvent dtmfEvt = new UserDtmfEvent(inter,false,key);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleDtmfRequest(dtmfEvt);
        }
        return true;
    }

    //----------- Mute
    public void muted(boolean isMuted) {
        phoneFrame.muted(isMuted);
    }

//----------------- Event dispatching------------------------
    void dialButton_actionPerformed(EventObject evt) {
        //TODO temporarily close alerts from here.
        alertManager.stopAllAlerts();
        String callee = phoneFrame.contactBox.getSelectedItem().toString();
        if (callee == null || callee.trim().length() < 1) {
            return;
        }
	
	callee = format(callee);

        UserCallInitiationEvent commEvt = new UserCallInitiationEvent(callee);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleDialRequest(commEvt);
        }
    }

    void hangupButton_actionPerformed(ActionEvent evt) {
        //TODO temporarily close alerts from here.
        alertManager.stopAllAlerts();
        if (interlocutors.getRowCount() < 1) {
            return;
        }
        int selectedRow = phoneFrame.participantsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            return;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        UserCallControlEvent commEvt = new UserCallControlEvent(inter);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleHangupRequest(
                commEvt);
        }
    }

    void muteButton_actionPerformed(ActionEvent evt) {
        if (interlocutors.getRowCount() < 1) {
            return;
        }
        int selectedRow = phoneFrame.participantsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            return;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        UserCallControlEvent commEvt = new UserCallControlEvent(inter);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleMuteRequest(
                 commEvt);
        }
    }

    void answerButton_actionPerformed(ActionEvent evt) {
        //TODO temporarily close alerts from here.
        alertManager.stopAllAlerts();
        if (interlocutors.getRowCount() < 1) {
            return;
        }
        int selectedRow = phoneFrame.participantsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow > interlocutors.getRowCount() - 1) {
            return;
        }
        InterlocutorUI inter = interlocutors.getInterlocutorAt(selectedRow);
        UserCallControlEvent commEvt = new UserCallControlEvent(inter);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            ( (UserActionListener) listeners.get(i)).handleAnswerRequest(
                commEvt);
        }
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
    private String format(String phoneNumber) {
	/*
	 * It's a softphone number.  Leave it as is.
	 */
	if (phoneNumber.indexOf("sip:") == 0) {
	    return phoneNumber;
	}

	if (phoneNumber.indexOf("@") > 0) {
	    /*
	     * It must be a sip number but the user left off sip:
	     */
	    return "sip:" + phoneNumber;
	}

        /*
         * Get rid of white space in the phone number
         */ 
        phoneNumber = phoneNumber.replaceAll("\\s", "");

        /*
         * Get rid of "-" in the phone number
         */
        phoneNumber = phoneNumber.replaceAll("-", "");

	if (phoneNumber.length() == 0) {
	    return null;
	}

	/*
	 * If the communicator is running standalone, leave the phone number as is.
	 */
	//if (!SipCommunicator.fromMC()) {
	//    return phoneNumber;
	//}

	
        /*
	 * Sip communicator is running with Meeting Central.
	 * Prepend the phone number with the right prefixes.
	 *
         * Replace leading "+" (from namefinder) with appropriate numbers.
         * +1 is a US number and becomes 91.
         * +<anything else> is considered to be an international number and 
	 * becomes 9011.
         */
        if (phoneNumber.charAt(0) == '+') {
            if (phoneNumber.charAt(1) == '1') {
                phoneNumber = "9" + phoneNumber.substring(1);
            } else {
                phoneNumber = "9011" + phoneNumber.substring(1);
	    }
        } else if (phoneNumber.charAt(0) == 'x' ||
	        phoneNumber.charAt(0) == 'X') {

	    phoneNumber = phoneNumber.substring(1);
        } else if (phoneNumber.length() > 7) {
            /*
             * It's an outside number
             *
             * XXX No idea what lengths of 8 and 9 would be for...
             */
            if (phoneNumber.length() == 10) {
                /*
                 * It's US or Canada, number needs 91
                 */
                phoneNumber = "91-" + phoneNumber;
            } else if (phoneNumber.length() >= 11) {
                /*
                 * If it starts with 9 or 1, it's US or Canada.
                 * Otherwise, it's international.
                 */
                if (phoneNumber.length() == 11 &&
                        phoneNumber.charAt(0) == '1') {

                    phoneNumber = "9-" + phoneNumber;
                } else if (phoneNumber.length() == 11 &&
                        phoneNumber.charAt(0) == '9') {

                    phoneNumber = "91-" + phoneNumber.substring(1);
                } else if (phoneNumber.length() == 12 &&
                        phoneNumber.substring(0,2).equals("91")) {
                    // nothing to do
                } else {
                    /*
                     * It's international, number needs 9011
                     */
                    if (phoneNumber.substring(0,3).equals("011")) {
                        /*
                         * 011 is already there, just prepend 9
                         */
                         phoneNumber = "9-" + phoneNumber;
                    } else if (!phoneNumber.substring(0,4).equals("9011")) {
                        phoneNumber = "9011-" + phoneNumber;
                    }
                }
            }
        }

        return phoneNumber;
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
            phoneFrame.registrationLabel.setForeground(SipCommunicatorColorTheme.REGISTERED);
            phoneFrame.registrationLabel.setText(statusCode);
            phoneFrame.registrationAddressLabel.setForeground(SipCommunicatorColorTheme.REGISTERED);
	    int ix;

	    if ((ix = reason.indexOf(";")) != -1) {
		reason = reason.substring(0, ix) + ">";
	    }
            phoneFrame.registrationAddressLabel.setText(reason);
        }
        else if (statusCode == REGISTERING) {
            phoneFrame.registrationLabel.setForeground(SipCommunicatorColorTheme.REGISTERING);
            phoneFrame.registrationLabel.setText(statusCode);
            phoneFrame.registrationAddressLabel.setForeground(SipCommunicatorColorTheme.REGISTERING);
            phoneFrame.registrationAddressLabel.setText(reason);
        }
        else if (statusCode == NOT_REGISTERED) {
            phoneFrame.registrationLabel.setForeground(SipCommunicatorColorTheme.NOT_REGISTERED);
            phoneFrame.registrationLabel.setText(statusCode + " ");
            phoneFrame.registrationAddressLabel.setForeground(SipCommunicatorColorTheme.NOT_REGISTERED);
            phoneFrame.registrationAddressLabel.setText(reason);
        }
        else if (statusCode == UNREGISTERING)
        {
            phoneFrame.registrationLabel.setForeground(SipCommunicatorColorTheme.NOT_REGISTERED);
            phoneFrame.registrationLabel.setText(statusCode + " ");
            phoneFrame.registrationAddressLabel.setForeground(SipCommunicatorColorTheme.NOT_REGISTERED);
            phoneFrame.registrationAddressLabel.setText(reason);
        }
        else {
            phoneFrame.registrationLabel.setForeground(Color.red);
            phoneFrame.registrationLabel.setText(statusCode);
        }
    }

    /* dummy as added to NewGuiManager */
    public void setCallStatus(String statusCode) {
    }

//===================================== Action classes ===============================

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
            new com.sun.mc.softphone.gui.config.ConfigurationFrame();
            //configFrame.show();
        }
    }

    private class DtmfAction
        extends AbstractAction
    {
        public DtmfAction()
        {
            super("DTMF Pad");
        }

        public void actionPerformed(ActionEvent evt)
        {
            dtmfPad.setVisible(true);
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
        phoneFrame.contactBox.addItemListener(new ContactBoxListener());

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

    private class ContactBoxListener implements ItemListener {
        public void itemStateChanged(ItemEvent evt)
        {
            if ( ( (DefaultComboBoxModel) phoneFrame.contactBox.getModel()).
                getIndexOf(evt.getItem()) == -1) {
                ( (DefaultComboBoxModel) phoneFrame.contactBox.getModel()).
                    addElement(evt.getItem().toString().trim());
            }
        }
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

    public void showLineTest(MediaManager mediaManager) {
    }

    public void showNetworkInterfaceConfig() {
    }

}
