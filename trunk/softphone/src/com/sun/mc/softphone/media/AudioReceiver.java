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

import com.sun.mc.softphone.SipCommunicator;

import com.sun.voip.AudioConversion;
import com.sun.voip.CurrentTime;
import com.sun.voip.JitterManager;
import com.sun.voip.JitterObject;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.SdpInfo;
import com.sun.voip.Recorder;
import com.sun.voip.RtpSocket;
import com.sun.voip.RtcpPacket;
import com.sun.voip.RtcpReceiverPacket;
import com.sun.voip.RtpSenderPacket;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpReceiverPacket;
import com.sun.voip.SdpManager;
import com.sun.voip.SpeexDecoder;
import com.sun.voip.SpeexException;
import com.sun.voip.Ticker;
import com.sun.voip.TickerException;
import com.sun.voip.TickerFactory;
import com.sun.voip.Util;

import java.io.*;

import java.text.ParseException;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.SocketException;

import javax.sound.sampled.LineUnavailableException;

import com.sun.mc.softphone.common.Utils;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * Serves as an RTP endpoint which receives data from the RtpSocket and
 * writes it to the speaker
 */
public class AudioReceiver extends Thread {
    private RtpSocket rtpSocket;		// socket for send and receive
    private RtpReceiverPacket rtpReceiverPacket;

    private RtcpReceiverPacket rtcpReceiverPacket;

    private InetSocketAddress isa;
    private Speaker speaker;
    private String encryptionKey;
    private String encryptionAlgorithm;

    private AudioFilePlayer comfortNoisePlayer;

    private boolean done = false;

    private boolean comfortPayloadReceived;

    private MediaInfo mediaInfo;

    private int sampleRate;
    private int channels;
    private int encoding;
    private int mediaPayload;

    byte[] speakerData;
    private int speakerWriteSize;

    /* enable / disable comfort noise */
    private static final String RECEIVE_COMFORT_PAYLOAD_PROPERTY =
        "com.sun.mc.softphone.media.RECEIVE_COMFORT_PAYLOAD";

    /* enable / disable swapping of stereo speaker channels. */
    private static final String SWAP_SPEAKER_CHANNELS =
        "com.sun.mc.softphone.media.SWAP_SPEAKER_CHANNELS";

    private Cipher cipher;

    private boolean debugDtmf = false;
    private String dtmfKey;

    private int count = 4;
    private int badCount = 4;

    private boolean verifySender = true;

    private int rejected = 0;

    private static int oldSynchronizationSource;

    private Recorder recorder;
    private boolean recordRtp;
    private long startRecordTime;
    private long dataRecorded;

    private AudioTransmitter audioTransmitter;

    private SpeexDecoder speexDecoder;

    private int[] speakerBufferDist = new int[300];

    private boolean connected = false;

    private int speakerBufferMillis;

    public AudioReceiver(RtpSocket rtpSocket, String encryptionKey, 
	    String encryptionAlgorithm) throws IOException {

	this.rtpSocket = rtpSocket;
	this.encryptionKey = encryptionKey;
	this.encryptionAlgorithm = encryptionAlgorithm;

        String s = System.getProperty("com.sun.mc.softphone.media.DEBUG_DTMF");

        if (s != null) {
            debugDtmf = true;
        }

        s = Utils.getPreference("com.sun.mc.softphone.media.VERIFY_SENDER");

        if (s != null && s.equalsIgnoreCase("false")) {
            verifySender = false;
        }

	if (Logger.logLevel >= Logger.LOG_MOREINFO && verifySender == true) {
	    Logger.println("Sender will be verified...");
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
	        cipher.init(Cipher.DECRYPT_MODE, secretKey);
		Logger.println("Decrypt cipher initialized for " 
		    + encryptionAlgorithm);
	    } catch (Exception e) {
		throw new IOException("Crytpo initialization failed.  " 
		    + e.getMessage());
	    }
	}
	
