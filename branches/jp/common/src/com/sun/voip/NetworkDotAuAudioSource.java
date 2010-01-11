/*
 * Copyright 2008 Sun Microsystems, Inc.
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

/*
 * Read a .au audio file from the network.
 */
package com.sun.voip;

import com.sun.voip.FileAudioSource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import java.nio.ShortBuffer;

import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 * Read audio samples from a .au file from the network.
 */
public class NetworkDotAuAudioSource extends DotAuAudioSource {

    private static final Logger logger =
            Logger.getLogger(NetworkDotAuAudioSource.class.getName());
    
    private static int bufferSize = 256 * 1024;

    private String path;

    private URL url;
 
    // a thread to read the data from the web
    private ReaderThread readerThread;
    
    // buffer time
    private long bufferTimeEnd;
    
    // decoded data
    private InputStream pipeIn;

    private File cacheFile;
    private FileOutputStream fos;
    private BufferedOutputStream bos;

    private int headerLength;

    static {
	String s = System.getProperty("com.sun.voip.server.NETWORK_AUDIO_BUFFER_SIZE");

	if (s != null && s.length() > 0) {
	    try {
	        int n = Integer.parseInt(s);

		if (n <= 0) {
		    logger.warning("Invalid NETWORK_AUDIO_BUFFER_SIZE:  " + s);
		} else {
		    bufferSize = n;
		    logger.info("SETTING NETWORK BUFFER SIZE TO " + n);
		}
	    } catch (NumberFormatException e) {
		logger.warning("Invalid NETWORK_AUDIO_BUFFER_SIZE:  " + s);
	    }
	}
    }

    private boolean useCachedFile;

    /*
     * Read audio data from a URL.
     */
    public NetworkDotAuAudioSource(String path) throws IOException {
	path = path.replaceAll(" ", "%20");

	url = new URL(path);

	try {
	    if ((useCachedFile = useCachedFile(path)) == true) {
	        return;
	    }
	} catch (IOException e) {
	    logger.warning(path + " Can't use cache file:  " + e.getMessage());
	}

	bufferTimeEnd = System.currentTimeMillis() + 2000;

	InputStream in = url.openStream();

	BufferedInputStream bis = new BufferedInputStream(in, bufferSize);

	byte[] header = readHeader(bis, path);

	if (bos != null) {
	    bos.write(header, 0, header.length);
	    bos.flush();
	}

	headerLength = header.length;

	readerThread = new ReaderThread(this, bis, bos, path);

	pipeIn = readerThread.getInputStream();

	readerThread.start();
    }

    private boolean useCachedFile(String path) throws IOException {
	String soundCachePath = TreatmentManager.getSoundCachePath();

	if (soundCachePath == null || soundCachePath.length() == 0) {
	    logger.fine("Sound path is null");
	    return false;
	}

	int ix = path.lastIndexOf("/");

	if (ix >= 0) {
	    path = path.substring(ix + 1);
	}

	path = soundCachePath + File.separator + path;

	try {
	    super.initialize(path);

            try {
		File cacheFile = new File(path);

                URLConnection urlConnection = url.openConnection();

		urlConnection.setIfModifiedSince(cacheFile.lastModified());

		urlConnection.connect();

		int response = ((HttpURLConnection) urlConnection).getResponseCode();

		if (response == HttpURLConnection.HTTP_NOT_MODIFIED) {
		    return true;
		}

		cacheFile.delete();
            } catch (Exception e) {
                logger.warning("Unable to open connection to " + url);
	    }
	} catch (IOException e) {
	    logger.warning("Couldn't open super:  " + e.getMessage());
	}

	path += ".part";

	cacheFile = new File(path);

	cacheFile.delete();

	try {
	    if (cacheFile.createNewFile() == false) {
		logger.warning("can't create cache file " + path);
	    } else {
		fos = new FileOutputStream(cacheFile);
		bos = new BufferedOutputStream(fos, bufferSize);
		this.cacheFile = cacheFile;
		logger.info("Creating cache file " + path);
	    }
	} catch (IOException e) {
	    fos = null;
	    bos = null;

	    logger.warning("Unable to cache " + path + ": " 
		+ e.getMessage());
	}

	return false;
    }

    private static final int MAX_NOT_ENOUGH_DATA_COUNT = 500;  // 10 seconds

