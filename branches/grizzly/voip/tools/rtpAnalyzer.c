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

#include <sys/types.h>
#include <sys/int_types.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <netdb.h>

/*
 * This program processes a raw snoop file or a bridge recording file
 * and optionally checks RTP headers, produces an audiofile, dumps speech
 * detector information, and produces a recording file.
 *
 * The raw snoop file must contain RTP data to or from the specified port.
 * The bridge recording file is produced by recording a conference or members.
 * The audio output file has the name <input file>.[to | from].<port>.au
 * The recording output file has the name <input file>.[to | from].<port>.rtp
 * and is the same format as the bridge recording file.
 */

#pragma pack(1)

#define HISTOGRAM_ENTRIES 500

int histogramTotals[HISTOGRAM_ENTRIES];

#define MARK 0x80
#define COMFORT_PAYLOAD 0xd

/*
 * A recording file has a 16 byte header consisting of "RTP" followed by zeros
 */
struct record_header {
	char x[16];
};

/*
 * RTP Header
 */
struct rtphdr {
	unsigned char version;
	unsigned char payload;
	short seq;
	int rtpTs;
	int ssrc;
};

/*
 * Each record in a recording file has the following format.
 * Data follows the rtphdr.
 */
struct record_format {
	short length;
	short timechange;
	struct rtphdr rtphdr;
};

/*
 * A raw snoop file has a 16 byte header strting with the string "snoop".
 */
struct snoop_header {
	char x[16];
};

/*
 * Each record in a raw snoop file has the following format
 */
struct snoop_format {
	int x;
	int y;
	int recordsize;
	int z;
	long seconds;
	long microseconds;
	char enetaddr[12];
	short nexthdr;
	struct ip ip;
	struct udphdr udphdr;
	struct rtphdr rtphdr;
};

/*
 * http://www.opengroup.org/public/pubs/external/auformat.html
 */
struct au_header {
        int magic;
        int hdrsize;
        int datasize;
        int encoding;
        int samplerate;
        int channels;
} auheader = {
        0x2e736e64,
        24,
        0,
        0,
        0,
        0
};

int encoding = 1;	/* ulaw = 1, linear = 3 */
int sampleRate = 8000;
int channels = 1;

/*
 * Function prototypes
 */
void analyze_snoop(char *cp, char *cpend);
void analyze_recording(char *cp, char *cpend);
void checkRtp(struct rtphdr *rtphdr, int len, int timechange);
void error(char *s1);
char *map(char *file, int size);
void dump(char *cp, int len);
void usage();

int fdAudio;
int fdRtp;
int size;
int port;
int ipAddress;
int expectedLength;

long lastseconds;
long lastmicroseconds;

int totalPackets;
int packetsProcessed;

int silencePackets;
int nonSilencePackets;

int audioDataLength;

int from = 0;
int checkRTP = 0;
int generateAudio = 0;
int generateRtp = 0;
int checkSilence = 0;
int checkNonSilence = 0;
int verbose = 0;
int generateTimePoints = 0;

