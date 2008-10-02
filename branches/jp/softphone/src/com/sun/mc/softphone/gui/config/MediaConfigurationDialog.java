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

package com.sun.mc.softphone.gui.config;

import java.awt.Container;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import javax.swing.border.TitledBorder;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import com.sun.mc.softphone.common.Utils;
import com.sun.mc.softphone.SipCommunicator;

/**
 * Creates a dialog for setting media parameters
 */
public class MediaConfigurationDialog extends JDialog {

    private JButton okButton;
    private JButton cancelButton;

    private JFrame frame;
    private Container contentPane;

    private MediaConfigDoneListener listener;

    private String sampleRate;
    private String channels;
    private String encoding;

    private String transmitSampleRate;
    private String transmitChannels;
    private String transmitEncoding;

    private JRadioButton rb32000;

    private JRadioButton trb8000;
    private JRadioButton trb16000;
    private JRadioButton trb32000;
    private JRadioButton trb44100;

    private JRadioButton crbMono;
    private JRadioButton crbStereo;

    /**
     * Class constructor.
     *
     * @param parent the parent of this dialog
     * @param modal if true, this dialog box is modal
     */
    public MediaConfigurationDialog(JFrame frame) {
        super((Frame) null, false);

	setTitle("Media Configuration");

	this.frame = frame;

	contentPane = frame.getContentPane();

        createMediaConfigPanel();

        pack();
    }

    public void addListener(MediaConfigDoneListener listener) {
	this.listener = listener;
    }

