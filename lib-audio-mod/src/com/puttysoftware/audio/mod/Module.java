package com.puttysoftware.audio.mod;

public class Module {
    public String songName = "Blank"; //$NON-NLS-1$
    public int numChannels = 4, numInstruments = 1;
    public int numPatterns = 1, sequenceLength = 1, restartPos = 0;
    public int defaultGVol = 64, defaultSpeed = 6, defaultTempo = 125,
            c2Rate = Sample.C2_PAL, gain = 64;
    public boolean linearPeriods = false, fastVolSlides = false;
    public int[] defaultPanning = { 51, 204, 204, 51 };
    public int[] sequence = { 0 };
    public Pattern[] patterns = { new Pattern(4, 64) };
    public Instrument[] instruments = { new Instrument(), new Instrument() };
    private static final int[] keyToPeriod = { 29020, 27392, 25855, 24403,
            23034, 21741, 20521, 19369, 18282, 17256, 16287, 15373, 14510,
            13696 };

    public Module() {
    }

    public Module(final byte[] moduleData) {
        if (Module.isoLatin1(moduleData, 0, 17).equals("Extended Module: ")) { //$NON-NLS-1$
            this.loadXM(moduleData);
        } else if (Module.isoLatin1(moduleData, 44, 4).equals("SCRM")) { //$NON-NLS-1$
            this.loadS3M(moduleData);
        } else {
            this.loadMOD(moduleData);
        }
    }