main(int argc, char *argv[])
{
	char *cp;
	char *cpend;
	char *inputfile;
	char outputfile[80];
	struct stat statbuf;

	if (argc < 2) {
	    usage();
	}

	argc--;		/* skip program name */
	argv++;		/* point to raw snoop file name */

	argc--;
	inputfile = *argv++;	/* input file name */

	strcpy(outputfile, inputfile);

	while (argc-- > 0) {
		if (strcmp(*argv, "-to") == 0) {
			argc--;
			argv++;		/* point to next argument */

			if (argc < 0 || (port = atoi(*argv)) <= 0) {
				usage();
			}

			strcat(outputfile, ".to.");
			strcat(outputfile, *argv);
		} else if (strcmp(*argv, "-from") == 0) {
			argc--;
			argv++;		/* point to next argument */

			from = 1;

			if (argc < 0 || (port = atoi(*argv)) <= 0) {
				usage();
			}

			strcat(outputfile, ".from.");
			strcat(outputfile, *argv);
		} else if (strcmp(*argv, "-ip") == 0) {
			struct hostent *hp;
			char **p;

			argc--;
                        argv++;         /* point to next argument */

                        if (argc < 0) {
                                usage();
                        }
#if 0
          	        if ((int)(addr = inet_addr(*argv)) == -1) {
              	            (void) printf("IP-address must be of the form a.b.c.d\n");
              		    exit (1);
          		}
#endif

			hp = gethostbyname(*argv);

			if (hp == NULL) {
			    printf("Unknown host %s\n", *argv);
			    exit(1);
			}

			strcat(outputfile, ".");
			strcat(outputfile, *argv);

			p = hp->h_addr_list;

			memcpy(&ipAddress, *p, sizeof(ipAddress));
#if 0
			printf("ipAddress %x\n", ipAddress);
#endif
		} else if (strcmp(*argv, "-len") == 0) {
                        argc--;
                        argv++;

                        if (argc < 0 || (expectedLength = atoi(*argv)) <= 0) {
                                usage();
                        }
		} else if (strcmp(*argv, "-checkRtp") == 0) {
		    checkRTP++;
		} else if (strcmp(*argv, "-checkSilence") == 0) {
		    checkSilence++;
		} else if (strcmp(*argv, "-checkNonSilence") == 0) {
		    checkNonSilence++;
		} else if (strcmp(*argv, "-au") == 0) {
		    generateAudio++;
		} else if (strcmp(*argv, "-rtp") == 0) {
		    generateRtp++;
		} else if (strcmp(*argv, "-verbose") == 0) {
		    verbose++;
		} else if (strcmp(*argv, "-t") == 0) {
		    generateTimePoints++;
		} else if (strcmp(*argv, "-sampleRate") == 0) {
		    argc--;
		    argv++;

		    if (argc < 0 || (sampleRate = atoi(*argv)) <= 0) {
			usage();
		    }
		} else if (strcmp(*argv, "-channels") == 0) {
		    argc--;
		    argv++;

		    if (argc < 0 || (channels = atoi(*argv)) <= 0) {
			usage();
		    }
		} else if (strcmp(*argv, "-encoding") == 0) {
                    argc--;
                    argv++;

                    if (argc < 0) {
                        usage();
                    }
		
		    if (strcmp(*argv, "PCMU") == 0) {
			encoding = 1;
		    } else if (strcmp(*argv, "PCM") == 0) {
			encoding = 3;
		    } else {
			usage();
		    }
		} else {
		    usage();
		}
		argv++;
	}

	if (generateAudio) {
	    char audioFile[100];

	    strcpy(audioFile, outputfile);
	    strcat(audioFile, ".au");

	    if ((fdAudio = creat(audioFile, 0666)) < 0) {
		error("creat audio file");
	    }

	    if (verbose) {
		printf("created audiofile %s\n", audioFile);
	    }

	    auheader.samplerate = sampleRate;
	    auheader.channels = channels;
	    auheader.encoding = encoding;  

	    /*
             * write initial header.  We'll fill in the total size later.
             */
            if (write(fdAudio, &auheader, sizeof(struct au_header)) !=
                sizeof(struct au_header)) {
                    error("write au_header");
            }
	} 

	if (generateRtp) {
	    char rtpFile[100];
	    char header[16];
	    int i;

            strcpy(rtpFile, outputfile);
            strcat(rtpFile, ".rtp");

            if ((fdRtp = creat(rtpFile, 0666)) < 0) {
		error("creat rtp file");
	    }

	    /*
	     * Write our Rtp header
	     */
	    header[0] = 'R';
	    header[1] = 'T';
	    header[2] = 'P';

	    for (i = 3; i < 16; i++) {
		header[i] = 0;
	    }

	    if (write(fdRtp, header, 16) != 16) {
		error("write RTP header");
	    }
	
	    if (verbose) {
		printf("created rtp file %s\n", rtpFile);
	    }
	}

	if (stat(inputfile, &statbuf) != 0)
	    error("stat");

	size = statbuf.st_size;

	cp = map(inputfile, size);

	cpend = cp + size;

#if 0
	printf("raw file size %x(%d.), cp %x, cpend %x\n", 
	    size, size, cp, cpend);
#endif

	if (strncmp(cp, "snoop", 5) == 0) {
	    if (port <= 0 || ipAddress == 0) {
		usage();
	    }
	    printf("analyzing snoop file... %s\n", inputfile);
	    analyze_snoop(cp, cpend);
	} else if (strncmp(cp, "RTP", 3) == 0) {
	    printf("analyzing recording file... %s\n", inputfile);
	    analyze_recording(cp, cpend);
	} else {
	    printf("unrecognized input file\n");
	    exit(1);
	}

        if (generateAudio) {
	    if (lseek(fdAudio, 8, SEEK_SET) != 8) {
		error("seek to write data length");
	    }

	    if (write(fdAudio, &audioDataLength, 4) != 4) {
		error("write data length");
	    }

	    close(fdAudio);
	}

	if (generateRtp) {
	    close(fdRtp);
	}

	if (checkRTP) {
	    printHistogram(histogramTotals);
	}

	if (checkSilence) {
	    printf("%d. non-silence packets\n", nonSilencePackets);
	}

	if (checkNonSilence) {
	    printf("%d. silence packets\n", silencePackets);
	}

	printf("total packets in input file %d., packets processed %d\n", totalPackets,
	    packetsProcessed);

	exit(0);
}

