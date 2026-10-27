package dev.simlab.server;

import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationInput;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A simulation whose frame and x-ray are fixed lists of floats, and which records the calls it gets */
public final class FakeSimulation implements Simulation {

    private final float[] frame;
    private final List<String> calls = new ArrayList<>();
    private float[] xray = new float[0];
    private double time;
    private @Nullable RuntimeException failure;

    public FakeSimulation(float... frame) {
        this.frame = frame.clone();
    }

    /** calls of {@link #apply}, {@link #step} and {@link #writeXray} in their order, like {@code "step 0.5"} */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    public void setXray(float... xray) {
        this.xray = xray.clone();
    }

    /** every following step throws the given exception */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public void apply(SimulationInput input) {
        calls.add("apply " + input);
    }

    @Override
    public void step(double dt) {
        if (failure != null) {
            throw failure;
        }
        calls.add("step " + dt);
        time += dt;
    }

    @Override
    public double time() {
        return time;
    }

    @Override
    public void writeFrame(FloatBuffer frame) {
        frame.put(this.frame);
    }

    @Override
    public void writeXray(FloatBuffer frame) {
        calls.add("writeXray");
        frame.put(xray);
    }
}
