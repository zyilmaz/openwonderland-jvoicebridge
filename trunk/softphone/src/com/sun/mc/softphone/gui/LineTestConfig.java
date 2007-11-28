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

import com.sun.mc.softphone.SipCommunicator;
import com.sun.mc.softphone.media.AudioFactory;
import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;
import com.sun.mc.softphone.media.javasound.GetDataLines;
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
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
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

/**
 *
 * @author mw17785
 */
public class LineTestConfig extends JFrame {
    private MediaManager mediaManager;
    private JComboBox micComboBox;
    private JComboBox speakerComboBox;
    private JButton test;
    private JTextArea text;
    private VuMeterPanel vuMeter;
    
    private Process lineTest;
    private ProcOutputListener stdOutListener;
    private ProcOutputListener stdErrListener;

    private static final String BEFORE_TEST =
            "Click the Test button and speak normally after the tone.";
    
    private static final String AFTER_TEST =
            "If you can hear the tone and your own voice, the test has been " +
            "successful.  If not, use the Configure button below to change " +
            "your system settings.";

    private static final String AFTER_CONFIGURE =
            "Your system audio settings panel should appear shortly. Once " +
            "you have changed your audio settings, use the Test button to " +
            "check your changes.";

    private void micTextChanged(ActionEvent ae) {
	System.out.println("Mic selected setting pref to: " + micComboBox.getSelectedItem());

	Utils.setPreference(Microphone.MICROPHONE_PREFERENCE,
	    (String) micComboBox.getSelectedItem());
    }

    private void speakerTextChanged(ActionEvent ae) {
	System.out.println("Speaker selected setting pref to:  " 
	    + speakerComboBox.getSelectedItem());

	Utils.setPreference(Speaker.SPEAKER_PREFERENCE,
	    (String) speakerComboBox.getSelectedItem());
    }

    private Action testAction = new AbstractAction("Test") {
        public void actionPerformed(ActionEvent evt) {
  	    if (Utils.isLinux() && mediaManager.isStarted()) {
                Object[] options = { "OK", "CANCEL" };
                int option = JOptionPane.showOptionDialog(null, 
		    "The call will be disconnected.  When you click Done it will be reconnected.  Click OK to continue", 
		    "Warning", JOptionPane.DEFAULT_OPTION, 
		    JOptionPane.WARNING_MESSAGE, null, options, options[0]);

	        if (option != JOptionPane.OK_OPTION) {
		    return;
		}

		SipCommunicator.getInstance().endCalls();
	    }

            startTest();
        }
    };
    
    private Action configAction = new AbstractAction("Configure") {
        public void actionPerformed(ActionEvent evt) {
            startConfigure();
        }
    };
    
    private Action doneAction = new AbstractAction("Done") {
        public void actionPerformed(ActionEvent evt) {
            done();
        }
    };
    
    private Action cancelAction = new AbstractAction("Cancel") {
        public void actionPerformed(ActionEvent evt) {
            cancel();
        }
    };
    
