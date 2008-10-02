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
import java.util.Hashtable;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

import com.sun.mc.softphone.common.Utils;
import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.MediaManagerFactory;

class LpfSliderControl extends JFrame implements ChangeListener, ActionListener {

    JPanel lpfPanel=new JPanel();
    JLabel  lpfLabel;
    JLabel  lpfTypeLabel;
    JSlider lpfNAvg;
    JLabel  speakerLabel;
    JSlider speakerVolume;
    JButton closeButton;

    MediaManager mediaManager;

    public LpfSliderControl() {
        super("Low Pass Filter ");

	int nAvg = 0;

	String s = Utils.getPreference(
	    "com.sun.mc.softphone.media.MICROPHONE_LPF_NAVG");

	if (s != null && s.length()> 0) {
	    try {
	        nAvg = Integer.parseInt(s);
	    } catch (NumberFormatException e) {
	    }
	}

        lpfNAvg = new JSlider(JSlider.HORIZONTAL, 0, 10, nAvg);
        lpfPanel.setLayout(new GridLayout(3,1));
        lpfLabel=new JLabel(nAvg + " Sample Moving Average for Microphone", 
	    JLabel.CENTER);
        lpfLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        lpfPanel.add(lpfLabel);

        //lpfTypeLabel=new JLabel("For resampling", JLabel.CENTER);
        //lpfTypeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        //lpfPanel.add(lpfTypeLabel);

        lpfNAvg.addChangeListener(this);
        lpfNAvg.setMajorTickSpacing(10);
        lpfNAvg.setMinorTickSpacing(1);
        lpfNAvg.setPaintTicks(true);
	Hashtable labelTable = new Hashtable();
	labelTable.put( new Integer( 0 ), new JLabel("0") );
	labelTable.put( new Integer( 1 ), new JLabel("1") );
	labelTable.put( new Integer( 2 ), new JLabel("2") );
	labelTable.put( new Integer( 3 ), new JLabel("3") );
	labelTable.put( new Integer( 4 ), new JLabel("4") );
	labelTable.put( new Integer( 5 ), new JLabel("5") );
	labelTable.put( new Integer( 6 ), new JLabel("6") );
	labelTable.put( new Integer( 7 ), new JLabel("7") );
	labelTable.put( new Integer( 8 ), new JLabel("8") );
	labelTable.put( new Integer( 9 ), new JLabel("9") );
	labelTable.put( new Integer( 10 ), new JLabel("10") );
	lpfNAvg.setLabelTable( labelTable );
        lpfNAvg.setPaintLabels(true);
        lpfNAvg.setBorder(
                BorderFactory.createEmptyBorder(0,0,10,0));
        lpfPanel.add(lpfNAvg);

        mediaManager = MediaManagerFactory.getInstance();

        double volume = mediaManager.getSpeakerVolume();

if (false) {
        speakerLabel=new JLabel("Speaker Volume " + volume, JLabel.CENTER);
        speakerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        lpfPanel.add(speakerLabel);
        speakerVolume = new JSlider(JSlider.HORIZONTAL, 0, 50, 
	    (int)(volume * 10.0));
        speakerVolume.addChangeListener(this);
        speakerVolume.setMajorTickSpacing(10);
        speakerVolume.setMinorTickSpacing(1);
        speakerVolume.setPaintTicks(true);

	Hashtable speakerLabelTable = new Hashtable();
	speakerLabelTable.put( new Integer( 0 ), new JLabel("0") );
	speakerLabelTable.put( new Integer( 10 ), new JLabel("1") );
	speakerLabelTable.put( new Integer( 20 ), new JLabel("2") );
	speakerLabelTable.put( new Integer( 30 ), new JLabel("3") );
	speakerLabelTable.put( new Integer( 40 ), new JLabel("4") );
	speakerLabelTable.put( new Integer( 50 ), new JLabel("5") );

	speakerVolume.setLabelTable( speakerLabelTable );
        speakerVolume.setPaintLabels(true);
        speakerVolume.setBorder(
                BorderFactory.createEmptyBorder(0,0,10,0));
        lpfPanel.add(speakerVolume);
}
        this.getContentPane().add(lpfPanel);

        JPanel closePanel=new JPanel();
	closeButton=new JButton("Close");
	closeButton.addActionListener(this);
        closePanel.add(closeButton);
        lpfPanel.add(closePanel);
        this.pack();
        
    }

    
    public void stateChanged(ChangeEvent e) {
        JSlider source = (JSlider)e.getSource();
        int i = (int)source.getValue();

        if(source == lpfNAvg) {
	    String nAvg = String.valueOf(i);

            lpfLabel.setText(nAvg + " Sample Moving Average for for Microphone");

            Utils.setPreference(
		"com.sun.mc.softphone.media.MICROPHONE_LPF_NAVG", nAvg);
        } else {
if (false) {
	    double volume = i / 10.0F;

            mediaManager.setSpeakerVolume(volume);
            speakerLabel.setText("Speaker Volume " + volume);
}
        }
    }

    public void actionPerformed(ActionEvent e) {
        JButton source = (JButton)e.getSource();
        if(source==closeButton) {
            this.setVisible(false);
        }
    }

}