void
analyze_snoop(char *cp, char *cpend) 
{
	int expectedOffset = 0;

	cp += sizeof(struct snoop_header);	/* skip snoop header */

	while (cp < cpend) {
		char dataBuffer[10000];
		int datalen;

		static int timechange;

		struct snoop_format *s = (struct snoop_format *)cp;
		struct ip ip;
		struct udphdr udphdr; 
		struct rtphdr rtphdr;
		char *dp = cp + sizeof(struct snoop_format);  /* point to audio data */
		u_short last_ip_id;
		u_short ip_off;
		int ipaddr;

		totalPackets++;

		/*
		 * We have to copy the data because in case of alignment problems
		 */
		memcpy(&ip, &s->ip, sizeof(struct ip));

		ip_off = ip.ip_off & ~(IP_DF | IP_MF);

		if (s->recordsize == 0) {
			printf("zero recordsize before end of file reached...\n");
		        printf("packet %d:  cp %x, , recordsize %x\n", 
		            totalPackets, cp, s->recordsize);
			break;
		}

		/*
		 * round up to int boundary
		 */
		cp += (s->recordsize + sizeof(int) - 1);
		cp = (char *)((int)cp & ~(sizeof(int) - 1));

		/*
		 * skip non-IP packets
		 */
		if (s->nexthdr != 0x0800) {
#if 1
		    printf("skipping non-ip %d\n", totalPackets);
#endif
		    continue;
		}

		
		if (from == 0) {
		    ipaddr = ip.ip_dst.s_addr;
		} else {
		    ipaddr = ip.ip_src.s_addr;
		} 

		if (ipAddress != ipaddr) {
#if 1
		    printf("skipping %d, wrong ip %x != %x\n", totalPackets, ipAddress, ipaddr);
#endif
		    continue;
		}
		
		if (ip_off != expectedOffset) {
#if 1
		    printf("skipping %d, expected offset %d, got %d\n", 
			totalPackets, expectedOffset, ip_off);
#endif
		    expectedOffset = 0;  /* drop any remaining fragments */
		    last_ip_id = 0;   
		    continue;
		}

		if (ip_off == 0) {
		    timechange = 0;

		    memcpy(&udphdr, &s->udphdr, sizeof(struct udphdr));

		    datalen = udphdr.uh_ulen - sizeof(struct udphdr) - sizeof(struct rtphdr);

		    /* 
		     * only copy the amount in packet.
		     * datalen has actual total length
		     */
		    memcpy(dataBuffer, &s->rtphdr, ip.ip_len - sizeof(struct ip) -
			sizeof(struct udphdr));
		} else {
		    int i;
		    int offset;

		    if (ip.ip_id != last_ip_id) {
#if 1
			printf("skipping %d, id's don't match %d != %d\n", 
			    totalPackets, ip.ip_id, last_ip_id);
#endif

			continue;	/* it's not the next fragment */
		    }

		    /*
		     * Since this is a fragment there is no udp or rtp header.
		     * The voice data starts where the udp header would be.
		     */
		    dp = (char *) &s->udphdr;

		    offset = (ip_off * 8) - sizeof(struct udphdr);
		    
		    /* reassemble */
		    for (i = 0; i < ip.ip_len - sizeof(struct ip); i++) {
			dataBuffer[offset + i] = dp[i];
		    }
		}

#if 0
                printf("got packet %d, len %d, offset %x, total len %d\n", 
			totalPackets, ip.ip_len - sizeof(struct ip), ip.ip_off, datalen);
#endif

	        if (ip.ip_off & IP_MF) {
		    expectedOffset += ((ip.ip_len - sizeof(struct ip)) / 8);

		    if (ip_off != 0) {
			continue;
		    }

                    last_ip_id = ip.ip_id;
		    continue;		/* more fragments to come */
		} else {
		    expectedOffset = 0;
		}

		if ((from == 0 && udphdr.uh_dport != port) ||
		    (from == 1 && udphdr.uh_sport != port)) {
#if 1
			printf("skipping bad port %d != %d or %d != %d\n",
			    udphdr.uh_dport, port, udphdr.uh_sport, port);
#endif

			continue;
		}

#if 0
	        dump(dataBuffer, 16);
#endif

		if (expectedLength != 0 && expectedLength != datalen) {
			printf("skipping because of wrong data size %d != %d\n",
			    expectedLength, datalen);
			continue;
		}

		/*
		 * At this point, we have a reassembled packet 
		 * starting with the rtp header.
		 */
		memcpy(&rtphdr, dataBuffer, 12);
		
		dp = dataBuffer + sizeof(struct rtphdr);

		packetsProcessed++;

		if (packetsProcessed == 1) {
			timechange = 20000;
		} else if (s->seconds == lastseconds) {
			timechange += (s->microseconds - lastmicroseconds);
		} else {
			timechange += (1000000 - lastmicroseconds + s->microseconds);
		}

		timechange = (timechange + 500) / 1000;  /* round up and convert to ms */

		lastseconds = s->seconds;
		lastmicroseconds = s->microseconds;

		if (generateRtp) {
		    int len = sizeof (struct rtphdr ) + datalen + 4;

		    char c = len >> 8;

		    if (write(fdRtp, &c, 1) != 1) {
			error("write rtp 1");
		    }

		    c = len;

		    if (write(fdRtp, &c, 1) != 1) {
			error("write rtp 1");
		    }

                    c = timechange >> 8; 

                    if (write(fdRtp, &c, 1) != 1) {
                        error("write rtp 1");
                    }

                    c = timechange;

                    if (write(fdRtp, &c, 1) != 1) {
                        error("write rtp 1");
                    }

		    if (write(fdRtp, dataBuffer, 
			    sizeof (struct rtphdr) + datalen) < 0) {

			error("write rtp 3");
		    }
		}

		if (checkRTP) {
		    checkRtp(&rtphdr, datalen, (int)timechange);
		}

    		if (checkSilence || checkNonSilence) {
		    silenceCheck(dp, 0, datalen);
		}

		if (generateTimePoints) {
        	    if ((rtphdr.payload & MARK) != 0) {
		        printf("%d, %d\n", packetsProcessed, 20);
		    } else {
			if (timechange > 200) {
		            printf("%d, %d\n", packetsProcessed, 200);
			} else {
		            printf("%d, %d\n", packetsProcessed, timechange);
			}
		    }
		}

		if (verbose) {
		    beVerbose(dataBuffer, datalen + sizeof(struct rtphdr), timechange);
		}

		if (generateAudio) {
        	    if ((rtphdr.payload & ~MARK) != COMFORT_PAYLOAD) {
		        if (write(fdAudio, dp, datalen) != datalen) {
                            error("write audio");
		        }
			audioDataLength += datalen;
		    }
                }
	}
}
	