    private void loadMOD(final byte[] moduleData) {
        this.songName = Module.isoLatin1(moduleData, 0, 20);
        this.sequenceLength = moduleData[950] & 0x7F;
        this.restartPos = moduleData[951] & 0x7F;
        if (this.restartPos >= this.sequenceLength) {
            this.restartPos = 0;
        }
        this.sequence = new int[128];
        for (int seqIdx = 0; seqIdx < 128; seqIdx++) {
            final int patIdx = moduleData[952 + seqIdx] & 0x7F;
            this.sequence[seqIdx] = patIdx;
            if (patIdx >= this.numPatterns) {
                this.numPatterns = patIdx + 1;
            }
        }
        switch (Module.ushortbe(moduleData, 1082)) {
        case 0x4b2e: /* M.K. */
        case 0x4b21: /* M!K! */
        case 0x5434: /* FLT4 */
            this.numChannels = 4;
            this.c2Rate = Sample.C2_PAL;
            this.gain = 64;
            break;
        case 0x484e: /* xCHN */
            this.numChannels = moduleData[1080] - 48;
            this.c2Rate = Sample.C2_NTSC;
            this.gain = 32;
            break;
        case 0x4348: /* xxCH */
            this.numChannels = (moduleData[1080] - 48) * 10;
            this.numChannels += moduleData[1081] - 48;
            this.c2Rate = Sample.C2_NTSC;
            this.gain = 32;
            break;
        default:
            throw new IllegalArgumentException("MOD Format not recognised!"); //$NON-NLS-1$
        }
        this.defaultGVol = 64;
        this.defaultSpeed = 6;
        this.defaultTempo = 125;
        this.defaultPanning = new int[this.numChannels];
        for (int idx = 0; idx < this.numChannels; idx++) {
            this.defaultPanning[idx] = 51;
            if ((idx & 3) == 1 || (idx & 3) == 2) {
                this.defaultPanning[idx] = 204;
            }
        }
        int moduleDataIdx = 1084;
        this.patterns = new Pattern[this.numPatterns];
        for (int patIdx = 0; patIdx < this.numPatterns; patIdx++) {
            final Pattern pattern = this.patterns[patIdx] = new Pattern(
                    this.numChannels, 64);
            for (int patDataIdx = 0; patDataIdx < pattern.data.length; patDataIdx += 5) {
                int period = (moduleData[moduleDataIdx] & 0xF) << 8;
                period = (period | moduleData[moduleDataIdx + 1] & 0xFF) * 4;
                if (period > 112) {
                    int key = 0, oct = 0;
                    while (period < 14510) {
                        period *= 2;
                        oct++;
                    }
                    while (key < 12) {
                        final int d1 = Module.keyToPeriod[key] - period;
                        final int d2 = period - Module.keyToPeriod[key + 1];
                        if (d2 >= 0) {
                            if (d2 < d1) {
                                key++;
                            }
                            break;
                        }
                        key++;
                    }
                    pattern.data[patDataIdx] = (byte) (oct * 12 + key);
                }
                int ins = (moduleData[moduleDataIdx + 2] & 0xF0) >> 4;
                ins = ins | moduleData[moduleDataIdx] & 0x10;
                pattern.data[patDataIdx + 1] = (byte) ins;
                int effect = moduleData[moduleDataIdx + 2] & 0x0F;
                int param = moduleData[moduleDataIdx + 3] & 0xFF;
                if (param == 0 && (effect < 3 || effect == 0xA)) {
                    effect = 0;
                }
                if (param == 0 && (effect == 5 || effect == 6)) {
                    effect -= 2;
                }
                if (effect == 8 && this.numChannels == 4) {
                    effect = param = 0;
                }
                pattern.data[patDataIdx + 3] = (byte) effect;
                pattern.data[patDataIdx + 4] = (byte) param;
                moduleDataIdx += 4;
            }
        }
        this.numInstruments = 31;
        this.instruments = new Instrument[this.numInstruments + 1];
        this.instruments[0] = new Instrument();
        for (int instIdx = 1; instIdx <= this.numInstruments; instIdx++) {
            final Instrument instrument = this.instruments[instIdx] = new Instrument();
            final Sample sample = instrument.samples[0];
            instrument.name = Module.isoLatin1(moduleData, instIdx * 30 - 10,
                    22);
            int sampleLength = Module.ushortbe(moduleData, instIdx * 30 + 12)
                    * 2;
            final int fineTune = (moduleData[instIdx * 30 + 14] & 0xF) << 4;
            sample.fineTune = fineTune < 128 ? fineTune : fineTune - 256;
            final int volume = moduleData[instIdx * 30 + 15] & 0x7F;
            sample.volume = volume <= 64 ? volume : 64;
            sample.panning = -1;
            sample.c2Rate = this.c2Rate;
            int loopStart = Module.ushortbe(moduleData, instIdx * 30 + 16) * 2;
            int loopLength = Module.ushortbe(moduleData, instIdx * 30 + 18) * 2;
            final short[] sampleData = new short[sampleLength];
            if (moduleDataIdx + sampleLength > moduleData.length) {
                sampleLength = moduleData.length - moduleDataIdx;
            }
            if (loopStart + loopLength > sampleLength) {
                loopLength = sampleLength - loopStart;
            }
            if (loopLength < 4) {
                loopStart = sampleLength;
                loopLength = 0;
            }
            for (int idx = 0, end = sampleLength; idx < end; idx++) {
                sampleData[idx] = (short) (moduleData[moduleDataIdx++] << 8);
            }
            sample.setSampleData(sampleData, loopStart, loopLength, false);
        }
    }

