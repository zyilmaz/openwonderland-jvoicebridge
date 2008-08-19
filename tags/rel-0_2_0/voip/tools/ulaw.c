/*-----------------------------------------------------------------------
 *
 *TWIMC,
 *
 *Below are two routines I wrote for converting between ulaw and linear. 
 *I use them with the SparcStation and they seem to work fine.
 *I am pretty sure (99.9%) that they implement the standard as specified
 *in the references. 
 *
 *Note that the standard deals with converting between 12 bit linear
 *and 8 bit ulaw.  These routines assume 16 bit linear.  Thus, some
 *bit shifting may be necessary.
 *
 *Craig F. Reese                           Email: cfreese@super.org
 *Institute for Defense Analyses/
 *Supercomputing Research Center
 *17100 Science Dr.
 *Bowie, MD  20715-4300
 *
 * Copyright 2007 Sun Microsystems, Inc. 
 *
 *------------------------------------------------------------------
 */

/**
 ** Signal conversion routines for use with the Sun4/60 audio chip
 **/

/*
 * This routine converts from linear to ulaw
 * 29 September 1989
 *
 * Craig Reese: IDA/Supercomputing Research Center
 * Joe Campbell: Department of Defense
 *
 * References:
 * 1) CCITT Recommendation G.711  (very difficult to follow)
 * 2) "A New Digital Technique for Implementation of Any 
 *     Continuous PCM Companding Law," Villeret, Michel,
 *     et al. 1973 IEEE Int. Conf. on Communications, Vol 1,
 *     1973, pg. 11.12-11.17
 * 3) MIL-STD-188-113,"Interoperability and Performance Standards
 *     for Analog-to_Digital Conversion Techniques,"
 *     17 February 1987
 *
 * Input: Signed 16 bit linear sample
 * Output: 8 bit ulaw sample
 */

#define ZEROTRAP    /* turn on the trap as per the MIL-STD */
#define BIAS 0x84   /* define the add-in bias for 16 bit samples */
#define CLIP 32635

