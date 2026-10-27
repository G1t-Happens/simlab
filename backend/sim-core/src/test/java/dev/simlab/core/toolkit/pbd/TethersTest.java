package dev.simlab.core.toolkit.pbd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TethersTest {

    @Test
    void pullsAParticleBackThatIsTooFarFromItsAnchor() {
        double[] position = {1, 1, 1, 1, 1, 6};
        var tethers = new Tethers(1);
        tethers.add(1, 0, 2);

        tethers.project(position, new double[] {0, 1});

        assertThat(position).containsExactly(new double[] {1, 1, 1, 1, 1, 3}, within(1e-12));
    }

    @Test
    void letsParticlesComeCloserToTheirAnchor() {
        double[] position = {0, 0, 0, 0.5, 0, 0};
        var tethers = new Tethers(1);
        tethers.add(1, 0, 2);

        tethers.project(position, new double[] {0, 1});

        assertThat(position).containsExactly(0, 0, 0, 0.5, 0, 0);
    }

    @Test
    void leavesHeldParticlesAlone() {
        double[] position = {0, 0, 0, 5, 0, 0};
        var tethers = new Tethers(1);
        tethers.add(1, 0, 2);

        tethers.project(position, new double[] {0, 0});

        assertThat(position[3]).isEqualTo(5);
    }

    @Test
    void holdsAsManyTethersAsItsCapacity() {
        var tethers = new Tethers(1);
        tethers.add(1, 0, 1);

        assertThat(tethers.size()).isOne();
        assertThatIllegalStateException().isThrownBy(() -> tethers.add(2, 0, 1));
    }
}