    private void loadS3M(final byte[] moduleData) {
        this.songName = Module.codePage850(moduleData, 0, 28);
        this.sequenceLength = Module.ushortle(moduleData, 32);
        this.numInstruments = Module.ushortle(moduleData, 34);
        this.numPatterns = Module.ushortle(moduleData, 36);
        final int flags = Module.ushortle(moduleData, 38);
        final int version = Module.ushortle(moduleData, 40);
        this.fastVolSlides = (flags & 0x40) == 0x40 || version == 0x1300;
        final boolean signedSamples = Module.ushortle(moduleData, 42) == 1;
        if (Module.intle(moduleData, 44) != 0x4d524353) {
            throw new IllegalArgumentException("Not an S3M file!"); //$NON-NLS-1$
        }
        this.defaultGVol = moduleData[48] & 0xFF;
        this.defaultSpeed = moduleData[49] & 0xFF;
        this.defaultTempo = moduleData[50] & 0xFF;
        this.c2Rate = Sample.C2_NTSC;
        this.gain = moduleData[51] & 0x7F;
        final boolean stereoMode = (moduleData[51] & 0x80) == 0x80;
        final boolean defaultPan = (moduleData[53] & 0xFF) == 0xFC;
        final int[] channelMap = new int[32];
        for (int chanIdx = 0; chanIdx < 32; chanIdx++) {
            channelMap[chanIdx] = -1;
            if ((moduleData[64 + chanIdx] & 0xFF) < 16) {
                channelMap[chanIdx] = this.numChannels++;
            }
        }
        this.sequence = new int[this.sequenceLength];
        for (int seqIdx = 0; seqIdx < this.sequenceLength; seqIdx++) {
            this.sequence[seqIdx] = moduleData[96 + seqIdx] & 0xFF;
        }
        int moduleDataIdx = 96 + this.sequenceLength;
        this.instruments = new Instrument[this.numInstruments + 1];
        this.instruments[0] = new Instrument();
        for (int instIdx = 1; instIdx <= this.numInstruments; instIdx++) {
            final Instrument instrument = this.instruments[instIdx] = new Instrument();
            final Sample sample = instrument.samples[0];
            final int instOffset = Module.ushortle(moduleData,
                    moduleDataIdx) << 4;
            moduleDataIdx += 2;
            instrument.name = Module.codePage850(moduleData, instOffset + 48,
                    28);
            if (moduleData[instOffset] != 1) {
                continue;
            }
            if (Module.ushortle(moduleData, instOffset + 76) != 0x4353) {
                continue;
            }
            int sampleOffset = (moduleData[instOffset + 13] & 0xFF) << 20;
            sampleOffset += Module.ushortle(moduleData, instOffset + 14) << 4;
            final int sampleLength = Module.intle(moduleData, instOffset + 16);
            int loopStart = Module.intle(moduleData, instOffset + 20);
            int loopLength = Module.intle(moduleData, instOffset + 24)
                    - loopStart;
            sample.volume = moduleData[instOffset + 28] & 0xFF;
            sample.panning = -1;
            final boolean packed = moduleData[instOffset + 30] != 0;
            final boolean loopOn = (moduleData[instOffset + 31] & 0x1) == 0x1;
            if (loopStart + loopLength > sampleLength) {
                loopLength = sampleLength - loopStart;
            }
            if (loopLength < 1 || !loopOn) {
                loopStart = sampleLength;
                loopLength = 0;
            }
            final boolean sixteenBit = (moduleData[instOffset + 31]
                    & 0x4) == 0x4;
            if (packed) {
                throw new IllegalArgumentException(
                        "Packed samples not supported!"); //$NON-NLS-1$
            }
            sample.c2Rate = Module.intle(moduleData, instOffset + 32);
            final short[] sampleData = new short[loopStart + loopLength];
            if (sixteenBit) {
                if (signedSamples) {
                    for (int idx = 0, end = sampleData.length; idx < end; idx++) {
                        sampleData[idx] = (short) (moduleData[sampleOffset]
                                & 0xFF | moduleData[sampleOffset + 1] << 8);
                        sampleOffset += 2;
                    }
                } else {
                    for (int idx = 0, end = sampleData.length; idx < end; idx++) {
                        final int sam = moduleData[sampleOffset] & 0xFF
                                | (moduleData[sampleOffset + 1] & 0xFF) << 8;
                        sampleData[idx] = (short) (sam - 32768);
                        sampleOffset += 2;
                    }
                }
            } else {
                if (signedSamples) {
                    for (int idx = 0, end = sampleData.length; idx < end; idx++) {
                        sampleData[idx] = (short) (moduleData[sampleOffset++] << 8);
                    }
                } else {
                    for (int idx = 0, end = sampleData.length; idx < end; idx++) {
                        sampleData[idx] = (short) ((moduleData[sampleOffset++]
                                & 0xFF) - 128 << 8);
                    }
                }
            }
            sample.setSampleData(sampleData, loopStart, loopLength, false);
        }
        this.patterns = new Pattern[this.numPatterns];
        for (int patIdx = 0; patIdx < this.numPatterns; patIdx++) {
            final Pattern pattern = this.patterns[patIdx] = new Pattern(
                    this.numChannels, 64);
            int inOffset = (Module.ushortle(moduleData, moduleDataIdx) << 4)
                    + 2;
            int rowIdx = 0;
            while (rowIdx < 64) {
                final int token = moduleData[inOffset++] & 0xFF;
                if (token == 0) {
                    rowIdx++;
                    continue;
                }
                int noteKey = 0;
                int noteIns = 0;
                if ((token & 0x20) == 0x20) { /* Key + Instrument. */
                    noteKey = moduleData[inOffset++] & 0xFF;
                    noteIns = moduleData[inOffset++] & 0xFF;
                    if (noteKey < 0xFE) {
                        noteKey = (noteKey >> 4) * 12 + (noteKey & 0xF) + 1;
                    }
                    if (noteKey == 0xFF) {
                        noteKey = 0;
                    }
                }
                int noteVol = 0;
                if ((token & 0x40) == 0x40) { /* Volume Column. */
                    noteVol = (moduleData[inOffset++] & 0x7F) + 0x10;
                    if (noteVol > 0x50) {
                        noteVol = 0;
                    }
                }
                int noteEffect = 0;
                int noteParam = 0;
                if ((token & 0x80) == 0x80) { /* Effect + Param. */
                    noteEffect = moduleData[inOffset++] & 0xFF;
                    noteParam = moduleData[inOffset++] & 0xFF;
                    if (noteEffect < 1 || noteEffect >= 0x40) {
                        noteEffect = noteParam = 0;
                    }
                    if (noteEffect > 0) {
                        noteEffect += 0x80;
                    }
                }
                final int chanIdx = channelMap[token & 0x1F];
                if (chanIdx >= 0) {
                    final int noteOffset = (rowIdx * this.numChannels + chanIdx)
                            * 5;
                    pattern.data[noteOffset] = (byte) noteKey;
                    pattern.data[noteOffset + 1] = (byte) noteIns;
                    pattern.data[noteOffset + 2] = (byte) noteVol;
                    pattern.data[noteOffset + 3] = (byte) noteEffect;
                    pattern.data[noteOffset + 4] = (byte) noteParam;
                }
            }
            moduleDataIdx += 2;
        }
        this.defaultPanning = new int[this.numChannels];
        for (int chanIdx = 0; chanIdx < 32; chanIdx++) {
            if (channelMap[chanIdx] < 0) {
                continue;
            }
            int panning = 7;
            if (stereoMode) {
                panning = 12;
                if ((moduleData[64 + chanIdx] & 0xFF) < 8) {
                    panning = 3;
                }
            }
            if (defaultPan) {
                final int panFlags = moduleData[moduleDataIdx + chanIdx] & 0xFF;
                if ((panFlags & 0x20) == 0x20) {
                    panning = panFlags & 0xF;
                }
            }
            this.defaultPanning[channelMap[chanIdx]] = panning * 17;
        }
    }

