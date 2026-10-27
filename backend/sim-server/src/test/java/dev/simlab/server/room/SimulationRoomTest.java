package dev.simlab.server.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.server.FakeDefinition;
import dev.simlab.server.FakeSimulation;
import dev.simlab.server.protocol.MessageEncoder;
import dev.simlab.server.protocol.MessageType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class SimulationRoomTest {

    private static final int TICK_RATE = 30;

    private final ScheduledExecutorService scheduler = mock();
    private final ScheduledFuture<?> tickLoop = mock();
    private final ScheduledFuture<?> joinDeadline = mock();
    private final FakeSimulation simulation = new FakeSimulation(1.0f, 2.0f, 3.0f);
    private final FakeDefinition definition = new FakeDefinition(simulation, 1);
    private final List<SimulationRoom> closedRooms = new ArrayList<>();
    private final SimulationRoom room =
            new SimulationRoom(definition, simulation, scheduler, TICK_RATE, closedRooms::add);

    @BeforeEach
    void stubScheduler() {
        doReturn(tickLoop).when(scheduler).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        doReturn(joinDeadline).when(scheduler).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void startsTheTickLoopWhenTheFirstViewerJoins() {
        room.join(new RecordingViewer());
        room.join(new RecordingViewer());

        verify(scheduler).scheduleAtFixedRate(any(), eq(0L), eq(33_333_333L), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    void sendsTheSceneToEveryViewerAsItJoins() {
        var first = new RecordingViewer();
        var second = new RecordingViewer();
        room.join(first);
        room.tick();

        room.join(second);

        ByteBuffer scene = second.messages().getFirst();
        assertThat(scene).isEqualTo(MessageEncoder.encodeScene(definition.scene()));
        assertThat(first.messages()).hasSize(2).first().isEqualTo(scene);
    }

    @Test
    void appliesTheQueuedInputsBeforeTheNextStep() {
        var viewer = new RecordingViewer();
        room.join(viewer);
        room.submit(viewer, new ParameterChange("wind", 2));
        room.submit(viewer, new Grab(1, 0, 1, 0));

        room.tick();

        assertThat(simulation.calls())
                .containsExactly(
                        "apply ParameterChange[name=wind, value=2.0]",
                        "apply Grab[vertex=1, x=0.0, y=1.0, z=0.0]",
                        "step " + 1.0 / TICK_RATE);
    }

    @Test
    void dropsTheInputsOfViewersThatAreNotInTheRoom() {
        room.submit(new RecordingViewer(), new ParameterChange("wind", 2));

        room.tick();

        assertThat(simulation.calls()).containsExactly("step " + 1.0 / TICK_RATE);
    }

    @Test
    void releasesTheVerticesThatALeavingViewerHolds() {
        var leaving = new RecordingViewer();
        room.join(leaving);
        room.join(new RecordingViewer());
        room.submit(leaving, new Grab(1, 0, 1, 0));
        room.tick();

        room.leave(leaving);
        room.tick();

        assertThat(simulation.calls()).contains("apply Release[vertex=1]");
    }

    @Test
    void sendsTheEncodedStateToEveryViewer() {
        var first = new RecordingViewer();
        var second = new RecordingViewer();
        room.join(first);
        room.join(second);

        room.tick();
        room.tick();

        ByteBuffer frame = first.messages().getLast().order(ByteOrder.LITTLE_ENDIAN);
        assertThat(frame.get(0)).as("message type").isEqualTo((byte) MessageType.FRAME.id());
        assertThat(frame.getInt(4)).as("tick").isEqualTo(2);
        assertThat(frame.getInt(12)).as("payload length of three floats").isEqualTo(12);
        assertThat(first.messages()).as("scene and two frames").hasSize(3);
        assertThat(second.messages()).hasSize(3).last().isEqualTo(frame);
    }

    @Test
    void appliesInputsAndSendsFramesButLetsTheSimulationStandStillWhilePaused() {
        var viewer = new RecordingViewer();
        room.join(viewer);
        room.pause();
        room.submit(viewer, new ParameterChange("wind", 2));

        room.tick();

        assertThat(simulation.calls()).containsExactly("apply ParameterChange[name=wind, value=2.0]");
        assertThat(viewer.messages()).as("scene and a frame").hasSize(2);
    }

    @Test
    void playsTheSimulationAgainAfterAPause() {
        room.join(new RecordingViewer());
        room.pause();
        room.tick();

        room.play();
        room.tick();

        assertThat(simulation.calls()).containsExactly("step " + 1.0 / TICK_RATE);
    }

    @Test
    void advancesAPausedSimulationByOneTickPerStep() {
        room.join(new RecordingViewer());
        room.pause();

        room.step();
        room.tick();
        room.tick();

        assertThat(simulation.calls()).containsExactly("step " + 1.0 / TICK_RATE);
    }

    @Test
    void forgetsAStepThatWasAskedForWhileTheSimulationPlayed() {
        room.join(new RecordingViewer());
        room.step();
        room.tick();

        room.pause();
        room.tick();

        assertThat(simulation.calls()).containsExactly("step " + 1.0 / TICK_RATE);
    }

    @Test
    void advancesTheSimulationByAShareOfEveryTickInSlowMotion() {
        room.join(new RecordingViewer());

        room.setSpeed(0.25);
        room.tick();

        assertThat(simulation.calls()).containsExactly("step " + 1.0 / TICK_RATE * 0.25);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.5, 1.5, Double.NaN})
    void rejectsSpeedsBeyondRealTimeOrWithoutProgress(double speed) {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> room.setSpeed(speed))
                .withMessage("The speed must lie above 0 and at most 1, but was " + speed);
    }

    @Test
    void showsTheXrayInTheFramesWhileAskedTo() {
        simulation.setXray(4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
        var withXray = new SimulationRoom(
                new FakeDefinition("fake", simulation, 1, 1), simulation, scheduler, TICK_RATE, closedRooms::add);
        var viewer = new RecordingViewer();
        withXray.join(viewer);

        withXray.showXray(true);
        withXray.tick();
        withXray.showXray(false);
        withXray.tick();

        List<ByteBuffer> frames = viewer.messages().subList(1, 3);
        assertThat(frames.getFirst().order(ByteOrder.LITTLE_ENDIAN).getInt(12))
                .as("payload length of the three floats of the frame and the five of the x-ray")
                .isEqualTo(4 * (3 + 5));
        assertThat(frames.getLast().order(ByteOrder.LITTLE_ENDIAN).getInt(12))
                .as("payload length of the frame alone")
                .isEqualTo(4 * 3);
        assertThat(simulation.calls()).as("x-ray computed once").containsOnlyOnce("writeXray");
    }

    @Test
    void rejectsShowingTheXrayOfASimulationWithoutXray() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> room.showXray(true))
                .withMessage("Fake simulation offers no x-ray");
    }

    @Test
    void closesWhenTheLastViewerLeaves() {
        var first = new RecordingViewer();
        var second = new RecordingViewer();
        room.join(first);
        room.join(second);

        room.leave(first);
        assertThat(closedRooms).isEmpty();

        room.leave(second);
        assertThat(closedRooms).containsExactly(room);
        verify(tickLoop).cancel(false);
    }

    @Test
    void disconnectsItsViewersAndTurnsNewOnesAwayWhenClosed() {
        var viewer = new RecordingViewer();
        room.join(viewer);
        var latecomer = new RecordingViewer();

        room.close(CloseReason.SHUTDOWN);

        assertThat(viewer.disconnectReason()).isEqualTo(CloseReason.SHUTDOWN);
        assertThat(room.join(latecomer)).as("joining a closed room").isFalse();
        assertThat(latecomer.messages()).isEmpty();
        assertThat(closedRooms).containsExactly(room);
    }

    @Test
    void closesWhenTheSimulationFails() {
        var viewer = new RecordingViewer();
        room.join(viewer);
        simulation.failWith(new IllegalStateException("Simulation diverged"));

        tickLoopTask().run();

        assertThat(viewer.disconnectReason()).isEqualTo(CloseReason.FAILED);
        assertThat(closedRooms).containsExactly(room);
    }

    @Test
    void closesUnlessAViewerJoinsInTime() {
        room.closeUnlessJoinedWithin(Duration.ofSeconds(30));

        joinDeadlineTask(Duration.ofSeconds(30)).run();

        assertThat(closedRooms).containsExactly(room);
    }

    @Test
    void staysOpenIfAViewerJoinsInTime() {
        room.closeUnlessJoinedWithin(Duration.ofSeconds(30));
        Runnable deadline = joinDeadlineTask(Duration.ofSeconds(30));

        room.join(new RecordingViewer());
        deadline.run();

        verify(joinDeadline).cancel(false);
        assertThat(closedRooms).isEmpty();
    }

    private Runnable tickLoopTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), any());
        return task.getValue();
    }

    private Runnable joinDeadlineTask(Duration timeout) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), eq(timeout.toNanos()), eq(TimeUnit.NANOSECONDS));
        return task.getValue();
    }
}
