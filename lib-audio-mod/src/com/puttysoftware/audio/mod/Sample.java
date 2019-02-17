package com.puttysoftware.audio.mod;

public class Sample {
    public static final int FP_SHIFT = 15, FP_ONE = 1 << Sample.FP_SHIFT,
            FP_MASK = Sample.FP_ONE - 1;
    public static final int C2_PAL = 8287, C2_NTSC = 8363;
    public String name = ""; //$NON-NLS-1$
    public int volume = 0, panning = -1, relNote = 0, fineTune = 0,
            c2Rate = Sample.C2_NTSC;
    private int loopStart = 0, loopLength = 0;
    private short[] sampleData;
    /* Constants for the 16-tap fixed-point sinc interpolator. */
    private static final int LOG2_FILTER_TAPS = 4;
    private static final int FILTER_TAPS = 1 << Sample.LOG2_FILTER_TAPS;
    private static final int DELAY = Sample.FILTER_TAPS / 2;
    private static final int LOG2_TABLE_ACCURACY = 4;
    private static final int TABLE_INTERP_SHIFT = Sample.FP_SHIFT
            - Sample.LOG2_TABLE_ACCURACY;
    private static final int TABLE_INTERP_ONE = 1 << Sample.TABLE_INTERP_SHIFT;
    private static final int TABLE_INTERP_MASK = Sample.TABLE_INTERP_ONE - 1;
    private static final short[] SINC_TABLE = { 0, 0, 0, 0, 0, 0, 0, 32767, 0,
            0, 0, 0, 0, 0, 0, 0, -1, 7, -31, 103, -279, 671, -1731, 32546, 2006,
            -747, 312, -118, 37, -8, 1, 0, -1, 12, -56, 190, -516, 1246, -3167,
            31887, 4259, -1549, 648, -248, 78, -18, 2, 0, -1, 15, -74, 257,
            -707, 1714, -4299, 30808, 6722, -2375, 994, -384, 122, -29, 4, 0,
            -2, 17, -87, 305, -849, 2067, -5127, 29336, 9351, -3196, 1338, -520,
            169, -41, 6, 0, -2, 18, -93, 334, -941, 2303, -5659, 27510, 12092,
            -3974, 1662, -652, 214, -53, 8, 0, -1, 17, -95, 346, -985, 2425,
            -5912, 25375, 14888, -4673, 1951, -771, 257, -65, 10, 0, -1, 16,
            -92, 341, -985, 2439, -5908, 22985, 17679, -5254, 2188, -871, 294,
            -76, 13, -1, -1, 15, -85, 323, -945, 2355, -5678, 20399, 20399,
            -5678, 2355, -945, 323, -85, 15, -1, -1, 13, -76, 294, -871, 2188,
            -5254, 17679, 22985, -5908, 2439, -985, 341, -92, 16, -1, 0, 10,
            -65, 257, -771, 1951, -4673, 14888, 25375, -5912, 2425, -985, 346,
            -95, 17, -1, 0, 8, -53, 214, -652, 1662, -3974, 12092, 27510, -5659,
            2303, -941, 334, -93, 18, -2, 0, 6, -41, 169, -520, 1338, -3196,
            9351, 29336, -5127, 2067, -849, 305, -87, 17, -2, 0, 4, -29, 122,
            -384, 994, -2375, 6722, 30808, -4299, 1714, -707, 257, -74, 15, -1,
            0, 2, -18, 78, -248, 648, -1549, 4259, 31887, -3167, 1246, -516,
            190, -56, 12, -1, 0, 1, -8, 37, -118, 312, -747, 2006, 32546, -1731,
            671, -279, 103, -31, 7, -1, 0, 0, 0, 0, 0, 0, 0, 0, 32767, 0, 0, 0,
            0, 0, 0, 0 };

    public void setSampleData(final short[] inSampleData, final int inLoopStart,
            final int inLoopLength, final boolean pingPong) {
        int sampleLength = inSampleData.length;
        short[] outSampleData = inSampleData;
        int outLoopStart = inLoopStart;
        int outLoopLength = inLoopLength;
        // Fix loop if necessary.
        if (outLoopStart < 0 || outLoopStart > sampleLength) {
            outLoopStart = sampleLength;
        }
        if (outLoopLength < 0 || outLoopStart + outLoopLength > sampleLength) {
            outLoopLength = sampleLength - outLoopStart;
        }
        sampleLength = outLoopStart + outLoopLength;
        // Compensate for sinc-interpolator delay.
        outLoopStart += Sample.DELAY;
        // Allocate new sample.
        final int newSampleLength = Sample.DELAY + sampleLength
                + (pingPong ? outLoopLength : 0) + Sample.FILTER_TAPS;
        final short[] newSampleData = new short[newSampleLength];
        System.arraycopy(outSampleData, 0, newSampleData, Sample.DELAY,
                sampleLength);
        outSampleData = newSampleData;
        if (pingPong) {
            // Calculate reversed loop.
            final int loopEnd = outLoopStart + outLoopLength;
            for (int idx = 0; idx < outLoopLength; idx++) {
                outSampleData[loopEnd + idx] = outSampleData[loopEnd - idx - 1];
            }
            outLoopLength *= 2;
        }
        // Extend loop for sinc interpolator.
        for (int idx = outLoopStart + outLoopLength, end = idx
                + Sample.FILTER_TAPS; idx < end; idx++) {
            outSampleData[idx] = outSampleData[idx - outLoopLength];
        }
        this.sampleData = outSampleData;
        this.loopStart = outLoopStart;
        this.loopLength = outLoopLength;
    }

