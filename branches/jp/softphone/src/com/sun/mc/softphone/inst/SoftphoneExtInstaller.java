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

package com.sun.mc.softphone.inst;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.prefs.Preferences;

import javax.jnlp.ExtensionInstallerService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;

/**
 * Extension installer for installing the Softphone via JNLP
 */
public class SoftphoneExtInstaller {
    /** the name of the softphone jars */
    private static final String SOFTPHONE_JAR_NAME = "/softphone.jar";

    private static final String CORE_AUDIO_NAME = 
	"/libMediaFramework.jnilib";
    
    private static final String INTEL_CORE_AUDIO_NAME = 
	"/intel.libMediaFramework.jnilib";
    
    private static final String POWERPC_CORE_AUDIO_NAME = 
	"/powerpc.libMediaFramework.jnilib";
    
    /** the key for softphone.jar in the user's preferences */
    private static final String SOFTPHONE_JAR_KEY = 
        SoftphoneExtInstaller.class.getName() + ".path";

    /** buffer size when writing the file */
    private static final int BUFFER_SIZE = 16384;
    
    /** the extension installer service */
    private ExtensionInstallerService eis;

    private ExtensionInstallerService installer;

    /** 
     * Creates a new instance of SoftphoneExtInstaller 
     */
    public SoftphoneExtInstaller() {
        System.out.println("Starting installer");
        
        // get the extension installer service
        try {
            installer = getInstallerService();
        } catch (UnavailableServiceException use) {
            System.out.println("Error getting service: " + use);
            use.printStackTrace();
            return;
        }
        
        try {
            // notify that we are starting an installation
            installer.setHeading("Installing Softphone...");
            installer.setStatus("Reading softphone data");
            
            // get the data for the softphone jar
            InputStream jarData = getFile(SOFTPHONE_JAR_NAME);
            if (jarData == null) {
                System.out.println("Softphone data not found");
                installer.setStatus("Softphone data not found");
             
                // give some time to figure out what happened
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ie) {
                    // ignore
                    ie.printStackTrace();
                }
                
                installer.installFailed();
                return;
            }
            
            // find the default install path
            String installPath = installer.getInstallPath();
     
            // create the install path if necessary
            File path = new File(installPath);
            if (!path.exists()) {
                path.mkdirs();
            }
            
            // write the Softphone data
            File phoneFile = new File(path, SOFTPHONE_JAR_NAME);
            installer.setStatus("Writing Softphone data");
            writeFile(jarData, phoneFile);
            
            // write a preference to tell where the softphone is installed
            installer.setStatus("Writing preferences");
            writePreference(phoneFile.getPath());
            
	    if (System.getProperty("os.name").startsWith("Mac OS X")) {
	        installCoreAudio(path);
	    }

            // all finished
            installer.setHeading("");
            installer.setStatus("Install succeeded");
            installer.installSucceeded(false);
        } catch (Exception ex) {
            ex.printStackTrace();
        
            installer.setStatus("Installation failed");
            
            // give some time to figure out what happened
            try {
                Thread.sleep(60 * 1000);
            } catch (InterruptedException ie) {
                // ignore
                ie.printStackTrace();
            }
            
            installer.installFailed();
        }
    }
    
    private void installCoreAudio(File path) throws IOException {
	String arch = System.getProperty("os.arch");

	if (arch.equalsIgnoreCase("i386") == false &&
	        arch.equalsIgnoreCase("ppc") == false) {

	    return;
	}

        installer.setHeading("Installing CoreAudio...");

	String nativeLibrary;

	if (arch.equalsIgnoreCase("i386")) {
	    nativeLibrary = INTEL_CORE_AUDIO_NAME;
	} else {
	    nativeLibrary = POWERPC_CORE_AUDIO_NAME;
	}
	    
        installer.setStatus("Reading " + nativeLibrary);

        // get the data for the Core Audio native library
        InputStream coreAudioData = getFile(nativeLibrary);

        if (coreAudioData == null) {
            System.out.println("Core Audio data not found");
            installer.setStatus("Core Audio data not found");

            // give some time to figure out what happened
            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException ie) {
                // ignore
                ie.printStackTrace();
            }
        } else {
            // write the Core Audio data
            File coreAudioFile = new File(path, CORE_AUDIO_NAME);
            installer.setStatus("Writing Core Audio data");

            writeFile(coreAudioData, coreAudioFile);
	}
    }

    /**
     * Get the extension installer service 
     *
     * @return the extension installer service
     */
    private ExtensionInstallerService getInstallerService() 
        throws UnavailableServiceException
    {   
        if (eis == null) {
            eis = (ExtensionInstallerService)
                ServiceManager.lookup("javax.jnlp.ExtensionInstallerService");
        }
        
        return eis;
    }
    
    /**
     * Read a jar from the current class loader.  This will
     * look for the jar at the top level of the current jar file
     * 
     * @return the data as a byte array
     */
    private InputStream getFile(String name) {
        return getClass().getResourceAsStream(name);
    }

    /**
     * Write data from an input stream to a file
     *
     * @param input stream the input stream to read from
     * @param file the file to write to
     */
    private void writeFile(InputStream input, File file) 
        throws IOException
    {
        OutputStream output = new FileOutputStream(file);
        
        int size = 0;
        byte[] data = new byte[BUFFER_SIZE];
        while ((size = input.read(data)) > 0) {
            output.write(data, 0, size);
        }
        
        output.close();
    }
    
    /**
     * Write a preference with the install path
     *
     * @param path the install path
     */
    private void writePreference(String path) {
        Preferences p = Preferences.userRoot();
        p.put(SOFTPHONE_JAR_KEY, path);
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new SoftphoneExtInstaller();
    }
}
