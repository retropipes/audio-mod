package com.puttysoftware.audio.mod;

public class Channel {
    public static final int NEAREST = 0, LINEAR = 1, SINC = 2;
    private static final int[] periodTable = {
            // Periods for keys -11 to 1 with 8 finetune values.
            54784, 54390, 53999, 53610, 53224, 52841, 52461, 52084, 51709,
            51337, 50968, 50601, 50237, 49876, 49517, 49161, 48807, 48456,
            48107, 47761, 47418, 47076, 46738, 46401, 46068, 45736, 45407,
            45081, 44756, 44434, 44115, 43797, 43482, 43169, 42859, 42550,
            42244, 41940, 41639, 41339, 41042, 40746, 40453, 40162, 39873,
            39586, 39302, 39019, 38738, 38459, 38183, 37908, 37635, 37365,
            37096, 36829, 36564, 36301, 36040, 35780, 35523, 35267, 35014,
            34762, 34512, 34263, 34017, 33772, 33529, 33288, 33049, 32811,
            32575, 32340, 32108, 31877, 31647, 31420, 31194, 30969, 30746,
            30525, 30306, 30088, 29871, 29656, 29443, 29231, 29021, 28812,
            28605, 28399, 28195, 27992, 27790, 27590, 27392, 27195, 26999,
            26805, 26612, 26421, 26231, 26042 };
    private static final int[] freqTable = {
            // Frequency for keys 109 to 121 with 8 fractional values.
            267616, 269555, 271509, 273476, 275458, 277454, 279464, 281489,
            283529, 285584, 287653, 289738, 291837, 293952, 296082, 298228,
            300389, 302566, 304758, 306966, 309191, 311431, 313688, 315961,
            318251, 320557, 322880, 325220, 327576, 329950, 332341, 334749,
            337175, 339618, 342079, 344558, 347055, 349570, 352103, 354655,
            357225, 359813, 362420, 365047, 367692, 370356, 373040, 375743,
            378466, 381209, 383971, 386754, 389556, 392379, 395222, 398086,
            400971, 403877, 406803, 409751, 412720, 415711, 418723, 421758,
            424814, 427892, 430993, 434116, 437262, 440430, 443622, 446837,
            450075, 453336, 456621, 459930, 463263, 466620, 470001, 473407,
            476838, 480293, 483773, 487279, 490810, 494367, 497949, 501557,
            505192, 508853, 512540, 516254, 519995, 523763, 527558, 531381,
            535232, 539111, 543017, 546952, 550915, 554908, 558929, 562979 };
    private static final short[] arpTuning = { 4096, 4340, 4598, 4871, 5161,
            5468, 5793, 6137, 6502, 6889, 7298, 7732, 8192, 8679, 9195, 9742 };
    private static final short[] sineTable = { 0, 24, 49, 74, 97, 120, 141, 161,
            180, 197, 212, 224, 235, 244, 250, 253, 255, 253, 250, 244, 235,
            224, 212, 197, 180, 161, 141, 120, 97, 74, 49, 24 };
    private final Module module;
    private final GlobalVol globalVol;
    private Instrument instrument;
    private Sample sample;
    private boolean keyOn;
    private int noteKey, noteIns, noteVol, noteEffect, noteParam;
    private int sampleIdx, sampleFra, step, ampl, pann;
    private int volume, panning, fadeOutVol, volEnvTick, panEnvTick;
    private int period, portaPeriod, retrigCount, fxCount, autoVibratoCount;
    private int portaUpParam, portaDownParam, tonePortaParam, offsetParam;
    private int finePortaUpParam, finePortaDownParam, extraFinePortaParam;
    private int arpeggioParam, vslideParam, globalVslideParam,
            panningSlideParam;
    private int fineVslideUpParam, fineVslideDownParam;
    private int retrigVolume, retrigTicks, tremorOnTicks, tremorOffTicks;
    private int vibratoType, vibratoPhase, vibratoSpeed, vibratoDepth;
    private int tremoloType, tremoloPhase, tremoloSpeed, tremoloDepth;
    private int tremoloAdd, vibratoAdd, arpeggioAdd;
    private final int sampleRate;
    private int randomSeed;
    public int plRow;