    public void resampleNearest(final int sampleIdx, final int sampleFrac,
            final int step, final int leftGain, final int rightGain,
            final int[] mixBuffer, final int offset, final int length) {
        final int loopLen = this.loopLength;
        final int loopEnd = this.loopStart + loopLen;
        int fixedSampleIdx = sampleIdx + Sample.DELAY;
        if (fixedSampleIdx >= loopEnd) {
            fixedSampleIdx = this.normaliseSampleIdx(fixedSampleIdx);
        }
        final short[] data = this.sampleData;
        int outIdx = offset << 1;
        final int outEnd = offset + length << 1;
        int fixedSampleFrac = sampleFrac;
        while (outIdx < outEnd) {
            if (fixedSampleIdx >= loopEnd) {
                if (loopLen < 2) {
                    break;
                }
                while (fixedSampleIdx >= loopEnd) {
                    fixedSampleIdx -= loopLen;
                }
            }
            final int y = data[fixedSampleIdx];
            mixBuffer[outIdx++] += y * leftGain >> Sample.FP_SHIFT;
            mixBuffer[outIdx++] += y * rightGain >> Sample.FP_SHIFT;
            fixedSampleFrac += step;
            fixedSampleIdx += fixedSampleFrac >> Sample.FP_SHIFT;
            fixedSampleFrac &= Sample.FP_MASK;
        }
    }

    public void resampleLinear(final int sampleIdx, final int sampleFrac,
            final int step, final int leftGain, final int rightGain,
            final int[] mixBuffer, final int offset, final int length) {
        final int loopLen = this.loopLength;
        final int loopEnd = this.loopStart + loopLen;
        int outSampleIdx = sampleIdx;
        int outSampleFrac = sampleFrac;
        outSampleIdx += Sample.DELAY;
        if (outSampleIdx >= loopEnd) {
            outSampleIdx = this.normaliseSampleIdx(outSampleIdx);
        }
        final short[] data = this.sampleData;
        int outIdx = offset << 1;
        final int outEnd = offset + length << 1;
        while (outIdx < outEnd) {
            if (outSampleIdx >= loopEnd) {
                if (loopLen < 2) {
                    break;
                }
                while (outSampleIdx >= loopEnd) {
                    outSampleIdx -= loopLen;
                }
            }
            final int c = data[outSampleIdx];
            final int m = data[outSampleIdx + 1] - c;
            final int y = (m * outSampleFrac >> Sample.FP_SHIFT) + c;
            mixBuffer[outIdx++] += y * leftGain >> Sample.FP_SHIFT;
            mixBuffer[outIdx++] += y * rightGain >> Sample.FP_SHIFT;
            outSampleFrac += step;
            outSampleIdx += outSampleFrac >> Sample.FP_SHIFT;
            outSampleFrac &= Sample.FP_MASK;
        }
    }

