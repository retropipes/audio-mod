package com.puttysoftware.audio.mod;

/*
 ProTracker, Scream Tracker 3, FastTracker 2 Replay (c)2011 mumart@gmail.com
 */
public class IBXM {
    public static final String VERSION = "a61 (c)2011 mumart@gmail.com"; //$NON-NLS-1$
    private static final int OVERSAMPLE = 2;
    private final Module module;
    private final int[] rampBuffer;
    private final Channel[] channels;
    private int interpolation, filtL, filtR;
    private final int sampleRate;
    private int tickLen;
    private int rampLen;
    private final int rampRate;
    private int seqPos, breakSeqPos, row, nextRow, tick;
    private int speed, plCount, plChannel;
    private final GlobalVol globalVol;
    private final Note note;

    /*
     * Initialise the replay to play the specified Module at the specified
     * sampling rate.
     */
    public IBXM(final Module newModule, final int newSampleRate) {
        this.module = newModule;
        this.sampleRate = newSampleRate;
        this.interpolation = Channel.LINEAR;
        if (newSampleRate * IBXM.OVERSAMPLE < 16000) {
            throw new IllegalArgumentException("Unsupported sampling rate!"); //$NON-NLS-1$
        }
        this.rampLen = 256;
        while (this.rampLen * 1024 > newSampleRate * IBXM.OVERSAMPLE) {
            this.rampLen /= 2;
        }
        this.rampBuffer = new int[this.rampLen * 2];
        this.rampRate = 256 / this.rampLen;
        this.channels = new Channel[newModule.numChannels];
        this.globalVol = new GlobalVol();
        this.note = new Note();
        this.setSequencePos(0);
    }

    /* Return the sampling rate of playback. */
    public int getSampleRate() {
        return this.sampleRate;
    }

    /*
     * Set the resampling quality to one of Channel.NEAREST, Channel.LINEAR, or
     * Channel.SINC.
     */
    public void setInterpolation(final int newInterpolation) {
        this.interpolation = newInterpolation;
    }

    /* Returns the minimum size of the buffer required by getAudio(). */
    public int getMixBufferLength() {
        return this.sampleRate * IBXM.OVERSAMPLE * 5 / 32 + this.rampLen * 2;
    }

    /*
     * Set the pattern in the sequence to play. The tempo is reset to the
     * default.
     */
    public void setSequencePos(final int pos) {
        int newPos = pos;
        if (newPos >= this.module.sequenceLength) {
            newPos = 0;
        }
        this.breakSeqPos = newPos;
        this.nextRow = 0;
        this.tick = 1;
        this.globalVol.volume = this.module.defaultGVol;
        this.speed = this.module.defaultSpeed > 0 ? this.module.defaultSpeed
                : 6;
        this.setTempo(
                this.module.defaultTempo > 0 ? this.module.defaultTempo : 125);
        this.plCount = this.plChannel = -1;
        for (int idx = 0; idx < this.module.numChannels; idx++) {
            this.channels[idx] = new Channel(this.module, idx,
                    this.sampleRate * IBXM.OVERSAMPLE, this.globalVol);
        }
        for (int idx = 0, end = this.rampLen * 2; idx < end; idx++) {
            this.rampBuffer[idx] = 0;
        }
        this.filtL = this.filtR = 0;
        this.tick();
    }

    /* Returns the song duration in samples at the current sampling rate. */
    public int calculateSongDuration() {
        int duration = 0;
        this.setSequencePos(0);
        boolean songEnd = false;
        while (!songEnd) {
            duration += this.tickLen / IBXM.OVERSAMPLE;
            songEnd = this.tick();
        }
        this.setSequencePos(0);
        return duration;
    }

    /*
     * Seek to approximately the specified sample position. The actual sample
     * position reached is returned.
     */
    public int seek(final int samplePos) {
        this.setSequencePos(0);
        int currentPos = 0;
        while (samplePos - currentPos >= this.tickLen) {
            for (int idx = 0; idx < this.module.numChannels; idx++) {
                this.channels[idx].updateSampleIdx(this.tickLen);
            }
            currentPos += this.tickLen / IBXM.OVERSAMPLE;
            this.tick();
        }
        return currentPos;
    }

    /*
     * Generate audio. The number of samples placed into output_buf is returned.
     * The output buffer length must be at least that returned by
     * get_mix_buffer_length(). A "sample" is a pair of 16-bit integer
     * amplitudes, one for each of the stereo channels.
     */
    public int getAudio(final int[] outputBuffer) {
        // Clear output buffer.
        int outIdx = 0;
        final int outEp1 = this.tickLen + this.rampLen << 1;
        while (outIdx < outEp1) {
            outputBuffer[outIdx++] = 0;
        }
        // Resample.
        for (int chanIdx = 0; chanIdx < this.module.numChannels; chanIdx++) {
            final Channel chan = this.channels[chanIdx];
            chan.resample(outputBuffer, 0, this.tickLen + this.rampLen,
                    this.interpolation);
            chan.updateSampleIdx(this.tickLen);
        }
        this.volumeRamp(outputBuffer);
        this.tick();
        return this.downsample(outputBuffer, this.tickLen);
    }

    private void setTempo(final int tempo) {
        // Make sure tick length is even to simplify 2x oversampling.
        this.tickLen = this.sampleRate * IBXM.OVERSAMPLE * 5 / (tempo * 2) & -2;
    }

