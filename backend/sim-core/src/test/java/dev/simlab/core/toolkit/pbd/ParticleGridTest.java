package dev.simlab.core.toolkit.pbd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticleGridTest {

    private static final double SPACING = 0.5;
    private static final ParticleGrid.Compliance RIGID = new ParticleGrid.Compliance(0, 0, 0);

    private final ParticleGrid grid = new ParticleGrid(4);

    @Test
    void countsTheCellsAroundAParticle() {
        assertThat(grid.cellsAround(0)).as("corner").isOne();
        assertThat(grid.cellsAround(1)).as("edge").isEqualTo(2);
        assertThat(grid.cellsAround(5)).as("inside").isEqualTo(4);
    }

    @Test
    void measuresDistancesInTheFlatClothInUnitsOfTheSpacing() {
        // from row 0, column 0 to row 3, column 2 and back
        assertThat(grid.flatDistance(0, 14)).isEqualTo(Math.hypot(3, 2));
        assertThat(grid.flatDistance(14, 0)).isEqualTo(Math.hypot(3, 2));
        assertThat(grid.flatDistance(5, 5)).isZero();
    }

    @Test
    void connectsNeighborsAgainstStretchingShearingAndBending() {
        // 2 * 4 * 3 along rows and columns, 2 * 3 * 3 diagonals, 2 * 4 * 2 to the next but one
        assertThat(grid.constraints(SPACING, RIGID).size()).isEqualTo(24 + 18 + 16);
    }

    @Test
    void putsTheConstraintsAgainstStretchingFirstAlongRowsAndColumns() {
        DistanceConstraints constraints = grid.constraints(SPACING, RIGID);

        assertThat(grid.stretchConstraintCount()).isEqualTo(24);
        for (int constraint = 0; constraint < constraints.size(); constraint++) {
            double flatDistance =
                    grid.flatDistance(constraints.firstParticle(constraint), constraints.secondParticle(constraint));
            assertThat(flatDistance == 1)
                    .as("constraint %d between neighbors in a row or column", constraint)
                    .isEqualTo(constraint < grid.stretchConstraintCount());
        }
    }

    @Test
    void fitsItsConstraintsToTheFlatCloth() {
        double[] position = flat(grid);
        double[] projected = position.clone();

        grid.constraints(SPACING, RIGID).project(projected, inverseMasses(grid), 0.01);

        assertThat(projected).containsExactly(position, within(1e-12));
    }

    @Test
    void tiesEveryOtherParticleToEachAnchorAtTheirDistanceInTheFlatCloth() {
        Tethers tethers = grid.tethers(SPACING, 0, 3);
        double[] position = flat(grid);
        double[] projected = position.clone();

        tethers.project(projected, inverseMasses(grid));

        assertThat(tethers.size()).isEqualTo(2 * 15);
        assertThat(projected).containsExactly(position, within(1e-12));
    }

    @Test
    void splitsEveryCellIntoTwoTrianglesOfTheSameOrientation() {
        List<Integer> triangles = grid.triangles();
        double[] position = flat(grid);

        assertThat(triangles).hasSize(3 * 2 * 9);
        for (int t = 0; t < triangles.size(); t += 3) {
            assertThat(normalY(position, triangles.get(t), triangles.get(t + 1), triangles.get(t + 2)))
                    .as("orientation of triangle %d", t / 3)
                    .isPositive();
        }
    }

    @Test
    void coversEveryCellAndAlternatesTheDiagonals() {
        List<Integer> triangles = grid.triangles();

        for (int cell = 0; cell < 9; cell++) {
            int topLeft = cell / 3 * 4 + cell % 3;
            assertThat(new HashSet<>(triangles.subList(6 * cell, 6 * cell + 6)))
                    .as("corners of cell %d", cell)
                    .containsOnly(topLeft, topLeft + 1, topLeft + 4, topLeft + 5);
            boolean diagonalFromTopLeft = triangles.get(6 * cell) == topLeft && triangles.get(6 * cell + 3) == topLeft;
            assertThat(diagonalFromTopLeft).as("diagonal of cell %d", cell).isEqualTo((cell / 3 + cell % 3) % 2 == 0);
        }
    }

    @Test
    void needsAtLeastThreeParticlesPerEdge() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ParticleGrid(2));
    }

    /** flat and horizontal, rows along z and columns along x */
    private static double[] flat(ParticleGrid grid) {
        double[] position = new double[3 * grid.particleCount()];
        for (int particle = 0; particle < grid.particleCount(); particle++) {
            int row = particle / grid.size();
            int column = particle % grid.size();
            position[3 * particle] = column * SPACING;
            position[3 * particle + 2] = row * SPACING;
        }
        return position;
    }

    private static double[] inverseMasses(ParticleGrid grid) {
        double[] inverseMass = new double[grid.particleCount()];
        Arrays.fill(inverseMass, 1);
        return inverseMass;
    }

    /** y component of the normal of a triangle, by the right-hand rule */
    private static double normalY(double[] position, int a, int b, int c) {
        double abX = position[3 * b] - position[3 * a];
        double abZ = position[3 * b + 2] - position[3 * a + 2];
        double acX = position[3 * c] - position[3 * a];
        double acZ = position[3 * c + 2] - position[3 * a + 2];
        return abZ * acX - abX * acZ;
    }
}
