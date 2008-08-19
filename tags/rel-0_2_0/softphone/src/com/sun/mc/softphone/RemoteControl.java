/*
 * RemoteControl.java  (2005)
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

package com.sun.mc.softphone;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * An interface to remotely control a softphone running in a separate
 * process.  This method uses system.in and system.out to communicate
 * with the softphone.
 * @author jkaplan
 */
public class RemoteControl {
    
    /** the singleton instance */
    private static RemoteControl singleton;
    
    /** the sip address */
    private String sipAddress;
    
    /** the softphone process */
    private Process softphone;

    /** output stream used to send commands to the softphone */
    private SoftphoneOutputStream softphoneOutputStream;
    
    /** whether or not we are done */
    private boolean done;
    
    /** the system property for the location of the softphone jar */
    private static final String SOFTPHONE_PROP = "com.sun.mc.softphone.jar";
        
    /** the preference for the location of the softphone jar */
    private static final String SOFTPHONE_PREF =
	"com.sun.mc.softphone.inst.SoftphoneExtInstaller.path";
    
    /** a logger */
    private static final Logger logger =
            Logger.getLogger(RemoteControl.class.getName());
    
    /** SipStarter is a singleton.  Don't create another */
    private RemoteControl() {}
    
    /**
     * Gets the one instance of SipStarter
     */
    public synchronized static RemoteControl getInstance() {
        if (singleton == null) {
            singleton = new RemoteControl();
        }
        
        return singleton;
    }
    
    /**
     * Determine if the softphone is already started
     * @return true if the sofphone is started, or false if not
     */
    public boolean isStarted() {
        return (softphone != null);
    }
    
    /**
     * Start up the SipCommunicator.  
     * @param userName the username to run the communicator as
     */
    public void startSipCommunicator(String userName) {
        startSipCommunicator(userName, null);
    }
    
    /**
     * Start up the SipCommunicator.  
     * @param userName the username to run the communicator as
     * @param softphone path the path to softphone.jar.  If this value is
     * null, the value of the com.sun.mc.softphone.jar is checked followed by
     * the preference .
     */
    public synchronized void startSipCommunicator(String userName, String softphonePath) {
        if (isStarted()) {
            logger.finest("Already started");
            return;
        }
        
        logger.info("Starting softphone");
        
        String javaHome = System.getProperty("java.home");
        try {
	    String jarPath = getJarPath(softphonePath);

	    if (jarPath == null) {
		logger.warning("Unable to find softphone jar!");
                return;
	    }

	    String fileSeparator = System.getProperty("file.separator");
            String[] command = new String[4];

	    command[0] = javaHome + fileSeparator + "bin" 
		+ fileSeparator + "java";
	    command[1] = "-jar";
	    command[2] = jarPath;
	    command[3] = userName;

            logger.fine("Command is " + command[0] + " " + command[1] + 
                        " " + command[2] + " " + command[3]);

            softphone = Runtime.getRuntime().exec(command);
            
            // create the input stream & wait to get an address
            SoftphoneInputStream sis = 
                    new SoftphoneInputStream(softphone.getInputStream());
            sipAddress = sis.getSipAddress();
            
            // create output and error streams
            softphoneOutputStream = 
		new SoftphoneOutputStream(softphone.getOutputStream());
	    new SoftphoneErrorStream(softphone.getErrorStream());
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Error starting softphone: " + ioe, ioe);
        }
    }
    
    /**
     * Stop a running communicator
     */
    public synchronized void stopSipCommunicator() {
        if (isStarted()) {
            logger.info("Stopping softphone");
            
            sendCommand("Shutdown");

            try {
                softphone.waitFor();
            } catch (InterruptedException e) {
            }

            softphone = null;
            sipAddress = null;
	    softphoneOutputStream = null;
            done = true;
        }
    }
      
    /**
     * Get the registered address of this softphone
     * @return the softphone's address
     */
    public String getSoftphoneAddress() {
	return sipAddress;
    }
     
    public void sendCommand(String s) {
	if (softphoneOutputStream == null) {
	    return;
	}

	softphoneOutputStream.write(s + "\n");
    }

    public void setMicrophoneVolume(float volume) {
	sendCommand("microphoneVolume=" + volume);
    }

    public void setSpeakerVolume(float volume) {
	sendCommand("speakerVolume=" + volume);
    }

    /**
     * Get the location of softphone.jar.  The location is determined as
     * follows:
     * <ol><li>If the value of the string passed in is not null and is a 
     *         valid file, use that
     *     <li>Next look for the file specified by the system property
     *         com.sun.mc.softphone.jar
     *     <li>Look for the user preference 
     *         com.sun.mc.softphone.inst.SoftphoneExtInstaller.path
     *     <li>Search the classpath for softphone.jar
     *     <li>See if there is a file in the current directory named 
     *         softphone.jar
     *
     * @param jarPath the path to softphone.jar, or null to use other
     * means to find the file
     * @return a path that points to softphone.jar, or null if the path
     * cannot be found
     */
    protected String getJarPath(String jarPath) {
        // try what was passed in
        if (jarPath != null && checkPath(jarPath)) {
            return jarPath;
        }
        
        // try the system property
        jarPath = System.getProperty(this.SOFTPHONE_PROP);
        if (jarPath != null && checkPath(jarPath)) {
            return jarPath;
        }
        
        // try the preference
	jarPath = Preferences.userRoot().get(SOFTPHONE_PREF, null);
        if (jarPath != null && checkPath(jarPath)) {
            return jarPath;
        }
        
        // try the classpath
        //...
        
        // try local directory
        jarPath = "softphone.jar";
        if (jarPath != null && checkPath(jarPath)) {
            return jarPath;
        }
        
        // no luck 
        return null;
    }
    
