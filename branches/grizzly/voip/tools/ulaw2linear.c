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

char *map(char *, int *);

void error(char *msg)
{
    perror(msg);
    exit(1);
}

void wr(int fd, char *buf, int size)
{
    if (write(fd, buf, size) != size)
	error("write");
}

main(int argc, char *argv[])
{
    int fd;
    char *cp;
    int size;
    int i;
    int hdr_size;

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
        3,	/* linear */
        8000,
        1
    };

    argc--;
    argv++;
	
    if (argc != 2) {
	printf("usage: <ulaw input file> <linear output file>\n");
	exit(1);
    }

    cp = map(argv[0], &size);

    hdr_size = *(int *)&cp[4];
    
    cp += hdr_size;
    size -= hdr_size;

    if ((fd = creat(argv[1], 0666)) < 0)
	error("creat");

    au_header.data_size = size * 2;

    wr(fd, (char *)&au_header, sizeof(struct au_header));

    for (i = 0; i < size; i ++) {
	short v;

	/*v = ulaw2linear(*cp++ & 0xff);*/
	v = u2l(*cp++ & 0xff);

	wr(fd, (char *)&v, 2);
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

	if ((fd = open(file, O_RDONLY)) < 0)
	    error("open");

	cp = mmap(0, *size, PROT_READ, MAP_PRIVATE, fd, 0);

	if (cp == (char *)-1)
		error("mmap");

	return (char *)cp;
}