    public Channel(final Module newModule, final int id,
            final int newSampleRate, final GlobalVol newGlobalVol) {
        this.module = newModule;
        this.randomSeed = id;
        this.sampleRate = newSampleRate;
        this.globalVol = newGlobalVol;
        this.panning = newModule.defaultPanning[id];
        this.instrument = new Instrument();
        this.sample = this.instrument.samples[0];
    }

    public void resample(final int[] outBuf, final int offset, final int length,
            final int interpolation) {
        if (this.ampl <= 0) {
            return;
        }
        final int lAmpl = this.ampl * (255 - this.pann) >> 8;
        final int rAmpl = this.ampl * this.pann >> 8;
        switch (interpolation) {
        case NEAREST:
            this.sample.resampleNearest(this.sampleIdx, this.sampleFra,
                    this.step, lAmpl, rAmpl, outBuf, offset, length);
            break;
        case LINEAR:
        default:
            this.sample.resampleLinear(this.sampleIdx, this.sampleFra,
                    this.step, lAmpl, rAmpl, outBuf, offset, length);
            break;
        case SINC:
            this.sample.resampleSinc(this.sampleIdx, this.sampleFra, this.step,
                    lAmpl, rAmpl, outBuf, offset, length);
            break;
        }
    }

    public void updateSampleIdx(final int length) {
        this.sampleFra += this.step * length;
        this.sampleIdx = this.sample.normaliseSampleIdx(
                this.sampleIdx + (this.sampleFra >> Sample.FP_SHIFT));
        this.sampleFra &= Sample.FP_MASK;
    }

