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

package com.sun.mc.softphone.media;

import com.sun.mc.softphone.common.Utils;

import com.sun.voip.AudioConversion;
import com.sun.voip.CurrentTime;
import com.sun.voip.Logger;
import com.sun.voip.LowPassFilter;
import com.sun.voip.MediaInfo;
import com.sun.voip.Recorder;
import com.sun.voip.RtcpPacket;
import com.sun.voip.RtcpSenderPacket;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpSenderPacket;
import com.sun.voip.RtpSocket;
import com.sun.voip.SdpInfo;
import com.sun.voip.SpeechDetector;
import com.sun.voip.SpeexEncoder;
import com.sun.voip.SpeexException;
import com.sun.voip.Ticker;
import com.sun.voip.TickerException;
import com.sun.voip.TickerFactory;
import com.sun.voip.TreatmentManager;
import com.sun.voip.Util;

import java.io.*;

import java.util.LinkedList;
import java.util.Vector;

import java.net.InetSocketAddress;

import  javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

/**
 * sends data from the microphone to a remote host
 */
public class AudioTransmitter extends Thread {

    private RtpSocket rtpSocket;		// socket for sending 
    private RtpSenderPacket rtpSenderPacket;   // packet for sending data
    private byte[] rtpSenderData;

    private RtcpSenderPacket rtcpSenderPacket;

    private InetSocketAddress remoteIsa;

    private TreatmentManager treatmentManager;

    private Microphone microphone;

    private Cipher cipher;
    private String encryptionKey;
    private String encryptionAlgorithm;

    private int lowVolumeCount = 0;

    private final int MIN_THRESHOLD_COUNT = 150;  // 3 seconds
    private int minThresholdCount = MIN_THRESHOLD_COUNT;

    private final int VOLUME_THRESHOLD = 100;
    private int volumeThreshold = VOLUME_THRESHOLD;

    private boolean sendComfortPayload = true;

    private byte comfortNoiseLevel;

    /* the default comfort noise level */
    private static final String COMFORT_NOISE_LEVEL_PROPERTY =
        "com.sun.mc.softphone.media.COMFORT_NOISE_LEVEL";

    /* enable / disable comfort noise */
    private static final String SEND_COMFORT_PAYLOAD_PROPERTY =
        "com.sun.mc.softphone.media.SEND_COMFORT_PAYLOAD";

    /* volume threshold for speech */
    private static final String VOLUME_THRESHOLD_PROPERTY =
        "com.sun.mc.softphone.media.VOLUME_THRESHOLD";

    /* volume count for speech */
    private static final String MIN_THRESHOLD_COUNT_PROPERTY =
        "com.sun.mc.softphone.media.MIN_THRESHOLD_COUNT";

    /* detect speaking */
    private static final String DETECT_SPEAKING =
	"com.sun.mc.softphone.media.DETECT_SPEAKING";

    private boolean done;

    private MediaInfo transmitMediaInfo;

    private int sampleRate;
    private int encoding;
    private int channels;
    private int telephoneEventPayload = 0;
    private byte mediaPayload;

    private Recorder micRecorder;
    private Recorder ulawRecorder;
    private Recorder rtpRecorder;

    private Recorder recorder;
    private boolean recordRtp;

    private boolean debugDtmf = false;
    private String dtmfKey = null;
    private Object dtmfKeyLock = new Object();

    private String treatmentToPlay;
    private int treatmentRepeats = -1;
    
    private boolean isMuted;

    private SpeechDetector speechDetector;

    private int leftOverCount = 0;
    private boolean needToSetMark;

    private SpeexEncoder speexEncoder;
    private int pcmPacketSize;
    private int speexQuality = 0;
    private int speexComplexity = 0;

    private AudioReceiver audioReceiver;

    private PacketGenerator packetGenerator;

    private boolean sendKeepAlive;

    private static final String SPEEX_ENABLED_PROPERTY =
	"com.sun.mc.softphone.media.SPEEX_ENABLED";

    private static final String SPEEX_QUALITY_PROPERTY =
	"com.sun.mc.softphone.media.SPEEX_QUALITY";

    private static final String SPEEX_COMPLEXITY_PROPERTY =
	"com.sun.mc.softphone.media.SPEEX_COMPLEXITY";

