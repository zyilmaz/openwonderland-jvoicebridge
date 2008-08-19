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

package com.sun.voip;

import java.awt.*;
import java.awt.event.*;
import javax.swing.AbstractAction;

import java.util.*;

class GraphObservable extends Observable {
    public void notifyObservers(Object b) {
	setChanged();
	super.notifyObservers(b);
    }
}

class GraphCanvas extends Canvas implements Observer {
    Vector samples = new Vector();
    Vector v;

    GraphCanvas(Observable notifier, Vector v) {
	this.v = v;
	notifier.addObserver(this);
    }

    public void paint() {
	paint(getGraphics());
    }

    public void paint(Graphics g) {
	draw(g, Color.green);
    }

    private void draw(Graphics g, Color c) {
	g.setColor(c);

	Dimension d = getSize();

	int height = d.height;

	double yScaleFactor = height / 65536.0D;
	double xScaleFactor = (double)d.width / v.size();

	//System.out.println("height " + height + " width " + d.width);
	//System.out.println("xScale " + xScaleFactor + " yScale " + yScaleFactor);

	Point previousPoint = null;

	for (int i = 0; i < v.size(); i++) {
            try {
		Point point1 = previousPoint;

	        int sample = ((Integer)v.elementAt(i)).intValue();

		int yScaled = (int) ((double)(sample + 32768) * yScaleFactor);

                Point point2 = new Point((int) (i * xScaleFactor), yScaled);

                if (point1 == null) {
                    point1 = point2;
		}

                previousPoint = point2;

		//System.out.println("(x,y) = (" + point1.x + "," + point1.y 
		//    + ")  (i, s) = (" + i + "," + sample + ")");

                g.drawLine(point1.x, height - point1.y, point2.x, height - point2.y);
            } catch (NoSuchElementException e) {
		break;
            }
	}
    }

    public void update(Observable o, Object arg) {
	paint(getGraphics());
    }

    public void erase() {
	getGraphics().clearRect(0, 0, getSize().width, getSize().height);
    }

}

public class Graph extends Frame {

    public static void main(String[] args) {
	Vector v = new Vector();

	for (int i = -32768; i < 32767 ; i++) {
	    v.add(new Integer(i));
	}

	//v.add(new Integer(-32768));
	//v.add(new Integer(0));
	//v.add(new Integer(32767));

	new Graph("Test", v);

	while (true) {
	    try {
		Thread.sleep(10000);
	    } catch (InterruptedException e) {
	    }
	}
    }

    class DWAdapter extends WindowAdapter {
        public void windowClosing(WindowEvent event) {
	    setVisible(false);
        }
    }

    private GraphCanvas pmc;
    private int height = 500;
    private int width = 700;

    public Graph(String title, int[] samples) {
	super(title);

	//addExitCallAction(new ExitAction());

	Vector v = new Vector();

	for (int i = 0; i < samples.length; i++) {
	    v.add(new Integer(samples[i]));
	}

	graph(v);
    }

    public Graph(String title, Vector v) {
	super(title);

	graph(v);
    }

    private void graph(Vector v) {
	addWindowListener(new DWAdapter());
	setBackground(Color.white);
	setLayout(new BorderLayout());
	pmc = new GraphCanvas(new GraphObservable(), v);
	ScrollPane sp = new ScrollPane();
	sp.add("Center", pmc);
	add(sp);
	setSize(800, 600);
	setVisible(true);
	//show();
 
	pmc.erase();
	pmc.paint();
    }

    //private class ExitAction extends AbstractAction {
    //    public ExitAction() {
    //        super("Exit");
    //    }
    //}

}
