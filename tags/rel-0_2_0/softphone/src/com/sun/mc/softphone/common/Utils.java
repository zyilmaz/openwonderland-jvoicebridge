/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2000 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * Portions of this software are based upon public domain software
 * originally written at the National Center for Supercomputing Applications,
 * University of Illinois, Urbana-Champaign.
 *
 * Copyright 2007 Sun Microsystems, Inc.
 */
package com.sun.mc.softphone.common;

import java.io.File;
import java.io.IOException;

import java.net.URL;
import java.net.InetAddress;

import java.util.logging.Level;

import java.util.prefs.Preferences;

import com.sun.mc.softphone.SipCommunicator;

import com.sun.voip.Logger;

import com.sun.stun.StunClient;

/**
 * <p>Title: SIP COMMUNICATOR-1.1</p>
 * <p>Description: JAIN-SIP-1.1 Audio/Video Phone Application</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Organisation: LSIIT Laboratory (http://lsiit.u-strasbg.fr)<p>
 * </p>Network Research Team (http://www-r2.u-strasbg.fr))<p>
 * </p>Louis Pasteur University - Strasbourg - France</p>
 *
 * @author Emil Ivov
 * @version 1.1
 */
public class Utils {
    private static Console console = Console.getConsole(Utils.class);

    private static boolean propertiesOverridePreferences = true;

    private String userName;
    private String authUserName;

    private static boolean loadGen;

    public Utils(String userName, boolean loadGen) {
	this.userName = userName;
	Utils.loadGen = loadGen;

	File logDir = null;

        // first check if we need to look at system properties
        String s = System.getProperty(
	    "com.sun.mc.softphone.sip.PROPERTIES_OVERRIDE_PREFERENCES", 
	    "true");
	if (s.equalsIgnoreCase("false")) {
	    propertiesOverridePreferences = false;
	}
        
        String logPath = Utils.getPreference("com.sun.mc.softphone.SERVER_LOG");

        if (logPath != null) {
	    logDir = new File(logPath);
	}

        if (logDir == null || logDir.isDirectory() == false) {
            // check in user.home directory
            logPath = Utils.getProperty("user.home")
                + File.separator + ".sip-communicator";

            logDir = new File(logPath);
	}

        s = "";
	if (userName != null) {
	    s += "." + userName.replaceAll("\\s", "_");
	} 

	File file;

	try {
            if (loadGen || logDir == null || logDir.isDirectory() == false) {
	    	file = File.createTempFile("sipServer" + s + "_", ".log");

                System.setProperty("gov.nist.javax.sip.SERVER_LOG",
		    file.getAbsolutePath());

		file = File.createTempFile("sipCommunicator" + s + "_", ".log");

                Logger.init(file.getAbsolutePath(), loadGen);
	    } else {
	    	file = File.createTempFile("sipServer" + s + "_", ".log", logDir);

                System.setProperty("gov.nist.javax.sip.SERVER_LOG",
		    file.getAbsolutePath());

		file = File.createTempFile("sipCommunicator" + s + "_", ".log", 
		    logDir);

                Logger.init(file.getAbsolutePath(), loadGen);
	    }
	} catch (IOException e) {
	    System.out.println("Unable to create temp file for log!");
        }

	if (loadGen) {
	    int logLevel = getIntPreference("com.sun.mc.softphone.media.LOG_LEVEL", 3);

	    if (logLevel <= Logger.LOG_PRODUCTION) {
	        Logger.suppressSystemOut = true;
	    } else {
	        Logger.suppressSystemOut = false;
	    }
	}

	Logger.writeThru = true;

	if (canWrite(System.getProperty("gov.nist.javax.sip.SERVER_LOG"))) {
	    Logger.forcePrintln("SIP Server log file is "
                + System.getProperty("gov.nist.javax.sip.SERVER_LOG"));
	} else {
            Logger.forcePrintln("Unable to write to log file "
                + System.getProperty("gov.nist.javax.sip.SERVER_LOG"));
            Logger.forcePrintln("Log output will go to System.out");
	}

	initUserName(userName);

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("userName="+userName);
	}

