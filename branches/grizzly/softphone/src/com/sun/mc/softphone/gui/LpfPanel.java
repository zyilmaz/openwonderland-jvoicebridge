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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;

public class LpfPanel extends JLayeredPane
        implements MouseListener, MouseMotionListener {

    private LpfLabel currentLabel;
    
    private boolean dragging;
    private int hotX;
    private int hotY;
    
    private JPanel bgPanel;
    
    private LpfPanelListener listener;
    
    /** Creates a new instance of Main */
    public LpfPanel() {
        setLayout(null);
        
        bgPanel = new BGPanel();
        bgPanel.setBounds(0, 0, getWidth(), getHeight());
        add(bgPanel, JLayeredPane.DEFAULT_LAYER);
        
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent evt) {
                bgPanel.setBounds(0, 0, getWidth(), getHeight());
            
		if (currentLabel != null) {
		    currentLabel.sizeChanged(getWidth(), getHeight());
	        }
            }
        });
    }
    
    public void setLpfPanelListener(LpfPanelListener listener) {
        this.listener = listener;
    }
    
    public void addMember(LpfParameters lpfParameters) {

	if (currentLabel != null) {
	    return;
	}

        currentLabel = new LpfLabel(lpfParameters);
        
        currentLabel.sizeChanged(getWidth(), getHeight());
        currentLabel.addMouseListener(this);
        currentLabel.addMouseMotionListener(this);
        add(currentLabel, JLayeredPane.DRAG_LAYER);
    }
   
    public void removeMember(LpfParameters lpfParameters) {
        if (currentLabel != null) {
            currentLabel.removeMouseListener(this);
            currentLabel.removeMouseMotionListener(this);
            
            remove(currentLabel);
        
            repaint();
        }

	currentLabel = null;
    }
   
    public void setLpfParameters(LpfParameters lpfParameters) {
        if (currentLabel != null) {
            currentLabel.setLpfParameters(lpfParameters);
        }
    }
    
    public void mouseEntered(MouseEvent e) {
        if (!dragging) {
            currentLabel = (LpfLabel) e.getSource();
            
            repaint();
        }
    }

    public void mouseExited(MouseEvent e) {
        if (!dragging && currentLabel != null && 
            currentLabel.equals(e.getSource())) 
        {
            currentLabel = null;
        
            repaint();
        }
    }
    
    public void mouseDragged(MouseEvent e) {
        if (currentLabel != null) {
            int x = ((LpfLabel) e.getSource()).getX() - hotX;
            int y = ((LpfLabel) e.getSource()).getY() - hotY;
            
            currentLabel.moveTo(x + e.getX(), y + e.getY());
      
            repaint();
            
            // notify the listener
            if (listener != null) {
                listener.lpfParametersChanged(currentLabel.getLpfParameters());
            }
        }
    }

    public void mouseMoved(MouseEvent e) {
        // ignore
    }
    
    public void mouseClicked(MouseEvent e) {
        // ignore
    }

    public void mousePressed(MouseEvent e) {
        dragging = true;
        
        hotX = e.getX();
        hotY = e.getY();
    }

    public void mouseReleased(MouseEvent e) {
        dragging = false;
        
        repaint();
    }
    
    /** 
     * Draw the background
     */
    class BGPanel extends JPanel {
        public void paint(Graphics g) {
            int height = getHeight();
            int width = getWidth();
       
            // fill with white
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            
            // draw horizontal lines
            g.setColor(Color.RED);
            for (int i = 2; i < 10; i++) {
                double heightFactor = Math.log(i) / Math.log(10);
                int lineHeight = height - ((int) (heightFactor * height));
                g.drawLine(0, lineHeight, width, lineHeight);
            }
            
            // write the coordinates if dragging
            if (currentLabel != null) {
                String coords = "(" + currentLabel.getHotX() + "," +
                                currentLabel.getHotY() + ")";
                LpfParameters lpfParameters = currentLabel.getLpfParameters();
                String s = "Number of samples to Average = " + lpfParameters.nAvg + ", volume = " 
		    + lpfParameters.volume;
                 
                //g.drawString(coords + " = " + vols, 3, height - 3);
                g.drawString(s, 3, height - 3);
            }
        }
    }

}
