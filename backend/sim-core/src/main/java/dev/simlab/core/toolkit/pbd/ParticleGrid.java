package dev.simlab.core.toolkit.pbd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A square grid of particles such as a cloth: which particles the constraints connect and which form the
 * triangles of its surface.
 *
 * <p>Particle {@code row * size + column} sits in the given row and column
 *
 * @param size particles along each edge, at least 3
 */
public record ParticleGrid(int size) {

    public ParticleGrid {
        if (size < 3) {
            throw new IllegalArgumentException("A grid needs at least 3 particles per edge, but got " + size);
        }
    }

    public int particleCount() {
        return size * size;
    }

    /** How many cells a particle is a corner of: 4 inside the grid, 2 along its edges, 1 in its corners */
    public int cellsAround(int particle) {
        int row = particle / size;
        int column = particle % size;
        return linesAround(row) * linesAround(column);
    }

    private int linesAround(int index) {
        return index == 0 || index == size - 1 ? 1 : 2;
    }

    /** Distance between two particles in the flat grid, in units of the spacing between neighbors */
    public double flatDistance(int a, int b) {
        return Math.hypot(a / size - b / size, a % size - b % size);
    }

    public int stretchConstraintCount() {
        return 2 * size * (size - 1);
    }

    /**
     * Connects every particle to its neighbors: to the next one in its row and column against stretching, to
     * the diagonal ones against shearing, and to the next but one in its row and column against bending. The
     * {@linkplain #stretchConstraintCount() constraints against stretching} come first, row by row from row 0,
     * so one pass carries the corrections from particles pinned in row 0 all the way down the grid
     *
     * @param spacing distance between neighboring particles in a row or column
     * @param compliance against stretching, shearing and bending
     */
    public DistanceConstraints constraints(double spacing, Compliance compliance) {
        int cells = size - 1;
        double diagonal = spacing * Math.sqrt(2);
        var constraints =
                new DistanceConstraints(stretchConstraintCount() + 2 * cells * cells + 2 * size * (cells - 1));
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int particle = row * size + column;
                if (column < cells) {
                    constraints.add(particle, particle + 1, spacing, compliance.stretch());
                }
                if (row < cells) {
                    constraints.add(particle, particle + size, spacing, compliance.stretch());
                }
            }
        }
        for (int row = 0; row < cells; row++) {
            for (int column = 0; column < cells; column++) {
                int particle = row * size + column;
                constraints.add(particle, particle + size + 1, diagonal, compliance.shear());
                constraints.add(particle + 1, particle + size, diagonal, compliance.shear());
            }
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int particle = row * size + column;
                if (column < cells - 1) {
                    constraints.add(particle, particle + 2, 2 * spacing, compliance.bend());
                }
                if (row < cells - 1) {
                    constraints.add(particle, particle + 2 * size, 2 * spacing, compliance.bend());
                }
            }
        }
        return constraints;
    }

    /**
     * Ties every particle to every anchor, at their distance in the flat grid
     *
     * @param anchors particles that don't move, like pinned corners
     */
    public Tethers tethers(double spacing, int... anchors) {
        var tethers = new Tethers(anchors.length * particleCount());
        for (int anchor : anchors) {
            for (int particle = 0; particle < particleCount(); particle++) {
                if (particle != anchor) {
                    tethers.add(particle, anchor, spacing * flatDistance(particle, anchor));
                }
            }
        }
        return tethers;
    }

    /**
     * Splits every cell into two triangles along one or the other diagonal in a checkerboard pattern, so the
     * grid folds alike in every direction. All triangles have the same orientation: with rows along z and
     * columns along x their corners run counterclockwise seen from above
     *
     * @return three particle indices per triangle
     */
    public List<Integer> triangles() {
        int cells = size - 1;
        List<Integer> triangles = new ArrayList<>(6 * cells * cells);
        for (int row = 0; row < cells; row++) {
            for (int column = 0; column < cells; column++) {
                int topLeft = row * size + column;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + size;
                int bottomRight = bottomLeft + 1;
                if ((row + column) % 2 == 0) {
                    Collections.addAll(triangles, topLeft, bottomLeft, bottomRight, topLeft, bottomRight, topRight);
                } else {
                    Collections.addAll(triangles, topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight);
                }
            }
        }
        return List.copyOf(triangles);
    }

    /**
     * Compliances of the constraints in m/N, the inverse of their stiffness
     *
     * @param stretch of the cell edges, 0 keeps the grid from stretching
     * @param shear of the cell diagonals
     * @param bend of the connections to the next but one particle
     */
    public record Compliance(double stretch, double shear, double bend) {}
}
