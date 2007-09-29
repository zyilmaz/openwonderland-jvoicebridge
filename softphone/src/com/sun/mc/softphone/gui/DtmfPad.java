/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package com.sun.mc.softphone.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

import com.sun.mc.softphone.gui.GuiCallback;

class DtmfPad extends JFrame {

    GuiCallback guiCallback;

    JPanel dtmfPanel=new JPanel();
    JTextField dtmfList=new JTextField(14);
    JButton clearButton=new JButton();

    JPanel keyPanel=new JPanel();
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

    private ProcessMouse processMouse=new ProcessMouse();

    public DtmfPad() {

        super("DTMF Pad");

        dtmfPanel.setLayout(new BorderLayout());
        dtmfList.setEditable(false);
        dtmfPanel.add(dtmfList,BorderLayout.NORTH);
        clearButton.setText("Clear");
        clearButton.setToolTipText("Clear DTMF Pad text");
        clearButton.addActionListener(new ActionListener() {
                                          public void actionPerformed(ActionEvent event) {
                                              dtmfList.setText("");
                                          }
                                      });
        dtmfPanel.add(clearButton,BorderLayout.CENTER);
        keyPanel.setLayout(new GridLayout(4,3));
        setupButton(oneButton,"1","Generate DTMF tone for 1");
        keyPanel.add(oneButton);
        setupButton(twoButton,"2 abc","Generate DTMF tone for 2");
        keyPanel.add(twoButton);
        setupButton(threeButton,"3 def","Generate DTMF tone for 3");
        keyPanel.add(threeButton);
        setupButton(fourButton,"4 ghi","Generate DTMF tone for 4");
        keyPanel.add(fourButton);
        setupButton(fiveButton,"5 jkl","Generate DTMF tone for 5");
        keyPanel.add(fiveButton);
        setupButton(sixButton,"6 mno","Generate DTMF tone for 6");
        keyPanel.add(sixButton);
        setupButton(sevenButton,"7 pqrs","Generate DTMF tone for 7");
        keyPanel.add(sevenButton);
        setupButton(eightButton,"8 tuv","Generate DTMF tone for 8");
        keyPanel.add(eightButton);
        setupButton(nineButton,"9 wxyz","Generate DTMF tone for 9");
        keyPanel.add(nineButton);
        setupButton(starButton,"*","Generate DTMF tone for *");
        keyPanel.add(starButton);
        setupButton(zeroButton,"0","Generate DTMF tone for 0");
        keyPanel.add(zeroButton);
        setupButton(hashButton,"#","Generate DTMF tone for #");
        keyPanel.add(hashButton);
        dtmfPanel.add(keyPanel,BorderLayout.SOUTH);

        this.getContentPane().add(dtmfPanel);

        this.pack();
        
    }

    public void setGuiCallback(GuiCallback guiCallback) {
        this.guiCallback=guiCallback;
    }

    private void setupButton(JButton button,String text,String tip) {
        button.setToolTipText(tip);
        button.setText(text);
        button.addMouseListener(processMouse);
    }

    class ProcessMouse extends MouseAdapter {

        public void mouseExited(MouseEvent event) {
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }

        public void mouseEntered(MouseEvent event) {
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        public void mousePressed(MouseEvent event) {
            // System.out.println("DtmfPad.mousePressed");
            JButton source=(JButton)event.getSource();
            dtmfList.setText(dtmfList.getText()+source.getText().substring(0,1));
            if(guiCallback!=null) {
                guiCallback.startDtmf(source.getText());
            }
        }

        public void mouseReleased(MouseEvent event) {
            // System.out.println("DtmfPad.mouseReleased");
            JButton source=(JButton)event.getSource();
            if(guiCallback!=null) {
                guiCallback.stopDtmf(source.getText().substring(0,1));
            }
        }

    }

}
