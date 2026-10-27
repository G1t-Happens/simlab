package dev.simlab.server.room;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.SimulationInput;
import dev.simlab.server.protocol.MessageEncoder;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simulation that runs at a fixed tick rate and streams its state to the viewers in the room.
 *
 * <p>The simulation starts with the first viewer and the room closes when the last one leaves. Every viewer
 * first gets the scene of the simulation, then a frame per tick. Only the tick loop touches the simulation:
 * the scheduler never runs it concurrently with itself and each run happens-before the next. Other threads
 * hand their inputs over through a queue.
 *
 * <p>The room's clock plays the simulation in real time (or slower, in slow motion) and can stand still. A
 * paused room still applies inputs and sends a frame per tick, so a point selected in the meantime shows its
 * values, and it advances the simulation tick by tick on request.
 *
 * <p>The frames show the x-ray of the simulation while somebody asks for it, and only then does the
 * simulation compute what the x-ray shows
 */
public final class SimulationRoom {

    private static final Logger logger = LoggerFactory.getLogger(SimulationRoom.class);

    private final UUID id = UUID.randomUUID();
    private final SimulationDefinition definition;
    private final Simulation simulation;
    private final ScheduledExecutorService scheduler;
    private final double tickSeconds;
    private final long tickNanos;
    private final Consumer<SimulationRoom> onClose;
    private final Queue<SimulationInput> inputs = new ConcurrentLinkedQueue<>();

    /** encoded once, the scene doesn't change while the simulation runs */
    private final ByteBuffer scene;

    private final int frameFloats;

    private final int xrayFloats;

    /** changed while holding the lock, read by the tick loop without it */
    private final Set<Viewer> viewers = new CopyOnWriteArraySet<>();

    @GuardedBy("this") private final HeldVertices heldVertices = new HeldVertices();

    /** only the tick loop accesses it */
    private long tick;

    /** whether the clock stands still. Any thread sets the clock, the tick loop reads it */
    private volatile boolean paused;

    /** share of a tick's wall-clock time by which the simulation advances, below 1 in slow motion */
    private volatile double speed = 1;

    /** whether the next tick advances the simulation although the clock stands still */
    private final AtomicBoolean stepRequested = new AtomicBoolean();

    /** any thread sets it, the tick loop reads it */
    private volatile boolean xrayShown;

    @GuardedBy("this") private @Nullable ScheduledFuture<?> tickLoop;

    @GuardedBy("this") private @Nullable ScheduledFuture<?> joinDeadline;

    @GuardedBy("this") private boolean closed;

    /**
     * Creates a room that waits for its first viewer
     *
     * @param simulation nothing else touches it from now on
     * @param onClose called once the room is closed
     */
    SimulationRoom(
            SimulationDefinition definition,
            Simulation simulation,
            ScheduledExecutorService scheduler,
            int tickRate,
            Consumer<SimulationRoom> onClose) {
        this.definition = definition;
        this.simulation = simulation;
        this.scheduler = scheduler;
        this.tickSeconds = 1.0 / tickRate;
        this.tickNanos = TimeUnit.SECONDS.toNanos(1) / tickRate;
        this.onClose = onClose;
        this.scene = MessageEncoder.encodeScene(definition.scene());
        this.frameFloats = definition.scene().frameFloats();
        this.xrayFloats = definition.scene().xrayFloats();
    }

    public UUID id() {
        return id;
    }

    public SimulationDefinition definition() {
        return definition;
    }

    /**
     * Queues an input of a viewer for the next tick, unless the viewer isn't in the room. The vertices the
     * viewer grabs get released when it leaves
     *
     * @param input validated against the definition of the room
     */
    public synchronized void submit(Viewer viewer, SimulationInput input) {
        if (viewers.contains(viewer)) {
            heldVertices.track(viewer, input);
            inputs.add(input);
        }
    }

    /**
     * Adds a viewer, which gets the scene right away. The first viewer starts the simulation
     *
     * @return {@code false} if the room is closed already
     */
    public boolean join(Viewer viewer) {
        synchronized (this) {
            if (closed) {
                return false;
            }
        }
        // sent without holding the lock since the first message to a slow browser may take a while. The tick
        // loop only sends frames to viewers in the set, so the scene arrives first
        viewer.send(scene.duplicate());
        synchronized (this) {
            if (closed) {
                return false;
            }
            viewers.add(viewer);
            if (joinDeadline != null) {
                joinDeadline.cancel(false);
            }
            if (tickLoop == null) {
                tickLoop = scheduler.scheduleAtFixedRate(this::runTick, 0, tickNanos, TimeUnit.NANOSECONDS);
            }
        }
        logger.debug("Viewer joined room {}", id);
        return true;
    }