    private void loadXM(final byte[] moduleData) {
        if (Module.ushortle(moduleData, 58) != 0x0104) {
            throw new IllegalArgumentException(
                    "XM format version must be 0x0104!"); //$NON-NLS-1$
        }
        this.songName = Module.codePage850(moduleData, 17, 20);
        final boolean deltaEnv = Module.isoLatin1(moduleData, 38, 20)
                .startsWith("DigiBooster Pro"); //$NON-NLS-1$
        int dataOffset = 60 + Module.intle(moduleData, 60);
        this.sequenceLength = Module.ushortle(moduleData, 64);
        this.restartPos = Module.ushortle(moduleData, 66);
        this.numChannels = Module.ushortle(moduleData, 68);
        this.numPatterns = Module.ushortle(moduleData, 70);
        this.numInstruments = Module.ushortle(moduleData, 72);
        this.linearPeriods = (Module.ushortle(moduleData, 74) & 0x1) > 0;
        this.defaultGVol = 64;
        this.defaultSpeed = Module.ushortle(moduleData, 76);
        this.defaultTempo = Module.ushortle(moduleData, 78);
        this.c2Rate = Sample.C2_NTSC;
        this.gain = 64;
        this.defaultPanning = new int[this.numChannels];
        for (int idx = 0; idx < this.numChannels; idx++) {
            this.defaultPanning[idx] = 128;
        }
        this.sequence = new int[this.sequenceLength];
        for (int seqIdx = 0; seqIdx < this.sequenceLength; seqIdx++) {
            final int entry = moduleData[80 + seqIdx] & 0xFF;
            this.sequence[seqIdx] = entry < this.numPatterns ? entry : 0;
        }
        this.patterns = new Pattern[this.numPatterns];
        for (int patIdx = 0; patIdx < this.numPatterns; patIdx++) {
            if (moduleData[dataOffset + 4] != 0) {
                throw new IllegalArgumentException(
                        "Unknown pattern packing type!"); //$NON-NLS-1$
            }
            final int numRows = Module.ushortle(moduleData, dataOffset + 5);
            final int numNotes = numRows * this.numChannels;
            final Pattern pattern = this.patterns[patIdx] = new Pattern(
                    this.numChannels, numRows);
            final int patternDataLength = Module.ushortle(moduleData,
                    dataOffset + 7);
            dataOffset += Module.intle(moduleData, dataOffset);
            final int nextOffset = dataOffset + patternDataLength;
            if (patternDataLength > 0) {
                int patternDataOffset = 0;
                for (int note = 0; note < numNotes; note++) {
                    int flags = moduleData[dataOffset];
                    if ((flags & 0x80) == 0) {
                        flags = 0x1F;
                    } else {
                        dataOffset++;
                    }
                    final byte key = (flags & 0x01) > 0
                            ? moduleData[dataOffset++]
                            : 0;
                    pattern.data[patternDataOffset++] = key;
                    final byte ins = (flags & 0x02) > 0
                            ? moduleData[dataOffset++]
                            : 0;
                    pattern.data[patternDataOffset++] = ins;
                    final byte vol = (flags & 0x04) > 0
                            ? moduleData[dataOffset++]
                            : 0;
                    pattern.data[patternDataOffset++] = vol;
                    byte fxc = (flags & 0x08) > 0 ? moduleData[dataOffset++]
                            : 0;
                    byte fxp = (flags & 0x10) > 0 ? moduleData[dataOffset++]
                            : 0;
                    if (fxc >= 0x40) {
                        fxc = fxp = 0;
                    }
                    pattern.data[patternDataOffset++] = fxc;
                    pattern.data[patternDataOffset++] = fxp;
                }
            }
            dataOffset = nextOffset;
        }
        this.instruments = new Instrument[this.numInstruments + 1];
        this.instruments[0] = new Instrument();
        for (int insIdx = 1; insIdx <= this.numInstruments; insIdx++) {
            final Instrument instrument = this.instruments[insIdx] = new Instrument();
            instrument.name = Module.codePage850(moduleData, dataOffset + 4,
                    22);
            final int numSamples = instrument.numSamples = Module
                    .ushortle(moduleData, dataOffset + 27);
            if (numSamples > 0) {
                instrument.samples = new Sample[numSamples];
                for (int keyIdx = 0; keyIdx < 96; keyIdx++) {
                    instrument.keyToSample[keyIdx
                            + 1] = moduleData[dataOffset + 33 + keyIdx] & 0xFF;
                }
                final Envelope volEnv = instrument.volumeEnvelope = new Envelope();
                volEnv.pointsTick = new int[12];
                volEnv.pointsAmpl = new int[12];
                int pointTick = 0;
                for (int point = 0; point < 12; point++) {
                    final int pointOffset = dataOffset + 129 + point * 4;
                    pointTick = (deltaEnv ? pointTick : 0)
                            + Module.ushortle(moduleData, pointOffset);
                    volEnv.pointsTick[point] = pointTick;
                    volEnv.pointsAmpl[point] = Module.ushortle(moduleData,
                            pointOffset + 2);
                }
                final Envelope panEnv = instrument.panningEnvelope = new Envelope();
                panEnv.pointsTick = new int[12];
                panEnv.pointsAmpl = new int[12];
                pointTick = 0;
                for (int point = 0; point < 12; point++) {
                    final int pointOffset = dataOffset + 177 + point * 4;
                    pointTick = (deltaEnv ? pointTick : 0)
                            + Module.ushortle(moduleData, pointOffset);
                    panEnv.pointsTick[point] = pointTick;
                    panEnv.pointsAmpl[point] = Module.ushortle(moduleData,
                            pointOffset + 2);
                }
                volEnv.numPoints = moduleData[dataOffset + 225] & 0xFF;
                if (volEnv.numPoints > 12) {
                    volEnv.numPoints = 0;
                }
                panEnv.numPoints = moduleData[dataOffset + 226] & 0xFF;
                if (panEnv.numPoints > 12) {
                    panEnv.numPoints = 0;
                }
                volEnv.sustainTick = volEnv.pointsTick[moduleData[dataOffset
                        + 227]];
                volEnv.loopStartTick = volEnv.pointsTick[moduleData[dataOffset
                        + 228]];
                volEnv.loopEndTick = volEnv.pointsTick[moduleData[dataOffset
                        + 229]];
                panEnv.sustainTick = panEnv.pointsTick[moduleData[dataOffset
                        + 230]];
                panEnv.loopStartTick = panEnv.pointsTick[moduleData[dataOffset
                        + 231]];
                panEnv.loopEndTick = panEnv.pointsTick[moduleData[dataOffset
                        + 232]];
                volEnv.enabled = volEnv.numPoints > 0
                        && (moduleData[dataOffset + 233] & 0x1) > 0;
                volEnv.sustain = (moduleData[dataOffset + 233] & 0x2) > 0;
                volEnv.looped = (moduleData[dataOffset + 233] & 0x4) > 0;
                panEnv.enabled = panEnv.numPoints > 0
                        && (moduleData[dataOffset + 234] & 0x1) > 0;
                panEnv.sustain = (moduleData[dataOffset + 234] & 0x2) > 0;
                panEnv.looped = (moduleData[dataOffset + 234] & 0x4) > 0;
                instrument.vibratoType = moduleData[dataOffset + 235] & 0xFF;
                instrument.vibratoSweep = moduleData[dataOffset + 236] & 0xFF;
                instrument.vibratoDepth = moduleData[dataOffset + 237] & 0xFF;
                instrument.vibratoRate = moduleData[dataOffset + 238] & 0xFF;
                instrument.volumeFadeOut = Module.ushortle(moduleData,
                        dataOffset + 239);
            }
            dataOffset += Module.intle(moduleData, dataOffset);
            int sampleHeaderOffset = dataOffset;
            dataOffset += numSamples * 40;
            for (int samIdx = 0; samIdx < numSamples; samIdx++) {
                final Sample sample = instrument.samples[samIdx] = new Sample();
                final int sampleDataBytes = Module.intle(moduleData,
                        sampleHeaderOffset);
                int sampleLoopStart = Module.intle(moduleData,
                        sampleHeaderOffset + 4);
                int sampleLoopLength = Module.intle(moduleData,
                        sampleHeaderOffset + 8);
                sample.volume = moduleData[sampleHeaderOffset + 12];
                sample.fineTune = moduleData[sampleHeaderOffset + 13];
                sample.c2Rate = Sample.C2_NTSC;
                final boolean looped = (moduleData[sampleHeaderOffset + 14]
                        & 0x3) > 0;
                final boolean pingPong = (moduleData[sampleHeaderOffset + 14]
                        & 0x2) > 0;
                final boolean sixteenBit = (moduleData[sampleHeaderOffset + 14]
                        & 0x10) > 0;
                sample.panning = moduleData[sampleHeaderOffset + 15] & 0xFF;
                sample.relNote = moduleData[sampleHeaderOffset + 16];
                sample.name = Module.codePage850(moduleData,
                        sampleHeaderOffset + 18, 22);
                sampleHeaderOffset += 40;
                int sampleDataLength = sampleDataBytes;
                if (sixteenBit) {
                    sampleDataLength /= 2;
                    sampleLoopStart /= 2;
                    sampleLoopLength /= 2;
                }
                if (!looped || sampleLoopStart
                        + sampleLoopLength > sampleDataLength) {
                    sampleLoopStart = sampleDataLength;
                    sampleLoopLength = 0;
                }
                final short[] sampleData = new short[sampleDataLength];
                if (sixteenBit) {
                    short ampl = 0;
                    for (int outIdx = 0; outIdx < sampleDataLength; outIdx++) {
                        final int inIdx = dataOffset + outIdx * 2;
                        ampl += moduleData[inIdx] & 0xFF;
                        ampl += (moduleData[inIdx + 1] & 0xFF) << 8;
                        sampleData[outIdx] = ampl;
                    }
                } else {
                    byte ampl = 0;
                    for (int outIdx = 0; outIdx < sampleDataLength; outIdx++) {
                        ampl += moduleData[dataOffset + outIdx] & 0xFF;
                        sampleData[outIdx] = (short) (ampl << 8);
                    }
                }
                sample.setSampleData(sampleData, sampleLoopStart,
                        sampleLoopLength, pingPong);
                dataOffset += sampleDataBytes;
            }
        }
    }

