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

import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import javax.swing.*;

import com.sun.mc.softphone.common.Utils;
import com.sun.mc.softphone.gui.plaf.SipCommunicatorColorTheme;

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
class NewPhoneFrame
    extends JFrame
{
    JPanel dialPanel = new JPanel();
    JButton answerButton = new JButton();
    JButton hangupButton = new JButton();
    GridLayout gridLayout1 = new GridLayout();
    JButton dialButton = new JButton();
    JButton muteButton = new JButton();

    JButton lineOneSelected = new JButton();
    JButton lineOneConnected = new JButton();
    JButton lineOneButton = new JButton();
    JButton lineTwoSelected = new JButton();
    JButton lineTwoConnected = new JButton();
    JButton lineTwoButton = new JButton();
    JButton lineThreeSelected = new JButton();
    JButton lineThreeConnected = new JButton();
    JButton lineThreeButton = new JButton();
    JButton lineFourSelected = new JButton();
    JButton lineFourConnected = new JButton();
    JButton lineFourButton = new JButton();

    JButton oneButton=new JButton();
    JButton twoButton=new JButton();
    JButton threeButton=new JButton();
    JButton fourButton=new JButton();
    JButton fiveButton=new JButton();
    JButton sixButton=new JButton();
    JButton sevenButton=new JButton();
    JButton eightButton=new JButton();
    JButton nineButton=new JButton();
    JButton starButton=new JButton();
    JButton zeroButton=new JButton();
    JButton hashButton=new JButton();

    JTextField contactBox = new JTextField(15);

    JMenuBar jMenuBar1 = new com.sun.mc.softphone.gui.MenuBar();
    JLabel registrationStatus = new JLabel();

    JLabel callStatus = new JLabel();

    private NewGuiManager guiManCallback = null;

    public NewPhoneFrame(NewGuiManager guiManCallback) //throws HeadlessException
    {
        try {

            this.guiManCallback = guiManCallback;

            jbInit();
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            this.setBounds(0,0,310,470);
            int x = (toolkit.getScreenSize().width - this.getWidth()) / 2;
            int y = (toolkit.getScreenSize().height - this.getHeight()) / 2;
            this.setLocation(x, y);
            this.setResizable(false);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jbInit() throws Exception
    {
        this.getContentPane().setLayout(null);
        this.setJMenuBar(jMenuBar1);
        this.setResizable(true);
        this.setState(Frame.NORMAL);
        this.setTitle("Sun SIP Softphone");

        setupButton(answerButton,"answer.gif");
        answerButton.setEnabled(true);
        answerButton.setMnemonic('A');

        setupButton(hangupButton,"hangup.gif");
        hangupButton.setEnabled(true);
        hangupButton.setMnemonic('H');

        setupButton(dialButton,"dial.gif");
        dialButton.setEnabled(true);
        dialButton.setMnemonic('D');

        setupButton(muteButton,"mute.gif");
        muteButton.setEnabled(true);
        muteButton.setMnemonic('M');

        contactBox.setEditable(true);

        oneButton.setEnabled(true);
        setupButton(oneButton,"1.gif");
        twoButton.setEnabled(true);
        setupButton(twoButton,"2.gif");
        threeButton.setEnabled(true);
        setupButton(threeButton,"3.gif");
           setupLedButton(lineOneSelected,"red.gif");
           setupLedButton(lineOneConnected,"green.gif");
           lineOneSelected.setEnabled(true);
           lineOneConnected.setEnabled(false);
           lineOneButton.setEnabled(true);
           lineOneButton.setMnemonic('1');
           setupButton(lineOneButton,"line1.gif");

        fourButton.setEnabled(true);
        setupButton(fourButton,"4.gif");
        fiveButton.setEnabled(true);
        setupButton(fiveButton,"5.gif");
        sixButton.setEnabled(true);
        setupButton(sixButton,"6.gif");
           setupLedButton(lineTwoSelected,"red.gif");
           setupLedButton(lineTwoConnected,"green.gif");
           lineTwoSelected.setEnabled(false);
           lineTwoConnected.setEnabled(false);
           lineTwoButton.setEnabled(true);
           lineTwoButton.setMnemonic('2');
           setupButton(lineTwoButton,"line2.gif");

        sevenButton.setEnabled(true);
        setupButton(sevenButton,"7.gif");
        eightButton.setEnabled(true);
        setupButton(eightButton,"8.gif");
        nineButton.setEnabled(true);
        setupButton(nineButton,"9.gif");
           setupLedButton(lineThreeSelected,"red.gif");
           setupLedButton(lineThreeConnected,"green.gif");
           lineThreeSelected.setEnabled(false);
           lineThreeConnected.setEnabled(false);
           lineThreeButton.setEnabled(true);
           lineThreeButton.setMnemonic('3');
           setupButton(lineThreeButton,"line3.gif");

        starButton.setEnabled(true);
        setupButton(starButton,"star.gif");
        zeroButton.setEnabled(true);
        setupButton(zeroButton,"0.gif");
        hashButton.setEnabled(true);
        setupButton(hashButton,"hash.gif");
           setupLedButton(lineFourSelected,"red.gif");
           setupLedButton(lineFourConnected,"green.gif");
           lineFourSelected.setEnabled(false);
           lineFourConnected.setEnabled(false);
           lineFourButton.setEnabled(true);
           lineFourButton.setMnemonic('4');
           setupButton(lineFourButton,"line4.gif");


        // layout the window
        dialPanel.setLayout(null);
        dialPanel.setBackground(new Color(180,180,210));
        contactBox.setBounds(10,5,220,32);
        dialPanel.add(contactBox);
        dialButton.setBounds(235,10,60,22);
        dialPanel.add(dialButton);

        callStatus.setBounds(10,42,250,22);
        dialPanel.add(callStatus);

        oneButton.setBounds(10,70,60,60);
        dialPanel.add(oneButton);
        twoButton.setBounds(80,70,60,60);
        dialPanel.add(twoButton);
        threeButton.setBounds(150,70,60,60);
        dialPanel.add(threeButton);
        lineOneSelected.setBounds(221,84,8,8);
        dialPanel.add(lineOneSelected);
        lineOneConnected.setBounds(221,112,8,8);
        dialPanel.add(lineOneConnected);
        lineOneButton.setBounds(240,75,50,50);
        dialPanel.add(lineOneButton);

        fourButton.setBounds(10,140,60,60);
        dialPanel.add(fourButton);
        fiveButton.setBounds(80,140,60,60);
        dialPanel.add(fiveButton);
        sixButton.setBounds(150,140,60,60);
        dialPanel.add(sixButton);
        lineTwoSelected.setBounds(221,154,8,8);
        dialPanel.add(lineTwoSelected);
        lineTwoConnected.setBounds(221,182,8,8);
        dialPanel.add(lineTwoConnected);
        lineTwoButton.setBounds(240,145,50,50);
        dialPanel.add(lineTwoButton);

        sevenButton.setBounds(10,210,60,60);
        dialPanel.add(sevenButton);
        eightButton.setBounds(80,210,60,60);
        dialPanel.add(eightButton);
        nineButton.setBounds(150,210,60,60);
        dialPanel.add(nineButton);
        lineThreeSelected.setBounds(221,224,8,8);
        dialPanel.add(lineThreeSelected);
        lineThreeConnected.setBounds(221,252,8,8);
        dialPanel.add(lineThreeConnected);
        lineThreeButton.setBounds(240,215,50,50);
        dialPanel.add(lineThreeButton);

        starButton.setBounds(10,280,60,60);
        dialPanel.add(starButton);
        zeroButton.setBounds(80,280,60,60);
        dialPanel.add(zeroButton);
        hashButton.setBounds(150,280,60,60);
        dialPanel.add(hashButton);
        lineFourSelected.setBounds(221,294,8,8);
        dialPanel.add(lineFourSelected);
        lineFourConnected.setBounds(221,322,8,8);
        dialPanel.add(lineFourConnected);
        lineFourButton.setBounds(240,285,50,50);
        dialPanel.add(lineFourButton);


        answerButton.setBounds(10,355,110,40);
        dialPanel.add(answerButton);

        muteButton.setBounds(130,355,40,40);
        dialPanel.add(muteButton);

        hangupButton.setBounds(180,355,110,40);
        dialPanel.add(hangupButton);


        registrationStatus.setBounds(10,398,250,22);
        dialPanel.add(registrationStatus);
       
        dialPanel.setBounds(0,0,310,430);
        this.getContentPane().add(dialPanel);

        registrationStatus.setForeground(SipCommunicatorColorTheme.NOT_REGISTERED);
        registrationStatus.setText("Not Registered");
    }

    private void setupButton(JButton button,String image) {
        try
        {
            button.setSize(50,50);
            button.setBackground(Color.white);
            button.setForeground(Color.white);
            button.setBorderPainted(false);
            button.setFocusPainted(false);

            URL iURL = Utils.getResource(image);
            if (iURL != null) {
                ImageIcon iIcon=new ImageIcon(iURL);
                button.setIcon(iIcon);
                button.setSize(iIcon.getIconWidth(),iIcon.getIconHeight());
            } else {
                System.out.println("cannot load "+image);
                button.setText("?");
            }
            URL pURL = Utils.getResource("pressed-"+image);
            if (pURL != null) {
                ImageIcon pIcon=new ImageIcon(pURL);
                button.setPressedIcon(pIcon);
            }
        } catch (Exception e) {
            System.out.println("cannot load "+image+": "+e.getMessage());
            button.setText("?");
        }
    }

    private void setupLedButton(JButton button,String image) {
        try
        {
            button.setSize(8,8);
            button.setBackground(Color.white);
            button.setForeground(Color.white);
            button.setBorderPainted(false);
            button.setFocusPainted(false);

            URL iURL = Utils.getResource(image);
            if (iURL != null) {
                ImageIcon iIcon=new ImageIcon(iURL);
                button.setIcon(iIcon);
                button.setSize(iIcon.getIconWidth(),iIcon.getIconHeight());
            } else {
                System.out.println("cannot load "+image);
                button.setText("?");
            }
            URL pURL = Utils.getResource("gray.gif");
            if (pURL != null) {
                ImageIcon pIcon=new ImageIcon(pURL);
                button.setDisabledIcon(pIcon);
            }
        } catch (Exception e) {
            System.out.println("cannot load "+image+": "+e.getMessage());
            button.setText("?");
        }
    }

    public void setLineSelected(int line,boolean state) {
        switch(line) {
            case 0: lineOneSelected.setEnabled(state); break;
            case 1: lineTwoSelected.setEnabled(state); break;
            case 2: lineThreeSelected.setEnabled(state); break;
            case 3: lineFourSelected.setEnabled(state); break;
            default: return;
        }
    }

    public void setLineConnected(int line,boolean state) {
        switch(line) {
            case 0: lineOneConnected.setEnabled(state); break;
            case 1: lineTwoConnected.setEnabled(state); break;
            case 2: lineThreeConnected.setEnabled(state); break;
            case 3: lineFourConnected.setEnabled(state); break;
            default: return;
        }
        if(flasher[line]!=null) {
            flasher[line].stopFlashing();
            flasher[line]=null;
        }
    }

    public boolean isLineConnected(int line) {
        boolean result=false;
        switch(line) {
            case 0: result = lineOneConnected.isEnabled(); break;
            case 1: result = lineTwoConnected.isEnabled(); break;
            case 2: result = lineThreeConnected.isEnabled(); break;
            case 3: result = lineFourConnected.isEnabled(); break;
            default: return false;
        }
        return result;
    }

    public Flasher[] flasher=new Flasher[4];

    public void setLineAlerting(int line) {
        switch(line) {
            case 0: flasher[line]=new Flasher(lineOneConnected); break;
            case 1: flasher[line]=new Flasher(lineTwoConnected); break;
            case 2: flasher[line]=new Flasher(lineThreeConnected); break;
            case 3: flasher[line]=new Flasher(lineFourConnected); break;
            default: return;
        }
    }

    public int getSelectedLine() {
       int i=-1;
       if(lineOneSelected.isEnabled()) {
           i=0;
       } else if(lineTwoSelected.isEnabled()) {
           i=1;
       } else if(lineThreeSelected.isEnabled()) {
           i=2;
       } else if(lineFourSelected.isEnabled()) {
           i=3;
       }
       return i;
    }

    public void muted(boolean isMuted) {
        if(isMuted) {
            setupButton(muteButton,"muted.gif");
        } else {
            setupButton(muteButton,"mute.gif");
        }
    }

    class Flasher extends Thread {
        JButton button;
        boolean flash=true;
        Flasher(JButton button) {
            this.button=button;
            this.start();
        }

        public void stopFlashing() {
            flash=false;
        }

        public void run() {
            boolean state=true;
            while(flash) {
                button.setEnabled(state);
                try {
                    sleep(250);
                } catch(InterruptedException e) {
                }
                state=!state;
            }
        }

      
    }

}