    public void row(final Note note) {
        this.noteKey = note.key;
        this.noteIns = note.instrument;
        this.noteVol = note.volume;
        this.noteEffect = note.effect;
        this.noteParam = note.param;
        this.retrigCount++;
        this.vibratoAdd = this.tremoloAdd = this.arpeggioAdd = this.fxCount = 0;
        if (this.noteEffect != 0x7D && this.noteEffect != 0xFD) {
            this.trigger();
        }
        switch (this.noteEffect) {
        case 0x01:
        case 0x86: /* Porta Up. */
            if (this.noteParam > 0) {
                this.portaUpParam = this.noteParam;
            }
            this.portamentoUp(this.portaUpParam);
            break;
        case 0x02:
        case 0x85: /* Porta Down. */
            if (this.noteParam > 0) {
                this.portaDownParam = this.noteParam;
            }
            this.portamentoDown(this.portaDownParam);
            break;
        case 0x03:
        case 0x87: /* Tone Porta. */
            if (this.noteParam > 0) {
                this.tonePortaParam = this.noteParam;
            }
            break;
        case 0x04:
        case 0x88: /* Vibrato. */
            if (this.noteParam >> 4 > 0) {
                this.vibratoSpeed = this.noteParam >> 4;
            }
            if ((this.noteParam & 0xF) > 0) {
                this.vibratoDepth = this.noteParam & 0xF;
            }
            this.vibrato(false);
            break;
        case 0x05:
        case 0x8C: /* Tone Porta + Vol Slide. */
            if (this.noteParam > 0) {
                this.vslideParam = this.noteParam;
            }
            this.volumeSlide();
            break;
        case 0x06:
        case 0x8B: /* Vibrato + Vol Slide. */
            if (this.noteParam > 0) {
                this.vslideParam = this.noteParam;
            }
            this.vibrato(false);
            this.volumeSlide();
            break;
        case 0x07:
        case 0x92: /* Tremolo. */
            if (this.noteParam >> 4 > 0) {
                this.tremoloSpeed = this.noteParam >> 4;
            }
            if ((this.noteParam & 0xF) > 0) {
                this.tremoloDepth = this.noteParam & 0xF;
            }
            this.tremolo();
            break;
        case 0x08: /* Set Panning. */
            this.panning = this.noteParam & 0xFF;
            break;
        case 0x09:
        case 0x8F: /* Set Sample Offset. */
            if (this.noteParam > 0) {
                this.offsetParam = this.noteParam;
            }
            this.sampleIdx = this.offsetParam << 8;
            this.sampleFra = 0;
            break;
        case 0x0A:
        case 0x84: /* Vol Slide. */
            if (this.noteParam > 0) {
                this.vslideParam = this.noteParam;
            }
            this.volumeSlide();
            break;
        case 0x0C: /* Set Volume. */
            this.volume = this.noteParam >= 64 ? 64 : this.noteParam & 0x3F;
            break;
        case 0x10:
        case 0x96: /* Set Global Volume. */
            this.globalVol.volume = this.noteParam >= 64 ? 64
                    : this.noteParam & 0x3F;
            break;
        case 0x11: /* Global Volume Slide. */
            if (this.noteParam > 0) {
                this.globalVslideParam = this.noteParam;
            }
            break;
        case 0x14: /* Key Off. */
            this.keyOn = false;
            break;
        case 0x15: /* Set Envelope Tick. */
            this.volEnvTick = this.panEnvTick = this.noteParam & 0xFF;
            break;
        case 0x19: /* Panning Slide. */
            if (this.noteParam > 0) {
                this.panningSlideParam = this.noteParam;
            }
            break;
        case 0x1B:
        case 0x91: /* Retrig + Vol Slide. */
            if (this.noteParam >> 4 > 0) {
                this.retrigVolume = this.noteParam >> 4;
            }
            if ((this.noteParam & 0xF) > 0) {
                this.retrigTicks = this.noteParam & 0xF;
            }
            this.retrigVolSlide();
            break;
        case 0x1D:
        case 0x89: /* Tremor. */
            if (this.noteParam >> 4 > 0) {
                this.tremorOnTicks = this.noteParam >> 4;
            }
            if ((this.noteParam & 0xF) > 0) {
                this.tremorOffTicks = this.noteParam & 0xF;
            }
            this.tremor();
            break;
        case 0x21: /* Extra Fine Porta. */
            if (this.noteParam > 0) {
                this.extraFinePortaParam = this.noteParam;
            }
            switch (this.extraFinePortaParam & 0xF0) {
            case 0x10:
                this.portamentoUp(0xE0 | this.extraFinePortaParam & 0xF);
                break;
            case 0x20:
                this.portamentoDown(0xE0 | this.extraFinePortaParam & 0xF);
                break;
            default:
                // Do nothing
                break;
            }
            break;
        case 0x71: /* Fine Porta Up. */
            if (this.noteParam > 0) {
                this.finePortaUpParam = this.noteParam;
            }
            this.portamentoUp(0xF0 | this.finePortaUpParam & 0xF);
            break;
        case 0x72: /* Fine Porta Down. */
            if (this.noteParam > 0) {
                this.finePortaDownParam = this.noteParam;
            }
            this.portamentoDown(0xF0 | this.finePortaDownParam & 0xF);
            break;
        case 0x74:
        case 0xF3: /* Set Vibrato Waveform. */
            if (this.noteParam < 8) {
                this.vibratoType = this.noteParam;
            }
            break;
        case 0x77:
        case 0xF4: /* Set Tremolo Waveform. */
            if (this.noteParam < 8) {
                this.tremoloType = this.noteParam;
            }
            break;
        case 0x7A: /* Fine Vol Slide Up. */
            if (this.noteParam > 0) {
                this.fineVslideUpParam = this.noteParam;
            }
            this.volume += this.fineVslideUpParam;
            if (this.volume > 64) {
                this.volume = 64;
            }
            break;
        case 0x7B: /* Fine Vol Slide Down. */
            if (this.noteParam > 0) {
                this.fineVslideDownParam = this.noteParam;
            }
            this.volume -= this.fineVslideDownParam;
            if (this.volume < 0) {
                this.volume = 0;
            }
            break;
        case 0x7C:
        case 0xFC: /* Note Cut. */
            if (this.noteParam <= 0) {
                this.volume = 0;
            }
            break;
        case 0x7D:
        case 0xFD: /* Note Delay. */
            if (this.noteParam <= 0) {
                this.trigger();
            }
            break;
        case 0x8A: /* Arpeggio. */
            if (this.noteParam > 0) {
                this.arpeggioParam = this.noteParam;
            }
            break;
        case 0x95: /* Fine Vibrato. */
            if (this.noteParam >> 4 > 0) {
                this.vibratoSpeed = this.noteParam >> 4;
            }
            if ((this.noteParam & 0xF) > 0) {
                this.vibratoDepth = this.noteParam & 0xF;
            }
            this.vibrato(true);
            break;
        case 0xF8: /* Set Panning. */
            this.panning = this.noteParam * 17;
            break;
        default:
            // Do nothing
            break;
        }
        this.autoVibrato();
        this.calculateFrequency();
        this.calculateAmplitude();
        this.updateEnvelopes();
    }