    private long notEnoughDataCount;

    public int[] getLinearData(int sampleTime) throws IOException {
	if (useCachedFile == true) {
	    return super.getLinearData(sampleTime);
	}

	if (done) {
	    return null;
	}

        int byteLength = (int) ((sampleTime / 1000f) * getSampleRate() * getChannels());

	if (getEncoding() == AudioSource.LINEAR) {
	    byteLength *= 2;
	}
        
        /*
	 * If we have buffer time specified, make sure we have passed it.
         * If we haven't passed the buffer time, return silence
	 */
        if (System.currentTimeMillis() < bufferTimeEnd) {
            return new int[byteLength / 2];  // silence while we buffer
	}

        int available = pipeIn.available();
        
	if (available < byteLength) {
	    if (readerThread != null && readerThread.quit()) {
		logger.fine("Done with treatment " + path);

		/*
		 * Notify writer thread if blocked.
		 */
		synchronized (this) {
		    notifyAll();
		}
		return null;
	    }
	    
	    logger.fine("Not enough data " + available 
		+ " need " + byteLength);

	    notEnoughDataCount++;

	    if (notEnoughDataCount >= MAX_NOT_ENOUGH_DATA_COUNT) {
		/*
		 * We haven't gotten any data for a long time.
		 * It's time to give up.
		 */
		done();
	    }

            // return silence
            return new int[byteLength / 2];
        }
       
	notEnoughDataCount = 0;

        // read data from the pipe
        byte[] data = new byte[byteLength];
        
	//long start = System.currentTimeMillis();

	int bytesToRead = byteLength;

	while (bytesToRead > 0) {
	    int n = pipeIn.read(data);

	    bytesToRead -= n;

	    if (bytesToRead > 0) {
		logger.finest("looping to read... still need " + bytesToRead);
	    }
	}

	return processLinearData(data);
    }

    public void rewind() throws IOException {
	if (useCachedFile == true) {
	    super.rewind(); 
	    return;
	}

	done();
    }
    
    private boolean cacheFileOK;

    public void setCacheFileOK() {
	cacheFileOK = true;
    }

    private boolean done;

    public void done() {
	if (done) {
	    return;
	}

	if (useCachedFile == true) {
	    super.done(); 
	    return;
	}

	done = true;

	if (fos != null) {
	    try {
	        fos.close();
	    } catch (IOException e) {
		logger.warning("Unable to close file output writer for " + path);
	    }
	}

	if (bos != null) {
	    try {
	        bos.flush();
	        bos.close();
	    } catch (IOException e) {
		logger.warning("Unable to close buffered output writer for " + path);
	    }
	}

	if (cacheFile != null) {
	    if (cacheFileOK == false || cacheFile.length() <= headerLength) {
		logger.info("Removing invalid cache file, ok " + cacheFileOK
		    + " len " + cacheFile.length());
		cacheFile.delete();
	    } else {
	        String path = cacheFile.getAbsolutePath();

	        int ix = path.indexOf(".part");

	        path = path.substring(0, ix);

	        File file = new File(path); 

	        cacheFile.renameTo(file);
	
		logger.info("Renamed to " + path + " len " + file.length());
	    }
	}

        logger.fine("Player for " + path + " done.  readerThread " 
	    + readerThread);
        
        if (readerThread != null) {
            readerThread.setQuit();
            readerThread = null;
        }
    }
    
    /**
     * Read data from a URL in a separate thread
     */
    static class ReaderThread extends Thread {
	private NetworkDotAuAudioSource parent;
        private String path;
        
        private PipedOutputStream pipeOut;
        private PipedInputStream pipeIn;

   	private BufferedInputStream bis;
   	private BufferedOutputStream bos;

        private boolean quit = false;
        
        // a monitor for our parent audio source.  If the parent gets GC'd, 
        // we need to go away too
        private ReaderMonitor monitor;
        