    /**
     * Creates the Media Config panel.
     * @param manager the media manager that controls this panel
     */
    void createMediaConfigPanel() {
        GridBagLayout gridBag = new GridBagLayout();
        GridBagConstraints constraints;
        Insets insets;

        contentPane.setLayout(gridBag);
        
        int y = 0;     // which row to display a component on

        Box wizard = Box.createHorizontalBox();
        Box choices = Box.createHorizontalBox();

        ButtonGroup wizardGroup = new ButtonGroup();

        JRadioButton rb8000 = new JRadioButton(set8000Action);
        rb8000.setAlignmentY(0);
        rb8000.setEnabled(true);
        choices.add(rb8000);
        wizardGroup.add(rb8000);

        JRadioButton rb16000 = new JRadioButton(set16000Action);
        rb16000.setAlignmentY(0);
        rb16000.setEnabled(true);
        choices.add(rb16000);
        wizardGroup.add(rb16000);

        rb32000 = new JRadioButton(set32000Action);
        rb32000.setAlignmentY(0);
        rb32000.setEnabled(true);
        choices.add(rb32000);
        wizardGroup.add(rb32000);

        JRadioButton rb44100 = new JRadioButton(set44100Action);
        rb44100.setAlignmentY(0);
        rb44100.setEnabled(true);
        choices.add(rb44100);
        wizardGroup.add(rb44100);
	
	sampleRate = Utils.getPreference(
	    "com.sun.mc.softphone.media.SAMPLE_RATE");

	if (sampleRate == null) {
	    sampleRate = "16000";
	}

	if (sampleRate.equals("8000")) {
            rb8000.setSelected(true);
	} else if (sampleRate.equals("16000")) {
            rb16000.setSelected(true);
	} else if (sampleRate.equals("32000")) {
            rb32000.setSelected(true);
	} else if (sampleRate.equals("44100")) {
            rb44100.setSelected(true);
	} else {
            rb8000.setSelected(true);
	}

        choices.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), 
		"Maximum Sample Rate",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.TOP));

        JTextPane wizardDescription = new JTextPane();
        StyledDocument wizDoc = wizardDescription.getStyledDocument();
        Style def = StyleContext.getDefaultStyleContext().
                        getStyle(StyleContext.DEFAULT_STYLE);

        Style basicStyle = wizDoc.addStyle("basic", def);
        Style boldStyle = wizDoc.addStyle("bold", basicStyle);
        StyleConstants.setBold(boldStyle, true);
        wizardDescription.setAlignmentX(0.5f);
        wizardDescription.setFont(new Font("Sans-serif", Font.PLAIN, 12));
        wizardDescription.setEditable(false);
        wizardDescription.setOpaque(false);

        choices.setAlignmentX(0.5f);

        wizard.add(choices);

        insets = new Insets(12, 7, 0, 12);  // top, left, bottom, right
        constraints = new GridBagConstraints(
            1, y++, 1, 1,                   // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.WEST,        // anchor
            GridBagConstraints.HORIZONTAL,  // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(wizard, constraints);
        contentPane.add(wizard);

        wizard = Box.createHorizontalBox();
        choices = Box.createHorizontalBox();

        wizardGroup = new ButtonGroup();

        JRadioButton rbMono = new JRadioButton(setMonoAction);
        rbMono.setAlignmentY(0);
        choices.add(rbMono);
        wizardGroup.add(rbMono);

        JRadioButton rbStereo = new JRadioButton(setStereoAction);
        rbStereo.setAlignmentY(0);
        choices.add(rbStereo);
        wizardGroup.add(rbStereo);

        rbMono.setEnabled(true);
        rbStereo.setEnabled(true);

        choices.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), 
		"Preferred Channels",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.TOP));

        wizardDescription = new JTextPane();
        wizDoc = wizardDescription.getStyledDocument();
        def = StyleContext.getDefaultStyleContext().
                        getStyle(StyleContext.DEFAULT_STYLE);
        basicStyle = wizDoc.addStyle("basic", def);
        boldStyle = wizDoc.addStyle("bold", basicStyle);
        StyleConstants.setBold(boldStyle, true);
        wizardDescription.setAlignmentX(0.5f);
        wizardDescription.setFont(new Font("Sans-serif", Font.PLAIN, 12));
        wizardDescription.setEditable(false);
        wizardDescription.setOpaque(false);

        choices.setAlignmentX(0.5f);

	channels = Utils.getPreference("com.sun.mc.softphone.media.CHANNELS");

	if (channels == null) {
	    channels = "2";
	}

	if (channels.equals("1")) {
	    rbMono.setSelected(true);
	} else {
	    rbStereo.setSelected(true);
	}

        wizard.add(choices);

        insets = new Insets(12, 7, 0, 12);  // top, left, bottom, right
        constraints = new GridBagConstraints(
            1, y++, 1, 1,                   // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.WEST,        // anchor
            GridBagConstraints.HORIZONTAL,  // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(wizard, constraints);
        contentPane.add(wizard);

	wizard = Box.createHorizontalBox();
	choices = Box.createHorizontalBox();

	wizardGroup = new ButtonGroup();

	JRadioButton rbUlaw = new JRadioButton(setUlawAction);
	rbUlaw.setAlignmentY(0);
	choices.add(rbUlaw);
	wizardGroup.add(rbUlaw);

	JRadioButton rbLinear = new JRadioButton(setLinearAction);
	rbLinear.setAlignmentY(0);
	choices.add(rbLinear);
	wizardGroup.add(rbLinear);

        JRadioButton rbSpeex = new JRadioButton(setSpeexAction);
        rbLinear.setAlignmentY(0);
        choices.add(rbSpeex);
        wizardGroup.add(rbSpeex);

	rbUlaw.setEnabled(true);
	rbLinear.setEnabled(true);
	rbSpeex.setEnabled(true);

	encoding = Utils.getPreference(
	    "com.sun.mc.softphone.media.ENCODING");

	if (encoding == null) {
	    encoding = "PCM";
	}

	if (encoding.equals("PCMU")) {
	    rbUlaw.setSelected(true);
	} else if (encoding.equals("PCM")) {
	    rbLinear.setSelected(true);
	} else {
	    rbSpeex.setSelected(true);
	}

        choices.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), 
		"Preferred Encoding",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.TOP));

        wizardDescription = new JTextPane();
        wizDoc = wizardDescription.getStyledDocument();
        def = StyleContext.getDefaultStyleContext().
                        getStyle(StyleContext.DEFAULT_STYLE);
        basicStyle = wizDoc.addStyle("basic", def);
        boldStyle = wizDoc.addStyle("bold", basicStyle);
        StyleConstants.setBold(boldStyle, true);
        wizardDescription.setAlignmentX(0.5f);
        wizardDescription.setFont(new Font("Sans-serif", Font.PLAIN, 12));
        wizardDescription.setEditable(false);
        wizardDescription.setOpaque(false);

        choices.setAlignmentX(0.5f);

	wizard.add(choices);

        insets = new Insets(12, 7, 0, 12);  // top, left, bottom, right
        constraints = new GridBagConstraints(
            1, y++, 1, 1,                   // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.WEST,        // anchor
            GridBagConstraints.HORIZONTAL,  // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(wizard, constraints);
	contentPane.add(wizard);

        /*
	 * SEPARATOR
         */
        JSeparator js = new JSeparator();
        constraints = new GridBagConstraints(
            1, y++, 1, 1,                   // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.WEST,        // anchor
            GridBagConstraints.HORIZONTAL,  // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        constraints.insets = new Insets(12, 7, 0, 12);
        gridBag.setConstraints(js, constraints);
        contentPane.add(js);

	/*
	 * Transmit parameters
	 */
        wizard = Box.createHorizontalBox();
        choices = Box.createHorizontalBox();

        wizardGroup = new ButtonGroup();

        trb8000 = new JRadioButton(set8000TransmitAction);
        trb8000.setAlignmentY(0);
        trb8000.setEnabled(true);
        choices.add(trb8000);
        wizardGroup.add(trb8000);

        trb16000 = new JRadioButton(set16000TransmitAction);
        trb16000.setAlignmentY(0);
        trb16000.setEnabled(true);
        choices.add(trb16000);
        wizardGroup.add(trb16000);

        trb32000 = new JRadioButton(set32000TransmitAction);
        trb32000.setAlignmentY(0);
        trb32000.setEnabled(true);
        choices.add(trb32000);
        wizardGroup.add(trb32000);

        trb44100 = new JRadioButton(set44100TransmitAction);
        trb44100.setAlignmentY(0);
        trb44100.setEnabled(true);
        choices.add(trb44100);
        wizardGroup.add(trb44100);
	
	transmitSampleRate = Utils.getPreference(
	    "com.sun.mc.softphone.media.TRANSMIT_SAMPLE_RATE");

	if (transmitSampleRate == null || transmitSampleRate.length() == 0) {
	    transmitSampleRate = Utils.getPreference(
	        "com.sun.mc.softphone.media.SAMPLE_RATE");

	    if (transmitSampleRate == null || 
		    transmitSampleRate.length() == 0) {

		transmitSampleRate = "16000";
	    }
	}

	if (Integer.parseInt(transmitSampleRate) >
	        Integer.parseInt(sampleRate)) {

	    transmitSampleRate = sampleRate;
	}

	if (transmitSampleRate.equals("8000")) {
            trb8000.setSelected(true);
	} else if (transmitSampleRate.equals("16000")) {
            trb16000.setSelected(true);
	} else if (transmitSampleRate.equals("32000")) {
            trb32000.setSelected(true);
	} else if (transmitSampleRate.equals("44100")) {
            trb44100.setSelected(true);
	} else {
            trb8000.setSelected(true);
	}

        choices.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), 
		"Transmit Sample Rate Limit",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.TOP));

        wizardDescription = new JTextPane();
        wizDoc = wizardDescription.getStyledDocument();
        def = StyleContext.getDefaultStyleContext().
                        getStyle(StyleContext.DEFAULT_STYLE);

        basicStyle = wizDoc.addStyle("basic", def);
        boldStyle = wizDoc.addStyle("bold", basicStyle);
        StyleConstants.setBold(boldStyle, true);
        wizardDescription.setAlignmentX(0.5f);
        wizardDescription.setFont(new Font("Sans-serif", Font.PLAIN, 12));
        wizardDescription.setEditable(false);
        wizardDescription.setOpaque(false);

        choices.setAlignmentX(0.5f);

        wizard.add(choices);

        insets = new Insets(12, 7, 0, 12);  // top, left, bottom, right
        constraints = new GridBagConstraints(
            1, y++, 1, 1,                   // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.WEST,        // anchor
            GridBagConstraints.HORIZONTAL,  // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(wizard, constraints);
        contentPane.add(wizard);

        wizard = Box.createHorizontalBox();
        choices = Box.createHorizontalBox();

        wizardGroup = new ButtonGroup();

        crbMono = new JRadioButton(setMonoTransmitAction);
        crbMono.setAlignmentY(0);
        choices.add(crbMono);
        wizardGroup.add(crbMono);

        crbStereo = new JRadioButton(setStereoTransmitAction);
        crbStereo.setAlignmentY(0);
        choices.add(crbStereo);
        wizardGroup.add(crbStereo);

        crbMono.setEnabled(true);
        crbStereo.setEnabled(true);

        choices.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), 
		"Transmit Channels",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.TOP));

        wizardDescription = new JTextPane();
        wizDoc = wizardDescription.getStyledDocument();
        def = StyleContext.getDefaultStyleContext().
                        getStyle(StyleContext.DEFAULT_STYLE);
        basicStyle = wizDoc.addStyle("basic", def);
        boldStyle = wizDoc.addStyle("bold", basicStyle);
        StyleConstants.setBold(boldStyle, true);
        wizardDescription.setAlignmentX(0.5f);
        wizardDescription.setFont(new Font("Sans-serif", Font.PLAIN, 12));
        wizardDescription.setEditable(false);
        wizardDescription.setOpaque(false);

        choices.setAlignmentX(0.5f);

	transmitChannels = Utils.getPreference(
	    "com.sun.mc.softphone.media.TRANSMIT_CHANNELS");

	if (transmitChannels == null || transmitChannels.length() == 0) {
	    transmitChannels = Utils.getPreference(
	        "com.sun.mc.softphone.media.CHANNELS");


	    if (transmitChannels == null || transmitChannels.length() == 0) {
		transmitChannels = "2";
	    } 
	}

	if (channels.equals("1")) {
	    transmitChannels = channels;
	}

	if (transmitChannels.equals("1")) {
	    crbMono.setSelected(true);
	} else {
	    crbStereo.setSelected(true);
	}

        wizard.add(choices);

        insets = new Insets(12, 7, 0, 12);  // top, left, bottom, right
        constraints = new GridBagConstraints(
            1, y++, 1, 1,                   // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.WEST,        // anchor
            GridBagConstraints.HORIZONTAL,  // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(wizard, constraints);
        contentPane.add(wizard);

	wizard = Box.createHorizontalBox();
	choices = Box.createHorizontalBox();

	wizardGroup = new ButtonGroup();

	rbUlaw = new JRadioButton(setUlawTransmitAction);
	rbUlaw.setAlignmentY(0);
	choices.add(rbUlaw);
	wizardGroup.add(rbUlaw);

	rbLinear = new JRadioButton(setLinearTransmitAction);
	rbLinear.setAlignmentY(0);
	choices.add(rbLinear);
	wizardGroup.add(rbLinear);

	rbSpeex = new JRadioButton(setSpeexTransmitAction);
	rbSpeex.setAlignmentY(0);
	choices.add(rbSpeex);
	wizardGroup.add(rbSpeex);

	rbUlaw.setEnabled(true);
	rbLinear.setEnabled(true);
	rbSpeex.setEnabled(true);

	transmitEncoding = Utils.getPreference(
	    "com.sun.mc.softphone.media.TRANSMIT_ENCODING");

	if (transmitEncoding == null || transmitEncoding.length() == 0) {
	    transmitEncoding = Utils.getPreference(
	        "com.sun.mc.softphone.media.ENCODING");

	    if (transmitEncoding == null || transmitEncoding.length() == 0) {
		transmitEncoding = "PCM";
	    }
	}

	if (transmitEncoding.equals("PCMU")) {
	    rbUlaw.setSelected(true);
	} else if (transmitEncoding.equals("PCM")) {
	    rbLinear.setSelected(true);
	} else {
	    rbSpeex.setSelected(true);
	}

        choices.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), 
		"Transmit Encoding",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.TOP));

        wizardDescription = new JTextPane();
        wizDoc = wizardDescription.getStyledDocument();
        def = StyleContext.getDefaultStyleContext().
                        getStyle(StyleContext.DEFAULT_STYLE);
        basicStyle = wizDoc.addStyle("basic", def);
        boldStyle = wizDoc.addStyle("bold", basicStyle);
        StyleConstants.setBold(boldStyle, true);
        wizardDescription.setAlignmentX(0.5f);
        wizardDescription.setFont(new Font("Sans-serif", Font.PLAIN, 12));
        wizardDescription.setEditable(false);
        wizardDescription.setOpaque(false);

        choices.setAlignmentX(0.5f);

	wizard.add(choices);

        insets = new Insets(12, 7, 0, 12);  // top, left, bottom, right
        constraints = new GridBagConstraints(
            1, y++, 1, 1,                   // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.WEST,        // anchor
            GridBagConstraints.HORIZONTAL,  // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        gridBag.setConstraints(wizard, constraints);
	contentPane.add(wizard);

        /* 
	 * BUTTON PANEL
         */
        insets = new Insets(12, 12, 12, 12); // top, left, bottom, right
        constraints = new GridBagConstraints(
            0, y, 4, 1,                     // x, y, width, height
            1.0, 1.0,                       // weightx, weighty
            GridBagConstraints.CENTER,      // anchor
            GridBagConstraints.NONE,        // fill
            insets,                         // insets
            0, 0);                          // ipadx, ipady
        JPanel buttonPanel = createButtonPanel();
        gridBag.setConstraints(buttonPanel, constraints);
        contentPane.add(buttonPanel);
    }

    /**
     * Creates the panel of buttons that goes along the bottom
     * of the dialog.
     *
     * @return a panel of buttons
     */
    JPanel createButtonPanel() {
        JPanel panel = new JPanel();

        okButton = new JButton("Okay");
        okButton.setEnabled(true);
        okButton.setMnemonic('O');
        okButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent event) {
		    Utils.setPreference(
			"com.sun.mc.softphone.media.SAMPLE_RATE",
			sampleRate);

		    Utils.setPreference(
			"com.sun.mc.softphone.media.CHANNELS", channels);

		    Utils.setPreference("com.sun.mc.softphone.media.ENCODING",
			encoding);

                    Utils.setPreference(
                        "com.sun.mc.softphone.media.TRANSMIT_SAMPLE_RATE",
                        transmitSampleRate);

                    Utils.setPreference(
                        "com.sun.mc.softphone.media.TRANSMIT_CHANNELS",
                        transmitChannels);

                    Utils.setPreference(
                        "com.sun.mc.softphone.media.TRANSMIT_ENCODING",
                        transmitEncoding);

		    SipCommunicator.getInstance().mediaChanged();

		    if (listener != null) {
			listener.mediaConfigDone(true);
		    }

		    frame.setVisible(false);
                }
            });

        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(true);
        cancelButton.setMnemonic('C');
        cancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent event) {
		    if (listener != null) {
			listener.mediaConfigDone(true);
		    }

		    frame.setVisible(false);
                }
            });

        getRootPane().setDefaultButton(okButton);        
        
        Box buttons = Box.createHorizontalBox();
	buttons.add(okButton);
	buttons.add(Box.createHorizontalStrut(32));
	buttons.add(cancelButton);

	panel.add(buttons);

        return panel;
    }

    private Action set8000Action =
        new AbstractAction("8000") {
            public void actionPerformed(ActionEvent evt) {
                sampleRate = "8000";

		transmitSampleRate = "8000";
		trb8000.setSelected(true);
            }
    };

    private Action set16000Action =
        new AbstractAction("16000") {
            public void actionPerformed(ActionEvent evt) {
                sampleRate = "16000";

		if (transmitSampleRate.equals("32000") ||
		        transmitSampleRate.equals("44100")) {

		    transmitSampleRate = sampleRate;
		    trb16000.setSelected(true);
		}
            }
    };

    private Action set32000Action =
        new AbstractAction("32000") {
            public void actionPerformed(ActionEvent evt) {
                sampleRate = "32000";

		if (transmitSampleRate.equals("44100")) {
		    transmitSampleRate = sampleRate;
		    trb32000.setSelected(true);
		}
            }
    };

    private Action set44100Action =
        new AbstractAction("44100") {
            public void actionPerformed(ActionEvent evt) {
		if (encoding.equals("SPEEX") == false) {
                    sampleRate = "44100";
		} else {
		    sampleRate = "32000";
		    rb32000.setSelected(true);

		    if (transmitSampleRate.equals("44100")) {
		        transmitSampleRate = "32000";
		        trb32000.setSelected(true);
		    }
		}
            }
    };

    private Action setMonoAction =
        new AbstractAction("Mono") {
            public void actionPerformed(ActionEvent evt) {
                channels = "1";

		transmitChannels = channels;
		crbMono.setSelected(true);
            }
    };

    private Action setStereoAction =
        new AbstractAction("Stereo") {
            public void actionPerformed(ActionEvent evt) {
                channels = "2";
            }
    };

    private Action setUlawAction =
        new AbstractAction("Ulaw") {
            public void actionPerformed(ActionEvent evt) {
		encoding = "PCMU";
            }
    };

    private Action setLinearAction =
        new AbstractAction("Linear") {
            public void actionPerformed(ActionEvent evt) {
		encoding = "PCM";
            }
    };

    private Action setSpeexAction =
        new AbstractAction("Speex") {
            public void actionPerformed(ActionEvent evt) {
		encoding = "SPEEX";

		if (sampleRate.equals("44100")) {
		    sampleRate = "32000";
		    rb32000.setSelected(true);

		    if (transmitSampleRate.equals("44100")) {
			transmitSampleRate = "32000";
		        trb32000.setSelected(true);
		    }
		}
            }
    };

    private Action set8000TransmitAction =
        new AbstractAction("8000") {
            public void actionPerformed(ActionEvent evt) {
                transmitSampleRate = "8000";
            }
    };

    private Action set16000TransmitAction =
        new AbstractAction("16000") {
            public void actionPerformed(ActionEvent evt) {

		if (sampleRate.equals("8000")) {
		    transmitSampleRate = sampleRate;
		    trb8000.setSelected(true);
		} else {
                    transmitSampleRate = "16000";
		}
            }
    };

    private Action set32000TransmitAction =
        new AbstractAction("32000") {
            public void actionPerformed(ActionEvent evt) {
		if (sampleRate.equals("8000")) {
		    transmitSampleRate = sampleRate;
		    trb8000.setSelected(true);
		} else if (sampleRate.equals("16000")) {
		    transmitSampleRate = sampleRate;
		    trb16000.setSelected(true);
		} else {
                    transmitSampleRate = "32000";
		}
            }
    };

    private Action set44100TransmitAction =
        new AbstractAction("44100") {
            public void actionPerformed(ActionEvent evt) {
		if (transmitEncoding.equals("SPEEX")) {
		    transmitSampleRate = "32000";
		    trb32000.setSelected(true);
		    return;
		}

		if (sampleRate.equals("8000")) {
		    transmitSampleRate = sampleRate;
		    trb8000.setSelected(true);
		} else if (sampleRate.equals("16000")) {
		    transmitSampleRate = sampleRate;
		    trb16000.setSelected(true);
		} else if (sampleRate.equals("32000")) {
		    transmitSampleRate = sampleRate;
		    trb32000.setSelected(true);
		} else {
                    transmitSampleRate = "44100";
		}
            }
    };

    private Action setMonoTransmitAction =
        new AbstractAction("Mono") {
            public void actionPerformed(ActionEvent evt) {
                transmitChannels = "1";
            }
    };

    private Action setStereoTransmitAction =
        new AbstractAction("Stereo") {
            public void actionPerformed(ActionEvent evt) {
		if (channels.equals("1")) {
		    transmitChannels = "1";
		    crbMono.setSelected(true);
		} else {
                    transmitChannels = "2";
		}
            }
    };

    private Action setUlawTransmitAction =
        new AbstractAction("Ulaw") {
            public void actionPerformed(ActionEvent evt) {
		transmitEncoding = "PCMU";
            }
    };

    private Action setLinearTransmitAction =
        new AbstractAction("Linear") {
            public void actionPerformed(ActionEvent evt) {
		transmitEncoding = "PCM";
            }
    };

    private Action setSpeexTransmitAction =
        new AbstractAction("Speex") {
            public void actionPerformed(ActionEvent evt) {
		transmitEncoding = "SPEEX";

		if (transmitSampleRate.equals("44100")) {
		    transmitSampleRate = "32000";
		    trb32000.setSelected(true);
		}
            }
    };

}
