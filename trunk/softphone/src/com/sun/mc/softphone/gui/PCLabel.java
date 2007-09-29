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
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import javax.swing.JLabel;
import javax.swing.border.AbstractBorder;

public class PCLabel extends JLabel {
    private int parentWidth;
    private int parentHeight;

    private Parameter param;

    /** Creates a new instance of PCLabel */
    public PCLabel(int parentWidth, int parentHeight, Parameter param) {
        super ("X");

	this.param = param;

        sizeChanged(parentWidth, parentHeight);

        setBorder(new HotSpotBorder());
    }
    
    public void sizeChanged(int parentWidth, int parentHeight) {
        this.parentWidth = parentWidth - 10;
        this.parentHeight = parentHeight - 10;
       
        int width = getPreferredSize().width;
        int height = getPreferredSize().height;

	int xIncr = (int)(parentWidth / (param.maxX / param.xParameterIncr));

	if (xIncr == 0) {
	    xIncr = 1;
	}

	int yIncr = (int)(parentHeight / (param.maxY / param.yParameterIncr));

	if (yIncr == 0) {
	    yIncr = 1;
	}

	int x = (int)((param.initialXParameter - param.minX) * parentWidth / (param.maxX - param.minX)); // / param.xParameterIncr));
	int y = (int)((param.initialYParameter - param.minY) * parentHeight / (param.maxY - param.minY)); // / param.yParameterIncr));

	y = parentHeight - y;

	//System.out.println("init x " + param.initialXParameter + " init y " 
	//    + param.initialYParameter
	//    + " w = " + parentWidth + " h = " + parentHeight
	//    + " x = " + x + " y = " + y);

        setBounds(x, y, width, height);

	moveTo(x, y);
    }
    
    public void moveTo(int x, int y) {
	if (x < 0) {
	    x = 0;
	}

	if (x > parentWidth) {
	    x = parentWidth;
	}

	if (y < 0) {
	    y = 0;
	}

	if (y > parentHeight) {
	    y = parentHeight;
	}

	//System.out.println("Move to:  w,h " + parentWidth + "," + parentHeight
	//    + " x,y " + x + "," + y);

	setLocation(x, y);
	repaint();
    }
    
    class HotSpotBorder extends AbstractBorder {
        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 16, 0);
        }
        
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets mine = getBorderInsets(c);
            
            insets.top = mine.top;
            insets.left = mine.left;
            insets.bottom = mine.bottom;
            insets.right = mine.right;
            
            return insets;
        }
        
        public boolean isBorderOpaque() {
            return true;
        }
        
        public void paintBorder(Component c, Graphics g, int x, int y, 
                                int width, int height) 
        {
            g.setColor(Color.RED);
            g.fillOval(width, height, 4, 4);
        }        
    }
}
