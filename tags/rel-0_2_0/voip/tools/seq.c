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

#define PAGESIZE (8 * 1024)

char *map(char *, int *);

void error(char *s1)
{
	perror(s1);
	exit(1);	
}

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
        1,      /* ulaw */
        8000, 
        1
};

char buf[10000000];

main(int argc, char *argv[])
{
	char *cp;
	int fd;
	int size;
	int total_size = 0;

	int i;

	argc--;		/* skip program name */
	argv++;

	for (i = 0; i < argc; i++) {
	    struct stat statbuf;

	    if (stat(argv[i], &statbuf) != 0) {
		printf("can't stat %s\n", argv[i]);
		error("stat");
	    }

	    total_size += (statbuf.st_size - sizeof(struct au_header));	/* add up file sizes */
	}

	au_header.data_size = total_size;

	printf("total file size is %d.\n", total_size + sizeof(struct au_header));

	if ((fd = creat("seq.ulaw", 0666)) < 0)
		error("creat");

	/*
	 * write header
	 */
	if (write(fd, &au_header, sizeof(struct au_header)) != 
	    sizeof(struct au_header)) {
		error("write");
	}

	while (argc-- > 0) {
		cp = map(*argv, &size);
		cp += sizeof(struct au_header);	  /* skip header */
		size -= sizeof(struct au_header);

		if (write(fd, cp, size) != size)  /* write next file */
			error("write");

		argv++;
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

 	printf("mmaping %s, size %d.\n", file, statbuf.st_size);

	cp = mmap(0, *size, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);

	if (cp == (char *)-1)
		error("mmap");

	/*printf("mmap succeeded... addr is %x, size is %d\n", cp, *size);*/

	return cp;
}
