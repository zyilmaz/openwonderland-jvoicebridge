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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import java.net.InetSocketAddress;

import java.util.Vector;

import com.sun.voip.AudioConversion;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpSenderPacket;
import com.sun.voip.RtpSocket;
import com.sun.voip.Ticker;
import com.sun.voip.TickerException;
import com.sun.voip.TickerFactory;
import com.sun.voip.Util;

import com.sun.mc.softphone.common.Utils;

public class PacketGenerator {

    public static final int AU_ULAW_ENCODING = 1;
    public static final int AU_LINEAR_ENCODING = 3;

    private String path;

    private BufferedInputStream bufferedInputStream;

    private Player player;

    private int jitter;
    private int ordering;
    private int dropped;

    private Vector listeners = new Vector();

    private boolean done = false;

    /*
     * Read records from an RTP file, a snoop file or an audio file
     * while preserving timing information in RTP and snoop files.
     *
     * TBD allow specification of parameters which could affect audio quality.
     */
    public PacketGenerator(String path) throws IOException {
	this.path = path;

	try {
            FileInputStream in = new FileInputStream(path);

            bufferedInputStream = new BufferedInputStream(in);

            byte[] buf = new byte[4];

            read(buf, 0, buf.length);

            String s = new String(buf, 0, 4);

            if (s.substring(0,3).equals("RTP")) {
		player = new RtpPlayer();
            } else if (s.equals("snoo")) {
		player = new SnoopPlayer();
            } else if (s.equals(".snd")) {
		player = new AuPlayer();
	    } else {
                throw new IOException("Invalid replay file!");
            }
        } catch (IOException e) {
            throw new IOException("Unable to play " + path + ":  " 
		+ e.getMessage());
        }

	Logger.println("Generating packets from " + path);
    }

    /*
     * Read next record in input stream
     */
    public RtpSenderPacket getPacket() throws IOException {
        if (done) {
            throw new IOException("PacketGenerater done");
        }

	try {
            return player.getPacket();
	} catch (IOException e) {
	    done();
	    throw e;
	}
    }

    public byte[] getAudioData() throws IOException {
        if (done) {
            throw new IOException("PacketGenerater done");
        }

	try {
            return player.getAudioData();
	} catch (IOException e) {
	    done();
	    throw e;
	}
    }

    public void done() {
	if (done) {
	    return;
	}

	done = true;

	synchronized (listeners) {
	    for (int i = 0; i < listeners.size(); i++) {
	        PacketGeneratorListener listener = (PacketGeneratorListener)
		    listeners.get(i);

	        listener.packetGeneratorDone();
	    }        
	}
    }

    public void addListener(PacketGeneratorListener listener) {
	synchronized (listeners) {
	    listeners.add(listener);
	}
    }

    public void removeListener(PacketGeneratorListener listener) {
	synchronized (listeners) {
	    listeners.remove(listener);
	}
    }

    public MediaInfo getMediaInfo() {
	return player.getMediaInfo();
    }

    public void setJitter(int jitter) {
	this.jitter = jitter;
    }

    public void setOrdering(int ordering) {
	this.ordering = ordering;
    }

    public void setDropped(int dropped) {
	this.dropped = dropped;
    }

    public Player getPlayer() {
	return player;
    }

    /*
     * Read from a file
     */
    private void read(byte[] buf, int offset, int length) throws IOException {
        int bytesRead = 0;

        while (bytesRead < length) {
            int n = bufferedInputStream.read(buf, bytesRead, 
		length - bytesRead);

            if (n < 0) {
		done();
                throw new IOException("End of InputStream");
            }

            bytesRead += n;
        }
    }

    /*
     * Read the next <length> bytes and wait <elapsed> milliseconds
     * before returning
     */
    private long lastTime = 0;