    public void resampleSinc(final int sampleIdx, final int sampleFrac,
            final int step, final int leftGain, final int rightGain,
            final int[] mixBuffer, final int offset, final int length) {
        final int loopLen = this.loopLength;
        final int loopEnd = this.loopStart + loopLen;
        int outSampleIdx = sampleIdx;
        int outSampleFrac = sampleFrac;
        if (outSampleIdx >= loopEnd) {
            outSampleIdx = this.normaliseSampleIdx(outSampleIdx);
        }
        final short[] data = this.sampleData;
        int outIdx = offset << 1;
        final int outEnd = offset + length << 1;
        while (outIdx < outEnd) {
            if (outSampleIdx >= loopEnd) {
                if (loopLen < 2) {
                    break;
                }
                while (outSampleIdx >= loopEnd) {
                    outSampleIdx -= loopLen;
                }
            }
            final int tableIdx = outSampleFrac >> Sample.TABLE_INTERP_SHIFT << Sample.LOG2_FILTER_TAPS;
            int a1 = 0, a2 = 0;
            a1 = Sample.SINC_TABLE[tableIdx + 0] * data[outSampleIdx + 0];
            a1 += Sample.SINC_TABLE[tableIdx + 1] * data[outSampleIdx + 1];
            a1 += Sample.SINC_TABLE[tableIdx + 2] * data[outSampleIdx + 2];
            a1 += Sample.SINC_TABLE[tableIdx + 3] * data[outSampleIdx + 3];
            a1 += Sample.SINC_TABLE[tableIdx + 4] * data[outSampleIdx + 4];
            a1 += Sample.SINC_TABLE[tableIdx + 5] * data[outSampleIdx + 5];
            a1 += Sample.SINC_TABLE[tableIdx + 6] * data[outSampleIdx + 6];
            a1 += Sample.SINC_TABLE[tableIdx + 7] * data[outSampleIdx + 7];
            a1 += Sample.SINC_TABLE[tableIdx + 8] * data[outSampleIdx + 8];
            a1 += Sample.SINC_TABLE[tableIdx + 9] * data[outSampleIdx + 9];
            a1 += Sample.SINC_TABLE[tableIdx + 10] * data[outSampleIdx + 10];
            a1 += Sample.SINC_TABLE[tableIdx + 11] * data[outSampleIdx + 11];
            a1 += Sample.SINC_TABLE[tableIdx + 12] * data[outSampleIdx + 12];
            a1 += Sample.SINC_TABLE[tableIdx + 13] * data[outSampleIdx + 13];
            a1 += Sample.SINC_TABLE[tableIdx + 14] * data[outSampleIdx + 14];
            a1 += Sample.SINC_TABLE[tableIdx + 15] * data[outSampleIdx + 15];
            a2 = Sample.SINC_TABLE[tableIdx + 16] * data[outSampleIdx + 0];
            a2 += Sample.SINC_TABLE[tableIdx + 17] * data[outSampleIdx + 1];
            a2 += Sample.SINC_TABLE[tableIdx + 18] * data[outSampleIdx + 2];
            a2 += Sample.SINC_TABLE[tableIdx + 19] * data[outSampleIdx + 3];
            a2 += Sample.SINC_TABLE[tableIdx + 20] * data[outSampleIdx + 4];
            a2 += Sample.SINC_TABLE[tableIdx + 21] * data[outSampleIdx + 5];
            a2 += Sample.SINC_TABLE[tableIdx + 22] * data[outSampleIdx + 6];
            a2 += Sample.SINC_TABLE[tableIdx + 23] * data[outSampleIdx + 7];
            a2 += Sample.SINC_TABLE[tableIdx + 24] * data[outSampleIdx + 8];
            a2 += Sample.SINC_TABLE[tableIdx + 25] * data[outSampleIdx + 9];
            a2 += Sample.SINC_TABLE[tableIdx + 26] * data[outSampleIdx + 10];
            a2 += Sample.SINC_TABLE[tableIdx + 27] * data[outSampleIdx + 11];
            a2 += Sample.SINC_TABLE[tableIdx + 28] * data[outSampleIdx + 12];
            a2 += Sample.SINC_TABLE[tableIdx + 29] * data[outSampleIdx + 13];
            a2 += Sample.SINC_TABLE[tableIdx + 30] * data[outSampleIdx + 14];
            a2 += Sample.SINC_TABLE[tableIdx + 31] * data[outSampleIdx + 15];
            a1 >>= Sample.FP_SHIFT;
            a2 >>= Sample.FP_SHIFT;
            final int y = a1 + ((a2 - a1) * (outSampleFrac
                    & Sample.TABLE_INTERP_MASK) >> Sample.TABLE_INTERP_SHIFT);
            mixBuffer[outIdx++] += y * leftGain >> Sample.FP_SHIFT;
            mixBuffer[outIdx++] += y * rightGain >> Sample.FP_SHIFT;
            outSampleFrac += step;
            outSampleIdx += outSampleFrac >> Sample.FP_SHIFT;
            outSampleFrac &= Sample.FP_MASK;
        }
    }

    public int normaliseSampleIdx(final int sampleIdx) {
        int fixedIdx = sampleIdx;
        final int loopOffset = fixedIdx - this.loopStart;
        if (loopOffset > 0) {
            fixedIdx = this.loopStart;
            if (this.loopLength > 1) {
                fixedIdx += loopOffset % this.loopLength;
            }
        }
        return fixedIdx;
    }

    public boolean looped() {
        return this.loopLength > 1;
    }

    public void toStringBuffer(final StringBuffer out) {
        out.append("Name: " + this.name + '\n'); //$NON-NLS-1$
        out.append("Volume: " + this.volume + '\n'); //$NON-NLS-1$
        out.append("Panning: " + this.panning + '\n'); //$NON-NLS-1$
        out.append("Relative Note: " + this.relNote + '\n'); //$NON-NLS-1$
        out.append("Fine Tune: " + this.fineTune + '\n'); //$NON-NLS-1$
        out.append("Loop Start: " + this.loopStart + '\n'); //$NON-NLS-1$
        out.append("Loop Length: " + this.loopLength + '\n'); //$NON-NLS-1$
        /*
         * out.append( "Sample Data: " ); for( int idx = 0; idx <
         * sampleData.length; idx++ ) out.append( sampleData[ idx ] + ", " );
         * out.append( '\n' );
         */
    }
}
