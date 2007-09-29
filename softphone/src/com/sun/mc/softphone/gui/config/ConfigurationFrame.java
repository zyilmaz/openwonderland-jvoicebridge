/*
 * ConfigurationFrame.java  (2004)
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * 
 * Unpublished - rights reserved under the Copyright Laws of the United States.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this document. In
 * particular, and without limitation, these intellectual property rights may
 * include one or more of the U.S. patents listed at http://www.sun.com/patents
 * and one or more additional patents or pending patent applications in the
 * U.S. and in other countries.
 * 
 * SUN PROPRIETARY/CONFIDENTIAL.
 * 
 * U.S. Government Rights - Commercial software. Government users are subject
 * to the Sun Microsystems, Inc. standard license agreement and applicable
 * provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties. Sun, Sun
 * Microsystems, the Sun logo, Java, Jini, Solaris and Sun Ray are trademarks
 * or registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries, exclusively
 * licensed through X/Open Company, Ltd.
 */

package com.sun.mc.softphone.gui.config;

import javax.swing.*;
import java.awt.Toolkit;

public class ConfigurationFrame extends JFrame {

    public ConfigurationFrame() {
        ConfigurationPanel configurationPanel = new ConfigurationPanel(this);
        this.setTitle(configurationPanel.getTitle());
        this.getContentPane().add(configurationPanel);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(640, 400);

        Toolkit toolkit = Toolkit.getDefaultToolkit();

        setLocation (
	    ((int)toolkit.getScreenSize().getWidth() - getWidth()) / 2,
            ((int)toolkit.getScreenSize().getHeight() - getHeight()) / 2);

        pack();
        setVisible(true);
    }

}