    public void tick() {
        this.vibratoAdd = 0;
        this.fxCount++;
        this.retrigCount++;
        if (!(this.noteEffect == 0x7D && this.fxCount <= this.noteParam)) {
            switch (this.noteVol & 0xF0) {
            case 0x60: /* Vol Slide Down. */
                this.volume -= this.noteVol & 0xF;
                if (this.volume < 0) {
                    this.volume = 0;
                }
                break;
            case 0x70: /* Vol Slide Up. */
                this.volume += this.noteVol & 0xF;
                if (this.volume > 64) {
                    this.volume = 64;
                }
                break;
            case 0xB0: /* Vibrato. */
                this.vibratoPhase += this.vibratoSpeed;
                this.vibrato(false);
                break;
            case 0xD0: /* Pan Slide Left. */
                this.panning -= this.noteVol & 0xF;
                if (this.panning < 0) {
                    this.panning = 0;
                }
                break;
            case 0xE0: /* Pan Slide Right. */
                this.panning += this.noteVol & 0xF;
                if (this.panning > 255) {
                    this.panning = 255;
                }
                break;
            case 0xF0: /* Tone Porta. */
                this.tonePortamento();
                break;
            default:
                // Do nothing
                break;
            }
        }
        switch (this.noteEffect) {
        case 0x01:
        case 0x86: /* Porta Up. */
            this.portamentoUp(this.portaUpParam);
            break;
        case 0x02:
        case 0x85: /* Porta Down. */
            this.portamentoDown(this.portaDownParam);
            break;
        case 0x03:
        case 0x87: /* Tone Porta. */
            this.tonePortamento();
            break;
        case 0x04:
        case 0x88: /* Vibrato. */
            this.vibratoPhase += this.vibratoSpeed;
            this.vibrato(false);
            break;
        case 0x05:
        case 0x8C: /* Tone Porta + Vol Slide. */
            this.tonePortamento();
            this.volumeSlide();
            break;
        case 0x06:
        case 0x8B: /* Vibrato + Vol Slide. */
            this.vibratoPhase += this.vibratoSpeed;
            this.vibrato(false);
            this.volumeSlide();
            break;
        case 0x07:
        case 0x92: /* Tremolo. */
            this.tremoloPhase += this.tremoloSpeed;
            this.tremolo();
            break;
        case 0x0A:
        case 0x84: /* Vol Slide. */
            this.volumeSlide();
            break;
        case 0x11: /* Global Volume Slide. */
            this.globalVol.volume += (this.globalVslideParam >> 4)
                    - (this.globalVslideParam & 0xF);
            if (this.globalVol.volume < 0) {
                this.globalVol.volume = 0;
            }
            if (this.globalVol.volume > 64) {
                this.globalVol.volume = 64;
            }
            break;
        case 0x19: /* Panning Slide. */
            this.panning += (this.panningSlideParam >> 4)
                    - (this.panningSlideParam & 0xF);
            if (this.panning < 0) {
                this.panning = 0;
            }
            if (this.panning > 255) {
                this.panning = 255;
            }
            break;
        case 0x1B:
        case 0x91: /* Retrig + Vol Slide. */
            this.retrigVolSlide();
            break;
        case 0x1D:
        case 0x89: /* Tremor. */
            this.tremor();
            break;
        case 0x79: /* Retrig. */
            if (this.fxCount >= this.noteParam) {
                this.fxCount = 0;
                this.sampleIdx = this.sampleFra = 0;
            }
            break;
        case 0x7C:
        case 0xFC: /* Note Cut. */
            if (this.noteParam == this.fxCount) {
                this.volume = 0;
            }
            break;
        case 0x7D:
        case 0xFD: /* Note Delay. */
            if (this.noteParam == this.fxCount) {
                this.trigger();
            }
            break;
        case 0x8A: /* Arpeggio. */
            if (this.fxCount > 2) {
                this.fxCount = 0;
            }
            if (this.fxCount == 0) {
                this.arpeggioAdd = 0;
            }
            if (this.fxCount == 1) {
                this.arpeggioAdd = this.arpeggioParam >> 4;
            }
            if (this.fxCount == 2) {
                this.arpeggioAdd = this.arpeggioParam & 0xF;
            }
            break;
        case 0x95: /* Fine Vibrato. */
            this.vibratoPhase += this.vibratoSpeed;
            this.vibrato(true);
            break;
        default:
            // Do nothing
            break;
        }
        this.autoVibrato();
        this.calculateFrequency();
        this.calculateAmplitude();
        this.updateEnvelopes();
    }