    private static int ushortbe(final byte[] buf, final int offset) {
        return (buf[offset] & 0xFF) << 8 | buf[offset + 1] & 0xFF;
    }

    private static int ushortle(final byte[] buf, final int offset) {
        return buf[offset] & 0xFF | (buf[offset + 1] & 0xFF) << 8;
    }

    private static int intle(final byte[] buf, final int offset) {
        int value = buf[offset] & 0xFF;
        value |= (buf[offset + 1] & 0xFF) << 8;
        value |= (buf[offset + 2] & 0xFF) << 16;
        value |= (buf[offset + 3] & 0x7F) << 24;
        return value;
    }

    private static String isoLatin1(final byte[] buf, final int offset,
            final int len) {
        final char[] str = new char[len];
        for (int idx = 0; idx < len; idx++) {
            final int c = buf[offset + idx] & 0xFF;
            str[idx] = c < 32 ? 32 : (char) c;
        }
        return new String(str);
    }

    private static String codePage850(final byte[] buf, final int offset,
            final int len) {
        try {
            final char[] str = new String(buf, offset, len, "Cp850") //$NON-NLS-1$
                    .toCharArray();
            for (int idx = 0; idx < str.length; idx++) {
                str[idx] = str[idx] < 32 ? 32 : str[idx];
            }
            return new String(str);
        } catch (final java.io.UnsupportedEncodingException e) {
            return Module.isoLatin1(buf, offset, len);
        }
    }