char
linear2ulaw(int sample)
{
    static int exp_lut[256] = {0,0,1,1,2,2,2,2,3,3,3,3,3,3,3,3,
                               4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
                               5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
                               5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
                               6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
                               6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
                               6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
                               6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                               7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7};
    int sign, exponent, mantissa;
    char ulawbyte;

    /** get the sample into sign-magnitude **/
    sign = (sample >> 8) & 0x80;        /* set aside the sign */
    if (sign != 0) sample = -sample;    /* get magnitude */
    if (sample > CLIP) sample = CLIP;   /* clip the magnitude */
    /** convert from 16 bit linear to ulaw **/
    sample = sample + BIAS;
    exponent = exp_lut[(sample>>7) & 0xFF];
    mantissa = (sample >> (exponent+3)) & 0x0F;
    ulawbyte = ~(sign | (exponent << 4) | mantissa);
#ifdef ZEROTRAP
    if (ulawbyte == 0 ) ulawbyte = 0x02;  /* optional CCITT trap */
#endif
    /** return the result **/
    return(ulawbyte);
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

short ulawtable_goldwave[] = {
        31615, 30591, 29567, 28543, 27519, 26495, 25471, 24447, 
        23423, 22399, 21375, 20351, 19327, 18303, 17279, 16255, 
        15743, 15231, 14719, 14207, 13695, 13183, 12671, 12159, 
        11647, 11135, 10623, 10111, 9599, 9087, 8575, 8063, 
        7807, 7551, 7295, 7039, 6783, 6527, 6271, 6015, 
        5759, 5503, 5247, 4991, 4735, 4479, 4223, 3967, 
        3839, 3711, 3583, 3455, 3327, 3199, 3071, 2943, 
        2815, 2687, 2559, 2431, 2303, 2175, 2047, 1919, 
        1855, 1791, 1727, 1663, 1599, 1535, 1471, 1407, 
        1343, 1279, 1215, 1151, 1087, 1023, 959, 895, 
        863, 831, 799, 767, 735, 703, 671, 639, 
        607, 575, 543, 511, 479, 447, 415, 383, 
        367, 351, 335, 319, 303, 287, 271, 255, 
        239, 223, 207, 191, 175, 159, 143, 127, 
        119, 111, 103, 95, 87, 79, 71, 63, 
        55, 47, 39, 31, 23, 15, 7, 0, 
        -31616, -30592, -29568, -28544, -27520, -26496, -25472, -24448, 
        -23424, -22400, -21376, -20352, -19328, -18304, -17280, -16256, 
        -15744, -15232, -14720, -14208, -13696, -13184, -12672, -12160, 
        -11648, -11136, -10624, -10112, -9600, -9088, -8576, -8064, 
        -7808, -7552, -7296, -7040, -6784, -6528, -6272, -6016, 
        -5760, -5504, -5248, -4992, -4736, -4480, -4224, -3968, 
        -3840, -3712, -3584, -3456, -3328, -3200, -3072, -2944, 
        -2816, -2688, -2560, -2432, -2304, -2176, -2048, -1920, 
        -1856, -1792, -1728, -1664, -1600, -1536, -1472, -1408, 
        -1344, -1280, -1216, -1152, -1088, -1024, -960, -896, 
        -864, -832, -800, -768, -736, -704, -672, -640, 
        -608, -576, -544, -512, -480, -448, -416, -384, 
        -368, -352, -336, -320, -304, -288, -272, -256, 
        -240, -224, -208, -192, -176, -160, -144, -128, 
        -120, -112, -104, -96, -88, -80, -72, -64, 
        -56, -48, -40, -32, -24, -16, -8, 0, 
};

short ulawtable_algorithm[] = {
	32124, 31100, 30076, 29052, 28028, 27004, 25980, 24956, 23932, 
	22908, 21884, 20860, 19836, 18812, 17788, 16764, 15996, 
	15484, 14972, 14460, 13948, 13436, 12924, 12412, 11900, 
	11388, 10876, 10364, 9852, 9340, 8828, 8316, 7932, 
	7676, 7420, 7164, 6908, 6652, 6396, 6140, 5884, 
	5628, 5372, 5116, 4860, 4604, 4348, 4092, 3900, 
	3772, 3644, 3516, 3388, 3260, 3132, 3004, 2876, 
	2748, 2620, 2492, 2364, 2236, 2108, 1980, 1884, 
	1820, 1756, 1692, 1628, 1564, 1500, 1436, 1372, 
	1308, 1244, 1180, 1116, 1052, 988, 924, 876, 
	844, 812, 780, 748, 716, 684, 652, 620, 
	588, 556, 524, 492, 460, 428, 396, 372, 
	356, 340, 324, 308, 292, 276, 260, 244, 
	228, 212, 196, 180, 164, 148, 132, 120, 
	112, 104, 96, 88, 80, 72, 64, 56, 
	48, 40, 32, 24, 16, 8, 0, 
	-32124, -31100, -30076, -29052, -28028, -27004, -25980, -24956, -23932, 
	-22908, -21884, -20860, -19836, -18812, -17788, -16764, -15996, 
	-15484, -14972, -14460, -13948, -13436, -12924, -12412, -11900, 
	-11388, -10876, -10364, -9852, -9340, -8828, -8316, -7932, 
	-7676, -7420, -7164, -6908, -6652, -6396, -6140, -5884, 
	-5628, -5372, -5116, -4860, -4604, -4348, -4092, -3900, 
	-3772, -3644, -3516, -3388, -3260, -3132, -3004, -2876, 
	-2748, -2620, -2492, -2364, -2236, -2108, -1980, -1884, 
	-1820, -1756, -1692, -1628, -1564, -1500, -1436, -1372, 
	-1308, -1244, -1180, -1116, -1052, -988, -924, -876, 
	-844, -812, -780, -748, -716, -684, -652, -620, 
	-588, -556, -524, -492, -460, -428, -396, -372, 
	-356, -340, -324, -308, -292, -276, -260, -244, 
	-228, -212, -196, -180, -164, -148, -132, -120, 
	-112, -104, -96, -88, -80, -72, -64, -56, 
	-48, -40, -32, -24, -16, -8, 0,
};

short u2l(char ulawbyte) {
    return ulawtable_goldwave[ulawbyte + 128];
}