	rtpSocket.flushSocket();
    }

    public void setAudioTransmitter(AudioTransmitter audioTransmitter) {
	this.audioTransmitter = audioTransmitter;
    }

    public void initialize(MediaInfo mediaInfo, Speaker speaker,
	    String remoteHost, int remotePort) {

	this.mediaInfo = mediaInfo;
	this.speaker = speaker;

	encoding = mediaInfo.getEncoding();
	sampleRate = mediaInfo.getSampleRate();
	channels = mediaInfo.getChannels();
	mediaPayload = mediaInfo.getPayload();

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("AudioReceiver " + sampleRate + "/" + channels
	        + " mediaPayload " + mediaPayload);
	}

	isa = new InetSocketAddress(remoteHost, remotePort);

        rtpReceiverPacket = 
	    new RtpReceiverPacket("SIP-Communicator", 
	    encoding, sampleRate, channels);

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("remote isa " + isa);

	    Logger.println("new rtpReceiverPacket " + rtpReceiverPacket
                + " expecting " + rtpReceiverPacket.getRtpSequenceNumber());
	}

	if (speaker != null) {
	    speakerWriteSize = speaker.getBufferSize(RtpPacket.PACKET_PERIOD);
	    speakerBufferMillis = speaker.getBufferSizeMillis();
	} else {
	    speakerWriteSize =  rtpReceiverPacket.getDataSize(
		RtpPacket.PCM_ENCODING, sampleRate, channels);
	}

        speakerData = new byte[speakerWriteSize];

	if (encoding == RtpPacket.SPEEX_ENCODING) {
	    try {
	        speexDecoder = new SpeexDecoder(sampleRate, channels);
	    } catch (SpeexException e) {
	        Logger.println(e.getMessage());
	    }
	}

	//Logger.println("Speaker data size for 20ms of data " 
	//    + speakerWriteSize);

	rtpSocket.flushSocket();

	connected = true;

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

	setName("AudioReceiver " + System.currentTimeMillis());
	setPriority(Thread.MAX_PRIORITY);

	start();
    }
    
    public void setOldSynchronizationSource(int oldSynchronizationSource) {
	this.oldSynchronizationSource = oldSynchronizationSource;
    }

    public int getPacketsReceived() {
	return received;
    }

    public int getNumberMissingPackets() {
	if (jitterManager == null) {
	    return 0;
	}

	return jitterManager.getNumberMissingPackets();
    }
	
    public int getJitterBufferSize() {
	if (jitterManager == null) {
	    return 0;
	}

	return jitterManager.getPacketListSize();
    }

    /*
     * Stop receiving data 
     */
    public void done() {
	if (done) {
	    return;
	}

        if (comfortNoisePlayer != null) {
            comfortNoisePlayer.done();
            comfortNoisePlayer = null;
	}

	if (speaker != null) {
            speaker.flush();
        }

        done = true;
	connected = false;

	stopRecording();
    }

    private DtmfBuffer dtmfBuffer;

    /*
     * Start playing a DTMF tone rather than received audio
     */
    public synchronized void startDtmf(String key) {
	if (speaker == null) {
	    return; 	// not initialized yet
	}

	if (comfortNoisePlayer != null) {
	    comfortNoisePlayer.done();
	}

	synchronized (jitterManager) {
	    jitterManager.flush();
	}

        dtmfKey = key.substring(0, 1);

        dtmfBuffer = new DtmfBuffer(speaker);

        dtmfBuffer.setDtmf(dtmfKey);

        /*
         * Start a separate thread to play the key to the speaker.
         * We don't want to depend on receiving packets for the timing.
         */
        dtmfBuffer.start();
    }

    /*
     * Stop playing a DTMF tone rather than received audio
     */
    public synchronized void stopDtmf() {
	if (dtmfBuffer == null) {
	    return;	// not initialized yet
	}

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("stopping dtmf sound");
	}
        dtmfBuffer.done();

        dtmfKey = null;
    }

    /*
     * Receive rtp packets and send the data to the speaker
     */ 
    private static final int ENCRYPTION_PAD_SIZE = 4;

    private int received = 0;
    private boolean receivedChanged;
    private int bytesReceived = 0;
    private int totalSamples = 0;
    private int dropped = 0;
    private int bytesDropped = 0;
    private int probableSpeakerUnderrun = 0;
    private int outOfSequenceFixed = 0;
    private int badPayload = 0;
    private long totalTimeSinceStart;
    private long totalTime = 0;

    private long timeLastPacketReceived;
    private long timeLastReportSent;

    private boolean droppingDueToDtmf = false;

    private int doCount = 2;

    private JitterManager jitterManager;

    public void run() {
	byte[] outOfSequenceData = null;

	byte[] receivedData = rtpReceiverPacket.getData();

	timeLastPacketReceived = System.currentTimeMillis();

	if (speaker != null) {
	    speaker.flush();
	}

        rtcpReceiverPacket = new RtcpReceiverPacket(
	    rtpReceiverPacket.getSynchronizationSource());

	rtcpReceiverPacket.setSocketAddress(
	    new InetSocketAddress(isa.getHostName(), isa.getPort() + 1));

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Expecting to receive from " + isa);

	    Logger.println("Receiver reports will be sent to " 
		+ rtcpReceiverPacket.getSocketAddress());
	}

	jitterManager = new JitterManager("Softphone");
	
	updateJitterParameters();

	//try {
        //    startRecording("/tmp/record.au", "au");
	//} catch (IOException e) {
	//    Logger.println("can't record!  " + e.getMessage());
	//}

	new PacketProcessor();

        while (!done) {
	    String s = Utils.getPreference(
		"com.sun.mc.softphone.media.FLUSH_SOCKET");

	    if (s != null && s.length() > 0) {
		Logger.println("flushing socket...");
	        Utils.setPreference(
		    "com.sun.mc.softphone.media.FLUSH_SOCKET", "");
	        rtpSocket.flushSocket();
	    }

	    s = Utils.getPreference(
	    	"com.sun.mc.softphone.media.PREVENT_SPEAKER_UNDERRUN");

            if (speaker != null && s != null &&
                    s.equalsIgnoreCase("false") == false &&
	    	    System.getProperty("os.name").indexOf("Windows") >= 0) {

                int timeout = speaker.getBufferSizeMillis() -
                    speaker.getBufferSizeMillis(speakerWriteSize);

                try {
                    rtpSocket.setSoTimeout(timeout);
                } catch (SocketException e) {
                    Logger.println("Can't set socket timeout!");
                }
            } else {
                try {
                    rtpSocket.setSoTimeout(0);
                } catch (SocketException e) {
                    Logger.println("Can't set socket timeout! "
			+ e.getMessage());
		    done();
		    break;
                }
            }

	    byte[] rtpData;
	    int length;

            try {
		receivedData = new byte[receivedData.length];
        	rtpReceiverPacket.setBuffer(receivedData);
        	rtpReceiverPacket.setLength(receivedData.length);

		//Logger.println("About to receive...");

		try {
                    if (rtpSocket.receive(rtpReceiverPacket) <= 0) {
		        Logger.println("socket receive returned no data!");

		        if (done) {
			    break;
		        }

		        continue;
		    }
		} catch (SocketTimeoutException e) {
		    if (speaker.isRunning() == false) {
		    	continue;
		    }

                    if (speaker.available() < speaker.getBufferSize() -
                            (speakerWriteSize * 2)) {

			continue;
		    }
			
                    if (System.currentTimeMillis() - timeLastPacketReceived >=
			    speaker.getBufferSizeMillis()) {

			if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
			    Logger.println(
				"Stopping speaker to prevent underrun...");
			}

			speaker.drain();
			speaker.stop();
                    }

                    continue;
                }
	        
		//Logger.println("back from receive");

		if (received == 0) {
		    rtpReceiverPacket.setMark();
		}

		if (received == 0 || Logger.logLevel >= Logger.LOG_DETAILINFO) {
		    Logger.println("Received packet from " + isa 
		        + ", sequence number " 
		        + rtpReceiverPacket.getRtpSequenceNumber() 
			+ " length " + rtpReceiverPacket.getLength()
			+ " timestamp diff " 
			+ rtpReceiverPacket.getRtpTimestampDiff());
		}

		if (done) {
		    break;
		}

                if (acceptPacket(rtpReceiverPacket, isa) == false) {
                    rejected++;
		    received++;
		    receivedChanged = true;
		    //Logger.println("rejected " + rejected + " " 
		    //	+ rtpReceiverPacket.getRtpSequenceNumber());
		    continue;
                }

		if (received == 0 && speaker != null) {
		    speaker.flush();
		}

		rtpData = receivedData;
	        length = rtpReceiverPacket.getLength();

		if (cipher != null) {
		    rtpData = decrypt(receivedData, length);

		    rtpReceiverPacket.setBuffer(rtpData);
		    length = rtpData.length;
	 	} 

	        recordPacket(rtpData, length);
            } catch (IOException e) {
		if (!done) {
                    Logger.println("receive failed! " + e.getMessage());
		}
		break;
            }

	    long timeReceived = System.currentTimeMillis();

	    int elapsed;

	    if (received == 1) {
		elapsed = RtpPacket.PACKET_PERIOD;
	    } else {
		elapsed = (int)
		    (System.currentTimeMillis() - timeLastPacketReceived);
	    }

	    synchronized (jitterManager) {
	        updateJitterParameters();

		int e = elapsed;

		if (rtpReceiverPacket.isMarkSet()) {
	            e = RtpPacket.PACKET_PERIOD;
		} 
	
		/*
		 * Insert place holder for this packet
		 */
		int count = jitterManager.insertPacket(
		    rtpReceiverPacket.getRtpSequenceNumber(), e);

		if (silenceCount == 0) {
		    silenceCount = count;
		}
	    }

            if (dtmfKey != null) {
		droppingDueToDtmf = true;
                continue;       // drop this packet and let the key get played
            }

	    if (droppingDueToDtmf == true) {
		droppingDueToDtmf = false;

		/*
		 * Set Mark so we don't complain about out of sequence packets.
		 */
		rtpReceiverPacket.setMark();
	    }

	    timeLastPacketReceived = timeReceived;

	    if ((timeLastPacketReceived - timeLastReportSent) / 1000 >=
		    RtcpPacket.SENDER_INTERVAL) {

		sendReport();
		timeLastReportSent = timeLastPacketReceived;
	    } 

	    if (comfortPayloadReceived) {
		comfortPayloadReceived = false;

	        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Received data after comfort");
		}

	        if (comfortNoisePlayer != null) {
		    comfortNoisePlayer.done();
		    comfortNoisePlayer = null;

		    if (speaker != null) {
	    	        speaker.flush();
		    }
	        } 

		elapsed = RtpPacket.PACKET_PERIOD;
	    }

	    if (rtpReceiverPacket.getRtpPayload() == 
		    RtpPacket.COMFORT_PAYLOAD) {

		comfortPayloadReceived = true;

		rtpReceiverPacket.updateRtpHeader(
		    length - RtpPacket.HEADER_SIZE);

		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Received comfort payload, seq " 
			+ rtpReceiverPacket.getRtpSequenceNumber());
		}

                s = Utils.getPreference(RECEIVE_COMFORT_PAYLOAD_PROPERTY);

        	if (speaker == null || 
		        s == null || s.length() == 0 ||
			s.equalsIgnoreCase("true") == false) {

		    if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
		        Logger.println("Ignoring comfort payload");
		    }

		    continue;	// ignore it
		}

		startComfort();
		continue;
	    }

	    if (elapsed <= 0) {
		elapsed = 0;
	    } 

	    totalTimeSinceStart += elapsed;

	    totalTime += elapsed;
	    received++;
	    receivedChanged = true;

	    //Logger.println("received packet " + received + " elapsed " 
	    //	+ elapsed);

	    bytesReceived += rtpReceiverPacket.getLength();

	    if (rtpReceiverPacket.getRtpPayload() != mediaPayload) {
		badPayload++;

                if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("bad payload " 
			+ rtpReceiverPacket.getRtpPayload());
		}
		rtpReceiverPacket.updateRtpHeader(
		    length - RtpPacket.HEADER_SIZE);

		continue;
	    }

	    byte decodedData[];
	    int dataLength = length - RtpPacket.HEADER_SIZE;

	    if (encoding == RtpPacket.PCMU_ENCODING) {
	        /*
		 * convert ulaw to linear
		 */
		decodedData = new byte[dataLength * 2];

	        AudioConversion.ulawToLinear(rtpData,
		    RtpPacket.HEADER_SIZE, dataLength, decodedData);

		totalSamples += dataLength;
	    } else if (speexDecoder != null && 
		    length < rtpReceiverPacket.getDataSize()) {

                long start = CurrentTime.getTime();

                try {
                    decodedData = speexDecoder.decodeToByteArray(rtpData, 
			RtpPacket.HEADER_SIZE, dataLength);

		    if (Logger.logLevel == -5) {
			if (doCount > 0) {
			    if (doCount-- > 0) {
			        Util.dump("decoded length " 
				    + decodedData.length, decodedData, 0, 
				    length);
			    }
			}
		    }
		    
		    dataLength = decodedData.length;
		    totalSamples += (dataLength / 2);
                } catch (Exception e) {
                    Util.dump("speex decoder failed, " 
			+ " length " + dataLength + ": " + e.getMessage(),
		        rtpData, 0, 16);
		    //e.printStackTrace();
                    //speexDecoder = null;
		    continue;
		}
	    } else {
		decodedData = new byte[dataLength];

		System.arraycopy(rtpData, RtpPacket.HEADER_SIZE, decodedData, 0,
		    dataLength);

		totalSamples += (dataLength / 2);
	    }

	    rtpReceiverPacket.updateRtpHeader(dataLength);

	    if (Logger.logLevel == -37) {
		boolean silence = true;

		for (int i = RtpPacket.HEADER_SIZE; i < length; i++) {
		    if (rtpData[i] != 0) {
			silence = false;
			break;
		    } 
		}

		if (silence) {
		    Logger.println("Got silence, packet # " + received);
		}
	    }

	    byte[] speakerData = decodedData;

	    //
            // There should no longer be a need to resample.
            //
            // The SDP parameters are negotiated to accomodate
            // the softphone.  When we use CoreAudio on the Mac,
            // we won't be limited to 44,100.
            //
	    synchronized (jitterManager) {
	        jitterManager.insertPacket(
		    rtpReceiverPacket.getRtpSequenceNumber(), speakerData);
	    }
	}

	oldSynchronizationSource = rtpReceiverPacket.getSynchronizationSource();

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println(
		"oldSynchronizationSource " + oldSynchronizationSource);
	}

	if (Logger.logLevel >= Logger.LOG_INFO && received > 0) {
	    double avg = Math.round(
		((double)totalTimeSinceStart / received) * 1000) / 1000D;

	    Logger.println("Average time between packets received " + avg
		+ " ms");

	    Logger.println("Packets Received " + received);

	    if (speaker != null) {
		speaker.printStatistics();
	    }

	    synchronized (jitterManager) {
	        jitterManager.printStatistics();
	    }
	}
    }

    private void updateJitterParameters() {
        int maxJitterBufferSize = Utils.getIntPreference(
            "com.sun.mc.softphone.media.MAX_JITTER_BUFFER_SIZE", -1);

        if (maxJitterBufferSize > 0) {
            if (jitterManager.getMaxJitterBufferSize() != maxJitterBufferSize) {
		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                    Logger.println("setting max jitter buffer size to "
                        + maxJitterBufferSize);
		}
                jitterManager.setMaxJitterBufferSize(maxJitterBufferSize);
            }
        }

        int minJitterBufferSize = Utils.getIntPreference(
            "com.sun.mc.softphone.media.MIN_JITTER_BUFFER_SIZE", -1);

        if (minJitterBufferSize >= 0) {
            if (jitterManager.getMinJitterBufferSize() != minJitterBufferSize) {
		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                    Logger.println("setting min jitter buffer size to "
                        + minJitterBufferSize);
                }
                jitterManager.setMinJitterBufferSize(minJitterBufferSize);
	    }
        }

        String s = Utils.getPreference(
            "com.sun.mc.softphone.media.DUP_LAST_PACKET");

	if (s != null) { 
	    if (s.equals("true")) {
	        jitterManager.setPlcClassName("com.sun.voip.PlcDuplicate");
	    } else {
	        jitterManager.setPlcClassName("com.sun.voip.PlcCompress");
	    }
	}
    }

    private int silenceCount;

    private int silencePackets;
    private int dataPackets;

    class PacketProcessor extends Thread {
	private Ticker ticker;
	private int n;

        public PacketProcessor() {
            setName("PacketProcessor");
            setPriority(Thread.MAX_PRIORITY);
            start();
        }

        public void run() {
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

            while (!done) {
		n++;

    	        try {
                    ticker.tick();
                } catch (TickerException e) {
                    Logger.println(getName() + ":  tick() failed! " 
			+ e.getMessage());
                }   

		byte[] speakerData = null;

		synchronized (jitterManager) {
		    if (silenceCount > 0) {
			if (Logger.logLevel >= Logger.LOG_MOREINFO) {
			    Logger.println("Stuffing speaker with " 
				+ silenceCount + " packets of silence "
				+ " available " + speaker.available()
				+ " bufferSize " + speaker.getBufferSize());
			}

			/*
			 * Write silence to the speaker now to prevent underrun
			 */
			while (silenceCount-- > 0) {
			    writeSpeaker(new byte[speakerWriteSize], 0,
				speakerWriteSize);
			}
		    }

		    try {
		        JitterObject jo = jitterManager.getFirstPacket();

			speakerData = (byte[]) jo.data;
		    } catch (NoSuchElementException e) {
		    }
		}

                if (speakerData == null) {
		    if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
			Logger.println("speaker data is null, nothing to write.");
		    }

		    recordSilence(1);
		    continue;
                }

		recordAudio(speakerData);
                sendDataToSpeaker(speakerData);

		if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
		    if ((n % 1000) == 0) {
			ticker.printStatistics();
		    }
		}
            }

	    ticker.disarm();
        }
    }

    private void sendDataToSpeaker(byte[] speakerData) {
	    String logLevel =
                Utils.getPreference("com.sun.mc.softphone.media.LOG_LEVEL");

	    if (logLevel != null && logLevel.length() > 0) {
                try {
                    Logger.logLevel = Integer.parseInt(logLevel);
                } catch (NumberFormatException e) {
                    Logger.println("Can't set log level to " + logLevel);
                }
	    }

	    writeSpeaker(speakerData, 0, speakerData.length);

	    if (receivedChanged && (received % 1000) == 0) {
		receivedChanged = false;

		double percentDropped =
		    Math.round(((double)bytesDropped * 100) / ((double)totalSamples));

		double avg = Math.round(
		    ((double)totalTime / 1000) * 1000) / 1000D;

                Logger.println("Received " + received + ", "
		    + percentDropped + "% dropped (speaker overflow), " 
		    + rejected +  " rejected, " + avg + " avg rcv time, "
		    + (rtpReceiverPacket.getWrongRtpTimestamp()
		    + rtpReceiverPacket.getOutOfSequencePackets() 
		    + badPayload) + " bad packets, "
		    + probableSpeakerUnderrun + " probable speaker underrun, "
		    + outOfSequenceFixed + " fixed out of order");

		if (speexDecoder != null) { 
		    int decodes = speexDecoder.getDecodes();
		    long decodeTime = speexDecoder.getDecodeTime();

		    if (decodes > 0) {
		        Logger.println("Average Speex decode time "
			    + (((double)decodeTime / decodes)) / 
			    CurrentTime.getTimeUnitsPerSecond() + " ms");
		    }

		    speexDecoder.resetStatistics();
		}

		totalTime = 0;
	    }
    }

    private boolean startComfort() {
	if (comfortNoisePlayer != null) {
	    return true;
	}

	try {
            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		Logger.println("Starting comfort noise...");
	    }
	    comfortNoisePlayer = 
		new AudioFilePlayer("comfort_noise.au", 1000, speaker);
	} catch (Exception e) {
	    Logger.println("Failed to start comfort noise player! "
		+ e.getMessage());
	    return false;
	}

	return true;
    }

    String recordingPath;
    String recordingType;

    public void startRecording(String path, String recordingType) 
	    throws IOException {

	pauseRecording = false;

	if (connected == false) {
	    Logger.println("Setting up to record " + path
		+ " " + recordingType);

	    this.recordingPath = path;
	    this.recordingType = recordingType;
	    return;
	}

	recordRtp = false;

        Logger.println("recording " + path
	    + " " + recordingType);

	if (recordingType.equalsIgnoreCase("Rtp")) {
	    recordRtp = true;
	}

        MediaInfo m = new MediaInfo(mediaInfo.getPayload(),
            RtpPacket.PCM_ENCODING, mediaInfo.getSampleRate(),
            mediaInfo.getChannels(), false);

	try {
            recorder = new Recorder(path, recordingType, m);
	    startRecordTime = System.currentTimeMillis();
        } catch (IOException e) {
            throw new IOException("Can't record to " + path + " " 
		+ e.getMessage());
        }
    }

    private boolean pauseRecording;

    public void pauseRecording() {
	pauseRecording = true;
    }

    public void resumeRecording() {
	pauseRecording = false;
    }

    public void stopRecording() {
	if (recorder == null) {
	    return;
	}

	Logger.println("Stop recording");

	long totalRecordTime = System.currentTimeMillis() - startRecordTime;

	Logger.println("total Record seconds = " 
	    + (totalRecordTime / 1000.)
	    + " seconds recorded = " 
	    + (dataRecorded / speakerWriteSize * RtpPacket.PACKET_PERIOD / 1000.));

	Logger.println("Silence packets " + silencePackets
	    + " data packets " + dataPackets);

	synchronized (this) {
	    recorder.done();
	    recorder = null;
	}
    }

    private void recordPacket(byte[] data, int length) {
	if (recorder == null || pauseRecording == true) {
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

    private void recordAudio(byte[] data) {
	if (recorder == null || pauseRecording == true) {
	    return;
	}

	if (recordRtp) {
	    return;
	}

	dataPackets++;

        try {
            recorder.write(data, 0, data.length);
	    dataRecorded += data.length; 
        } catch (IOException e) {
            Logger.println("Unable to record data " + e.getMessage());
            recorder = null;
        }
    }

    private void recordSilence(int nPackets) {
	if (recorder == null || speaker == null) {
	    return;
	}

	silencePackets += nPackets;

	int writeSize = speakerWriteSize;

	if (recordRtp) {
	    writeSize += RtpPacket.HEADER_SIZE;
	} 

	byte[] data = new byte[nPackets * writeSize];

        try {
            recorder.write(data, 0, data.length);
	    dataRecorded += data.length;
        } catch (IOException e) {
            Logger.println("Unable to record silence " + e.getMessage());
        }
    }

    private int badPackets;

    private void sendReport() {
	int lostNow = rtpReceiverPacket.getWrongRtpTimestamp() +
	    rtpReceiverPacket.getOutOfSequencePackets() - badPackets;

	badPackets = rtpReceiverPacket.getWrongRtpTimestamp() +
            rtpReceiverPacket.getOutOfSequencePackets();

	lostNow /= 256;

	rtcpReceiverPacket.setFractionLost((byte)lostNow);

	rtcpReceiverPacket.setCumulativeLost(badPackets);

	rtcpReceiverPacket.setHighestSeqReceived(
	    rtpReceiverPacket.getRtpSequenceNumber());

	rtcpReceiverPacket.setLSR((int)
	    (((double)bytesDropped * 100) / ((double)totalSamples)));
	
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Receiver:  "
	        + "fraction lost " + lostNow
	        + ", cumulative lost " + badPackets
	        + ", highest sequence received " 
	        + rtpReceiverPacket.getRtpSequenceNumber()
	        + ", LSR "  
	        + ((int) (((double)bytesDropped * 100) / ((double)totalSamples))));
	}

	try {
            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("sending Receiver report to "
                    + rtcpReceiverPacket.getSocketAddress());
            }

	    rtpSocket.send(rtcpReceiverPacket);
	} catch (IOException e) {
	    Logger.println("Can't send Rtcp packet " + e.getMessage());
	}
    }

    private long timeLastPacketWritten;

    private void writeSpeaker(byte[] speakerData, int offset, int size) {
	if (speaker == null) {
	    return;
	}

        if (channels == 2) {
            String s = Utils.getPreference(SWAP_SPEAKER_CHANNELS);

            if (s != null && s.equalsIgnoreCase("true")) {
                int ix = offset;

                for (int i = 0; i < size; i += 4) {
                    byte b = speakerData[ix];
                    speakerData[ix] = speakerData[ix + 2];
                    speakerData[ix + 2] = b;

                    b = speakerData[ix + 1];
                    speakerData[ix + 1] = speakerData[ix + 3];
                    speakerData[ix + 3] = b;

                    ix += 4;
                }
            }
        }

        int available = speaker.available();

	int elapsed;

	if (received == 0) {
	    elapsed = 20;
	    timeLastPacketWritten = System.currentTimeMillis();
	} else {
	    long now = System.currentTimeMillis();

	    elapsed = (int) (now - timeLastPacketWritten);

	    timeLastPacketWritten = now;
   
	    if (available == speaker.getBufferSize() ||
		    elapsed > speakerBufferMillis) {

	        probableSpeakerUnderrun++;
	    }
	}

        int used = speaker.getBufferSize() - available;

	if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
	    if (used < speaker.getBufferSize(20)) {
	        Logger.println("writeToSpeaker " + received 
		    + ":  elapsed=" + elapsed + " used " 
		    + speaker.getBufferSizeMillis(used)
		    + " len " + size);
	    }
	}

        int writeSize = Math.min(available, size);
        
	if (writeSize < size) {
	    String s = Utils.getPreference(
		"com.sun.mc.softphone.media.FLUSH_SPEAKER_WHEN_FULL");

	    if (s != null && s.length() > 0 && s.equalsIgnoreCase("true")) {
		if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Speaker full, flushing speaker...");
		}
	        speaker.flush();
	    }

            writeSize = Math.min(speaker.available(), size);
	}

	long now = System.currentTimeMillis();

        if (writeSize == 0) {
	    dropped++;
            bytesDropped += size;

	    /*
	     * Don't flush if on a Mac.
	     */
	    if (Utils.isMacOS()) {
	        if (Logger.logLevel >= Logger.LOG_INFO) {
	            Logger.println("dropping packet, speaker full...");
		}
		return;
	    }

	    //
	    // HACK!  We shouldn't have to flush but it seems
	    // that available() mysteriously returns 0 when it
	    // shouldn't and the only way to recover is by flushing.
	    // Seems to only happen on a SunRay.
	    //
	    speaker.flush();  // might as well flush everything

	    available = speaker.available();

            writeSize = Math.min(available, size);

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("flushed speaker, size " + size
		    + " writeSize = " + writeSize
		    + " available " + available + " speakerWriteSize "
		    + speakerWriteSize);
	    }

            if (writeSize == 0) {
		if (!done) {
                    Logger.println(
			"no space available after flush! seq " + received);
		}
                return;       // something is wrong, we just flushed
	    }
        } else if (writeSize < size) {
            /*
             * It is better to drop the whole packet rather than to
             * write as much as we can.  If we write as much as we can,
             * we're likely to keep the speaker buffer full and have
             * to drop some of subsequent packets as well.
             * By dropping the whole packet, there should be room
             * next time for more data.
             */
            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("dropped " + size + " bytes");
            }

            bytesDropped += size;
            return;
	}
                
	if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
	    Logger.println("writing to speaker:  " + writeSize);
	}

	try {
	    speaker.write(speakerData, offset, writeSize);
	} catch (IOException e) {
	    Logger.println("Unable to write to speaker!  "
		+ e.getMessage());
	}

	return;
    }

    /*
     * Decide whether or not we should accept this packet.
     */
    private boolean acceptPacket(RtpReceiverPacket rtpReceiverPacket, 
	    InetSocketAddress isa) {

	if (verifySender == false) {
	    return true;
	}

	/*
	 * If we're still getting packets from the last session, drop them.
	 * This can happen if the communicator is using the same receiver port
	 * for each session.
	 */
	if (oldSynchronizationSource != 0 && 
	    rtpReceiverPacket.getSynchronizationSource() ==
	        oldSynchronizationSource) {

	    Logger.println("Dropping packets with old sync source... old "
		+ Integer.toHexString(oldSynchronizationSource));
	    return false;
	}

        InetSocketAddress from = (InetSocketAddress)
            rtpReceiverPacket.getSocketAddress();

        String fromAddress = from.getAddress().getHostAddress();
        String expectedAddress = isa.getAddress().getHostAddress();

	/*
	 * We can't verify the sender's port because the SIP exchange
	 * only tells us what port we should send to and that may not
	 * be the same port the sender uses as a source port when sending
	 * to us.
	 */
        if (!fromAddress.equals(expectedAddress)) {
            if ((rejected % 100) == 0 || Logger.logLevel >= Logger.LOG_DETAILINFO) {
                Logger.println("Dropping.  Expected "
                    + isa.getAddress().getHostAddress() + ":" + isa.getPort()
                    + " got " + from.getAddress().getHostAddress()
                    + ":" + from.getPort());
	    }

            return false;
        }

        return true;
    }

    private byte[] decrypt(byte[] data, int length) {
	try {
	    return cipher.doFinal(data, 0, length);
	} catch (Exception e) {
	    Logger.println("SipCommunicator:  decryption failed:  "
	        + "length " + length + " " + e.getMessage());
	    done();    
	    return data;
	}
    }   
 
}