    public void toStringBuffer(final StringBuffer out) {
        out.append("Song Name: " + this.songName + '\n' + "Num Channels: " //$NON-NLS-1$ //$NON-NLS-2$
                + this.numChannels + '\n' + "Num Instruments: " //$NON-NLS-1$
                + this.numInstruments + '\n' + "Num Patterns: " //$NON-NLS-1$
                + this.numPatterns + '\n' + "Sequence Length: " //$NON-NLS-1$
                + this.sequenceLength + '\n' + "Restart Pos: " + this.restartPos //$NON-NLS-1$
                + '\n' + "Default Speed: " + this.defaultSpeed + '\n' //$NON-NLS-1$
                + "Default Tempo: " + this.defaultTempo + '\n' //$NON-NLS-1$
                + "Linear Periods: " + this.linearPeriods + '\n'); //$NON-NLS-1$
        out.append("Sequence: "); //$NON-NLS-1$
        for (final int element : this.sequence) {
            out.append(element + ", "); //$NON-NLS-1$
        }
        out.append('\n');
        for (int patIdx = 0; patIdx < this.patterns.length; patIdx++) {
            out.append("Pattern " + patIdx + ":\n"); //$NON-NLS-1$ //$NON-NLS-2$
            this.patterns[patIdx].toStringBuffer(out);
        }
        for (int insIdx = 1; insIdx < this.instruments.length; insIdx++) {
            out.append("Instrument " + insIdx + ":\n"); //$NON-NLS-1$ //$NON-NLS-2$
            this.instruments[insIdx].toStringBuffer(out);
        }
    }
}
