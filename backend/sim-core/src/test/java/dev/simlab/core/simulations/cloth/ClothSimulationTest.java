package dev.simlab.core.simulations.cloth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import dev.simlab.core.Drag;
import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Release;
import dev.simlab.core.Select;
import java.nio.FloatBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ClothSimulationTest {

    private static final int SIZE = 48;
    private static final double TICK = 1.0 / 30;
    private static final double SPACING = ClothSimulation.WIDTH / (SIZE - 1);
    private static final double GRAVITY = 9.81;

    /** middle of the free edge, opposite the pinned corners */
    private static final int HEM = SIZE * (SIZE - 1) + SIZE / 2;

    private static final int VERTEX_FLOATS = 3 * SIZE * SIZE;

    /** vertices, center and radius of the sphere, then the wind readouts */
    private static final int FRAME_FLOATS = VERTEX_FLOATS + 4 + 4;

    private static final int STRETCH_CONSTRAINTS = 2 * SIZE * (SIZE - 1);

    /** count, then start, end and strain of each constraint */
    private static final int XRAY_FLOATS = 1 + 7 * STRETCH_CONSTRAINTS;

    @Test
    void showsTheConstraintsAlongRowsAndColumnsWithoutStrainWhileTheClothLiesFlat() {
        float[] xray = xray(cloth(0, 0));

        assertThat(xray[0]).as("number of constraints").isEqualTo(STRETCH_CONSTRAINTS);
        assertThat(Arrays.copyOfRange(xray, 1, 7))
                .as("the first constraint, between the first two particles of row 0")
                .containsExactly(new float[] {-1, 2.5f, 0, (float) (SPACING - 1), 2.5f, 0}, within(1e-6f));
        for (int constraint = 0; constraint < STRETCH_CONSTRAINTS; constraint++) {
            assertThat(xray[7 * constraint + 7]).as("strain").isCloseTo(0, within(1e-4f));
        }
    }

    @Test
    void showsTheStrainOfEveryConstraintAlongRowsAndColumnsInPercent() {
        ClothSimulation cloth = cloth(2, 1);
        run(cloth, 3);

        float[] xray = xray(cloth);

        double[] stretches = stretches(frame(cloth));
        for (int constraint = 0; constraint < STRETCH_CONSTRAINTS; constraint++) {
            assertThat(Math.abs(xray[7 * constraint + 7]) / 100.0).isCloseTo(stretches[constraint], within(1e-4));
        }
        assertThat(largestStretch(frame(cloth)))
                .as("strain of a constraint that gravity stretches")
                .isPositive();
    }

    @Test
    void startsHorizontalAtTheHeightOfItsPinsWithTheSphereAfterTheVertices() {
        float[] frame = frame(cloth(0, 0));

        assertThat(vertex(frame, 0)).containsExactly(-1f, 2.5f, 0f);
        assertThat(vertex(frame, SIZE - 1)).containsExactly(1f, 2.5f, 0f);
        assertThat(vertex(frame, SIZE * SIZE - 1)).containsExactly(1f, 2.5f, 2f);
        assertThat(Arrays.copyOfRange(frame, VERTEX_FLOATS, VERTEX_FLOATS + 4))
                .containsExactly(0.3f, 0.85f, 0.8f, 0.4f);
    }

    @Test
    void reportsTheGustyWindThatBlowsAtTheTimeOfTheFrame() {
        ClothSimulation cloth = cloth(2, 1);
        run(cloth, 1);

        float[] wind = readouts(frame(cloth));

        float gustNoise = wind[0];
        float turnNoise = wind[2];
        assertThat(gustNoise).isBetween(-1f, 1f);
        assertThat(wind[1]).as("speed").isCloseTo(Math.max(0, 2 + gustNoise), within(1e-6f));
        assertThat(turnNoise).isBetween(-1f, 1f);
        assertThat(wind[3]).as("turn").isCloseTo((float) (ClothSimulation.MAX_TURN * turnNoise), within(1e-6f));
    }

    @Test
    void reportsASteadyWindWithoutGusts() {
        ClothSimulation cloth = cloth(3, 0);
        run(cloth, 1);

        assertThat(readouts(frame(cloth))[1]).as("speed").isEqualTo(3);
    }

    @Test
    void fallsAndHangsStraightDownFromItsPinsWithoutWind() {
        ClothSimulation cloth = cloth(0, 0);

        run(cloth, 10);

        float[] frame = frame(cloth);
        assertThat(lowestY(frame)).isCloseTo(ClothSimulation.PIN_HEIGHT - ClothSimulation.WIDTH, within(0.02));
        assertThat(meanZ(frame)).isCloseTo(0, within(0.05));
    }

    @Test
    void barelyStretchesWhileItHangsStill() {
        ClothSimulation cloth = cloth(0, 0);

        run(cloth, 10);

        assertThat(largestStretch(frame(cloth))).isLessThan(0.01);
    }

    @Test
    void keepsTheStretchSmallInGustyWind() {
        ClothSimulation cloth = cloth(2, 1);
        run(cloth, 1);

        for (int tick = 0; tick < 270; tick++) {
            cloth.step(TICK);

            assertThat(meanStretch(frame(cloth)))
                    .as("mean stretch at tick %d", tick)
                    .isLessThan(0.01);
        }
    }

    @Test
    void keepsThePinnedCornersWhereTheyAre() {
        ClothSimulation cloth = cloth(4, 2);
        cloth.apply(new Grab(0, 0, 3, 1));
        cloth.apply(new Drag(SIZE - 1, 0, 3, 1));

        run(cloth, 5);

        float[] frame = frame(cloth);
        assertThat(cloth.isPinned(0)).isTrue();
        assertThat(cloth.isPinned(SIZE - 1)).isTrue();
        assertThat(vertex(frame, 0)).containsExactly(-1f, 2.5f, 0f);
        assertThat(vertex(frame, SIZE - 1)).containsExactly(1f, 2.5f, 0f);
    }

    @Test
    void drapesOverTheSphereWithoutEnteringIt() {
        ClothSimulation cloth = cloth(2, 1);
        double smallestGap = Double.MAX_VALUE;

        for (int tick = 0; tick < 300; tick++) {
            cloth.step(TICK);
            smallestGap = Math.min(smallestGap, smallestGapToTheSphere(frame(cloth)));
        }

        assertThat(smallestGap).isCloseTo(ClothSimulation.COLLISION_MARGIN, within(1e-5));
    }

    @Test
    void blowsDownwind() {
        ClothSimulation cloth = cloth(4, 0);

        run(cloth, 5);

        assertThat(meanZ(frame(cloth))).isGreaterThan(0.5);
    }

    @Test
    void floatsWhereItIsWithoutGravityAndWind() {
        ClothSimulation cloth = new ClothSimulation(SIZE, 1, 0, 0, 0);
        float[] start = vertices(frame(cloth));

        run(cloth, 1);

        assertThat(vertices(frame(cloth))).containsExactly(start, within(1e-6f));
    }

    @Test
    void changesItsParameters() {
        ClothSimulation cloth = new ClothSimulation(SIZE, 1, 0, 0, 0);

        cloth.apply(new ParameterChange("gravity", GRAVITY));
        run(cloth, 2);

        assertThat(lowestY(frame(cloth))).isLessThan(1);
    }

    @Test
    void movesAGrabbedVertexWhereTheHandDragsIt() {
        ClothSimulation cloth = cloth(0, 0);
        run(cloth, 3);
        float[] start = vertex(frame(cloth), HEM);

        cloth.apply(new Grab(HEM, start[0], start[1], start[2]));
        cloth.apply(new Drag(HEM, 0, 1.5, -1));
        run(cloth, 1);

        assertThat(vertex(frame(cloth), HEM)).containsExactly(new float[] {0, 1.5f, -1}, within(1e-6f));
    }

    @Test
    void movesAHandNoFasterThanItsTopSpeed() {
        ClothSimulation cloth = cloth(0, 0);
        run(cloth, 3);
        float[] start = vertex(frame(cloth), HEM);

        cloth.apply(new Grab(HEM, start[0], start[1], start[2]));
        cloth.apply(new Drag(HEM, start[0], start[1] + 1, start[2] + 1));
        cloth.step(TICK);

        assertThat(distance(vertex(frame(cloth), HEM), start))
                .isCloseTo(ClothSimulation.MAX_HAND_SPEED * TICK, within(1e-5));
    }

    @Test
    void pullsAGrabbedVertexNoFartherThanTheClothReaches() {
        ClothSimulation cloth = cloth(0, 0);
        run(cloth, 3);
        float[] start = vertex(frame(cloth), HEM);

        cloth.apply(new Grab(HEM, start[0], start[1], start[2]));
        cloth.apply(new Drag(HEM, 10, -5, 10));
        run(cloth, 2);

        float[] frame = frame(cloth);
        for (int pin : new int[] {0, SIZE - 1}) {
            double reach = ClothSimulation.HAND_SLACK * SPACING * Math.hypot(SIZE - 1, SIZE / 2 - pin);
            assertThat(distance(vertex(frame, HEM), vertex(frame, pin)))
                    .as("distance to pin %d", pin)
                    .isLessThan(reach + 1e-4);
        }
        for (int neighbor : new int[] {HEM - 1, HEM + 1, HEM - SIZE}) {
            assertThat(distance(vertex(frame, HEM), vertex(frame, neighbor)))
                    .as("distance to neighbor %d", neighbor)
                    .isLessThan(SPACING + 1e-6);
        }
        // even pulled as far as it reaches the cloth only stretches a little
        assertThat(meanStretch(frame)).isLessThan(0.02);
    }

    @Test
    void keepsAGrabbedVertexOutOfTheSphere() {
        ClothSimulation cloth = cloth(0, 0);
        run(cloth, 3);

        cloth.apply(new Grab(HEM, ClothSimulation.SPHERE_X, ClothSimulation.SPHERE_Y, ClothSimulation.SPHERE_Z));
        run(cloth, 1);

        assertThat(gapToTheSphere(frame(cloth), HEM)).isCloseTo(ClothSimulation.COLLISION_MARGIN, within(1e-5));
    }

    @Test
    void letsAReleasedVertexFallAgain() {
        ClothSimulation cloth = cloth(0, 0);
        run(cloth, 3);
        cloth.apply(new Grab(HEM, 0, 3, 0));
        run(cloth, 1);

        cloth.apply(new Release(HEM));
        run(cloth, 1);

        assertThat(vertex(frame(cloth), HEM)[1]).isLessThan(2);
    }

    @Test
    void ignoresDragsAndReleasesOfVerticesThatNobodyHolds() {
        ClothSimulation untouched = cloth(2, 1);
        ClothSimulation touched = cloth(2, 1);

        touched.apply(new Drag(HEM, 0, 3, 0));
        touched.apply(new Release(HEM));
        run(untouched, 1);
        run(touched, 1);

        assertThat(frame(touched)).containsExactly(frame(untouched));
    }

    @Test
    void recoversFromBeingDraggedAsFarAsAHandReaches() {
        ClothSimulation cloth = cloth(2, 1);
        run(cloth, 2);
        float[] start = vertex(frame(cloth), HEM);
        cloth.apply(new Grab(HEM, start[0], start[1], start[2]));
        cloth.apply(new Drag(HEM, ClothSimulation.REACH, ClothSimulation.MAX_HEIGHT, ClothSimulation.REACH));
        run(cloth, 2);

        cloth.apply(new Release(HEM));
        run(cloth, 5);

        assertThat(meanStretch(frame(cloth))).isLessThan(0.01);
    }

    @Test
    void repeatsItselfForTheSameSeedOnly() {
        float[] first = frameAfter(new ClothSimulation(SIZE, 7, 2, 1, GRAVITY), 2);
        float[] again = frameAfter(new ClothSimulation(SIZE, 7, 2, 1, GRAVITY), 2);
        float[] other = frameAfter(new ClothSimulation(SIZE, 8, 2, 1, GRAVITY), 2);

        assertThat(again).containsExactly(first);
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    void advancesTheSimulatedTimeByTheWallClockTime() {
        ClothSimulation cloth = cloth(0, 0);

        cloth.step(TICK);
        cloth.step(TICK);
        cloth.step(0);

        assertThat(cloth.time()).isCloseTo(2 * TICK, within(1e-12));
    }

    @Test
    void rejectsUnknownParametersAndVertices() {
        ClothSimulation cloth = cloth(0, 0);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> cloth.apply(new ParameterChange("temperature", 20)));
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> cloth.apply(new Grab(SIZE * SIZE, 0, 1, 0)));
    }

    @Test
    void rejectsSelectingAParticle() {
        ClothSimulation cloth = cloth(0, 0);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> cloth.apply(new Select(0)))
                .withMessage("Particles of the cloth cannot be selected");
    }

    @Test
    void rejectsNegativeTimeSpans() {
        ClothSimulation cloth = cloth(0, 0);

        assertThatIllegalArgumentException().isThrownBy(() -> cloth.step(-1));
    }

    private static ClothSimulation cloth(double windSpeed, double gusts) {
        return new ClothSimulation(SIZE, 1, windSpeed, gusts, GRAVITY);
    }

    private static void run(ClothSimulation cloth, double seconds) {
        for (long tick = Math.round(seconds / TICK); tick > 0; tick--) {
            cloth.step(TICK);
        }
    }

    private static float[] frameAfter(ClothSimulation cloth, double seconds) {
        run(cloth, seconds);
        return frame(cloth);
    }

    private static float[] frame(ClothSimulation cloth) {
        FloatBuffer frame = FloatBuffer.allocate(FRAME_FLOATS);
        cloth.writeFrame(frame);
        assertThat(frame.hasRemaining()).as("floats missing from the frame").isFalse();
        return frame.array();
    }

    private static float[] xray(ClothSimulation cloth) {
        FloatBuffer xray = FloatBuffer.allocate(XRAY_FLOATS);
        cloth.writeXray(xray);
        assertThat(xray.hasRemaining()).as("floats missing from the x-ray").isFalse();
        return xray.array();
    }

    private static float[] vertex(float[] frame, int vertex) {
        return Arrays.copyOfRange(frame, 3 * vertex, 3 * vertex + 3);
    }

    private static float[] vertices(float[] frame) {
        return Arrays.copyOf(frame, VERTEX_FLOATS);
    }

    private static float[] readouts(float[] frame) {
        return Arrays.copyOfRange(frame, VERTEX_FLOATS + 4, FRAME_FLOATS);
    }

    private static double lowestY(float[] frame) {
        double lowest = Double.MAX_VALUE;
        for (int vertex = 0; vertex < SIZE * SIZE; vertex++) {
            lowest = Math.min(lowest, frame[3 * vertex + 1]);
        }
        return lowest;
    }

    private static double meanZ(float[] frame) {
        double sum = 0;
        for (int vertex = 0; vertex < SIZE * SIZE; vertex++) {
            sum += frame[3 * vertex + 2];
        }
        return sum / (SIZE * SIZE);
    }

    private static double gapToTheSphere(float[] frame, int vertex) {
        float[] center = {
            (float) ClothSimulation.SPHERE_X, (float) ClothSimulation.SPHERE_Y, (float) ClothSimulation.SPHERE_Z
        };
        return distance(vertex(frame, vertex), center) - ClothSimulation.SPHERE_RADIUS;
    }

    private static double smallestGapToTheSphere(float[] frame) {
        double smallest = Double.MAX_VALUE;
        for (int vertex = 0; vertex < SIZE * SIZE; vertex++) {
            smallest = Math.min(smallest, gapToTheSphere(frame, vertex));
        }
        return smallest;
    }

    /** relative deviation of every cell edge from its length */
    private static double[] stretches(float[] frame) {
        double[] stretches = new double[2 * SIZE * (SIZE - 1)];
        int edge = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int vertex = row * SIZE + column;
                if (column < SIZE - 1) {
                    stretches[edge++] =
                            Math.abs(distance(vertex(frame, vertex), vertex(frame, vertex + 1)) / SPACING - 1);
                }
                if (row < SIZE - 1) {
                    stretches[edge++] =
                            Math.abs(distance(vertex(frame, vertex), vertex(frame, vertex + SIZE)) / SPACING - 1);
                }
            }
        }
        return stretches;
    }

    private static double largestStretch(float[] frame) {
        return Arrays.stream(stretches(frame)).max().orElseThrow();
    }

    private static double meanStretch(float[] frame) {
        return Arrays.stream(stretches(frame)).average().orElseThrow();
    }

    private static double distance(float[] a, float[] b) {
        return Math.sqrt(square(a[0] - b[0]) + square(a[1] - b[1]) + square(a[2] - b[2]));
    }

    private static double square(double value) {
        return value * value;
    }
}
