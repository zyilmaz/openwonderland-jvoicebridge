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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.Point;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/*
 * Display a rectangle and allow user to move within the rectanagle.
 * The x and y coordinates are used to set whatever parameters
 * the caller wants.
 */
public class ParameterControl {

    private JFrame jf;
    private PCPanel pCPanel;

    public ParameterControl(String title, Parameter param) {
        pCPanel = new PCPanel(param);
        buildGUI(title);
    }
    
    private void buildGUI(String title) {
	if (title == null || title.length() == 0) {
	    title = "No Title";
	}

        jf = new JFrame(title);
        
        jf.getContentPane().add(pCPanel);
        
        jf.setSize(600, 200);
        jf.setVisible(true);

	jf.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    }

    public void addPCPanelListener(PCPanelListener listener) {
	pCPanel.addPCPanelListener(listener);
    }

    public void removePCPanelListener(PCPanelListener listener) {
	pCPanel.removePCPanelListener(listener);
    }

    public void setString(String s) {
	pCPanel.setString(s);
    }

    public int getWidth() {
	return pCPanel.getWidth() - 10;
    }

    public int getHeight() {
	return pCPanel.getHeight() - 10;
    }

    public void setVisible(boolean visible) {
        jf.setVisible(visible);
    }

}
