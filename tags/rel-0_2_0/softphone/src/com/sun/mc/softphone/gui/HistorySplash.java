/*
 * HistorySplash.java  (2004)
 *
 * Copyright (c) 2005 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.mc.softphone.gui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.GridBagConstraints;
import java.awt.Insets;


public class HistorySplash
    extends JDialog
{

    /**
     * Resource bundle with default locale
     */
    private ResourceBundle resources = null;

    /**
     * Path to the image resources
     */
    private String imagePath = null;

    /**
     * Command string for a cancel action (e.g., a button).
     * This string is never presented to the user and should
     * not be internationalized.
     */
    private String CMD_CANCEL = "cmd.cancel" /*NOI18N*/;

    /**
     * Command string for a login action (e.g., a button).
     * This string is never presented to the user and should
     * not be internationalized.
     */
    private String CMD_OK = "cmd.ok" /*NOI18N*/;

    private String CMD_CLEAR = "cmd.clear" /*NOI18N*/;

    // Components we need to manipulate after creation
    private JList   dialedList = new JList(new DialedListModel());
    private JList   receivedList = new JList(new ReceivedListModel());
    private JList   missedList = new JList(new MissedListModel());

    JTabbedPane tabbedPane = null;
    private JButton okButton = null;
    private JButton cancelButton = null;
//    private JButton clearButton = null;

    private String number;

    /**
     * Creates new form HistorFlash
     */
    public HistorySplash(Frame parent, boolean modal)
    {
        super(parent, modal);
        setBackground(new Color(180,180,210));
        initResources();
        initComponents();
        pack();
        centerWindow();
    }

    public String getNumber() {
        return number;
    }

    public void addDialedNumber(String number) {
        ((DefaultListModel)dialedList.getModel()).addElement(number);
        pack();
        centerWindow();
    }

    public void addReceivedNumber(String number) {
        ((DefaultListModel)receivedList.getModel()).addElement(number);
        pack();
        centerWindow();
    }

    public void addMissedNumber(String number) {
        ((DefaultListModel)missedList.getModel()).addElement(number);
        pack();
        centerWindow();
    }

    /**
     * Loads locale-specific resources: strings, images, et cetera
     */
    private void initResources()
    {
        Locale locale = Locale.getDefault();
//        resources = ResourceBundle.getBundle(
//            "samples.resources.bundles.HistorySplashResources", locale);
        imagePath = ".";
    }

    /**
     * Centers the window on the screen.
     */
    private void centerWindow()
    {
        Rectangle screen = new Rectangle(
            Toolkit.getDefaultToolkit().getScreenSize());
        Point center = new Point(
            (int) screen.getCenterX(), (int) screen.getCenterY());
        Point newLocation = new Point(
            center.x - this.getWidth() / 2, center.y - this.getHeight() / 2);
        if (screen.contains(newLocation.x, newLocation.y,
                            this.getWidth(), this.getHeight())) {
            this.setLocation(newLocation);
        }
    } // centerWindow()

    /**
     *
     * We use dynamic layout managers, so that layout is dynamic and will
     * adapt properly to user-customized fonts and localized text. The
     * GridBagLayout makes it easy to line up components of varying
     * sizes along invisible vertical and horizontal grid lines. It
     * is important to sketch the layout of the interface and decide
     * on the grid before writing the layout code.
     *
     * Here we actually use
     * our own subclass of GridBagLayout called StringGridBagLayout,
     * which allows us to use strings to specify constraints, rather
     * than having to create GridBagConstraints objects manually.
     *
     *
     * We use the JLabel.setLabelFor() method to connect
     * labels to what they are labeling. This allows mnemonics to work
     * and assistive to technologies used by persons with disabilities
     * to provide much more useful information to the user.
     */
    private void initComponents() {
        // set properties on window
        Container contents = getContentPane();
        contents.setLayout(new BorderLayout());
        setTitle("Sun Softphone - Call History");
        setResizable(false);
        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent event)
            {
                dialogDone(CMD_CANCEL);
            }
        });

        // Accessibility -- all frames, dialogs, and applets should
        // have a description
        getAccessibleContext().setAccessibleDescription("Call History");

        

        tabbedPane=new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setBackground(new Color(180,180,210));
        dialedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dialedList.setVisibleRowCount(12);
        dialedList.setPrototypeCellValue("sip:jm57878@microsat.uk.sun.com:5070;transport=udp");
        dialedList.addMouseListener(listMouseListener);
        tabbedPane.add("Dialed",dialedList);

        receivedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        receivedList.setVisibleRowCount(12);
        receivedList.setPrototypeCellValue("sip:jm57878@microsat.uk.sun.com:5070;transport=udp");
        receivedList.addMouseListener(listMouseListener);
        tabbedPane.add("Received",receivedList);

        missedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        missedList.setVisibleRowCount(12);
        missedList.setPrototypeCellValue("sip:jm57878@microsat.uk.sun.com:5070;transport=udp");
        missedList.addMouseListener(listMouseListener);
        tabbedPane.add("Missed",missedList);

        JPanel centerPane = new JPanel();
        centerPane.setBackground(new Color(180,180,210));

        centerPane.add(tabbedPane);

        contents.add(centerPane, BorderLayout.CENTER);

        // Buttons along bottom of window
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setBackground(new Color(180,180,210));

        okButton = new JButton();
        okButton.setText("OK");
        okButton.setActionCommand(CMD_OK);
        okButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                dialogDone(event);
            }
        });
        buttonPanel.add(okButton);

        // space
        buttonPanel.add(Box.createRigidArea(new Dimension(5, 0)));

        cancelButton = new JButton();
        cancelButton.setText("Cancel");
        cancelButton.setActionCommand(CMD_CANCEL);
        cancelButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                dialogDone(event);
            }
        });
        buttonPanel.add(cancelButton);

