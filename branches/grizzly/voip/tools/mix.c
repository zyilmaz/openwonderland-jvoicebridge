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

short *map(char *, int *);

void error(char *s)
{
	perror(s);
	exit(1);	
}

short buf[10000000];

struct stream {
	int size;
	short *start;
};

/*
 * mix linear audio files
 */
main(int argc, char *argv[])
{
	struct stream stream[100];
	struct stream *stp = stream;

	short *outp;

	struct au_header *ahp;
	int max_size = 0;
	int size;

	int i;

	argc--;
	argv++;

	if (argc < 2) {
		fprintf(stderr, "usage:  <file1> <file2> ...\n");
		exit(1);
	}

	while (argc-- > 0) {
	    stp->start = map(*argv, &stp->size);

	    if (stp->size > max_size)
		max_size = stp->size;

	    stp++;
	    stp->size = 0;

	    argv++;
	}

	ahp = (struct au_header *)buf;
	ahp->data_size = max_size - sizeof(struct au_header);
	*ahp = au_header;

	for (stp = stream; stp->size != 0; stp++) {
	    int size = stp->size >> 1;
	    outp = (short *)((int)buf + sizeof(au_header));

	    for (i = 0; i < size; i++) {
		*outp += stp->start[i];

		if (*outp > 32767)
			*outp = 0x7fff;	  /* max positive number */
		if (*outp < -32767)
			*outp = -32767;   /* min negative number */
		outp++;
	    }
	}

	if (write(1, buf, max_size) != max_size) {
		error("write");
	}
}

short *
map(char *file, int *size)
{
	short *sp;
	int fd;
	struct stat statbuf;

	if (stat(file, &statbuf) != 0)
	    error("stat");

	if ((fd = open(file, 2)) < 0)
	    error("open");

	sp = (short *)mmap(0, statbuf.st_size, PROT_READ, MAP_PRIVATE, fd, 0);

	if (sp == (short *)-1)
		error("mmap");

	*size = statbuf.st_size - sizeof(struct au_header);


#if 0
	fprintf(stderr, "mmap succeeded... addr is %x, mapped size is %d, "
	    " returned size is %d\n", sp, statbuf.st_size, *size);
#endif

	return (short *)(((int)sp) + sizeof(struct au_header));
}