    private void volumeRamp(final int[] mixBuffer) {
        int a1, a2, s1, s2, offset = 0;
        for (a1 = 0; a1 < 256; a1 += this.rampRate) {
            a2 = 256 - a1;
            s1 = mixBuffer[offset] * a1;
            s2 = this.rampBuffer[offset] * a2;
            mixBuffer[offset++] = s1 + s2 >> 8;
            s1 = mixBuffer[offset] * a1;
            s2 = this.rampBuffer[offset] * a2;
            mixBuffer[offset++] = s1 + s2 >> 8;
        }
        System.arraycopy(mixBuffer, this.tickLen << 1, this.rampBuffer, 0,
                offset);
    }

    private int downsample(final int[] buf, final int count) {
        // 2:1 downsampling with simple but effective anti-aliasing.
        // Count is the number of stereo samples to process, and must be even.
        int fl = this.filtL, fr = this.filtR;
        int inIdx = 0, outIdx = 0;
        while (outIdx < count) {
            final int outL = fl + (buf[inIdx++] >> 1);
            final int outR = fr + (buf[inIdx++] >> 1);
            fl = buf[inIdx++] >> 2;
            fr = buf[inIdx++] >> 2;
            buf[outIdx++] = outL + fl;
            buf[outIdx++] = outR + fr;
        }
        this.filtL = fl;
        this.filtR = fr;
        return count >> 1;
    }

    private boolean tick() {
        boolean songEnd = false;
        if (--this.tick <= 0) {
            this.tick = this.speed;
            songEnd = this.row();
        } else {
            for (int idx = 0; idx < this.module.numChannels; idx++) {
                this.channels[idx].tick();
            }
        }
        return songEnd;
    }

    private boolean row() {
        boolean songEnd = false;
        if (this.breakSeqPos >= 0) {
            if (this.breakSeqPos >= this.module.sequenceLength) {
                this.breakSeqPos = this.nextRow = 0;
            }
            while (this.module.sequence[this.breakSeqPos] >= this.module.numPatterns) {
                this.breakSeqPos++;
                if (this.breakSeqPos >= this.module.sequenceLength) {
                    this.breakSeqPos = this.nextRow = 0;
                }
            }
            if (this.breakSeqPos <= this.seqPos) {
                songEnd = true;
            }
            this.seqPos = this.breakSeqPos;
            for (int idx = 0; idx < this.module.numChannels; idx++) {
                this.channels[idx].plRow = 0;
            }
            this.breakSeqPos = -1;
        }
        final Pattern pattern = this.module.patterns[this.module.sequence[this.seqPos]];
        this.row = this.nextRow;
        if (this.row >= pattern.numRows) {
            this.row = 0;
        }
        this.nextRow = this.row + 1;
        if (this.nextRow >= pattern.numRows) {
            this.breakSeqPos = this.seqPos + 1;
            this.nextRow = 0;
        }
        final int noteIdx = this.row * this.module.numChannels;
        for (int chanIdx = 0; chanIdx < this.module.numChannels; chanIdx++) {
            final Channel channel = this.channels[chanIdx];
            pattern.getNote(noteIdx + chanIdx, this.note);
            if (this.note.effect == 0xE) {
                this.note.effect = 0x70 | this.note.param >> 4;
                this.note.param &= 0xF;
            }
            if (this.note.effect == 0x93) {
                this.note.effect = 0xF0 | this.note.param >> 4;
                this.note.param &= 0xF;
            }
            if (this.note.effect == 0 && this.note.param > 0) {
                this.note.effect = 0x8A;
            }
            channel.row(this.note);
            switch (this.note.effect) {
            case 0x81: /* Set Speed. */
                if (this.note.param > 0) {
                    this.tick = this.speed = this.note.param;
                }
                break;
            case 0xB:
            case 0x82: /* Pattern Jump. */
                if (this.plCount < 0) {
                    this.breakSeqPos = this.note.param;
                    this.nextRow = 0;
                }
                break;
            case 0xD:
            case 0x83: /* Pattern Break. */
                if (this.plCount < 0) {
                    this.breakSeqPos = this.seqPos + 1;
                    this.nextRow = (this.note.param >> 4) * 10
                            + (this.note.param & 0xF);
                }
                break;
            case 0xF: /* Set Speed/Tempo. */
                if (this.note.param > 0) {
                    if (this.note.param < 32) {
                        this.tick = this.speed = this.note.param;
                    } else {
                        this.setTempo(this.note.param);
                    }
                }
                break;
            case 0x94: /* Set Tempo. */
                if (this.note.param > 32) {
                    this.setTempo(this.note.param);
                }
                break;
            case 0x76:
            case 0xFB: /* Pattern Loop. */
                if (this.note.param == 0) {
                    channel.plRow = this.row;
                }
                if (channel.plRow < this.row) { /*
                                                 * Marker valid. Begin looping.
                                                 */
                    if (this.plCount < 0) { /* Not already looping, begin. */
                        this.plCount = this.note.param;
                        this.plChannel = chanIdx;
                    }
                    if (this.plChannel == chanIdx) { /* Next Loop. */
                        if (this.plCount == 0) { /* Loop finished. */
                            /* Invalidate current marker. */
                            channel.plRow = this.row + 1;
                        } else { /* Loop and cancel any breaks on this row. */
                            this.nextRow = channel.plRow;
                            this.breakSeqPos = -1;
                        }
                        this.plCount--;
                    }
                }
                break;
            case 0x7E:
            case 0xFE: /* Pattern Delay. */
                this.tick = this.speed + this.speed * this.note.param;
                break;
            default:
                // Do nothing
                break;
            }
        }
        return songEnd;
    }
}