    /** Creates a new instance of LineTestConfig */
    public LineTestConfig(MediaManager mediaManager) {
	super("Audio Configuration");

        this.mediaManager = mediaManager;

	micComboBox = new JComboBox();
	micComboBox.setEditable(false);

        micComboBox.addActionListener(
            new AbstractAction("Mic") {
                public void actionPerformed(ActionEvent ae) {
                    micTextChanged(ae);
                }
            });

	speakerComboBox = new JComboBox();
	speakerComboBox.setEditable(false);

        speakerComboBox.addActionListener(
            new AbstractAction("Speaker") {
                public void actionPerformed(ActionEvent ae) {
                    speakerTextChanged(ae);
                }
            });

	fillDeviceComboBoxes();

        vuMeter = new VuMeterPanel();
        test = new JButton(testAction);
        JButton config = new JButton(configAction);
        JButton done = new JButton(doneAction);
        JButton cancel = new JButton(cancelAction);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
                done();
            }
        });
        text = new JTextArea();
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setFont(new Font("Sans-serif", Font.BOLD, 12));
        text.setPreferredSize(new Dimension(300, 100));
        text.setBorder(BorderFactory.createEtchedBorder());
        Container content = getContentPane();
        content.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.insets = new Insets(4, 12, 8, 12);

	JLabel label = new JLabel("Mic:");
	label.setDisplayedMnemonic('M');
        label.setLabelFor(micComboBox);
	content.add(label, gbc);

	gbc.gridx = 1;
	content.add(micComboBox, gbc);

	gbc.gridy++;
	gbc.gridx = 0;

	label = new JLabel("Speaker:");
	label.setDisplayedMnemonic('S');
        label.setLabelFor(speakerComboBox);
	content.add(label, gbc);

	gbc.gridx = 1;
	content.add(speakerComboBox, gbc);

	gbc.gridy++;
	gbc.gridx = 0;
        content.add(vuMeter, gbc);
        gbc.weightx = 0;
        gbc.gridx = 1;
        content.add(test, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weighty = 1;
        gbc.gridwidth = 3;
        content.add(text, gbc);
        gbc.weighty = 0;
        gbc.gridwidth = 1;
        gbc.anchor = gbc.WEST;
        gbc.gridx = 0;
        gbc.gridy++;
        content.add(config, gbc);
        gbc.anchor = gbc.CENTER;
        gbc.gridx = 1;
        content.add(done, gbc);
        gbc.anchor = gbc.EAST;
        gbc.gridx = 2;
	content.add(cancel, gbc);
        pack();
        setResizable(false);
        setVisible(true);
        typeText(BEFORE_TEST);
    }
    
    private void restart() throws IOException {
        JOptionPane.showMessageDialog(this, "The softphone will restart " +
                "shortly with your new configuration.", 
                "Configuration complete", JOptionPane.INFORMATION_MESSAGE);

        System.out.println("*** Restart softphone now");
    }
    
    private void done() {
	if (stdOutListener != null) {
	    stdOutListener.done();
	}

	if (stdErrListener != null) {
	    stdErrListener.done();
	}

        vuMeter.stop();
        setVisible(false);
        try {
            restart();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
//        text.setText(BEFORE_TEST);
    }
    
    private void cancel() {
        vuMeter.stop();
        setVisible(false);
    }

    private void startTest() {
        testAction.setEnabled(false);
        Runnable r = new Runnable() {
            public void run() {
                performTest();
            }
        };
        new Thread(r).start();
    }
    
    private void typeText(final String content) {
        text.setText("");
        Runnable r = new Runnable() {
            public void run() {
                for (int i=0; i<content.length(); i++) {
                    final String c = String.valueOf(content.charAt(i));
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            text.append(c);
                        }
                    });
                    try {
                        Thread.sleep(15);
                    } catch (InterruptedException ie) {}
                }
            }
        };
        new Thread(r).start();
    }
    
    private void performTest() {
        // launch the mic tester
        ServerSocket srv = null;
        boolean fail = false;
        try {
            srv = new ServerSocket(0);
        } catch (IOException ioe) {
            typeText("Unable to test the audio input.  Please use your " +
                    "system's sound utilities to check your input and output.");
            return;
        }
        int port = srv.getLocalPort();
        float mySampleRate =
            Utils.getIntPreference("com.sun.mc.softphone.media.SAMPLE_RATE");
        if (mySampleRate == 0) {
            mySampleRate = (int) MediaManager.DEFAULT_SAMPLE_RATE;
        }
	if (Utils.isMacOS() == true) {
	    mySampleRate = 44100.0f;
        }
        int myChannels =
                Utils.getIntPreference("com.sun.mc.softphone.media.CHANNELS");
        if (myChannels == 0) {
            myChannels = (int) MediaManager.DEFAULT_CHANNELS;
        }
        
        String javaHome = System.getProperty("java.home");
        String classPath = System.getProperty("java.class.path");
        String sep = System.getProperty("file.separator");
        String cmd[] = new String[12];
        cmd[0] = javaHome+sep+"bin"+sep+"java";
        cmd[1] = "-cp";
        cmd[2] = classPath;
        cmd[3] = "com.sun.mc.softphone.media.LevelTest";
        cmd[4] = "-port";
        cmd[5] = String.valueOf(port);
        cmd[6] = "-sampleRate";
        cmd[7] = String.valueOf(mySampleRate);
        cmd[8] = "-channels";
        cmd[9] = String.valueOf(myChannels);
        cmd[10] = "-duration";
        cmd[11] = "4";  // four seconds worth of data.
        Socket sock = null;
        DataInputStream dis = null;
        try {
            vuMeter.setState(vuMeter.STANDBY);
            lineTest = Runtime.getRuntime().exec(cmd);

	    stdOutListener = new ProcOutputListener(lineTest.getInputStream());
            stdErrListener = new ProcOutputListener(lineTest.getErrorStream());

            stdOutListener.start();
            stdErrListener.start();

            srv.setSoTimeout(5000);
            sock = srv.accept();
            sock.setTcpNoDelay(true);
            dis = new DataInputStream(sock.getInputStream());
            vuMeter.setState(vuMeter.ENABLED);
            while (true) {
                float val = dis.readFloat();
                vuMeter.setValue(val);
            }
        } catch (SocketTimeoutException ste) {
            typeText("No response from testing program.  Please try again.");
            fail = true;
        } catch (IOException ioe) {
//            System.out.println(ioe);
        } finally {
            vuMeter.setState(vuMeter.DISABLED);
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException ioe) {}
            }
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException ioe) {}
            }
            if (srv != null) {
                try {
                    srv.close();
                } catch (IOException ioe) {}
            }
            if (!fail) {
                typeText(AFTER_TEST);
            }
            testAction.setEnabled(true);
        }
        
    }
    
    public void startConfigure() {
        System.out.println("os.name: "+System.getProperty("os.name", "<unknown>"));
        System.out.println("os.arch: "+System.getProperty("os.arch", "<unknown>"));
        System.out.println("os.version: "+System.getProperty("os.version", "<unknown>"));
        String os = System.getProperty("os.name", "<unk>");
        //+System.getProperty("os.version","<unk>");
        try {
	    if (startAudioConfigTool() == false) {
                typeText("Operating system: "+os+":\n" +
                        "Please open your system's audio controls to adjust " +
                        "the audio input and settings, then use the Test " +
                        "button to check your settings.");
                return;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        typeText(AFTER_CONFIGURE);
    }

    public static boolean startAudioConfigTool() throws IOException {
        String os = System.getProperty("os.name", "<unk>");

        if (os.startsWith("Mac OS X")) {
            Runtime.getRuntime().exec("open /System/Library/PreferencePanes/Sound.prefPane/");
	    return true;
        } 

	if (os.startsWith("Windows XP")) {
            Runtime.getRuntime().exec("control mmsys.cpl,,2");
	    return true;
        } 

	if (os.startsWith("SunOS")) {
            String command=Utils.getProperty("com.sun.mc.softphone.media.AUDIO_CONTROL");
            if(command==null) {
                command = "/usr/dt/bin/sdtaudiocontrol";
            }
            Runtime.getRuntime().exec(command);
	    return true;
        } 

	if (os.startsWith("Linux")) {
	    Runtime.getRuntime().exec("/usr/bin/gnome-volume-control");
	    return true;
	}

	return false;
    }
    
    public void refreshDevices() {
	//fillDeviceComboBoxes();
    }

    private void fillDeviceComboBoxes() {
	speakerComboBox.removeAllItems();
	micComboBox.removeAllItems();

	AudioFactory audioFactory = AudioFactory.getInstance();

	String[] microphones = audioFactory.getMicrophoneList();

	String preferredMic = Utils.getPreference(Microphone.MICROPHONE_PREFERENCE);

	if (preferredMic != null) {
	    /*
	     * TODO:  This should only be added if it's still valid
	     */
	    micComboBox.addItem(preferredMic);
	}

	for (int i = 0; i < microphones.length; i++) {
	    if (preferredMic != null && preferredMic.equals(microphones[i])) {
		continue;
	    }

	    micComboBox.addItem(microphones[i]);
	}

	String[] speakers = audioFactory.getSpeakerList();

	String preferredSpeaker = Utils.getPreference(Speaker.SPEAKER_PREFERENCE);

	if (preferredSpeaker != null) {
	    /*
	     * TODO:  This should only be added if it's still valid
	     */
	    speakerComboBox.addItem(preferredSpeaker);
	}

	for (int i = 0; i < speakers.length; i++) {
	    if (preferredSpeaker != null && preferredSpeaker.equals(speakers[i])) {
		continue;
	    }

	    speakerComboBox.addItem(speakers[i]);
	}
    }

    /**
     * Listens for lines from an input stream.
     */
    class ProcOutputListener extends Thread {
        BufferedReader reader;
	boolean done;

        public ProcOutputListener(InputStream stream) {
            this.reader = new BufferedReader(new InputStreamReader(stream));
        }
        
        public void done() {
	    done = true;

            try {
		System.out.println("closing input stream");
                reader.close();
            } catch (IOException ioe) {}
        }
        
        public void run() {
            try {
                while (!done) {
                    String line = reader.readLine();
                    if (line == null) {
			System.out.println("readLine returned null!");
			done();
                    } else {
                        System.out.println("Linetest: " + line);
                    }
                }
            } catch (IOException e) {
                if (!done) {
                    e.printStackTrace();
                }
            } finally {
		System.out.println("Lost connection to the line testunexpectedly.");
		done();
            }
        }
        
    }

}
