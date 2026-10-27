package dev.simlab.server.room;

import static org.assertj.core.api.Assertions.assertThat;

import dev.simlab.core.Deselect;
import dev.simlab.core.Drag;
import dev.simlab.core.Grab;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Release;
import dev.simlab.core.Select;
import org.junit.jupiter.api.Test;

class HeldVerticesTest {

    private final HeldVertices heldVertices = new HeldVertices();
    private final Viewer viewer = new RecordingViewer();

    @Test
    void releasesTheVerticesThatAViewerStillHolds() {
        heldVertices.track(viewer, new Grab(7, 0, 1, 0));
        heldVertices.track(viewer, new Drag(7, 0, 2, 0));
        heldVertices.track(viewer, new Grab(3, 0, 1, 0));
        heldVertices.track(viewer, new Grab(5, 0, 1, 0));
        heldVertices.track(viewer, new Release(5));

        assertThat(heldVertices.releaseAll(viewer)).containsExactly(new Release(3), new Release(7));
        assertThat(heldVertices.releaseAll(viewer)).as("after forgetting them").isEmpty();
    }

    @Test
    void keepsTheVerticesOfEveryViewerApart() {
        var other = new RecordingViewer();
        heldVertices.track(viewer, new Grab(1, 0, 1, 0));
        heldVertices.track(other, new Grab(2, 0, 1, 0));
        heldVertices.track(other, new Release(1));

        assertThat(heldVertices.releaseAll(viewer)).containsExactly(new Release(1));
        assertThat(heldVertices.releaseAll(other)).containsExactly(new Release(2));
    }

    @Test
    void ignoresOtherInputs() {
        heldVertices.track(viewer, new ParameterChange("wind", 3));
        heldVertices.track(viewer, new Drag(4, 0, 1, 0));
        heldVertices.track(viewer, new Release(4));
        heldVertices.track(viewer, new Select(4));
        heldVertices.track(viewer, new Deselect());

        assertThat(heldVertices.releaseAll(viewer)).isEmpty();
    }
}
