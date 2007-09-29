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
#include <stdio.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>

/*
 * http://www.opengroup.org/public/pubs/external/auformat.html
 */
struct au_header {
        int magic;
        int hdr_size;
        int data_size;
        int encoding;
        int sample_rate;
        int channels;
} au_header = {
        0x2e736e64,
        24,
        0,
        3,      /* linear */
        8000,
        1
};

char *map(char *, int *);

void error(char *s)
{
	perror(s);
	exit(1);	
}

char buf[10000000];

/*
 * get rid of silence in linear audio files
 */
main(int argc, char *argv[])
{
	int size;
	int newsize;
	char *cp;
	char *outp;
	char *last;

	struct au_header *ahp;

	int i;

	if (argc < 2) {
	    fprintf(stderr, "usage:  trimsilence <file1>\n");
	    exit(1);
	}

	argv++;
	argc--;

	cp = map(*argv, &size);

	last = (char *)&cp[size];

	outp = buf;

	for (i = 0; i < sizeof(au_header); i++) {
	    *outp++ = *cp++;
	}

	newsize = sizeof(struct au_header);

	for (i = sizeof(au_header); i < size; i++) {
	    int j;

	    if (checkForSilence(cp, last) != 0) {
		fprintf(stderr, "dropping %x\n", i);
		cp += 160;
	    } else {
	        for (j = 0; j < 160; j++) {
		    if (cp >= last)
		        break;

		    *outp++ = *cp++;
		    newsize++;
	        }
	    }
	}

	((struct au_header *)buf)->data_size = 
	    newsize - sizeof(struct au_header);

	fprintf(stderr, "writing %d. bytes\n", newsize);
	
	if (write(1, buf, newsize) != newsize) {
		error("write");
	}
}

int checkForSilence(char *cp, char *last) {
	int i;
	int n;

	for (i = 0; i < 160; i++) {
	    if (cp >= last)
	        return 0;

	    n = (int)*cp++;
	    n &= 0xff;
	    
	    if (n < 0xd0) {
		fprintf(stderr, "found %x %x\n", cp, n);
		return 0;
	    }
	}
	return 1;
}

char *
map(char *file, int *size)
{
	char *cp;
	int fd;
	struct stat statbuf;

	if (stat(file, &statbuf) != 0)
	    error("stat");

	if ((fd = open(file, 2)) < 0)
	    error("open");

	cp = (char *)mmap(0, statbuf.st_size, PROT_READ, MAP_PRIVATE, fd, 0);

	if (cp == (char *)-1)
		error("mmap");

	*size = statbuf.st_size;

#if 1
	fprintf(stderr, "mmap succeeded... addr is %x, mapped size is %d\n",
	    cp, statbuf.st_size, *size);
#endif

	return cp;
}