void
analyze_recording(char *cp, char *cpend) 
{
	cp += sizeof(struct record_header);		/* skip header */

	while (cp < cpend) {
	    struct record_format record;
	    int length;
	    int timechange;
	    int datalen;

	    memcpy(&record, cp, sizeof(struct record_format));

	    length = record.length;

	    timechange = record.timechange;

	    datalen = length - sizeof(struct record_format);

	    totalPackets++;
	    packetsProcessed++;

	    if (checkRTP) {
	        checkRtp(&record.rtphdr, datalen, timechange);
	    }

	    if (verbose) {
		beVerbose(&record.rtphdr, datalen, timechange);
	    }

            if (generateAudio) {
        	if ((record.rtphdr.payload & ~MARK) != COMFORT_PAYLOAD) {
	    	    char *dp = cp + sizeof(struct record_format);

                    if (write(fdAudio, dp, datalen) != datalen) {
                        error("write audio");
		    }
		    audioDataLength += datalen;
                }
            }

	    cp += length;
	}
}

short seq = 1;
int rtpTs = 0;

int maxtc = 0;

int total_time;
int comfort_received = 0;

int 
getExpectedLength(int payload) 
{
	switch (payload) {
	case 0:
		return 160;	/* 8k PCMU */
	
	case 102:
		return 640;	/* 8k/2 PCM */

	case 103:
		return 640;	/* 16k/1 PCM */

	case 104:
		return 1280;    /* 16k/2 PCM */

	case 105:
		return 1280;	/* 32k/1 PCM */

	case 106:
		return 2560;	/* 32k/2 PCM */

	case 107:
		return 1764;	/* 44100/1 PCM */

	case 108:
		return 3528;	/* 44100/2 PCM */

	case 109:
		return 1920;	/* 48000/1 PCM */

	case 110:
		return 3840;	/* 48000/2 PCM */

	case 111:
		return 320;	/* 8000/2 PCMU */

	case 112:
		return 320;	/* 16000/1 PCMU */
	
	case 113:
		return 640;	/* 16000/2 PCMU */
	
	case 114:
		return 640;	/* 32000/1 PCMU */

	case 115:
		return 1280;	/* 32000/2 PCMU */

	case 116:
		return 882;	/* 44100/1 PCMU */

	case 117:
		return 1764;	/* 44100/2 PCMU */

	case 118:
		return 960;	/* 48000/1 PCMU */

	case 119:
	  	return 1920;	/* 48000/2 PCMU */
	}
}

