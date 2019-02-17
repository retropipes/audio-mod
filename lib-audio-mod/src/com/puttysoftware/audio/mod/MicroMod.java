package com.puttysoftware.audio.mod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class MicroMod {
    private static final int SAMPLE_RATE = 41000;
    private Module module;
    IBXM ibxm;
    volatile boolean playing;
    private int interpolation;
    private Thread playThread;

    public MicroMod() {
        // Do nothing
    }

    public boolean isPlayThreadAlive() {
        return this.playThread != null && this.playThread.isAlive();
    }

    public synchronized void loadModule(final File modFile) throws IOException {
        final byte[] moduleData = new byte[(int) modFile.length()];
        try (FileInputStream inputStream = new FileInputStream(modFile)) {
            int offset = 0;
            while (offset < moduleData.length) {
                final int len = inputStream.read(moduleData, offset,
                        moduleData.length - offset);
                if (len < 0) {
                    inputStream.close();
                    throw new IOException("Unexpected end of file."); //$NON-NLS-1$
                }
                offset += len;
            }
            inputStream.close();
            this.module = new Module(moduleData);
            this.ibxm = new IBXM(this.module, MicroMod.SAMPLE_RATE);
            this.ibxm.setInterpolation(this.interpolation);
        } catch (final IOException ioe) {
            throw ioe;
        }
    }

    public synchronized void playModule() {
        if (this.ibxm != null) {
            this.playing = true;
            this.playThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final int[] mixBuf = new int[MicroMod.this.ibxm
                            .getMixBufferLength()];
                    final byte[] outBuf = new byte[mixBuf.length * 4];
                    AudioFormat audioFormat = null;
                    audioFormat = new AudioFormat(MicroMod.SAMPLE_RATE, 16, 2,
                            true, true);
                    try (SourceDataLine audioLine = AudioSystem
                            .getSourceDataLine(audioFormat)) {
                        audioLine.open();
                        audioLine.start();
                        while (MicroMod.this.playing) {
                            final int count = MicroMod.this.getAudio(mixBuf);
                            int outIdx = 0;
                            for (int mixIdx = 0, mixEnd = count
                                    * 2; mixIdx < mixEnd; mixIdx++) {
                                int ampl = mixBuf[mixIdx];
                                if (ampl > 32767) {
                                    ampl = 32767;
                                }
                                if (ampl < -32768) {
                                    ampl = -32768;
                                }
                                outBuf[outIdx++] = (byte) (ampl >> 8);
                                outBuf[outIdx++] = (byte) ampl;
                            }
                            audioLine.write(outBuf, 0, outIdx);
                        }
                        audioLine.drain();
                    } catch (final Exception e) {
                        // Ignore
                    }
                }
            });
            this.playThread.start();
        }
    }

    public synchronized void stopModule() {
        this.playing = false;
        try {
            if (this.playThread != null) {
                this.playThread.join();
            }
        } catch (final InterruptedException e) {
        }
    }

    synchronized int getAudio(final int[] mixBuf) {
        final int count = this.ibxm.getAudio(mixBuf);
        return count;
    }
}