        public ReaderThread(NetworkDotAuAudioSource parent, BufferedInputStream bis,
		BufferedOutputStream bos, String path) throws IOException {

	    this.parent = parent;
	    this.bis = bis;
	    this.bos = bos;
            this.path = path;

	    setName(path);

            // we don't want to read from a URL directly, as this
            // can easily break if the URL becomes unreachable.  Instead,
            // we create a pipe, and read data into the pipe in a separate
            // thread.
            pipeOut = new PipedOutputStream();
            pipeIn = new LargePipedInputStream(parent, bufferSize);
            pipeIn.connect(pipeOut);
            
	    /*
	     * Read the header synchronously.  
	     * This potentially could block which would be very bad!
	     * We think that since we were able to connect to the URL,
	     * we should not block reading the header.
	     * 
	     * In the future, we will consider modifying the bridge
	     * to deal better with the posibility of blocking.
	     */
            monitor = new ReaderMonitor(path, this, parent);
            monitor.start();
        }
        
	public InputStream getInputStream() throws IOException {
            return pipeIn; 
        }

        public void run() {
            long startTime = System.currentTimeMillis();
            
	    int total = 0;

            try {
                while (!quit()) {
		    synchronized (parent) {
			while (false && !quit && pipeIn.available() > 3 * bufferSize / 4 ) {
                            try {
                                logger.finer(getName() + " waiting to write... available "
                                    + pipeIn.available());
                                parent.wait();
                            } catch (InterruptedException e) {
                            }
                        }
		    }

		    if (quit) {
			logger.fine(getName() + " quit " + quit);
			break;
		    }

		    int available = bis.available();

		    byte[] buf;

		    if (available > 0) {
		        buf = new byte[bis.available()];
		    } else {
			/*
			 * We have to read at least one byte to get end of stream notification
			 */
			buf = new byte[1];
		    }

		    if (bis.read(buf, 0, buf.length) < 0) {
			parent.setCacheFileOK();
			logger.fine("END OF STREAM!");
			break;  // end of stream
		    }

		    if (bos != null) {
	    		bos.write(buf, 0, buf.length);
	    	 	bos.flush();
		    }

		    total += buf.length;

		    pipeOut.write(buf, 0, buf.length);
		    pipeOut.flush();
                }
            } catch (IOException e) {
                logger.warning("Error in reader for " + getName() + " " + e.getMessage());
            } finally {
                logger.fine(getName() + " reader thread exiting");
                 
                if (monitor != null && monitor.isAlive()) {
                    monitor.setQuit();
                }

		new Closer(bis);
		setQuit();
	    }
        }
        
        public synchronized boolean quit() {
            return quit;
        }
        
        public synchronized void setQuit() {
            quit = true;

	    logger.fine("quitting... " + getName());

	    synchronized (parent) {
                parent.notifyAll();
	    }

	    new Closer(bis);
        }
    }
    
    static class Closer extends Thread {
	BufferedInputStream bis;

	public Closer(BufferedInputStream bis) {
	    this.bis = bis;
	    start();
	}

	public void run() {
	    try {
	        bis.close();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * Monitor a reader thread and clean up if the parent goes away
     */     
    static class ReaderMonitor extends Thread {
        private String path;
        
        private ReferenceQueue refQueue;
        private Reference<NetworkDotAuAudioSource> sourceRef;
        
        private ReaderThread reader;
        private boolean quit = false;
        
        public ReaderMonitor(String path, ReaderThread reader, NetworkDotAuAudioSource parent) {
            this.path = path;
            this.reader = reader;
            
            // create a weak reference to the parent to track when it goes
            // away
            refQueue = new ReferenceQueue();
            sourceRef = new WeakReference<NetworkDotAuAudioSource>(parent, refQueue);
        }
        
        @Override
        public void run() {
            try {
                refQueue.remove();
                
                logger.fine(path + " cleaning up reader thread");
                
                reader.setQuit();
            } catch (InterruptedException ie) {
                Level level = quit() ? Level.FINER : Level.WARNING;
                logger.log(level, "Reference monitor interrupted");
            }
        }
        
        private synchronized boolean quit() {
            return quit;
        }
        
        private synchronized void setQuit() {
            quit = true;
            interrupt();
        }
    }
    
    static class LargePipedInputStream extends PipedInputStream {
	NetworkDotAuAudioSource parent;

        public LargePipedInputStream(NetworkDotAuAudioSource parent, int size) {
            super();

	    this.parent = parent;            
            buffer = new byte[size];
        }

	public int read(byte[] b) throws IOException {
	    int available = available();

	    int n = super.read(b);

	    if (false && available < buffer.length / 2) {
		synchronized (parent) {
		    parent.notifyAll();
		}
	    }

	    return n;
	}
    }

}