void
checkRtp(struct rtphdr *rtp, int len, int timechange)
{
	int i;
	int outOfSequence = 0;

	struct rtphdr rtphdr;

	int expectedLength;

	memcpy(&rtphdr, rtp, sizeof(struct rtphdr));  /* for alignment */

	if (rtphdr.payload == COMFORT_PAYLOAD) {
	    if (len != 0) {
	        len = 1;
	    }
	    comfort_received = 1;
	}

	expectedLength = getExpectedLength(rtphdr.payload);

#if 0
	if (len != expectedLength && rtphdr.payload != COMFORT_PAYLOAD) {

	    printf("packet %d:%d., pl %x, seq %x, ts %x, unusual data length %d\n", 
		totalPackets, packetsProcessed, rtphdr.payload, rtphdr.seq & 0xffff, 
		rtphdr.rtpTs, len);
	}
#endif

	if (rtphdr.version != 0x80) {
	    printf("bad data packet %d:%d.\n", totalPackets, packetsProcessed);
	}

	if (timechange < 0) {
	    printf("packet %d:%d, negative timechange %d.\n",
		totalPackets, packetsProcessed, timechange);

	    timechange = 0;
	}

	if (rtphdr.payload == COMFORT_PAYLOAD) {
	    timechange = 0;
	}
	    
	if (timechange > HISTOGRAM_ENTRIES) {
	    timechange = HISTOGRAM_ENTRIES - 1;
	}

	if (timechange > maxtc) {
	    maxtc = timechange;
	    /* printf("Max %d., %d:%d\n", maxtc, totalPackets, packetsProcessed); */
	}

	if (comfort_received != 1 || rtphdr.payload == COMFORT_PAYLOAD) {
	    total_time += timechange;
	}

	comfort_received = 0;

	histogramTotals[timechange]++;
	
	outOfSequence = 0;

	if (packetsProcessed > 1 && rtphdr.seq != seq) {
	    outOfSequence = 1;

	    printf("packet %d:%d., out of sequence packet, expected %x, "
		"got %x, off by %d.\n",
		totalPackets, packetsProcessed, seq & 0xffff, 
		rtphdr.seq & 0xffff, rtphdr.seq - seq);
	}

	if ((rtphdr.payload & MARK) == 0) {
	    if (!outOfSequence && packetsProcessed > 1 && rtphdr.rtpTs != rtpTs) {
	        if (len > 0) {
	            printf("packet %d:%d., bad rtp timestamp, expected %x, "
		        "got %x, off by %x\n",
		        totalPackets, packetsProcessed, rtpTs, rtphdr.rtpTs, 
		        rtphdr.rtpTs - rtpTs);
	        }
	    }
	}
	seq = rtphdr.seq + 1;
	rtpTs = rtphdr.rtpTs + len;
}

printHistogram(int *histogram) {
	int i;

	printf("ms\tpackets\n");

	for (i = 0; i < HISTOGRAM_ENTRIES; i++) {
	    if (histogram[i] != 0) {
		printf("%d\t%d\n", i, histogram[i]);
	    }
	}

	printf("Average time between packets %f ms\n",
	    (float)((float)total_time / (float)packetsProcessed));
}

/*
 * This routine converts from ulaw to 16 bit linear
 * 29 September 1989
 *
 * Craig Reese: IDA/Supercomputing Research Center
 *
 * References:
 * 1) CCITT Recommendation G.711  (very difficult to follow)
 * 2) MIL-STD-188-113,"Interoperability and Performance Standards
 *     for Analog-to_Digital Conversion Techniques,"
 *     17 February 1987
 *
 * Input: 8 bit ulaw sample
 * Output: signed 16 bit linear sample
 */
