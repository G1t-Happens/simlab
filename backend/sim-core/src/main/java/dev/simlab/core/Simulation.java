package dev.simlab.core;

import java.nio.FloatBuffer;

/**
 * A running simulation.
 *
 * <p>Not thread-safe: all calls have to come from one thread at a time, with a happens-before relation
 * between them like a single-threaded scheduler guarantees
 */
public interface Simulation {

    /**
     * Applies an input before the next step
     *
     * @param input validated against the {@link SimulationDefinition} of this simulation
     */
    void apply(SimulationInput input);

    /**
     * Advances the simulation as if a span of wall-clock time passed, how much simulated time that means is up
     * to the simulation. For slow motion callers pass a share of the time that passed
     *
     * @param dt the span in seconds, not negative
     */
    void step(double dt);

    /** Simulated time in the {@linkplain SimulationDefinition#timeUnit() unit of time} */
    double time();

    /**
     * Writes the current state as a frame starting at the buffer's position: the floats of one object of the
     * {@linkplain SimulationDefinition#scene() scene} after another.
     *
     * <p>Frames can follow each other without a step in between (e.g. while paused), so the readouts of a
     * frame also reflect inputs applied since the last step, like a point that was selected
     *
     * @param frame has room for the {@linkplain dev.simlab.core.scene.Scene#frameFloats() floats of the frame}
     */
    void writeFrame(FloatBuffer frame);

    /**
     * Writes what the x-ray shows starting at the buffer's position: the floats of one {@linkplain
     * dev.simlab.core.scene.Scene#xray() object of the x-ray} after another. Callers write it after the frame
     * and only while the x-ray is shown, so a simulation computes it only while somebody looks.
     *
     * <p>Writes nothing by default, as fits a scene without x-ray
     *
     * @param frame has room for the {@linkplain dev.simlab.core.scene.Scene#xrayFloats() floats of the x-ray}
     */
    default void writeXray(FloatBuffer frame) {
        // a scene without x-ray takes no floats for it
    }
}
