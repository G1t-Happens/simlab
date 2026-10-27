package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PointCloudTest {

    private static final Color BLUE = Color.rgb(0x0000ff);
    private static final Color RED = Color.rgb(0xff0000);

    @Test
    void takesUpThreeFloatsPerPoint() {
        assertThat(new PointCloud(1_000, BLUE, RED, 32, false, true).frameFloats())
                .isEqualTo(3_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void needsAtLeastOnePoint(int count) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PointCloud(count, BLUE, RED, 0, false, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 32})
    void drawsNoTrailsOrTrailsBetweenTwoFramesAtLeast(int trailLength) {
        assertThat(new PointCloud(1, BLUE, RED, trailLength, false, false).trailLength())
                .isEqualTo(trailLength);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void rejectsTrailsThatConnectNoTwoFrames(int trailLength) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PointCloud(1, BLUE, RED, trailLength, true, false));
    }
}