short
ulaw2linear(char ulawbyte)
{
    static int exp_lut[8]={0,132,396,924,1980,4092,8316,16764};
    int sign, exponent, mantissa, sample;

    ulawbyte = ~ulawbyte;
    sign = (ulawbyte & 0x80);
    exponent = (ulawbyte >> 4) & 0x07;
    mantissa = ulawbyte & 0x0F;
    sample = exp_lut[exponent] + (mantissa << (exponent+3));
    if (sign != 0) sample = -sample;

    return((short)sample);
}

#define CNTHRESH	50  	   /* # of avgs to test speaking (1/10 sec) */
#define POW_THRESH	50000.     /* initial power threshold */
#define RTP_DATA_SIZE	160

void error(char *s1)
{
	perror(s1);
	exit(1);	
}

silenceCheck(struct rtphdr *rtphdr, int offset, int len) {
	int silence;
	int i;
	char *dp = ((char *) rtphdr) + offset;

	dp += sizeof(struct rtphdr);

        if ((rtphdr->payload & ~MARK) == 0 ||
		(rtphdr->payload & ~MARK) > 112) {

		silence = 0xff;	   /* ulaw silence */
	} else {
		silence = 0;	   /* linear silence */
	}

	if (checkSilence) {
	    for (i = 0; i < len; i ++) {
		 if ((dp[i] & 0xff) != (silence & 0xff)) {
			nonSilencePackets++;
			printf("non-silence at %d., value %2x\n", 
			    i + sizeof(struct rtphdr), dp[i] & 0xff);
			break;
		 }
	    }
	}

	if (checkNonSilence) {
		for (i = 0; i < len; i++) {
		    if ((dp[i] & 0xff) == (silence & 0xff)) {
			silencePackets++;
			break;
		    }
		}
	}
}

beVerbose(struct rtphdr *rtphdr, int len, int timechange) {
	int i;
	int foundSilence = 0;
	int foundNonSilence = 0;
	int silence;

	struct rtphdr r;

	memcpy(&r, rtphdr, sizeof(struct rtphdr));    /* copy for alignment */

	if (verbose) {
            printf("%5d:%5d:  ", totalPackets, packetsProcessed);

            printf("%4d. bytes, %4d ms:    ", len, timechange);

            for (i = 0; i < sizeof(struct rtphdr); i++) {
                char *cp = (char *)rtphdr;
                printf("%3x", cp[i] & 0xff);
            }

            if ((r.payload & ~MARK) == COMFORT_PAYLOAD) {
                printf(" COMFORT ");
            }

	    if ((r.payload & MARK) != 0) {
		printf(" MARK");

                if ((r.payload & ~MARK) == COMFORT_PAYLOAD) {
		    printf(" (MARK shouldn't be set with COMFORT!)");
		}
	    } else {
	        if (timechange < 20) {
		    printf(" -");
	        } else if (timechange > 20) {
		    printf(" +");
	        }
	    }

	    if (checkSilence && foundNonSilence) {
		printf(" NON-SILENCE");
	    } 

	    if (checkNonSilence && foundSilence) {
		printf(" SILENCE");
	    }

            printf("\n");
        }

	if (verbose >= 2) {
	    /*
	     * Dump the packet
	     */
	    printf("\ndata length %d.\n", len);

	    for (i = 0; i < len + sizeof(struct rtphdr); i++) {
                char *cp = (char *)rtphdr;

		if ((i % 16) == 0) {
		    printf("\n%3x:  ", i);
	        }

		printf("%3x", cp[i] & 0xff);
	    }

            printf("\n\n");
	}

	if ((r.payload & MARK) != 0) {
	    seq = r.seq + 1;

	    rtpTs = r.rtpTs + len;
	    return;
	}
}

void dump(char *cp, int len) {
	int i;

        for (i = 0; i < len; i++) {
             if ((i % 16) == 0) {
                 printf("\n%3x:  ", i);
             }

             printf("%3x", cp[i] & 0xff);
        }

	printf("\n");
}

void usage() {
	printf("usage:  rtpAnalyzer <raw snoop file> | <recording file>\n"
		"\t[-to | -from <port>] [-checkRtp] [-au] [-rtp]\n"
		"\t-debug -verbose\n");
	exit(1);
}

char *
map(char *file, int size)
{
	int fd;
	char *cp;

	if ((fd = open(file, 0)) < 0)
	    error("open");

	cp = mmap(0, size, PROT_READ, MAP_PRIVATE, fd, 0);

	if (cp == (char *)-1)
		error("mmap");

	return cp;
}