    private void updateEnvelopes() {
        if (this.instrument.volumeEnvelope.enabled) {
            if (!this.keyOn) {
                this.fadeOutVol -= this.instrument.volumeFadeOut;
                if (this.fadeOutVol < 0) {
                    this.fadeOutVol = 0;
                }
            }
            this.volEnvTick = this.instrument.volumeEnvelope
                    .nextTick(this.volEnvTick, this.keyOn);
        }
        if (this.instrument.panningEnvelope.enabled) {
            this.panEnvTick = this.instrument.panningEnvelope
                    .nextTick(this.panEnvTick, this.keyOn);
        }
    }

    private void autoVibrato() {
        int depth = this.instrument.vibratoDepth & 0x7F;
        if (depth > 0) {
            final int sweep = this.instrument.vibratoSweep & 0x7F;
            final int rate = this.instrument.vibratoRate & 0x7F;
            final int type = this.instrument.vibratoType;
            if (this.autoVibratoCount < sweep) {
                depth = depth * this.autoVibratoCount / sweep;
            }
            this.vibratoAdd += this.waveform(this.autoVibratoCount * rate >> 2,
                    type + 4) * depth >> 8;
            this.autoVibratoCount++;
        }
    }

    private void volumeSlide() {
        final int up = this.vslideParam >> 4;
        final int down = this.vslideParam & 0xF;
        if (down == 0xF && up > 0) { /* Fine slide up. */
            if (this.fxCount == 0) {
                this.volume += up;
            }
        } else if (up == 0xF && down > 0) { /* Fine slide down. */
            if (this.fxCount == 0) {
                this.volume -= down;
            }
        } else if (this.fxCount > 0 || this.module.fastVolSlides) {
            this.volume += up - down;
        }
        if (this.volume > 64) {
            this.volume = 64;
        }
        if (this.volume < 0) {
            this.volume = 0;
        }
    }

    private void portamentoUp(final int param) {
        switch (param & 0xF0) {
        case 0xE0: /* Extra-fine porta. */
            if (this.fxCount == 0) {
                this.period -= param & 0xF;
            }
            break;
        case 0xF0: /* Fine porta. */
            if (this.fxCount == 0) {
                this.period -= (param & 0xF) << 2;
            }
            break;
        default:/* Normal porta. */
            if (this.fxCount > 0) {
                this.period -= param << 2;
            }
            break;
        }
        if (this.period < 0) {
            this.period = 0;
        }
    }

    private void portamentoDown(final int param) {
        if (this.period > 0) {
            switch (param & 0xF0) {
            case 0xE0: /* Extra-fine porta. */
                if (this.fxCount == 0) {
                    this.period += param & 0xF;
                }
                break;
            case 0xF0: /* Fine porta. */
                if (this.fxCount == 0) {
                    this.period += (param & 0xF) << 2;
                }
                break;
            default:/* Normal porta. */
                if (this.fxCount > 0) {
                    this.period += param << 2;
                }
                break;
            }
            if (this.period > 65535) {
                this.period = 65535;
            }
        }
    }

    private void tonePortamento() {
        if (this.period > 0) {
            if (this.period < this.portaPeriod) {
                this.period += this.tonePortaParam << 2;
                if (this.period > this.portaPeriod) {
                    this.period = this.portaPeriod;
                }
            } else {
                this.period -= this.tonePortaParam << 2;
                if (this.period < this.portaPeriod) {
                    this.period = this.portaPeriod;
                }
            }
        }
    }

    private void vibrato(final boolean fine) {
        this.vibratoAdd = this.waveform(this.vibratoPhase,
                this.vibratoType & 0x3) * this.vibratoDepth >> (fine ? 7 : 5);
    }

    private void tremolo() {
        this.tremoloAdd = this.waveform(this.tremoloPhase,
                this.tremoloType & 0x3) * this.tremoloDepth >> 6;
    }

