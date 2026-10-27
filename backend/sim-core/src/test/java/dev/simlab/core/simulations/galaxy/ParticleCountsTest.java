package dev.simlab.core.simulations.galaxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParticleCountsTest {

    @Test
    void countsTheStarsOfDiskAndBulgeAndAllParticles() {
        var counts = new ParticleCounts(8, 2, 5);

        assertThat(counts.stars()).isEqualTo(10);
        assertThat(counts.total()).isEqualTo(15);
    }

    @ParameterizedTest
    @CsvSource({"0, 1, 1", "1, 0, 1", "1, 1, 0", "-1, 1, 1"})
    void requiresParticlesInEveryPartOfTheGalaxy(int disk, int bulge, int halo) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ParticleCounts(disk, bulge, halo));
    }
}