    /**
     * Check if the given path is a valid jar file
     * @param path the path
     * @return true if this is a valid jar file, or false if not
     */
    private boolean checkPath(String jarPath) {
        return new File(jarPath).exists();
    }
    

    /**
     * A thread that reads input from the softphone
     */
    class SoftphoneInputStream extends Thread {
        /** the source input stream */
        private InputStream in;
        
        /** the sip address */
        private String sipAddress;
        
        /**
         * Create a new SoftphoneInputStream
         * @param in the input stream to read
         */
	public SoftphoneInputStream(InputStream in) {
	    this.in = in;
            
            start();
	}

        /**
         * Get the sip address
         * @return the sip address
         */
        public String getSipAddress() {
            if (sipAddress == null && isAlive()) {
                // wait for the address
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException ie) {
                        // ignore
                    }
                }
            }
            
            return sipAddress;
        }
        
	public void run() {
	    BufferedInputStream bin = new BufferedInputStream(in);
	    byte[] buffer = new byte[1024];

            try {
                while (true) {
		    int n = bin.read(buffer, 0, buffer.length);
	   	    if (n < 0) {
                        // finished
		 	break;
		    }

		    String s = new String(buffer, 0, n);

		    int ix;

		    /*
		     * WARNING:  The Sip Communicator generates this exact 
		     * message.  If the communicator is changed, 
		     * you'll need to change searchString.
		     */
		    String searchString = "SipCommunicator Public Address is '";

		    if ((ix = s.indexOf(searchString)) >= 0) {
			sipAddress = s.substring(ix + searchString.length());
			int ixEnd = sipAddress.indexOf("'");
			sipAddress = sipAddress.substring(0, ixEnd);

                        logger.fine("Sip address is: " + sipAddress);
                        synchronized (this) {
                            // notify any listeners
                            notify();
                        }
		    }

		    String[]ss = s.split("[\n]");

		    for (int i = 0; i < ss.length; i++) {
		        System.out.println(ss[i]);
		    }
                }
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error reading softphone input " +
                           "stream: " + ioe, ioe);
	    }
	}
    }   

    /**
     * Just read the error stream
     */
    class SoftphoneErrorStream extends Thread {
        /** the error stream */
        private InputStream in;
        
        public SoftphoneErrorStream(InputStream in) {
            this.in = in;
            
            start();
        }

        public void run() {
            BufferedInputStream bin = new BufferedInputStream(in);
            byte[] buffer = new byte[1024];

            try {
                while (true) {
                    int n = bin.read(buffer, 0, buffer.length);
                    if (n < 0) {
                        break;
                    }

                    String s = new String(buffer, 0, n);
                    logger.warning("Softphone error: " + s);

		    String[]ss = s.split("[\n]");

		    for (int i = 0; i < ss.length; i++) {
		        System.out.println(ss[i]);
		    }
                }
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "IOException reading softphone " +
                           "error stream: " + ioe, ioe);
            }
        }
    }

    /**
     * Periodically ping the output stream
     */
    class SoftphoneOutputStream extends Thread {
        /** the output stream */
        private OutputStream out;
        
        public SoftphoneOutputStream(OutputStream out) {
            this.out = out;
            
            start();
        }

        /*
         * Ping the softphone every 5 seconds.
         * If the softphone doesn't receive this ping, it
         * assumes that MC has terminated and then terminates itself.
         *
         * Since there is no UI for the softphone when started by MC,
         * this is the way we automatically let the softphone terminate.
         */
	public void run() {
            String s = "ping\n";

            try {
                while (!done) {
                    logger.finest("Pinging softphone");
                    
                    out.write(s.getBytes());
		    out.flush();
                
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                    }
                }
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error writing softphone output " +
                           "stream: " + ioe, ioe);
                 softphone.destroy();
            }
        }

	/*
	 * Send a command to the softphone
	 */
	public void write(String s) {
	    if (out == null) {
		return;
	    }

	    try {
                out.write(s.getBytes());
                out.flush();
	    } catch (IOException e) {
		logger.log(Level.WARNING, "Error writing softphone output "
		    + "stream: " + e);
	    }
	}

    }

    public static void main(String args[]) {
	RemoteControl remoteControl = RemoteControl.getInstance();

	remoteControl.startSipCommunicator("User");

        System.out.println(remoteControl.getSoftphoneAddress());

	remoteControl.sendCommand("Show Config");
    }
}