    /** Removes a viewer and releases the vertices it holds, the room closes when the last viewer leaves */
    public void leave(Viewer viewer) {
        List<Viewer> remaining;
        synchronized (this) {
            if (!viewers.remove(viewer)) {
                return;
            }
            inputs.addAll(heldVertices.releaseAll(viewer));
            if (!viewers.isEmpty()) {
                return;
            }
            remaining = markClosed();
        }
        finishClosing(remaining, CloseReason.ABANDONED);
    }

    /** Stops the clock of the room: the simulation stands still but inputs still apply */
    public void pause() {
        paused = true;
    }

    public void play() {
        paused = false;
    }

    /** Lets the next tick advance the simulation although the room is paused, does nothing while it plays */
    public void step() {
        stepRequested.set(true);
    }

    /**
     * Plays the simulation at a share of real time: every tick advances it by that share of a tick's
     * wall-clock time
     *
     * @param speed above 0 and at most 1, below 1 in slow motion
     * @throws InvalidInputException if the share lies outside this range
     */
    public void setSpeed(double speed) {
        if (!(speed > 0 && speed <= 1)) {
            throw new InvalidInputException("The speed must lie above 0 and at most 1, but was " + speed);
        }
        this.speed = speed;
    }

    /**
     * Lets the frames show the x-ray of the simulation from the next tick on, or no longer
     *
     * @throws InvalidInputException if the simulation offers no x-ray
     */
    public void showXray(boolean shown) {
        if (xrayFloats == 0) {
            throw new InvalidInputException("%s offers no x-ray".formatted(definition.name()));
        }
        xrayShown = shown;
    }

    public synchronized void closeUnlessJoinedWithin(Duration timeout) {
        if (!closed && tickLoop == null) {
            joinDeadline = scheduler.schedule(this::closeUnlessJoined, timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    /** Closes the room and disconnects its viewers, does nothing if it's closed already */
    public void close(CloseReason reason) {
        List<Viewer> remaining;
        synchronized (this) {
            if (closed) {
                return;
            }
            remaining = markClosed();
        }
        finishClosing(remaining, reason);
    }

    /**
     * Applies the queued inputs, advances the simulation by one tick unless the room is paused, and sends the
     * new state to every viewer, with the x-ray while it's shown
     */
    void tick() {
        for (SimulationInput input = inputs.poll(); input != null; input = inputs.poll()) {
            simulation.apply(input);
        }
        // a step that was asked for while the simulation played passes with this tick
        boolean stepping = stepRequested.getAndSet(false);
        if (!paused || stepping) {
            simulation.step(tickSeconds * speed);
        }
        tick++;
        ByteBuffer frame = MessageEncoder.encodeFrame(tick, simulation, frameFloats, xrayShown ? xrayFloats : 0);
        for (Viewer viewer : viewers) {
            viewer.send(frame.duplicate());
        }
    }

    private void runTick() {
        try {
            tick();
        } catch (RuntimeException e) {
            logger.error("Simulation in room {} failed", id, e);
            close(CloseReason.FAILED);
        }
    }

    private void closeUnlessJoined() {
        List<Viewer> remaining;
        synchronized (this) {
            if (closed || tickLoop != null) {
                return;
            }
            remaining = markClosed();
        }
        finishClosing(remaining, CloseReason.NOT_JOINED);
    }

    @GuardedBy("this") private List<Viewer> markClosed() {
        closed = true;
        if (tickLoop != null) {
            tickLoop.cancel(false);
        }
        if (joinDeadline != null) {
            joinDeadline.cancel(false);
        }
        List<Viewer> remaining = List.copyOf(viewers);
        viewers.clear();
        return remaining;
    }

    private void finishClosing(List<Viewer> remaining, CloseReason reason) {
        remaining.forEach(viewer -> viewer.disconnect(reason));
        onClose.accept(this);
        logger.info("Closed room {}: {}", id, reason);
    }
}