    private int waveform(final int phase, final int type) {
        int amplitude = 0;
        switch (type) {
        default: /* Sine. */
            amplitude = Channel.sineTable[phase & 0x1F];
            if ((phase & 0x20) > 0) {
                amplitude = -amplitude;
            }
            break;
        case 6: /* Saw Up. */
            amplitude = ((phase + 0x20 & 0x3F) << 3) - 255;
            break;
        case 1:
        case 7: /* Saw Down. */
            amplitude = 255 - ((phase + 0x20 & 0x3F) << 3);
            break;
        case 2:
        case 5: /* Square. */
            amplitude = (phase & 0x20) > 0 ? 255 : -255;
            break;
        case 3:
        case 8: /* Random. */
            amplitude = this.randomSeed - 255;
            this.randomSeed = this.randomSeed * 65 + 17 & 0x1FF;
            break;
        }
        return amplitude;
    }

    private void tremor() {
        if (this.retrigCount >= this.tremorOnTicks) {
            this.tremoloAdd = -64;
        }
        if (this.retrigCount >= this.tremorOnTicks + this.tremorOffTicks) {
            this.tremoloAdd = this.retrigCount = 0;
        }
    }

    private void retrigVolSlide() {
        if (this.retrigCount >= this.retrigTicks) {
            this.retrigCount = this.sampleIdx = this.sampleFra = 0;
            switch (this.retrigVolume) {
            case 0x1:
                this.volume -= 1;
                break;
            case 0x2:
                this.volume -= 2;
                break;
            case 0x3:
                this.volume -= 4;
                break;
            case 0x4:
                this.volume -= 8;
                break;
            case 0x5:
                this.volume -= 16;
                break;
            case 0x6:
                this.volume -= this.volume / 3;
                break;
            case 0x7:
                this.volume >>= 1;
                break;
            case 0x8: /* ? */
                break;
            case 0x9:
                this.volume += 1;
                break;
            case 0xA:
                this.volume += 2;
                break;
            case 0xB:
                this.volume += 4;
                break;
            case 0xC:
                this.volume += 8;
                break;
            case 0xD:
                this.volume += 16;
                break;
            case 0xE:
                this.volume += this.volume >> 1;
                break;
            case 0xF:
                this.volume <<= 1;
                break;
            default:
                // Do nothing
                break;
            }
            if (this.volume < 0) {
                this.volume = 0;
            }
            if (this.volume > 64) {
                this.volume = 64;
            }
        }
    }

    private void calculateFrequency() {
        if (this.module.linearPeriods) {
            int per = this.period + this.vibratoAdd - (this.arpeggioAdd << 6);
            if (per < 28 || per > 7680) {
                per = 7680;
            }
            final int tone = 7680 - per;
            final int i = (tone >> 3) % 96;
            final int c = Channel.freqTable[i];
            final int m = Channel.freqTable[i + 1] - c;
            final int x = tone & 0x7;
            final int y = (m * x >> 3) + c;
            final int freq = y >> 9 - tone / 768;
            if (freq < 65536) {
                this.step = (freq << Sample.FP_SHIFT) / this.sampleRate;
            } else {
                this.step = (freq << Sample.FP_SHIFT - 3)
                        / (this.sampleRate >> 3);
            }
        } else {
            int per = this.period + this.vibratoAdd;
            if (per < 28) {
                per = Channel.periodTable[0];
            }
            int freq = this.module.c2Rate * 1712 / per;
            freq = freq * Channel.arpTuning[this.arpeggioAdd] >> 12 & 0x7FFFF;
            if (freq < 65536) {
                this.step = (freq << Sample.FP_SHIFT) / this.sampleRate;
            } else {
                this.step = (freq << Sample.FP_SHIFT - 3)
                        / (this.sampleRate >> 3);
            }
        }
    }

    private void calculateAmplitude() {
        int envVol = this.keyOn ? 64 : 0;
        if (this.instrument.volumeEnvelope.enabled) {
            envVol = this.instrument.volumeEnvelope
                    .calculateAmpl(this.volEnvTick);
        }
        int vol = this.volume + this.tremoloAdd;
        if (vol > 64) {
            vol = 64;
        }
        if (vol < 0) {
            vol = 0;
        }
        vol = vol * this.module.gain * Sample.FP_ONE >> 13;
        vol = vol * this.fadeOutVol >> 15;
        this.ampl = vol * this.globalVol.volume * envVol >> 12;
        int envPan = 32;
        if (this.instrument.panningEnvelope.enabled) {
            envPan = this.instrument.panningEnvelope
                    .calculateAmpl(this.panEnvTick);
        }
        final int panRange = this.panning < 128 ? this.panning
                : 255 - this.panning;
        this.pann = this.panning + (panRange * (envPan - 32) >> 5);
    }

