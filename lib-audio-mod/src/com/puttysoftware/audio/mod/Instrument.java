package com.puttysoftware.audio.mod;

public class Instrument {
    public String name = ""; //$NON-NLS-1$
    public int numSamples = 1;
    public int vibratoType = 0, vibratoSweep = 0, vibratoDepth = 0,
            vibratoRate = 0;
    public int volumeFadeOut = 0;
    public Envelope volumeEnvelope = new Envelope();
    public Envelope panningEnvelope = new Envelope();
    public int[] keyToSample = new int[97];
    public Sample[] samples = new Sample[] { new Sample() };

    public void toStringBuffer(final StringBuffer out) {
        out.append("Name: " + this.name + '\n'); //$NON-NLS-1$
        if (this.numSamples > 0) {
            out.append("Num Samples: " + this.numSamples + '\n'); //$NON-NLS-1$
            out.append("Vibrato Type: " + this.vibratoType + '\n'); //$NON-NLS-1$
            out.append("Vibrato Sweep: " + this.vibratoSweep + '\n'); //$NON-NLS-1$
            out.append("Vibrato Depth: " + this.vibratoDepth + '\n'); //$NON-NLS-1$
            out.append("Vibrato Rate: " + this.vibratoRate + '\n'); //$NON-NLS-1$
            out.append("Volume Fade Out: " + this.volumeFadeOut + '\n'); //$NON-NLS-1$
            out.append("Volume Envelope:\n"); //$NON-NLS-1$
            this.volumeEnvelope.toStringBuffer(out);
            out.append("Panning Envelope:\n"); //$NON-NLS-1$
            this.panningEnvelope.toStringBuffer(out);
            for (int samIdx = 0; samIdx < this.numSamples; samIdx++) {
                out.append("Sample " + samIdx + ":\n"); //$NON-NLS-1$ //$NON-NLS-2$
                this.samples[samIdx].toStringBuffer(out);
            }
            out.append("Key To Sample: "); //$NON-NLS-1$
            for (int keyIdx = 1; keyIdx < 97; keyIdx++) {
                out.append(this.keyToSample[keyIdx] + ", "); //$NON-NLS-1$
            }
            out.append('\n');
        }
    }
}
