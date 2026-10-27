package dev.simlab.core.toolkit.pbd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import org.junit.jupiter.api.Test;

class DistanceConstraintsTest {

    private static final double SUBSTEP = 0.01;

    @Test
    void movesTwoParticlesOfEqualMassTowardsEachOtherByTheSameAmount() {
        double[] position = {0, 0, 0, 3, 0, 0};
        var constraints = new DistanceConstraints(1);
        constraints.add(0, 1, 2, 0);

        constraints.project(position, new double[] {1, 1}, SUBSTEP);

        assertThat(position).containsExactly(new double[] {0.5, 0, 0, 2.5, 0, 0}, within(1e-12));
    }

    @Test
    void pushesParticlesApartThatAreTooClose() {
        double[] position = {0, 0, 0, 0, 1, 0};
        var constraints = new DistanceConstraints(1);
        constraints.add(0, 1, 2, 0);

        constraints.project(position, new double[] {1, 1}, SUBSTEP);

        assertThat(position).containsExactly(new double[] {0, -0.5, 0, 0, 1.5, 0}, within(1e-12));
    }

    @Test
    void movesTheLighterParticleFarther() {
        double[] position = {0, 0, 0, 0, 0, 4};
        var constraints = new DistanceConstraints(1);
        constraints.add(0, 1, 1, 0);

        // the first particle is three times as heavy as the second
        constraints.project(position, new double[] {1, 3}, SUBSTEP);

        assertThat(position).containsExactly(new double[] {0, 0, 0.75, 0, 0, 1.75}, within(1e-12));
    }

    @Test
    void neverMovesParticlesWithoutInverseMass() {
        double[] position = {0, 0, 0, 3, 0, 0, 6, 0, 0};
        var constraints = new DistanceConstraints(2);
        constraints.add(0, 1, 1, 0);
        constraints.add(1, 2, 1, 0);

        constraints.project(position, new double[] {0, 1, 0}, SUBSTEP);

        assertThat(position[0]).isZero();
        assertThat(position[6]).isEqualTo(6);
        assertThat(position[3])
                .as("the particle in between, pulled to the first and then to the last one")
                .isEqualTo(5);
    }

    @Test
    void actsLikeASpringOfTheInverseStiffnessWhenCompliant() {
        double[] position = {0, 0, 0, 1.1, 0, 0};
        double compliance = 0.5;
        var constraints = new DistanceConstraints(1);
        constraints.add(0, 1, 1, compliance);

        constraints.project(position, new double[] {0, 1}, SUBSTEP);

        // a spring of stiffness 1 / compliance accelerates the free particle of unit mass by
        // 0.1 m / 0.5 m/N = 0.2 m/s², which moves it by 0.2 m/s² * SUBSTEP² in one substep
        assertThat(1.1 - position[3]).isCloseTo(0.2 * SUBSTEP * SUBSTEP, withinPercentage(0.1));
    }

    @Test
    void leavesParticlesAtTheSamePointAlone() {
        double[] position = {1, 2, 3, 1, 2, 3};
        var constraints = new DistanceConstraints(1);
        constraints.add(0, 1, 1, 0);

        constraints.project(position, new double[] {1, 1}, SUBSTEP);

        assertThat(position).containsExactly(1, 2, 3, 1, 2, 3);
    }

    @Test
    void tellsTheParticlesOfAConstraintAndHowMuchItIsStrained() {
        double[] position = {0, 0, 0, 3, 0, 0, 3, 1.5, 0};
        var constraints = new DistanceConstraints(2);
        constraints.add(0, 1, 2, 0);
        constraints.add(2, 1, 2, 0);

        assertThat(constraints.firstParticle(1)).isEqualTo(2);
        assertThat(constraints.secondParticle(1)).isOne();
        assertThat(constraints.strain(0, position)).as("stretched to 3 m").isCloseTo(0.5, within(1e-12));
        assertThat(constraints.strain(1, position)).as("compressed to 1.5 m").isCloseTo(-0.25, within(1e-12));
    }

    @Test
    void tellsNothingOfConstraintsThatItDoesNotHold() {
        var constraints = new DistanceConstraints(2);
        constraints.add(0, 1, 2, 0);

        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> constraints.firstParticle(1))
                .withMessage("There is no constraint 1 among 1");
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> constraints.secondParticle(-1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> constraints.strain(1, new double[6]));
    }

    @Test
    void holdsAsManyConstraintsAsItsCapacity() {
        var constraints = new DistanceConstraints(1);
        constraints.add(0, 1, 1, 0);

        assertThat(constraints.size()).isOne();
        assertThatIllegalStateException().isThrownBy(() -> constraints.add(1, 2, 1, 0));
    }
}
