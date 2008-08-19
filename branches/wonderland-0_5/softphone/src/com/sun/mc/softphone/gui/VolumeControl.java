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

import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.MediaManagerFactory;

class VolumeControl extends JFrame implements ChangeListener, ActionListener {

    JPanel volumePanel=new JPanel();
    JLabel  microphoneLabel;
    JSlider microphoneVolume;
    JLabel  speakerLabel;
    JSlider speakerVolume;
    JButton closeButton;

    MediaManager mediaManager;

    public VolumeControl() {

        super("Volume Control");

        volumePanel.setLayout(new GridLayout(5,1));

        mediaManager = MediaManagerFactory.getInstance();

	double volume = mediaManager.getMicrophoneVolume();

        microphoneVolume = new JSlider(JSlider.HORIZONTAL, 0, 50, 
	    (int) (volume * 10.0));
        microphoneLabel=new JLabel("Microphone Volume " + volume, 
	    JLabel.CENTER);
        microphoneLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        volumePanel.add(microphoneLabel);
        microphoneVolume.addChangeListener(this);
        microphoneVolume.setMajorTickSpacing(10);
        microphoneVolume.setMinorTickSpacing(1);
        microphoneVolume.setPaintTicks(true);
	Hashtable labelTable = new Hashtable();
	labelTable.put( new Integer( 0 ), new JLabel("0") );
	labelTable.put( new Integer( 10 ), new JLabel("1") );
	labelTable.put( new Integer( 20 ), new JLabel("2") );
	labelTable.put( new Integer( 30 ), new JLabel("3") );
	labelTable.put( new Integer( 40 ), new JLabel("4") );
	labelTable.put( new Integer( 50 ), new JLabel("5") );
	microphoneVolume.setLabelTable( labelTable );
        microphoneVolume.setPaintLabels(true);
        microphoneVolume.setBorder(
                BorderFactory.createEmptyBorder(0,0,10,0));
        volumePanel.add(microphoneVolume);

        volume = mediaManager.getSpeakerVolume();

        speakerVolume = new JSlider(JSlider.HORIZONTAL, 0, 50, 
	    (int) (volume * 10.0));
        speakerLabel=new JLabel("Speaker Volume " + volume, JLabel.CENTER);
        speakerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        volumePanel.add(speakerLabel);
        speakerVolume.addChangeListener(this);
        speakerVolume.setMajorTickSpacing(10);
        speakerVolume.setMinorTickSpacing(1);
        speakerVolume.setPaintTicks(true);
	speakerVolume.setLabelTable( labelTable );
        speakerVolume.setPaintLabels(true);
        speakerVolume.setBorder(
                BorderFactory.createEmptyBorder(0,0,10,0));
        volumePanel.add(speakerVolume);
        this.getContentPane().add(volumePanel);

        JPanel closePanel=new JPanel();
	closeButton=new JButton("Close");
	closeButton.addActionListener(this);
        closePanel.add(closeButton);
        volumePanel.add(closePanel);
        this.pack();
        
    }

    
    public void stateChanged(ChangeEvent e) {
        JSlider source = (JSlider)e.getSource();
        double volume = source.getValue() / 10.0F;
        if(source == microphoneVolume) {
	    microphoneLabel.setText("Microphone Volume " + volume);
            mediaManager.setMicrophoneVolume(volume);
        } else {
	    speakerLabel.setText("Speaker Volume " + volume);
            mediaManager.setSpeakerVolume(volume);
        }
    }

    public void actionPerformed(ActionEvent e) {
        JButton source = (JButton)e.getSource();
        if(source==closeButton) {
            this.setVisible(false);
        }
    }

}
