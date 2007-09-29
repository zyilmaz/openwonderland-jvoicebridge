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

import com.sun.stun.NetworkAddressManager;

import com.sun.voip.Logger;

import com.sun.mc.softphone.SipCommunicator;
import com.sun.mc.softphone.common.Utils;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import java.io.IOException;

import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.util.Enumeration;

public class NetworkInterfaceConfig extends JFrame {
    private JComboBox interfaceComboBox;
    private JTextArea text;
    
    private void interfaceTextChanged(ActionEvent ae) {
	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("interface selected setting pref to: " 
	        + interfaceComboBox.getSelectedItem());
	}

	String s = (String) interfaceComboBox.getSelectedItem();

	int ix = s.indexOf(":");

	if (ix >= 0) {
	    s = s.substring(0, ix);
	}

	ix = s.indexOf(" ");

	if (ix >= 0) {
	    s = s.substring(0, ix);
	}

	Utils.setPreference("com.sun.mc.stun.LOCAL_IP_ADDRESS", s);
    }

    private Action doneAction = new AbstractAction("Done") {
        public void actionPerformed(ActionEvent evt) {
            done();
        }
    };
    
    private Action cancelAction = new AbstractAction("Cancel") {
        public void actionPerformed(ActionEvent evt) {
            setVisible(false);
        }
    };
    
    /** Creates a new instance of NetworkInterfaceConfig */
    public NetworkInterfaceConfig() {
	interfaceComboBox = new JComboBox();
	interfaceComboBox.setEditable(false);

        interfaceComboBox.addActionListener(
            new AbstractAction("Network Interface") {
                public void actionPerformed(ActionEvent ae) {
                    interfaceTextChanged(ae);
                }
            });

	fillInterfaceComboBox();

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
                done();
            }
        });
        Container content = getContentPane();
        content.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.insets = new Insets(4, 12, 8, 12);

	JLabel label = new JLabel("Network Interface:");
	label.setDisplayedMnemonic('N');
        label.setLabelFor(interfaceComboBox);
	content.add(label, gbc);

	gbc.gridx = 1;
	content.add(interfaceComboBox, gbc);

	gbc.gridy++;

        gbc.anchor = gbc.EAST;
        gbc.gridx = 1;

	JButton done = new JButton(doneAction);
        content.add(done, gbc);
        pack();
        setResizable(false);
        setVisible(true);
    }
    
    private void done() {
        setVisible(false);
    }
    
    private void fillInterfaceComboBox() {
	interfaceComboBox.removeAllItems();

	InetAddress defaultAddress = null;
	InetAddress preferredAddress = null;

	try {
	    defaultAddress = NetworkAddressManager.getPrivateLocalHost();
	    Logger.println("Default address is " + defaultAddress);
	} catch (IOException e) {
	    Logger.println("Unable to determine default local address:  "
		+ e.getMessage());
	}

	String s = 
	    Utils.getPreference("com.sun.mc.stun.LOCAL_IP_ADDRESS");

	if (s != null && s.length() > 0) {
	    InetAddress address = null;

	    try {
		preferredAddress = InetAddress.getByName(s);
	    } catch (UnknownHostException e) {
		Logger.println("Bad address: " + e.getMessage());
	    }

	    s = "";

	    if (preferredAddress != null) {
	        s += preferredAddress.getHostAddress();

		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	            Logger.println("Setting preferred local address to " 
			+ preferredAddress);
		}

		if (defaultAddress != null && preferredAddress.equals(defaultAddress)) {
		    s += " Default ";
		} 

		s += "UserSpecified ";

		try {
		    if (preferredAddress.isReachable(1000)) {
			s += "Reachable ";
		    } else {
		        s += "NotReachable ";
		    }
		} catch (IOException e) {
		    s += "NotReachable ";
		}
	    } else {
		s += preferredAddress + " BadAddress";

		Logger.println("Preferred local address " + preferredAddress
		    + " is not valid");
	    }

	    interfaceComboBox.addItem(s);
	} 

        Enumeration iFaces;

	try {
            iFaces = NetworkInterface.getNetworkInterfaces();
	} catch (SocketException e) {
	    Logger.println("Unable to get network interfaces:  " 
		+ e.getMessage());
	    return;
	}

	while (iFaces.hasMoreElements()) {
	    NetworkInterface iFace = (NetworkInterface) iFaces.nextElement();

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("Interface:  " + iFace.getName());
	    }

	    Enumeration addresses = iFace.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress address = (InetAddress) addresses.nextElement();

	      	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Interface: " + iFace.getName() 
			+ " Address:  " + address.getHostAddress());
		}

		if (address instanceof Inet6Address) {
		    continue;
		}

	        if (preferredAddress != null && 
	    	    preferredAddress.equals(address)) {

	            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		        Logger.println(
			    "Skipping preferred address which is already set");
		    }

		    continue;
		}

		s = address.getHostAddress() + " ";

		if (defaultAddress != null && address.equals(defaultAddress)) {
		    s += "Default ";
		}

		// XXX isUP() is not implemented before java 6
		//
		//try {
		//    if (iFace.isUp() == true) {
		//        s += "Up ";
		//    } else {
		//        s += "Down ";
		//    }
		//} catch (SocketException e) {
		//    s += "Down ";
		//
		//    Logger.println(iFace.getName() + " got exception:  " 
		//	+ e.getMessage());
		//}

		try {
		    if (address.isReachable(1000)) {
			s += "Reachable ";
		    } else {
		       s += "NotReachable ";
		    }
		} catch (IOException e) {
		    s += "NotReachable ";

	      	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		        Logger.println("Address " + address + " is not reachable");
		    }
		}

		Logger.println("adding " + iFace.getName() + " " + s);
	        interfaceComboBox.addItem(s);
	    }
	}
    }

}