//        clearButton = new JButton();
//        clearButton.setText("Clear");
//        clearButton.setActionCommand(CMD_CLEAR);
//        clearButton.addActionListener(new ActionListener()
//        {
//            public void actionPerformed(ActionEvent event)
//            {
//                clearList(event);
//            }
//        });
//        buttonPanel.add(clearButton);

        contents.add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okButton);
        equalizeButtonSizes();

    } // initComponents()

    /**
     * Sets the buttons along the bottom of the dialog to be the
     * same size. This is done dynamically by setting each button's
     * preferred and maximum sizes after the buttons are created.
     * This way, the layout automatically adjusts to the locale-
     * specific strings.
     */
    private void equalizeButtonSizes()
    {

        JButton[] buttons = new JButton[] {
            okButton, cancelButton /*, clearButton */
        };

        String[] labels = new String[buttons.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = buttons[i].getText();
        }

        // Get the largest width and height
        int i = 0;
        Dimension maxSize = new Dimension(0, 0);
        Rectangle2D textBounds = null;
        Dimension textSize = null;
        FontMetrics metrics = buttons[0].getFontMetrics(buttons[0].getFont());
        Graphics g = getGraphics();
        for (i = 0; i < labels.length; ++i) {
            textBounds = metrics.getStringBounds(labels[i], g);
            maxSize.width =
                Math.max(maxSize.width, (int) textBounds.getWidth());
            maxSize.height =
                Math.max(maxSize.height, (int) textBounds.getHeight());
        }

        Insets insets =
            buttons[0].getBorder().getBorderInsets(buttons[0]);
        maxSize.width += insets.left + insets.right;
        maxSize.height += insets.top + insets.bottom;

        // reset preferred and maximum size since BoxLayout takes both
        // into account
        for (i = 0; i < buttons.length; ++i) {
            buttons[i].setPreferredSize( (Dimension) maxSize.clone());
            buttons[i].setMaximumSize( (Dimension) maxSize.clone());
        }
    } // equalizeButtonSizes()

    /**
     * The user has selected an option. Here we close and dispose the dialog.
     * If actionCommand is an ActionEvent, getCommandString() is called,
     * otherwise toString() is used to get the action command.
     *
     * @param actionCommand may be null
     */
    private void dialogDone(Object actionCommand)
    {
        String cmd = null;
        if (actionCommand != null) {
            if (actionCommand instanceof ActionEvent) {
                cmd = ( (ActionEvent) actionCommand).getActionCommand();
            }
            else {
                cmd = actionCommand.toString();
            }
        }
        if (cmd == null) {
            // do nothing
        }
        else if (cmd.equals(CMD_CANCEL)) {
            number = null;
        }
        else if (cmd.equals(CMD_OK)) {
            JList list=(JList)(tabbedPane.getSelectedComponent());
            if(list.getSelectedValue()!=null) {
                number = list.getSelectedValue().toString(); 
            } else {
                number = null;
            }
        }
        setVisible(false);
        dispose();
    } // dialogDone()

    private void clearList(Object actionCommand) {
        JList list=(JList)(tabbedPane.getSelectedComponent());
        ((DefaultListModel)list.getModel()).clear();
    }

    MouseListener listMouseListener = new MouseAdapter() {
        public void mouseClicked(MouseEvent event) {
            if(event.getClickCount()==2) {
                JList list=(JList)(tabbedPane.getSelectedComponent());
                if(list.getSelectedValue()!=null) {
                    number = list.getSelectedValue().toString(); 
                    setVisible(false);
                    dispose();
                }
            }
        }
    };
} // class HistorySplash