    /**
     * Constructor.
     */
    public AudioTransmitter(RtpSocket rtpSocket, String encryptionKey, 
	    String encryptionAlgorithm) throws IOException {

	this.rtpSocket = rtpSocket;
	this.encryptionKey = encryptionKey;
	this.encryptionAlgorithm = encryptionAlgorithm;

        String s = Utils.getPreference(SEND_COMFORT_PAYLOAD_PROPERTY);

        if (s != null && s.equalsIgnoreCase("false")) {
            sendComfortPayload = false;

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("Comfort payload sending disabled");
	    }
        } else {
            sendComfortPayload = true;

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("Comfort payload sending enabled");
	    }
        }

        comfortNoiseLevel = RtpPacket.defaultNoiseLevel;

        s = Utils.getPreference(COMFORT_NOISE_LEVEL_PROPERTY);

        if (s != null) {
            comfortNoiseLevel = (byte)Integer.parseInt(s);
        }

	s = System.getProperty("com.sun.mc.softphone.sip.DEBUG_DTMF");

	if (s != null) {
	    Logger.println("Setting debugDtmf to true");
	    debugDtmf = true;
	}

	if (encryptionKey != null) {
	    try {
	        if (encryptionKey.length() < 8) {
		    encryptionKey += String.valueOf(System.currentTimeMillis());
	        }

	        if (encryptionKey.length() > 8 &&
			encryptionAlgorithm.equals("DES")) {
	            encryptionKey = encryptionKey.substring(0, 8);
	        }

	        byte[] keyBytes = encryptionKey.getBytes();
	        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 
		    encryptionAlgorithm);

	        cipher = Cipher.getInstance(encryptionAlgorithm);
	        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		Logger.println("Encrypt cipher initialized for " 
		    + encryptionAlgorithm);
	    } catch (Exception e) {
		throw new IOException("Crytpo initialization failed.  " 
		    + e.getMessage());
	    }
	}

	setSpeechDetectorParameters();
    }

    public void initialize(SdpInfo sdpInfo, MediaInfo transmitMediaInfo,
	    Microphone microphone) throws LineUnavailableException, 
	    IOException {

	this.transmitMediaInfo = transmitMediaInfo;
	this.microphone = microphone;

	encoding = transmitMediaInfo.getEncoding();
	sampleRate = transmitMediaInfo.getSampleRate();
	channels = transmitMediaInfo.getChannels();

if (false) {
	try {
            MediaInfo m = new MediaInfo(transmitMediaInfo.getPayload(),
                RtpPacket.PCM_ENCODING, sampleRate, channels, false);

            micRecorder = new Recorder("send.mic.au", "au", m);

	    if (encoding == RtpPacket.PCMU_ENCODING) {
                m = new MediaInfo(transmitMediaInfo.getPayload(),
                    RtpPacket.PCMU_ENCODING, sampleRate, channels, false);

	        ulawRecorder = new Recorder("send.ulaw.au", "au", m);
	    }

	    rtpRecorder = new Recorder("send.rtp", "rtp", m);
        } catch (IOException e) {
            throw new IOException("Can't create recorders! "
		+ e.getMessage());
        }
}

	String s = Utils.getPreference(
	   "com.sun.mc.softphone.USE_TELEPHONE_EVENT_PAYLOAD");

	if (s == null || s.equalsIgnoreCase("false") == false) {
	    telephoneEventPayload = sdpInfo.getTelephoneEventPayload();
	}

	mediaPayload = transmitMediaInfo.getPayload();

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("AudioTransmitter:  payload " + mediaPayload
	        + " encoding " + encoding + " sampleRate " + sampleRate
	        + " channels " + channels);
	}
	
        rtpSenderPacket = new RtpSenderPacket(encoding, sampleRate, channels);
        rtpSenderPacket.setRtpPayload(mediaPayload);
        rtpSenderPacket.setMark();

	rtpSenderData = rtpSenderPacket.getData();

	remoteIsa = new InetSocketAddress(sdpInfo.getRemoteHost(), 
	    sdpInfo.getRemotePort());

	s = Utils.getPreference(
           "com.sun.mc.softphone.media.SEND_ADDRESS");

	if (s != null && s.length() > 0) {
	    String[] tokens = s.split(":");

	    if (tokens.length == 2) {
	        try {
		    int port = Integer.parseInt(tokens[1]);

		    InetSocketAddress isa1 = new InetSocketAddress(
			tokens[0], port);

		    if (isa1.isUnresolved()) {
			Logger.println("Unresolved address:  " + s);
		    } else {
			remoteIsa = isa1;
			Logger.println("Sending to " + remoteIsa);
		    } 
	        } catch (NumberFormatException e) {
			Logger.println("Invalid port:  " + s);
		}
	    } else {
		Logger.println("Bad address:  " + s);
	    }
	}

        rtpSenderPacket.setSocketAddress(remoteIsa);

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Sending to " + remoteIsa);
	}

	rtcpSenderPacket = 
	    new RtcpSenderPacket(rtpSenderPacket.getSynchronizationSource());

	InetSocketAddress isa = new InetSocketAddress(sdpInfo.getRemoteHost(), 
	    sdpInfo.getRemotePort() + 1);

	rtcpSenderPacket.setSocketAddress(isa);

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Rtcp sender reports will be sent to " 
	        + rtcpSenderPacket.getSocketAddress());
	}
	
	if (microphone != null) {
            dtmfBuffer = new DtmfBuffer(microphone);
	} else {
	    dtmfBuffer = new DtmfBuffer(sampleRate, channels);
	}

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("AudioTransmitter Initialize done... "
	        + "sampleRate " + sampleRate + " channels " + channels);
	}

        if (recordingPath != null) {
            try {
                startRecording(recordingPath, recordingType);
            } catch (IOException e) {
                Logger.println("Unable to record to " + recordingPath
                    + " recording type " + recordingType
                    + " " + e.getMessage());
            }
            recordingPath = null;
            recordingType = null;
        }

	setPriority(Thread.MAX_PRIORITY);
	setName("AudioTransmitter");
	start();

	if (treatmentToPlay != null) {
	    playTreatment(treatmentToPlay, treatmentRepeats);
	    treatmentToPlay = null;
	}
    }

    public void setAudioReceiver(AudioReceiver audioReceiver) {
	this.audioReceiver = audioReceiver;
    }

    public void mediaChanged(byte mediaPayload) {
        rtpSenderPacket.setX(true);
        rtpSenderPacket.setRtpPayload(RtpPacket.CHANGE_MEDIA_PAYLOAD);
        rtpSenderPacket.setMark();

        rtpSenderData[RtpPacket.HEADER_SIZE + 3] = 3;  // extended hdr size
        rtpSenderData[RtpPacket.HEADER_SIZE + 7] = mediaPayload;

	rtpSenderPacket.setLength(RtpPacket.HEADER_SIZE + 8);

	Logger.println("Notifying remote of new payload " + mediaPayload);
        sendPacket();

	done();
    }

    /*
     * All done.  Stop everything.
     */
    public void done() {
	if (done) {
	    return;
	}

	done = true;

	if (micRecorder != null) {
	    micRecorder.done();
	}

        if (ulawRecorder != null) {
	    ulawRecorder.done();
	}

        if (rtpRecorder != null) {
	    rtpRecorder.done();
	}

	synchronized(this) {
	    if (isAlive()) {
	        try {
		    wait();
	        } catch (InterruptedException e) {
	        }
	    }
	}

	stopTreatment();
    }

    private Integer treatmentLock = new Integer(0);

    public void playTreatment(String audioFile, int repeats) 
        throws IOException 
    {
	if (sampleRate == 0) {
	    treatmentToPlay = audioFile;	// defer until connected
	    treatmentRepeats = repeats;
            return;
	}

	synchronized(treatmentLock) {
  	    treatmentManager = new TreatmentManager(
	        audioFile, repeats, sampleRate, channels);
	}
    }

    public void pauseTreatment(boolean pause) {
	if (treatmentManager == null) {
	    return;
	}

	synchronized(treatmentLock) {
	    treatmentManager.pause(pause);
	}
    }

    public void stopTreatment() {
	if (treatmentManager == null) {
	    return;
	}

	synchronized(treatmentLock) {
	    treatmentManager.stopTreatment();
	}
    }

    /* DTMF tone generation */
    private DtmfBuffer dtmfBuffer;

    /*
     * Start sending a DTMF tone rather than microphone audio
     */
    public void startDtmf(String dtmf) {
	if (dtmfBuffer == null) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Dtmf buffer not initialized");
	    }
	    return;	// not initialized yet
	}

	new DtmfThread(dtmf);
    }

    class DtmfThread extends Thread {

	private String dtmf;

	public DtmfThread(String dtmf) {
	    this.dtmf = dtmf;
	    start();
	}

	public void run() {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Dtmf string:  '" + dtmf + "'");
	    }

	    for (int i = 0; i < dtmf.length(); i++) {
	        synchronized (dtmfKeyLock) {
                    dtmfKey = dtmf.substring(i, i + 1);

                    if (telephoneEventPayload == 0) {
                        dtmfBuffer.setDtmf(dtmfKey);
                    }

		    /*
		     * The next time through the loop to read the
		     * microphone, dtmfKey will be detected as 
		     * non-null and the key will be sent rather
		     * than the microphone data.
		     */
		    try {
		        dtmfKeyLock.wait();
		    } catch (InterruptedException e) {
		    }
	        }
	    }

	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Done sending Dtmf string:  '" + dtmf + "'");
	    }
        }
    }

    /*
     * Stop sending a DTMF tone rather than microphone audio
     */
    public void stopDtmf() {
        if (telephoneEventPayload == 0) {
	    synchronized (dtmfKeyLock) {
                dtmfKey = null;

		dtmfKeyLock.notifyAll();
	    }
        }
    }

    public void mute(boolean isMuted) {
	this.isMuted = isMuted;

	if (microphone == null) {
	    return;
	}

	microphone.mute(isMuted);
	
	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("Softphone telling microphone to "
		+ (isMuted ? "mute" : "unmute"));
	} 
    }

    public boolean isMuted() {
	if (microphone == null) {
	    return isMuted;
	}

	return microphone.isMuted();
    }

    /*
     * Read data from the microphone and if there is a connection to
     * the conference bridge server, send the data to the server.
     */
    private int packetsSent = 0;

    private int micOverflow = 0;
    private int lastMicOverflow = 0;

    private boolean firstTime = true;

    private boolean speaking;

    private boolean comfortPayloadSent = false;

    private long timeLastReportSent;

    public void run() {
	int expectedPacketSize;
        int microphoneDataSize;

	if (microphone != null) {
	    microphoneDataSize = 
		microphone.getBufferSize(RtpPacket.PACKET_PERIOD);
	} else {
            microphoneDataSize = rtpSenderPacket.getDataSize(
		RtpPacket.PCM_ENCODING, sampleRate, channels);
	}

        expectedPacketSize = microphoneDataSize;

	if (encoding == RtpPacket.PCMU_ENCODING) {
	    expectedPacketSize /= 2;
	} 

	expectedPacketSize += RtpPacket.HEADER_SIZE;
	    
	byte[] microphoneData = new byte[microphoneDataSize];

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Microphone data size " + microphoneData.length);
	}

	if (microphone != null) {
	    microphone.flush();
	}

	long timeLastPacketSent = 
	    System.currentTimeMillis() - RtpPacket.PACKET_PERIOD;

	timeLastReportSent = timeLastPacketSent;

	long elapsedTime = 0;

	long startTime = System.currentTimeMillis();
        boolean sendExtraPacket = false;

	Ticker ticker = null;

        String tickerClassName = System.getProperty(
            "com.sun.mc.softphone.media.TICKER");

        TickerFactory tickerFactory = TickerFactory.getInstance();

        try {
            ticker = tickerFactory.createTicker(tickerClassName, getName());
        } catch (TickerException e) {
            Logger.println(e.getMessage());
	    done();
	    return;
        }

        ticker.arm(RtpPacket.PACKET_PERIOD, RtpPacket.PACKET_PERIOD);

	long lastReadTime = 0;
 	int micBufferSizeMillis = 0;

	if (microphone != null) { 
	    micBufferSizeMillis = microphone.getBufferSizeMillis();
	}

	new KeepAliveSocket();

        while (!done) {
	    if (dtmfKey != null) {
	        /* 
                 * Send dtmf key
                 */
                sendDtmf();

                rtpSenderPacket.setMark();
                
		if (microphone != null) {
                    microphone.flush();
		}
            }

	    long now = System.currentTimeMillis();

	    if ((now - timeLastReportSent) / 1000 >= 
		    RtcpPacket.SENDER_INTERVAL) {

		sendReport();
	        timeLastReportSent = System.currentTimeMillis();
	    }

	    if (microphone != null && packetGenerator == null) {
	        /*
	         * Read linear data from the microphone
	         */ 
	        if (firstTime && Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("reading mic...");
		    lastReadTime = now;
	        } else {
		    long elapsed = now - lastReadTime;
		    lastReadTime = now;

		    if (elapsed > micBufferSizeMillis 
			    + RtpPacket.PACKET_PERIOD) {

			if (Logger.logLevel >= Logger.LOG_MOREINFO) {
			    Logger.println("Microphone overflow, elapsed "
				+ elapsed);
			}
			micOverflow++;
		    }

	            String s = Utils.getPreference(
           		"com.sun.mc.softphone.media.SMOOTH_MICROPHONE");

        	    if (s != null && s.equalsIgnoreCase("true")) {
                        try {
                            ticker.tick();
                        } catch (TickerException e) {
                            Logger.println(getName() + ":  tick() failed! "
                                + e.getMessage());
                            done();
                            break;
                        }
		    }
		}

		try {
		    microphoneData = new byte[microphoneData.length];
	            microphone.read(microphoneData, 0, microphoneData.length);

		    adjustData(microphoneData);

		    if (micRecorder != null) {
		        try {
		            micRecorder.write(microphoneData, 0, 
				microphoneData.length);
		        } catch (IOException e) {
			    Logger.println("Unable to record mic:  " 
				+ e.getMessage());
			    micRecorder = null;
		        }
		    }
		} catch (IOException e) {
		    Logger.println("Unable to read microphone!  "
			+ e.getMessage());
		    break;
		}

	        if (firstTime && Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("back from reading mic...");
	        }

		//applyLpf(microphoneData);
	    } else if (packetGenerator == null) {
		/*
		 * Silence
		 */
		microphoneData = new byte[microphoneData.length];

		try {
		    ticker.tick();
		} catch (TickerException e) {
		    Logger.println(getName() + ":  tick() failed! "
			+ e.getMessage());
		    done();
		    break;
		}
	    }

	    firstTime = false;

	    if (microphone == null && isMuted) {
		continue;
	    }

	    if (done) {
		break;
	    }

	    recordAudio(microphoneData, microphoneData.length);

	    String s = Utils.getPreference(
		"com.sun.mc.softphone.media.DETECT_SPEAKING");

	    if (microphone != null && s != null && s.equalsIgnoreCase("true")) {
		if (speechDetector == null) {
	            speechDetector = new SpeechDetector("AudioTransmitter",
			transmitMediaInfo);
		}
	    } else {
		if (speechDetector != null) {
		    speechDetector = null;
		}
	    }

	    if (speechDetector != null) {
                if (speechDetector.processData(microphoneData)) {
                    speaking = speechDetector.isSpeaking();

		    if (Logger.logLevel >= Logger.LOG_INFO) {
                        if (speaking) {
                            Logger.println("Started Speaking...");
			} else {
                            Logger.println("Stopped Speaking...");
			}
                    }
                }
	    }

	    if (treatmentManager != null) {
	        if (treatmentManager.isPaused() == false) {
		    synchronized(treatmentLock) {
		        addAudioData(microphoneData);
		    }
	 	}
	    } else {
	        checkVolumeLevel(microphoneData);
	    }

	    if (isMuted() || lowVolumeCount >= minThresholdCount || sendKeepAlive) {

                if (comfortPayloadSent && sendKeepAlive == false) {
		    continue;	// already sent comfort payload change
		}

		sendKeepAlive = false;

        	comfortNoiseLevel = RtpPacket.defaultNoiseLevel;

		s = Utils.getPreference(COMFORT_NOISE_LEVEL_PROPERTY);

        	if (s != null) {
		    try {
            	        comfortNoiseLevel = (byte)Integer.parseInt(s);
		    } catch (NumberFormatException e) {
		    }
        	}

		for (int i = RtpPacket.HEADER_SIZE; i < rtpSenderData.length;
			i++) {

		    rtpSenderData[i] = 0;
		}

		rtpSenderPacket.setComfortPayload();
		rtpSenderPacket.setComfortNoiseLevel(comfortNoiseLevel);
		comfortPayloadSent = true;

                if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("sending comfort payload level " 
			+ comfortNoiseLevel + " sequence " 
			+ rtpSenderPacket.getRtpSequenceNumber());
		}
	    } else {
		sendKeepAlive = false;

		if (comfortPayloadSent == true) {
		    comfortPayloadSent = false;

	    	    timeLastPacketSent = System.currentTimeMillis() - 
			RtpPacket.PACKET_PERIOD;

		    rtpSenderPacket.adjustRtpTimestamp();
		    rtpSenderPacket.setRtpPayload(mediaPayload);
		    rtpSenderPacket.setMark();
                    rtpSenderPacket.setLength(rtpSenderData.length);

                    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		        Logger.println("resume sending data after comfort... "
			    + "sequence " + rtpSenderPacket.getRtpSequenceNumber());
		    }
		}

		byte[] data = microphoneData;

		if (packetGenerator == null) {
		    //
		    // There should no longer be a need to resample.
		    //
		    // The SDP parameters are negotiated to accomodate
		    // the softphone.  When we use CoreAudio on the Mac,
		    // we won't be limited to 44,100.
		    //
		    if (encoding == RtpPacket.PCMU_ENCODING) {
	                AudioConversion.linearToUlaw(data, rtpSenderData, 
			    RtpPacket.HEADER_SIZE);

			if (ulawRecorder != null) {
			    try {
			        ulawRecorder.write(rtpSenderData, 
				    RtpPacket.HEADER_SIZE,
			            rtpSenderData.length - RtpPacket.HEADER_SIZE);
			    } catch (IOException e) {
				Logger.println("Unable to record ulaw data:  " +
				    e.getMessage());
				ulawRecorder = null;
			    }
			}
		    } else if (encoding == RtpPacket.SPEEX_ENCODING) {
                        speexInit();

                        if (speexEncoder != null) {
                            try {
                                int length = speexEncoder.encode(data, 
				    rtpSenderData, RtpPacket.HEADER_SIZE);

                                rtpSenderPacket.setLength(RtpPacket.HEADER_SIZE 
				    + length);
                            } catch (SpeexException e) {
                                Logger.println("Speex encode failed: "
                                    + e.getMessage());
                                e.printStackTrace();
                                //speexEncoder = null;
                                continue;
                            }
		        } else {
		            System.arraycopy(data, 0, rtpSenderData,
			        RtpPacket.HEADER_SIZE, data.length);
		        }
	 	    } else {
		        System.arraycopy(data, 0, rtpSenderData,
			    RtpPacket.HEADER_SIZE, data.length);
		    }
		} else {
		    try {
			/*
			 * Packet generator generates packet with an RTP header
			 */
			getPacket(rtpSenderPacket, expectedPacketSize);
		    } catch (IOException e) {
			Logger.println("done with playback " + e.getMessage());
			packetGenerator.done();
			packetGenerator = null;
			rtpSenderPacket.setMark();
			continue;
		    }

		    if (packetsSent == 0) {
			rtpSenderPacket.setMark();
		    }
		}
	    }

            now = System.currentTimeMillis();

            elapsedTime += (now - timeLastPacketSent);

	    if (rtpSenderPacket.getRtpPayload() == mediaPayload) {
		recordPacket(rtpSenderData, rtpSenderPacket.getLength());
	    }

	    if (rtpRecorder != null) {
                try {
                    rtpRecorder.writePacket(rtpSenderData, 0, 
			rtpSenderData.length);
                } catch (IOException e) {
                    Logger.println("Unable to record ulaw data:  " 
			+ e.getMessage());
		    ulawRecorder = null;
                }
            }

	    if (sendPacket() == false) {
		done = true;
		break;
	    }

	    timeLastPacketSent = System.currentTimeMillis();

            rtpSenderPacket.updateRtpHeader(rtpSenderPacket.getLength());

	    if (comfortPayloadSent == false) {
	        /*
                 * reset payload to mediaPayload, clear MARK_BIT
                 * and adjust Rtp timestamp and sequence number.
                 */
                rtpSenderPacket.setRtpPayload(mediaPayload);
	        rtpSenderPacket.clearMark();
	    }

	    if (packetsSent != 0 && (packetsSent % 1000) == 0) {
		double avg = Math.round(
		    ((double)elapsedTime / 1000) * 1000) / 1000D;

		Logger.println("avg time between sends of last 1000 "
		    + " packets " + avg 
		    + " ms, " + "microphone overflows " + 
			(micOverflow - lastMicOverflow));

		elapsedTime = 0;
		lastMicOverflow = micOverflow;

		if (speexEncoder != null) {
		    int encodes = speexEncoder.getEncodes();
		    long encodeTime = speexEncoder.getEncodeTime();
		    int bytesEncoded = speexEncoder.getBytesEncoded();

		    if (encodes > 0) {
		        Logger.println("Average Speex Encode time " +
			    (((double)encodeTime / encodes) / 
			    CurrentTime.getTimeUnitsPerSecond()) + " ms");

		        if (bytesEncoded > 0) {
		            Logger.println("Average compression ratio " +
			        ((encodes * rtpSenderData.length) /
			        bytesEncoded) + " to 1");
		        }
		    }

		    speexEncoder.resetStatistics();
		}
	    }
        }

	ticker.disarm();

	synchronized(this) {
	    notifyAll();
	}
    }

    private long nSamples;
    private long sampleSumLeft;
    private long sampleSumRight;

    private short biasLeft;
    private short biasRight;

    private void adjustData(byte[] microphoneData) {
	updateBias(microphoneData);

	if (biasLeft == 0 && biasRight == 0) {
	    return;
	}

	String adjustMic = Utils.getPreference(
	    "com.sun.mc.softphone.media.ADJUST_MICROPHONE_BIAS");

	if (adjustMic == null || !adjustMic.equalsIgnoreCase("true")) {
	    return;
	}    

	if ((nSamples % (5 * sampleRate)) == 0) {
	    Logger.println("biasLeft " + biasLeft + " biasRight " + biasRight);
	}

	for (int i = 0; i < microphoneData.length; i += 2 * channels) {
	    short s = (short) (((microphoneData[i] << 8) & 0xff00) |
		((microphoneData[i + 1] & 0xff)));

	    s -= biasLeft;

	    microphoneData[i] = (byte) ((s >> 8) & 0xff);
	    microphoneData[i + 1] = (byte) (s & 0xff);

	    if (channels == 2) {
		s = (short) (((microphoneData[2 + i] << 8) & 0xff00) |
		    ((microphoneData[3 + i] & 0xff)));

                s -= biasRight;

                microphoneData[2 + i] = (byte) ((s >> 8) & 0xff);
                microphoneData[3 + i] = (byte) (s & 0xff);
	    }
	}
    }

    private void updateBias(byte[] microphoneData) {
	for (int i = 0; i < microphoneData.length; i += channels * 2) {
	    short s = (short) (((microphoneData[i] << 8) & 0xff00) |
                ((microphoneData[i + 1] & 0xff)));

	    sampleSumLeft += s;

	    if (channels == 2) {
	        s = (short) (((microphoneData[i + 2] << 8) & 0xff00) |
                    ((microphoneData[i + 3] & 0xff)));

	        sampleSumRight += s;
	    }

	    nSamples++;
	}

	if (nSamples >= sampleRate) {
	    biasLeft = (short) (sampleSumLeft / nSamples);
	    biasRight = (short) (sampleSumRight / nSamples);
	}
    }

    //private LowPassFilter lowPassFilter;

    //private void applyLpf(byte[] microphoneData) {
    //	if (lowPassFilter == null) {
    //	    lowPassFilter = new LowPassFilter("softphone", sampleRate, 
    //		channels);
    //	}
    //
    //	byte[] lpfData = lowPassFilter.lpfSP(microphoneData);
    //
    //	for (int i = 0; i < microphoneData.length; i++) {
    //	    microphoneData[i] = lpfData[i];
    //	}
    //}

    String recordingPath;
    String recordingType;

    public void startRecording(String path, String recordingType) 
	    throws IOException {

	if (microphone == null) {
	    this.recordingPath = path;
	    this.recordingType = recordingType;
	    return;
	}

	recordRtp = false;

	if (recordingType.equalsIgnoreCase("Rtp")) {
	    recordRtp = true;
	}

        MediaInfo m = new MediaInfo(transmitMediaInfo.getPayload(),
            RtpPacket.PCM_ENCODING, microphone.getSampleRate(),
            microphone.getChannels(), false);

	try {
            recorder = new Recorder(path, recordingType, m);
        } catch (IOException e) {
            throw new IOException("Can't record to " + path + " " 
		+ e.getMessage());
        }
    }
	
    public void stopRecording() {
	if (recorder != null) {
	    recorder.done();
	    recorder = null;
	}
    }

    private void recordPacket(byte[] data, int length) {
	if (recorder == null) {
	    return;
	}

	if (recordRtp == false) {
	    return;
	}

	try {
	    recorder.writePacket(data, 0, length);
        } catch (IOException e) {
            Logger.println("Unable to record data " + e.getMessage());
            recorder = null;
        }
    }

    private void recordAudio(byte[] data, int length) {
	if (recorder == null) {
	    return;
	}

	if (recordRtp) {
	    return;
	}

        try {
            recorder.write(data, 0, length);
        } catch (IOException e) {
            Logger.println("Unable to record data " + e.getMessage());
            recorder = null;
        }
    }

    private void sendReport() {
	rtcpSenderPacket.setRTPTimestamp(
	    (int)(rtpSenderPacket.getRtpTimestamp() & 0xffffffff));

	rtcpSenderPacket.setPacketCount(packetsSent);

	rtcpSenderPacket.setHighestSeqReceived(
	    rtpSenderPacket.getRtpSequenceNumber());

        if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("Sender:  ts "
	        + (int)(rtpSenderPacket.getRtpTimestamp() & 0xffffffff)
	        + ", packets sent " + rtcpSenderPacket.getPacketCount()
	        + ", highest sequence " 
		+ rtpSenderPacket.getRtpSequenceNumber());
	}

	try {
            if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("sending Sender report to " 
		    + rtcpSenderPacket.getSocketAddress());
	    }
	    rtpSocket.send(rtcpSenderPacket);
	} catch (IOException e) {
	    Logger.println("Can't send receiver report " + e.getMessage());
	}
    }

    private void addAudioData(byte[] microphoneData) {
	byte[] audioData;

        audioData = treatmentManager.getLinearDataBytes(
	    RtpPacket.PACKET_PERIOD);

	if (audioData == null) {
	    Logger.println("End of file for treatment file!");
	    treatmentManager = null;
	    return;
	}

	/*
	 * Now add the audio data to the microphone data.
	 */
	for (int i = 0; i < audioData.length; i += 2) {
	    int b1 = (((int)microphoneData[i]) << 8) & 0xff00;
	    int b2 = ((int)microphoneData[i + 1]) & 0xff;
	    
	    int m = b1 + b2;

	    b1 = (((int)audioData[i]) << 8) & 0xff00;
	    b2 = ((int)audioData[i + 1]) & 0xff;

	    m += (b1 + b2);

            microphoneData[i] = (byte)((m >> 8) & 0xff);
            microphoneData[i + 1] = (byte)(m & 0xff);
	}
    }

    private void checkVolumeLevel(byte[] microphoneData) {
        if (microphone == null || sendComfortPayload == false) {
            return;
        }

        String s = Utils.getPreference(VOLUME_THRESHOLD_PROPERTY);

        if (s != null && s.length() > 0) {
	    int n = 0;

	    try {
                n = Integer.parseInt(s);
	    } catch (NumberFormatException e) {
	    }

	    if (n != volumeThreshold) {
		if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Setting volumeThreshold to " + n);
		}

		volumeThreshold = n;
	    }
        }

        s = Utils.getPreference(MIN_THRESHOLD_COUNT_PROPERTY);

        if (s != null && s.length() > 0) {
	    int n = 0;

	    try {
                n = Integer.parseInt(s);
            } catch (NumberFormatException e) {
            }

            if (n != minThresholdCount) {
                if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("Setting minThresholdCount to " + n);
                }

                minThresholdCount = n;
            }
        }

        int packetVolumeLevel = getPacketVolumeLevel(
	    microphoneData, RtpPacket.HEADER_SIZE, microphoneData.length);

        if (packetVolumeLevel < volumeThreshold) {
            lowVolumeCount++;
        } else {
            lowVolumeCount = 0;
        }

	if (Logger.logLevel == -29) {
	    Logger.println("level " + packetVolumeLevel
		+ " thresh " + volumeThreshold + " count " + lowVolumeCount);
	}
    }

    private int getPacketVolumeLevel(byte[] buf, int offset, int len) {
	int volumeLevel = 0;
	int packetVolumeLevel = 0;

        for (int i = offset; i < len; i += 2) {
            volumeLevel = Math.abs((((int)buf[i]) << 8) |
                ((int)buf[i+1] & 0xff));

            if (volumeLevel > packetVolumeLevel) {
                packetVolumeLevel = volumeLevel;
            }
        }
    
	return packetVolumeLevel;
    }

    private boolean isSpeaking(byte[] buf, int offset, int len) {
	int packetVolumeLevel = getPacketVolumeLevel(buf, offset, len);

	return packetVolumeLevel >= volumeThreshold;
    }

    private BufferedOutputStream bo;

    private boolean sendPacket() {
	/*
	 * Send RTP data
	 */
	try {
            if (cipher != null) {
	 	byte[] data;

	        try {
	    	    data = cipher.doFinal(rtpSenderData, 0, 
			rtpSenderData.length);
	        } catch (Exception e) {
                    Logger.println("SipCommunicator Encryption failed:  " 
		        + e.getMessage());
		    return false;
		}
    
                rtpSenderPacket.setBuffer(data);
                rtpSenderPacket.setLength(data.length);
            } else {
		if (bo != null) {
		    try {
		        bo.write(rtpSenderData, RtpPacket.HEADER_SIZE,
			    rtpSenderData.length - RtpPacket.HEADER_SIZE);
		    } catch (IOException e) {
			Logger.println(e.getMessage());
        	    }
		}

		//Logger.println("sending " + rtpSenderPacket.getLength()
		//	+ " len " + len);

	    }

	    rtpSocket.send(rtpSenderPacket);

	    //rtpSenderData = new byte[rtpSenderData.length];
	    rtpSenderPacket.setBuffer(rtpSenderData);
            rtpSenderPacket.setLength(rtpSenderData.length);
	} catch (IOException e) {
	    if (!done && rtpSenderPacket != null) {
	        Logger.println("Unable to send data! " + e.getMessage());
	        return false;
	    }
	}

	packetsSent++;
	return true;
    }

    private int quality = -1;
    private int complexity = -1;

    private void speexInit() {
	if (encoding != RtpPacket.SPEEX_ENCODING || sampleRate > 32000) {
	    return;
	}

	//String s = Utils.getPreference(SPEEX_ENABLED_PROPERTY);

        //if (s == null || s.equalsIgnoreCase("true") == false) {
	//    speexEncoder = null;
	//    return;
	//}

        int quality = Utils.getIntPreference(SPEEX_QUALITY_PROPERTY);

        if (quality < 0) {
            quality = 0;
        }

        if (quality > 10) {
            quality = 10;
        }

        int complexity = Utils.getIntPreference(SPEEX_COMPLEXITY_PROPERTY);

        if (complexity < 0) {
            complexity = 0;
        }

        if (complexity > 10) {
            complexity = 10;
        }

	if (speexEncoder != null && quality == speexQuality &&
	        complexity == speexComplexity) { 

	    return;
	}
	
	this.quality = quality;
	this.complexity = complexity;

	try {
	    speexEncoder = new SpeexEncoder(sampleRate, channels);
	} catch (SpeexException e) {
	    Logger.println("SpeexInit failed:  " + e.getMessage());
	    return;
	}

        speexEncoder.setQuality(quality);

	/*
	 * Setting complexity seems to hose speex!
	 */
        //speexEncoder.setComplexity(complexity);

	pcmPacketSize = speexEncoder.getPcmPacketSize();

	Logger.println("Started speex encoder with quality "
	    + quality + " and complexity " + complexity);
    }

    private void waitRtpPacketPeriod() {
        long now = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - now <
               RtpPacket.PACKET_PERIOD) {
               
           ;       // busy wait 20ms
        }  
    }   
    
    private void sendDtmf() {
	if (microphone != null) {
            microphone.flush(); 
	}
        
        if (telephoneEventPayload == 0) {
	    //if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		Logger.println("Sending Dtmf sound...");
	    //}

            sendDtmfSound();
        } else {
	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		Logger.println("Sending Dtmf telephone event payload...");
	    }

            sendDtmfPayload();
        }   
       	
	if (microphone != null) { 
            microphone.flush();
	}
    }   
    
    private void sendDtmfSound() {
	if (comfortPayloadSent == true) {
            comfortPayloadSent = false;

            rtpSenderPacket.adjustRtpTimestamp();
            rtpSenderPacket.setRtpPayload(mediaPayload);
            rtpSenderPacket.setMark();
            rtpSenderPacket.setLength(rtpSenderData.length);

            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("sending dtmf after comfort...");
            }
	}

	int len = RtpPacket.getDataSize(encoding, sampleRate, channels);

        if (encoding == RtpPacket.PCMU_ENCODING) {
	    len *= 2;
	}

	len += RtpPacket.HEADER_SIZE;

        byte[] data = new byte[len];

        while (dtmfKey != null) {
            dtmfBuffer.fillBuffer(data, RtpPacket.HEADER_SIZE,
                data.length - RtpPacket.HEADER_SIZE);

            if (encoding == RtpPacket.PCMU_ENCODING) {
                AudioConversion.linearToUlaw(data, RtpPacket.HEADER_SIZE,
                    rtpSenderPacket.getData(), RtpPacket.HEADER_SIZE);
            }

            sendPacket();

            rtpSenderPacket.updateRtpHeader(data.length);

            waitRtpPacketPeriod();
        }
    }

    /*
     * It takes 12 packets to generate a dtmf key!
     * The first 3 are silence packets with the MARK bit set.
     * All the rest have the same RTP timestamp.
     * Next 3 packets have the MARK bit set and a duration of 0.
     * The next 3 packets have the MARK bit clear and a duration of
     * 400, 800, and 1200, respectively.
     * The least 3 packets have the MARK bit clear, the END bit set,
     * and a duration of 1304.
     *
     * The next packet sent has the MARK bit set.
     *
     * This is what we receive if a dtmf key is pressed on the lucent phone.
     * So the assumption is that this is what we should generate for dtmf keys.
     */
    private void sendDtmfPayload() {
        for (int i = 0; i < 3; i++) {
            /*
             * Send Silence with the MARK BIT
             */
            for (int n = RtpPacket.HEADER_SIZE; n < rtpSenderData.length; n++) {
                rtpSenderData[n] = AudioConversion.PCMU_SILENCE;
            }

            rtpSenderPacket.setMark();
            rtpSenderPacket.setLength(rtpSenderData.length);

            sendPacket();

            rtpSenderPacket.updateRtpHeader(rtpSenderData.length);
            rtpSenderPacket.setRtpPayload(mediaPayload);
            waitRtpPacketPeriod();
        }

        for (int i = 0; i < 3; i++) {
            /*
             * These 3 packets have MARK bit set and duration of 0
             */
            rtpSenderPacket.setRtpPayload((byte)telephoneEventPayload);
	    rtpSenderPacket.setMark();
            rtpSenderData[RtpPacket.DATA + 0] = getTelephoneEvent();
            rtpSenderData[RtpPacket.DATA + 1] = (byte)10;   // volume level
            rtpSenderData[RtpPacket.DATA + 2] = (byte)0;
            rtpSenderData[RtpPacket.DATA + 3] = (byte)0;
            rtpSenderPacket.setLength(RtpPacket.DATA + 4);

            sendPacket();

            rtpSenderPacket.incrementRtpSequenceNumber();
            waitRtpPacketPeriod();
        }

        int duration = 400;

        for (int i = 0; i < 3; i++) {
            /*
             * Next 3 packets have MARK bit clear, duration of 400, 800, 
	     * and 1200.
             */
            rtpSenderPacket.setRtpPayload((byte)telephoneEventPayload);
	    rtpSenderPacket.clearMark();

            rtpSenderData[RtpPacket.DATA + 0] = getTelephoneEvent();
            rtpSenderData[RtpPacket.DATA + 1] = (byte)10;   // volume level
            rtpSenderData[RtpPacket.DATA + 2] = (byte)((duration >> 8) & 0xff);
            rtpSenderData[RtpPacket.DATA + 3] = (byte)(duration & 0xff);
            rtpSenderPacket.setLength(RtpPacket.DATA + 4);

            sendPacket();
            rtpSenderPacket.incrementRtpSequenceNumber();
            waitRtpPacketPeriod();

            duration += 400;
        }

        duration = 1304;

        for (int i = 0; i < 3; i++) {
            /*
             * Last 3 packets have MARK bit clear, END bit set, and a 
	     * duration of 1304.
             */
            rtpSenderData[RtpPacket.DATA + 0] = getTelephoneEvent();
            rtpSenderData[RtpPacket.DATA + 1] |= (byte)0x80; // end
            rtpSenderData[RtpPacket.DATA + 2] = (byte)((duration >> 8) & 0xff);
            rtpSenderData[RtpPacket.DATA + 3] = (byte)(duration & 0xff);
	    rtpSenderPacket.setLength(RtpPacket.DATA + 4);

            sendPacket();
            rtpSenderPacket.incrementRtpSequenceNumber();
            waitRtpPacketPeriod();
        }

        rtpSenderPacket.updateRtpHeader(rtpSenderData.length);
        rtpSenderPacket.setRtpPayload(mediaPayload);
        rtpSenderPacket.setLength(rtpSenderData.length);

	synchronized (dtmfKeyLock) {
            dtmfKey = null;

	    dtmfKeyLock.notifyAll();
	}
    }

    private byte getTelephoneEvent() {
	if (dtmfKey.equals("0")) {
	    return 0;
	}
	if (dtmfKey.equals("1")) {
	    return 1;
	}
	if (dtmfKey.equals("2")) {
	    return 2;
	}
	if (dtmfKey.equals("3")) {
	    return 3;
	}
	if (dtmfKey.equals("4")) {
	    return 4;
	}
	if (dtmfKey.equals("5")) {
	    return 5;
	}
	if (dtmfKey.equals("6")) {
	    return 6;
	}
	if (dtmfKey.equals("7")) {
	    return 7;
	}
	if (dtmfKey.equals("8")) {
	    return 8;
	}
	if (dtmfKey.equals("9")) {
	    return 9;
	}
	if (dtmfKey.equals("*")) {
	    return 10;
	}
	if (dtmfKey.equals("#")) {
	    return 11;
	}
	if (dtmfKey.equals("A")) {
	    return 12;
	}
	if (dtmfKey.equals("B")) {
	    return 13;
	}
	if (dtmfKey.equals("C")) {
	    return 14;
	}

	return 15;
    }

    public static void setSpeechDetectorParameters() {
	String s = Utils.getPreference("com.sun.mc.softphone.media.CN_THRESH");

	if (s != null && s.length() > 0) {
	    try {
		setCnThresh(Integer.parseInt(s));
	    } catch (NumberFormatException e) {
	    }
	} 
		
	s = Utils.getPreference(
	    "com.sun.mc.softphone.media.POWER_THRESHOLD_LIMIT");

	if (s != null && s.length() > 0) {
	    try {
		setPowerThresholdLimit(Double.parseDouble(s));
	    } catch (NumberFormatException e) {
	    }
	} 
		
        s = Utils.getPreference("com.sun.mc.softphone.media.ON_THRESHOLD");

        if (s != null && s.length() > 0) {
            try {
		setOnThresh(Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        }

        s = Utils.getPreference("com.sun.mc.softphone.media.OFF_THRESHOLD");

        if (s != null && s.length() > 0) {
            try {
		setOffThresh(Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        }
    }

    public static void setCnThresh(int cnThresh) {
	if (cnThresh != SpeechDetector.getCnThresh()) {
	    SpeechDetector.setCnThresh(cnThresh);
		Utils.setPreference("com.sun.mc.softphone.media.CN_THRESH",
		    String.valueOf(cnThresh));
	}
    }

    public static int getCnThresh() {
	return SpeechDetector.getCnThresh();
    }

    public static void setPowerThresholdLimit(double powerThresholdLimit) {
	if (powerThresholdLimit != SpeechDetector.getPowerThresholdLimit()) {
	    SpeechDetector.setPowerThresholdLimit(powerThresholdLimit);
	    Utils.setPreference(
		"com.sun.mc.softphone.media.POWER_THRESHOLD_LIMIT",
		String.valueOf(powerThresholdLimit));
        }
    }

    public static double getPowerThresholdLimit() {
	return SpeechDetector.getPowerThresholdLimit();
    }

    public static void setOnThresh(int onThresh) {
	if (onThresh != SpeechDetector.getOnThresh()) {
            SpeechDetector.setOnThresh(onThresh);
	    Utils.setPreference( "com.sun.mc.softphone.media.ON_THRESHOLD",
		String.valueOf(onThresh));
	}
    }

    public static int getOnThresh() {
	return SpeechDetector.getOnThresh();
    }

    public static void setOffThresh(int offThresh) {
	if (offThresh != SpeechDetector.getOffThresh()) {
            SpeechDetector.setOffThresh(offThresh);
	    Utils.setPreference("com.sun.mc.softphone.media.OFF_THRESHOLD",
		String.valueOf(offThresh));
	}
    }

    public static int getOffThresh() {
	return SpeechDetector.getOffThresh();
    }

    public void playFile(String path, PacketGeneratorListener listener) 
	    throws IOException {

	packetGenerator = new PacketGenerator(path);
	packetGenerator.addListener(listener);
    }

    public void stopPlaying() {
	if (packetGenerator != null) {
	    packetGenerator.done();
	    packetGenerator = null;
	}
	Logger.logLevel = 5;
    }

    public int getPacketsSent() {
	return packetsSent;
    }

    public int getMicOverflow() {
	return micOverflow;
    }

    private int dataSize;
    private short lastSequence;
    private int lastRtpTimestamp;

    private void getPacket(RtpSenderPacket rtpSenderPacket, 
	    int length) throws IOException {

	RtpSenderPacket generatedPacket = packetGenerator.getPacket();

	if (generatedPacket.getRtpPayload() != mediaPayload) {
	    throw new IOException("Can't play data, bad payload "
		+ generatedPacket.getRtpPayload() + " expected " 
		+ mediaPayload);
	}

	int sequenceAdjustment = 0;
	int rtpTimestampAdjustment = 0;

	if (dataSize == 0) {
	    dataSize = RtpPacket.getDataSize(encoding, sampleRate, channels);

	    lastSequence = generatedPacket.getRtpSequenceNumber();
	    lastRtpTimestamp = (int) generatedPacket.getRtpTimestamp();
	} else {
	    sequenceAdjustment = generatedPacket.getRtpSequenceNumber() - 
		lastSequence - 1;

	    rtpTimestampAdjustment = (int) generatedPacket.getRtpTimestamp() -
		lastRtpTimestamp - dataSize;

	    lastSequence = generatedPacket.getRtpSequenceNumber();
	    lastRtpTimestamp = (int) generatedPacket.getRtpTimestamp();
	}

	if (sequenceAdjustment != 0) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
		Logger.println("Adjusting sequence number by " 
		    + sequenceAdjustment + " last seq " + lastSequence
		    + " current seq " + generatedPacket.getRtpSequenceNumber());
	    }

	    short sequenceNumber = rtpSenderPacket.getRtpSequenceNumber();

	    sequenceNumber += sequenceAdjustment;

	    rtpSenderPacket.setRtpSequenceNumber(sequenceNumber);
	}

	if (rtpTimestampAdjustment != 0) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
		Logger.println("Adjusting rtp timestamp by " 
		    + rtpTimestampAdjustment + " last timestamp " 
		    + lastRtpTimestamp + " current timestamp " 
		    + generatedPacket.getRtpTimestamp());
	    }

	    int timestamp = (int) rtpSenderPacket.getRtpTimestamp();

	    timestamp += rtpTimestampAdjustment;

	    rtpSenderPacket.setRtpTimestamp(timestamp);
	}

	byte[] generatedData = generatedPacket.getData();

	if (generatedData.length != length) {
	    throw new IOException("linear data, packet generator data "
		+ "length " + generatedData.length + " != expected length " 
		+ length);
	}

	byte[] data = rtpSenderPacket.getData();

	for (int i = 0; i < length - RtpPacket.HEADER_SIZE; i++) {
	    data[RtpPacket.HEADER_SIZE + i] = 
		generatedData[RtpPacket.HEADER_SIZE + i]; 
	}
    }

    private class KeepAliveSocket extends Thread {

	public KeepAliveSocket() {
	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("KeepAlive port " 
		    + rtpSocket.getDatagramSocket().getLocalPort());
	    }

	    start();
	}

	public void run() {
	    int lastPacketsSent = packetsSent;

	    while (!done) {
		if (lastPacketsSent == packetsSent) {
		    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
			Logger.println("need to send keep alive");
		    }

		    sendKeepAlive = true;
		} else {
		    lastPacketsSent = packetsSent;
		}

		try {
		    Thread.sleep(15000);
		} catch (InterruptedException e) {
		}

	    }
	}

    }

}
