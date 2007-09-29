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
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>

/*
 * Given an audio file and a volume factor, adjust the volume by 
 * the volume factor and write the data back to the original file.
 */

#define MAX_VOLUME_FACTOR 10

char *map(char *, int *);

void usage() {
	printf("usage:  adjustVolume <ulaw (or linear) audio file> "
	    "<volume factor adjustment>\n");
	exit(1);
}

void error(char *s1)
{
	perror(s1);
	exit(1);	
}

/*
 * Audio file header http://www.opengroup.org/public/pubs/external/auformat.html
 */
#define MAGIC 0x2e736e64	/* ".snd" */
#define ULAW_ENCODING 1
#define LINEAR_ENCODING 3

struct au_header {
        int magic;	   
        int hdr_size;
        int data_size;
        int encoding;     
        int sample_rate;
        int channels;
} au_header;

main(int argc, char *argv[])
{
	struct au_header *ahp;
	char *filename;
	double volumeFactor;

	char *cp;
	int len;

	int i;

	if (argc != 3) {
	        usage();
	}

	filename = *++argv;	/* skip program name, point to file name */

	cp = map(filename, &len);

	ahp = (struct au_header *)cp;

	if (ahp->magic != MAGIC) {
	        printf("%s %x %x is not an audio file!\n", filename);
		exit(1);
	}

	if (ahp->encoding != ULAW_ENCODING && 
	    ahp->encoding != LINEAR_ENCODING) {
		printf("%s is not a ulaw or linear audio file!\n", filename);
		exit(1);
	}

	volumeFactor = atof(*++argv);

	if (volumeFactor <= 0 || volumeFactor > MAX_VOLUME_FACTOR) {
		printf("invalid volume factor %s, range is (0 - %d]\n",
		    MAX_VOLUME_FACTOR);
		exit(1);
	}

	cp += sizeof(struct au_header);	  /* skip header */

	/*
	 * XXX Should check for clipping.
	 */
	for (i = 0; i < len; i++) {
		if (ahp->encoding == ULAW_ENCODING) {
		    short s = ulaw2linear(*cp);
	
		    *cp++ = linear2ulaw((short)(s * volumeFactor));
		} else {
		    short s = (short)((cp[0] & 0xff) | ((cp[1] << 8) & 0xff00));

		    s *= volumeFactor;

		    *cp++ = (char)(s & 0xff);
		    *cp++ = (char)((s >> 8) & 0xff);
		}
	}
}

char *
map(char *file, int *size)
{
	int fd;
	char *cp;
	struct stat statbuf;

	if (stat(file, &statbuf) != 0)
	    error("stat");

	*size = statbuf.st_size;

	if ((fd = open(file, 2)) < 0)
	    error("open");

	cp = mmap(0, *size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);

	if (cp == (char *)-1)
		error("mmap");

	return cp;
}