    private void trigger() {
        if (this.noteIns > 0 && this.noteIns <= this.module.numInstruments) {
            this.instrument = this.module.instruments[this.noteIns];
            final Sample sam = this.instrument.samples[this.instrument.keyToSample[this.noteKey < 97
                    ? this.noteKey
                    : 0]];
            this.volume = sam.volume >= 64 ? 64 : sam.volume & 0x3F;
            if (sam.panning >= 0) {
                this.panning = sam.panning & 0xFF;
            }
            if (this.period > 0 && sam.looped()) {
                this.sample = sam; /* Amiga trigger. */
            }
            this.volEnvTick = this.panEnvTick = 0;
            this.fadeOutVol = 32768;
            this.keyOn = true;
        }
        if (this.noteVol >= 0x10 && this.noteVol < 0x60) {
            this.volume = this.noteVol < 0x50 ? this.noteVol - 0x10 : 64;
        }
        switch (this.noteVol & 0xF0) {
        case 0x80: /* Fine Vol Down. */
            this.volume -= this.noteVol & 0xF;
            if (this.volume < 0) {
                this.volume = 0;
            }
            break;
        case 0x90: /* Fine Vol Up. */
            this.volume += this.noteVol & 0xF;
            if (this.volume > 64) {
                this.volume = 64;
            }
            break;
        case 0xA0: /* Set Vibrato Speed. */
            if ((this.noteVol & 0xF) > 0) {
                this.vibratoSpeed = this.noteVol & 0xF;
            }
            break;
        case 0xB0: /* Vibrato. */
            if ((this.noteVol & 0xF) > 0) {
                this.vibratoDepth = this.noteVol & 0xF;
            }
            this.vibrato(false);
            break;
        case 0xC0: /* Set Panning. */
            this.panning = (this.noteVol & 0xF) * 17;
            break;
        case 0xF0: /* Tone Porta. */
            if ((this.noteVol & 0xF) > 0) {
                this.tonePortaParam = this.noteVol & 0xF;
            }
            break;
        default:
            // Do nothing
            break;
        }
        if (this.noteKey > 0) {
            if (this.noteKey > 96) {
                this.keyOn = false;
            } else {
                final boolean isPorta = (this.noteVol & 0xF0) == 0xF0
                        || this.noteEffect == 0x03 || this.noteEffect == 0x05
                        || this.noteEffect == 0x87 || this.noteEffect == 0x8C;
                if (!isPorta) {
                    this.sample = this.instrument.samples[this.instrument.keyToSample[this.noteKey]];
                }
                byte fineTune = (byte) this.sample.fineTune;
                if (this.noteEffect == 0x75 || this.noteEffect == 0xF2) {
                    fineTune = (byte) ((this.noteParam & 0xF) << 4);
                }
                int key = this.noteKey + this.sample.relNote;
                if (key < 1) {
                    key = 1;
                }
                if (key > 120) {
                    key = 120;
                }
                if (this.module.linearPeriods) {
                    this.portaPeriod = 7680 - (key - 1 << 6) - (fineTune >> 1);
                } else {
                    final int tone = 768 + (key - 1 << 6) + (fineTune >> 1);
                    final int i = (tone >> 3) % 96;
                    final int c = Channel.periodTable[i];
                    final int m = Channel.periodTable[i + 1] - c;
                    final int x = tone & 0x7;
                    final int y = (m * x >> 3) + c;
                    this.portaPeriod = y >> tone / 768;
                    this.portaPeriod = this.module.c2Rate * this.portaPeriod
                            / this.sample.c2Rate;
                }
                if (!isPorta) {
                    this.period = this.portaPeriod;
                    this.sampleIdx = this.sampleFra = 0;
                    if (this.vibratoType < 4) {
                        this.vibratoPhase = 0;
                    }
                    if (this.tremoloType < 4) {
                        this.tremoloPhase = 0;
                    }
                    this.retrigCount = this.autoVibratoCount = 0;
                }
            }
        }
    }
}