	initAuthenticationUserName();

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("authenticationUserName="+authUserName);
	}

	/*
	 * Try reading these properties from the system config and setting
	 * them in the Preferences.
	 */
	initPreference("com.sun.mc.stun.STUN_SERVER_ADDRESS", null);
	initPreference("com.sun.mc.stun.STUN_SERVER_PORT", null);

	initPreference("com.sun.mc.softphone.media.AUDIO_PORT", "0");

	String registrarAddress = Utils.getPreference(
            "com.sun.mc.softphone.sip.REGISTRAR_ADDRESS", false);

        if (registrarAddress == null) {
	    initPreference("com.sun.mc.softphone.sip.REGISTRAR_ADDRESS", 
	 	"129.148.75.104");
	}

	/*
	 * Had to change the name because of erroneously setting DEFAULT_DOMAIN_NAME
	 */
	removePreference("com.sun.mc.softphone.sip.DEFAULT_DOMAIN_NAME");
	initPreference("com.sun.mc.softphone.sip.DOMAIN_NAME", null);

	initPreference("com.sun.mc.softphone.sip.REGISTRAR_UDP_PORT", "5060");
	initPreference("com.sun.mc.softphone.sip.REGISTRAR_TRANSPORT", "udp");
	initPreference("com.sun.mc.softphone.sip.WAIT_UNREGISTGRATION_FOR", "15");
	
        initPreference("com.sun.mc.softphone.sip.PREFERRED_LOCAL_PORT", "5070");

	initPreference("com.sun.mc.softphone.media.SPEAKER_BUFFER_SIZE", "0");

	initPreference("com.sun.mc.softphone.media.ADD_LATENCY", "40");

	if (getPreference("com.sun.mc.softphone.media.ADD_LATENCY") == null) {
	    setPreference("com.sun.mc.softphone.media.ADD_LATENCY", "40");
	}

	initPreference("com.sun.mc.softphone.media.MICROPHONE_BUFFER_SIZE", "0");

	initPreference("com.sun.mc.softphone.media.SEND_COMFORT_PAYLOAD", "false");
	initPreference("com.sun.mc.softphone.media.RECEIVE_COMFORT_PAYLOAD", "true");
	initPreference("com.sun.mc.softphone.media.FLUSH_SOCKET", "");

	initPreference("com.sun.mc.softphone.media.DETECT_SPEAKING", "false");
	initPreference("com.sun.mc.softphone.media.MAX_DELAYED_PACKETS", "3");
	initPreference("com.sun.mc.softphone.media.CN_THRESHOLD", "50");
	initPreference("com.sun.mc.softphone.media.POWER_THRESHOLD_LIMIT", "1.05");

	initPreference("com.sun.mc.softphone.media.LOG_LEVEL", "3");

        String stackName = "SipCommunicator";
        //Add the stack name to the properties that will pass the stack
        Utils.setProperty("javax.sip.STACK_NAME", stackName);
        if (console.isDebugEnabled()) {
            console.debug("stack name is:" + stackName);
        }

        //Add the retransmission filter param to the properties that will pass the stack
        Utils.setProperty("javax.sip.RETRANSMISSION_FILTER", "true");
        if (console.isDebugEnabled()) {
            console.debug("retransmission filter is:" + stackName);
        }

        String sipStackPath = "gov.nist";
        if (console.isDebugEnabled()) {
            console.debug("stack path=" + sipStackPath);
        }

	String routerPath = "com.sun.mc.softphone.sip.SipCommRouter";
        Utils.setProperty("javax.sip.ROUTER_PATH", routerPath);
        if (console.isDebugEnabled()) {
            console.debug("router path=" + routerPath);
        }

	String outboundProxy = 
	    System.getProperty("javax.sip.OUTBOUND_PROXY_ADDRESS");

	if (outboundProxy == null && 
		getPreference("javax.sip.OUTBOUND_PROXY_ADDRESS") == null) {

	    outboundProxy = "129.148.75.104:5060/udp";

            Utils.setProperty("javax.sip.OUTBOUND_PROXY_ADDRESS", outboundProxy);
	
	    setPreference("javax.sip.OUTBOUND_PROXY_ADDRESS", outboundProxy);
	}

	String stunClientTimeout = getPreference("com.sun.mc.stun.CLIENT_TIMEOUT");

	if (stunClientTimeout != null) {
	    Logger.println("setting com.sun.stun.CLIENT_TIMEOUT to " + stunClientTimeout);
	    System.setProperty("com.sun.stun.CLIENT_TIMEOUT", stunClientTimeout);
	}

	String stunClientRetries = getPreference("com.sun.mc.stun.CLIENT_RETRIES");

	if (stunClientRetries != null) {
	    Logger.println("setting com.sun.stun.CLIENT_RETRIES to " + stunClientRetries);
	    System.setProperty("com.sun.stun.CLIENT_RETRIES", stunClientRetries);
	}

        if (console.isDebugEnabled()) {
            console.debug("outboundProxy=" + outboundProxy);
        }
	
    }

    private void initPreference(String preference, String defaultValue) {
	// first get the value
        String p = Utils.getPreference(preference);
        
        // no luck there -- try the default
        if (p == null || p.length() == 0) {
            p = defaultValue;
        }
        
        // save the value we found
        if (p != null) {
            setPreference(preference, p);
        }
    }

    public static String getProperty(String property) {
	String retval;

        try {
            console.logEntry();
            retval = System.getProperty(property);
        }
        finally {
            console.logExit();
        }
	return retval;
    }

    public static void setProperty(String propertyName, String propertyValue) {
	System.setProperty(propertyName, propertyValue);
    }

    public static URL getResource(String name) {
	String s = "/com/sun/mc/softphone/common/resource/" + name;

	//System.out.println("getResource for " + s + " "
	//    + Utils.class.getResource(s));

        return Utils.class.getResource(s);
    }

    public static String getResourcePath(String resource) {
	return "/com/sun/mc/softphone/common/resource/" + resource;
    }

    /**
     * Get a preference
     */
    public static String getPreference(String preference) {
	return getPreference(preference, propertiesOverridePreferences);
    }
    
    public static String getPreference(String preference, 
	    boolean propertiesOverridePreferences) {

        String pref = null;
        
        // first try the system property
        if (propertiesOverridePreferences) {
            pref = System.getProperty(preference);
        }
        
        // if that didn't exist, try a saved value
        if (pref == null) {
            Preferences prefs = Preferences.userNodeForPackage(Utils.class);
            pref = prefs.get(preference, null);
        }
    
        if (Logger.logLevel > Logger.LOG_DETAIL) {
            Logger.println("Request for pref: " + preference + 
                           " returns " + pref);
        }
        
        return pref;
    }

    /*
     * Get integer preference
     */
    public static int getIntPreference(String preference) {
	return getIntPreference(preference, 0);
    }

    public static int getIntPreference(String preference, 
	    int notFoundReturnValue) {

	String s = getPreference(preference);

	if (s == null || s.equals("")) {
	    return notFoundReturnValue;
	}

	try {
	    return Integer.parseInt(s);
	} catch (NumberFormatException e) {
	    System.out.println("bad integer for " + preference
		+ ".  returning 0 " + e.getMessage());
	}
	return notFoundReturnValue;
    }

    /*
     * Get double preference
     */
    public static double getDoublePreference(String preference) {
        String s = getPreference(preference);

        if (s == null || s.equals("")) {
            return -1F;
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            System.out.println("bad float for " + preference
                + ".  returning 0 " + e.getMessage());
        }
        return -1F;
    }

    public static boolean isMacOS() {
	String s = getPreference("com.sun.mc.softphone.media.FAKE_MAC");

	if (s != null && s.equalsIgnoreCase("true")) {
	    return true;
	}

        s = System.getProperty("os.name");

	if (System.getProperty("MacOS") != null) {
	    System.out.println("Faking MacOs...");
	    return true;
	}

        return s.startsWith("Mac OS X");
    }

    public static boolean isLinux() {
	String osName = System.getProperty("os.name");

	return System.getProperty("os.name").equalsIgnoreCase("Linux");
    }
    
    public static boolean isWindows() {
	String osName = System.getProperty("os.name");

	return osName.indexOf("Windows") >= 0;
    }
    
    /*
     * Remove a preference
     */
    public static void removePreference(String preference) {
        Preferences prefs = Preferences.userNodeForPackage(Utils.class);

	prefs.remove(preference);
    }

    /**
     * Set a preference
     */
    public static void setPreference(String preference, String value) {
	if (preference.indexOf("LOG_LEVEL") >= 0) {
	    try {
	        Logger.logLevel = Integer.parseInt(value);

		Logger.println("log level is " + Logger.logLevel);

		if (loadGen && Logger.logLevel <= Logger.LOG_PRODUCTION) {
		    Logger.suppressSystemOut = true;
		}

		if (Logger.logLevel <= Logger.LOG_PRODUCTION) {
		    StunClient.setLogLevel(Level.INFO);
		} else if (Logger.logLevel >= Logger.LOG_INFO) {
		    StunClient.setLogLevel(Level.FINE);
		} else if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    StunClient.setLogLevel(Level.FINER);
		} else if (Logger.logLevel >= Logger.LOG_DETAIL) {
		    StunClient.setLogLevel(Level.FINEST);
		}
	    } catch (NumberFormatException e) {
                System.out.println("Can't set media log level to " 
		    + value);
	    }
	}

	if (preference.indexOf("SAMPLE_RATE") >= 0) {
	    int sampleRate;

	    if (value.length() == 0) {
		sampleRate = 16000;
	    } else {
	        try {
		    sampleRate = (int)Float.parseFloat(value);
	        } catch (NumberFormatException e) {
		    System.out.println("Invalid sample rate " + value
		        + " using 16000");
		    sampleRate = 16000;
		    value = "16000";
	        }

	        if (sampleRate > 48000) {
		    System.out.println("Invalid sample rate " + value
		        + " using 48000");

		    value = "48000";
	        } else if (sampleRate > 44100 && sampleRate < 48000) {
		    System.out.println("Invalid sample rate " + value
		        + " using 44100");

		    value = "44100";
	        } else if (sampleRate > 32000 && sampleRate < 44100) {
		    System.out.println("Invalid sample rate " + value
		        + " using 32000");

		    value = "32000";
	        } else if (sampleRate > 16000 && sampleRate < 32000) {
		    System.out.println("Invalid sample rate " + value
		        + " using 16000");

		    value = "16000";
	        } else if (sampleRate != 8000 && sampleRate < 16000) {
		    System.out.println("Invalid sample rate " + value
		        + " using 8000");

		    value = "16000";
	        }
	    }

if (false) {
	    if (isMacOS() && sampleRate != 44100) {
	        System.out.println(
		    "Defaulting to only sample rate supported which is 44100");
		value = "44100";
	    }
}
	}

	if (preference.indexOf("CHANNELS") == 0) {
	    int channels;

	    if (value.length() == 0) {
		channels = 2;
	    } else {
	        try {
		    channels = Integer.parseInt(value);

if (false) {
		    if (isMacOS() && channels != 2) {
		        System.out.println(
			    "Defaulting to only number of supported channels which is 2");

		        value = "2";
		    }
}
	        } catch (NumberFormatException e) {
		    System.out.println("Invalid channels, defaulting to 2");
		    value = "2";
	        }
	    }
	}

	if (preference.indexOf("ENCODING") >= 0) {
	    if (value.length() == 0 || value.equalsIgnoreCase("PCM")) {
		value = "PCM";
	    } else if (value.equalsIgnoreCase("PCMU") == false &&
	            value.equalsIgnoreCase("SPEEX") == false) {

		System.out.println("Invalid encoding " + value 
		    + " defaulting to PCM");
	        value = "PCM";
	    }
	}

	if (preference.indexOf("MTU") >= 0) {
	    int mtu = 0;

            try {
                mtu = (int)Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Invalid MTU " + value
                    + " defaulting to 0 (unlimited)");
            }

	    if (mtu != 0 && mtu < 300) {
                System.out.println("Invalid MTU " + value
                    + " defaulting to 0 (unlimited)");
		mtu = 300;
	    }
	}

	if (preference.indexOf("SPEAKER_BUFFER_SIZE") >= 0) {
	    try {
		/*
		 * Round up to 20ms boundary
		 */
	        value = String.valueOf(((Integer.parseInt(value) + 19) / 20) * 20);
            } catch (NumberFormatException e) {
                System.out.println("Invalid speaker buffer size " + value
                    + ", ignoring");
            }
	}

	if (preference.indexOf("MICROPHONE_BUFFER_SIZE") >= 0) {
            try {
		/*
		 * Round up to 20ms boundary
		 */
		value = String.valueOf(((Integer.parseInt(value) + 19) / 20) * 20);
            } catch (NumberFormatException e) {
                System.out.println("Invalid microphone buffer size " + value
                    + ", ignoring");
            }
	}

        Preferences prefs = Preferences.userNodeForPackage(Utils.class);

        prefs.put(preference, value);

	try {
	    prefs.sync();
	} catch (Exception e) {
	}
    }

    private void initUserName(String userName) {
        if (userName == null || userName.length() == 0) {
	    userName = getPreference("com.sun.mc.softphone.sip.USER_NAME");

	    if (userName == null) {
                userName = System.getProperty("user.name");

                if (userName == null) {
                    userName = "User";
                } 
            }
        }

	userName = cleanUserName(userName);

	if (this.userName == null) {
	    initPreference("com.sun.mc.softphone.sip.USER_NAME", userName);
	}

	this.userName = userName;
    }

    public String getUserName() {
	return userName;
    }

    public void setUserName(String userName) {
	userName = cleanUserName(userName);

	setPreference("com.sun.mc.softphone.sip.USER_NAME", userName);
	this.userName = userName;
    }

    public static String cleanUserName(String userName) {
        userName = userName.replaceAll("\\s", "_");         // get rid of white space
        userName = userName.replaceAll("[:<>;]", "_");      // get rid of bad characters

        int ix = userName.indexOf("@");

        if (ix < 0) {
            userName = userName.replaceAll("[\\.]", "");        // get rid of periods
        } else {
            String name = userName.substring(0, ix);

            name.replaceAll("[\\.]", "");        // get rid of periods in name

            userName = name + userName.substring(ix);
        }

	return userName;
    }

    private void initAuthenticationUserName() {
	String authUserName = getPreference("com.sun.mc.softphone.sip.AUTHENTICATION_USER_NAME");

	if (authUserName == null || authUserName.length() == 0) {
            authUserName = userName;
        }

	authUserName = cleanUserName(authUserName);

	if (this.authUserName == null) {
	    initPreference("com.sun.mc.softphone.sip.AUTHENTICATION_USER_NAME", authUserName);
	}

	this.authUserName = authUserName;
    }

    public String getAuthenticationUserName() {
	return authUserName;
    }

    public void setAuthenticationUserName(String authUserName) {
	authUserName = cleanUserName(authUserName);

	setPreference("com.sun.mc.softphone.sip.AUTHENTICATION_USER_NAME", authUserName);
	this.authUserName = authUserName;
    }

    private boolean canWrite(String logFile) {
        File file = null;
 
        try {
            file = new File(System.getProperty("gov.nist.javax.sip.SERVER_LOG"));
        } catch (Exception e) {
	    return false;
        }

	if (file.exists()) {
	    return file.canWrite();
	}

	try {
	    file.createNewFile();
	} catch (IOException e) {
	    return false;
	}

	return true;
    }

    public static boolean isJdk14() {
	String s = System.getProperty("java.version");

        if (s.indexOf("1.4.") >= 0) {
	    return true;
	}

	return false;
    }

}