    private byte[] getRecord(int elapsed, int length) throws IOException {
        if (elapsed < 0) {
	    done();
            throw new IOException("Bad elapsed " + elapsed);
        }

	if (Logger.logLevel >= Logger.LOG_DETAIL) {
            Logger.println("PacketGenerator elapsed:  " + elapsed
                + " length " + length);
        }

	byte[] data = new byte[length];

	try {
            read(data, 0, data.length);
        } catch (IOException e) {
	    done();

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("Done replaying data... " + e.getMessage());
	    }

	    throw new IOException("Done replaying data... " + e.getMessage());
        }

	if (lastTime == 0) {
	    lastTime = System.currentTimeMillis();
	}

	if (elapsed == 0) {
	    lastTime = System.currentTimeMillis();
	    
	    return data;
	}

	if (elapsed >= RtpPacket.PACKET_PERIOD) {
	    try {
		Thread.sleep(RtpPacket.PACKET_PERIOD / 2);
	    } catch (InterruptedException e) {
	    }
	}

	long now;

	while ((now = System.currentTimeMillis()) - lastTime < 
	        elapsed) {
	    ;
	}

	//Logger.println("elapsed " + (now - lastTime));
	lastTime = now;
	return data;
    }

    interface Player {

	public RtpSenderPacket getPacket() throws IOException;

	public byte[] getAudioData() throws IOException;

	public MediaInfo getMediaInfo();

    }
	
    /*
     * Read packets from an RTP file.  An RTP file has
     * a 16 byte header starting with "RTP".
     *
     * Each record in the file has a short for the total length
     * of the record followed by a short for the time delta since
     * the last record followed by an RTP header followed by audio data.
     */
    public class RtpPlayer implements Player {

	private MediaInfo mediaInfo;

	private int expectedDataLength;

	public RtpPlayer() throws IOException {
	    byte[] buf = new byte[12];

	    read(buf, 0, buf.length);	// Skip the rest of the initial header
	}

        public RtpSenderPacket getPacket() throws IOException {
            byte[] buf;

            buf = new byte[4];

            read(buf, 0, buf.length);	// read length and time delta

            int recordLength = ((buf[0] << 8) & 0xff00) | (buf[1] & 0xff);

            recordLength -= 4;       // account for header

            int elapsed = ((buf[2] << 8) & 0xff00) | (buf[3] & 0xff);

	    byte[] data = getRecord(elapsed, recordLength);

	    byte payload = (byte) (data[1] & ~RtpPacket.MARK_BIT);

	    if (expectedDataLength == 0) {
		/*
		 * Use the first media packet to determine 
		 * the media information.
		 */
		//XXX Fix me!  We want to ignore non-media payload types

		if (payload != 13) {
                    mediaInfo = MediaInfo.findMediaInfo(payload);

                    expectedDataLength = RtpPacket.getDataSize(mediaInfo.getEncoding(),
                        mediaInfo.getSampleRate(), mediaInfo.getChannels());

                    expectedDataLength += RtpPacket.HEADER_SIZE;
		}
	    } 

	    if (payload == mediaInfo.getPayload() && 
		   expectedDataLength != recordLength) {

		done();

		throw new IOException("Rtp:  Expected " + expectedDataLength
		   + " got " + recordLength);
	    }

	    RtpSenderPacket rtpSenderPacket = new RtpSenderPacket(
		mediaInfo.getEncoding(), mediaInfo.getSampleRate(), 
		mediaInfo.getChannels());

	    rtpSenderPacket.setBuffer(data);
	    return rtpSenderPacket;
	}

	public byte[] getAudioData() throws IOException {
	    RtpSenderPacket rtpSenderPacket = getPacket();

	    byte[] data = rtpSenderPacket.getData();

	    byte[] audioData = new byte[data.length - RtpPacket.HEADER_SIZE];

	    for (int i = 0; i < audioData.length; i++) {
		audioData[i] = data[RtpPacket.HEADER_SIZE + i];
	    }

	    return audioData;
	}

	public MediaInfo getMediaInfo() {
	    return mediaInfo;
	}

    }

    /*
     * Play packets from a raw snoop file.
     *
     * A raw snoop file has a 16 byte header starting with "snoop".
     * Each snoop record is as follows:
     * 
     *     8 byte pad
     *     4 byte record size
     *     4 byte pad
     *	   4 byte seconds since last record
     *	   4 byte microseconds since last record
     *    12 byte ethernet header
     *	   2 byte next header (IP)
     *    20 byte IP header
     *     8 byte UDP header (offset 4 is udp length)
     *    12 byte RTP header
     *     ? audio data
     *
     * The raw snoop file must contain only data which is to be recorded.
     * This means that there is audio data for once source or destination only.
     */
    public class SnoopPlayer implements Player {

	private MediaInfo mediaInfo;

	private int expectedDataLength;

	private int lastSeconds;
	private int lastMicroseconds;

	private short lastIPId;
	private short lastIPOffset;

	private int packetNumber = 1;

 	public SnoopPlayer() throws IOException {
	    byte[] buf = new byte[RtpPacket.HEADER_SIZE];

	    read(buf, 0, buf.length);	// skip the rest of the snoop header
	}

        public RtpSenderPacket getPacket() throws IOException {
	    byte[] buf = new byte[66];  // read everything up to RTP Header

	    read(buf, 0, buf.length);

	    int recordLength = (int) (((buf[8] << 24) & 0xff000000) |
		((buf[9] << 16) & 0xff0000) |
		((buf[10] << 8) & 0xff00) |
		(buf[11] & 0xff));

	    int seconds = (int) (((buf[16] << 24) & 0xff000000) |
		((buf[17] << 16) & 0xff0000) |
                ((buf[18] << 8) & 0xff00) | 
                (buf[19] & 0xff));

	    int microseconds = (int) (((buf[20] << 24) & 0xff000000) |
		((buf[21] << 16) & 0xff0000) |
                ((buf[22] << 8) & 0xff00) | 
                (buf[23] & 0xff));

	    //Logger.println("seconds " + Integer.toHexString(seconds) 
	    //	+ " lastSeconds " + Integer.toHexString(lastSeconds));

	    //Logger.println("microseconds " + Integer.toHexString(microseconds)
	    //	+ " lastmicroSeconds " + Integer.toHexString(lastMicroseconds));

	    //Util.dump("snoop", buf, 0, 32);

	    int elapsed = 20000;

	    if (packetNumber > 1) {
		if (seconds == lastSeconds) {
		    elapsed = microseconds - lastMicroseconds;
		} else {
		    elapsed = ((seconds - lastSeconds) * 1000000) - 
			lastMicroseconds + microseconds;
		}
	    } 

	    elapsed /= 1000;   // convert to milliseconds

	    lastSeconds = seconds;
	    lastMicroseconds = microseconds;

	    if (Logger.logLevel >= Logger.LOG_DETAIL) {
	        Logger.println("elapsed " + elapsed);
	    }

	    // XXX Need to deal with fragments.  udp length
	    // has reassembled length but each packet only has
	    // ip.ip_len - sizeof(ip) in each packet.
	    // first frag has udp and rtp, others don't.
	    // Since we require the snoop file to have the exact data
	    // to be played we know that successive fragments have the
	    // right ip id.  We still have to deal with missing fragments.
	    //
	    // I think the best approach is to reassemble first.
	    //
	   
	    int udpLength = (int) (((buf[62] << 8) & 0xff00) |
		(buf[63] & 0xff));

	    udpLength -= 8;	// account for udp header itself

	    byte[] data = getRecord(elapsed, udpLength);

            if (expectedDataLength == 0) {
		/*
		 * Use the first packet to determine the media information.
		 */
		byte payload = (byte) (data[1] & ~RtpPacket.MARK_BIT);

                mediaInfo = MediaInfo.findMediaInfo(payload);

                expectedDataLength = RtpPacket.getDataSize(
		    mediaInfo.getEncoding(), mediaInfo.getSampleRate(), 
		    mediaInfo.getChannels());

                expectedDataLength += RtpPacket.HEADER_SIZE;
	    }

	    int leftover = recordLength - 66 - udpLength;

	    if (leftover > 0) {
		buf = new byte[leftover];
		read(buf, 0, buf.length);
	    }

	    if (expectedDataLength != udpLength) {
                Util.dump("Skipping Packet " + packetNumber 
		    + " Expected " + expectedDataLength
                    + " got " + recordLength, buf, 0, buf.length);
		Util.dump("Rtp Header", data, 0, RtpPacket.HEADER_SIZE);

		return getPacket();
            }   

	    /* IP starts at 38 */
	    short ipId = (short) (((data[38 + 4] << 8) & 0xff) |
		(data[38 + 5] & 0xff));

	    short ipOffset = (short) (((data[38 + 6] << 8) & 0xff) |
		(data[38 + 7] & 0xff));

	    if ((ipOffset & 0x2000) != 0) {
		/* We have to reassemble.  Go get the next packet. */
		//return;
	    }

	    RtpSenderPacket rtpSenderPacket = new RtpSenderPacket(
		mediaInfo.getEncoding(), mediaInfo.getSampleRate(), 
		mediaInfo.getChannels());

	    rtpSenderPacket.setBuffer(data);
	    packetNumber++;
	    return rtpSenderPacket;
	}

        public byte[] getAudioData() throws IOException {
            RtpSenderPacket rtpSenderPacket = getPacket();

	    byte[] data = rtpSenderPacket.getData();

            byte[] audioData = new byte[data.length - RtpPacket.HEADER_SIZE];

            for (int i = 0; i < audioData.length; i++) {
                audioData[i] = data[RtpPacket.HEADER_SIZE + i];
            }

            return audioData;
        }

	public MediaInfo getMediaInfo() {
	    return mediaInfo;
	}

    }

    /*
     * Play a sun audio file.
     *
     * A Sun audio file has the following format:
     *
     * 4 bytes ".snd"
     * 4 bytes header size
     * 4 bytes data size
     * 4 bytes encoding
     * 4 bytes sample rate
     * 4 bytes channels
     * ? audio data follows <header size> bytes
     */
    public class AuPlayer implements Player {

	private MediaInfo mediaInfo;

	private RtpSenderPacket rtpSenderPacket;

	private int dataLength;

	private int packetNumber = 1;

	private Ticker ticker;	

	public AuPlayer() throws IOException {
	    byte[] buf = new byte[4];

	    read(buf, 0, buf.length);	// get header size

	    int headerSize = (int) (((buf[0] << 24) & 0xff000000) |
		((buf[1] << 16) & 0xff0000) |
		((buf[2] << 8) & 0xff00) |
		(buf[3] & 0xff));

	    buf = new byte[headerSize];

	    read(buf, 0, buf.length);	// skip the rest of the header

	    int auEncoding = (int) (((buf[4] << 24) & 0xff000000) |
		((buf[5] << 16) & 0xff0000) |
		((buf[6] << 8) & 0xff00) |
		(buf[7] & 0xff));

	    int sampleRate = (int) (((buf[8] << 24) & 0xff000000) |
		((buf[9] << 16) & 0xff0000) |
		((buf[10] << 8) & 0xff00) |
		(buf[11] & 0xff));

	    int channels = (int) (((buf[12] << 24) & 0xff000000) |
		((buf[13] << 16) & 0xff0000) |
		((buf[14] << 8) & 0xff00) |
		(buf[15] & 0xff));

	    int encoding;

	    if (auEncoding == AU_ULAW_ENCODING) {
		encoding = RtpPacket.PCMU_ENCODING;
	    } else {
		encoding = RtpPacket.PCM_ENCODING;
	    }

	    mediaInfo = MediaInfo.findMediaInfo(encoding, sampleRate, channels);

	    dataLength = RtpPacket.getDataSize(encoding, sampleRate, channels);

	    String e;

	    if (auEncoding == AU_ULAW_ENCODING) {
		e = "ulaw";
	    } else if (auEncoding == AU_LINEAR_ENCODING) {
		e = "linear";
	    } else {
		e = String.valueOf(encoding);
	    }

 	    rtpSenderPacket = 
		new RtpSenderPacket(mediaInfo.getEncoding(), 
		    mediaInfo.getSampleRate(), mediaInfo.getChannels());

	    rtpSenderPacket.setRtpPayload(mediaInfo.getPayload());
	    rtpSenderPacket.setMark();

	    ticker = createTicker();

	    //if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Playing audio file " + path
		    + " " + e + "/" + sampleRate + "/" + channels 
		    + " data length " + dataLength 
		    + " payload " + mediaInfo.getPayload());
	    //}
	}

        public RtpSenderPacket getPacket() throws IOException {
	    byte[] data;

	    try {
	        data = getRecord(0, dataLength);
	    } catch (IOException e) {
		ticker.disarm();
		throw e;
	    }

	    byte[] senderData = rtpSenderPacket.getData();

	    for (int i = 0; i < data.length; i++) {
		senderData[RtpPacket.HEADER_SIZE + i] = data[i];
	    }

	    if (packetNumber == 1) {
	        ticker.arm(RtpPacket.PACKET_PERIOD, RtpPacket.PACKET_PERIOD);
	    } else {
	        rtpSenderPacket.updateRtpHeader(senderData.length);
	    }

	    try {
	        ticker.tick();
	    } catch (TickerException e) {
		throw new IOException(e.getMessage());
	    }

	    packetNumber++;
	    return rtpSenderPacket;
	}

	public byte[] getAudioData() throws IOException {
	    if (packetNumber == 1) {
	        ticker.arm(RtpPacket.PACKET_PERIOD, RtpPacket.PACKET_PERIOD);
	    }

            try {
                ticker.tick();
            } catch (TickerException e) {
                throw new IOException(e.getMessage());
            }

	    try {
	        return getRecord(0, dataLength);
 	    } catch (IOException e) {
		ticker.disarm();
		throw e;
	    }
	}

	public MediaInfo getMediaInfo() {
	    return mediaInfo;
	}

        private Ticker createTicker() throws IOException {
            String tickerClassName = System.getProperty(
                "com.sun.mc.softphone.media.TICKER");

            TickerFactory tickerFactory = TickerFactory.getInstance();

	    Ticker ticker = null;

	    try {
                ticker = tickerFactory.createTicker(tickerClassName, "AuPlayer");
	    } catch (TickerException e) {
		throw new IOException("AuPlayer unable to create ticker for "
		    + tickerClassName + " " + e.getMessage());
            } 

	    return ticker;
	}
    }

    public static void main(String[] args) {
	if (args.length < 1) {
	    System.out.println(
		"java com.sun.mc.softphone.media.PacketGenerator <path> "
		+ "[-debug]");
	    System.exit(1);
	}

        PacketGenerator packetGenerator = null;

	try {
	    packetGenerator = new PacketGenerator(args[0]);
	} catch (IOException e) {
	    Logger.println(e.getMessage());
	    System.exit(1);
	}

	packetGenerator.debug();
    }

    private void debug() {
	while (true) {
	    try {
		RtpSenderPacket rtpSenderPacket = getPacket();

		byte[] data = rtpSenderPacket.getData();

		Util.dump("data " + data.length, data, 0, 
		    data.length >= 16 ? 16 : data.length);
	    } catch (IOException e) {
	        Logger.println(e.getMessage());
		System.exit(1);
	    }
	}
    }

}
