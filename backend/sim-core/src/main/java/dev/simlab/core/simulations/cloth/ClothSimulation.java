package dev.simlab.core.simulations.cloth;

import dev.simlab.core.Drag;
import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Release;
import dev.simlab.core.Selection;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationInput;
import dev.simlab.core.toolkit.noise.SmoothNoise;
import dev.simlab.core.toolkit.pbd.DistanceConstraints;
import dev.simlab.core.toolkit.pbd.ParticleGrid;
import dev.simlab.core.toolkit.pbd.Tethers;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A square cloth that hangs from two corners, blows in gusty wind and drapes over a sphere.
 *
 * <p>The cloth is a {@linkplain ParticleGrid grid} of particles held together by distance constraints
 * and tied by {@linkplain Tethers tethers} to its pins and to the particles that hands hold. Each step
 * is split into substeps (the small-steps scheme of XPBD): Verlet integration predicts where gravity and
 * wind move the particles, one pass over the constraints corrects these positions, and the sphere and
 * the floor push particles back out.
 *
 * <p>Meters and seconds. y points up, the floor is the plane y = 0 and the wind blows along z. The cloth
 * starts out horizontal at the height of its two pinned corners and falls from there
 */
final class ClothSimulation implements Simulation {

    static final double WIDTH = 2;

    static final double PIN_HEIGHT = 2.5;

    // sphere lies downwind and a bit to the side so the cloth drapes over it unevenly
    static final double SPHERE_X = 0.3;
    static final double SPHERE_Y = 0.85;
    static final double SPHERE_Z = 0.8;
    static final double SPHERE_RADIUS = 0.4;

    /** Gap that particles keep from the sphere and the floor, so the triangles between them stay outside too */
    static final double COLLISION_MARGIN = 0.01;

    static final double REACH = 3;

    static final double MAX_HEIGHT = 4;

    /**
     * Top speed of a hand in m/s. A hand that jumped to a far target at once would yank the cloth into a
     * tangle the constraints can't undo
     */
    static final double MAX_HAND_SPEED = 10;

    /**
     * Top speed of a particle in m/s, 3x the fastest gusts. Large corrections by the constraints turn into
     * large velocities, this limit keeps them from building up
     */
    static final double MAX_SPEED = 30;

    /**
     * Longest substep. With 10 substeps per frame at 30 fps one pass over the constraints per substep keeps
     * the cloth from stretching visibly
     */
    static final double MAX_SUBSTEP = 1.0 / 300;

    /** mass per area in kg/m², that of a heavy cotton fabric like canvas */
    private static final double AREAL_DENSITY = 0.3;

    /**
     * Cell edges keep their length, shearing takes little more force. Bending is softer by orders of
     * magnitude so the cloth falls into folds - stiffer bending would fight the other constraints where
     * the cloth crumples
     */
    private static final ParticleGrid.Compliance COMPLIANCE = new ParticleGrid.Compliance(0, 1e-4, 2e-2);

    /** Air density in kg/m³ times a drag coefficient of 1, a bit below a solid plate since some air gets through */
    private static final double AIR_DRAG = 1.2 * 1.0;

    /** rate in 1/s at which particles lose velocity, on top of the wind's drag */
    private static final double DAMPING = 0.2;

    /** rate in 1/s at which particles lose velocity while they touch the sphere or the floor */
    private static final double FRICTION = 20;

    /** seconds over which the speed of the gusts changes */
    private static final double GUST_PERIOD = 1.2;

    /** seconds over which the direction of the wind changes */
    private static final double TURN_PERIOD = 3.5;

    /** largest angle (radians) by which the wind turns away from the z axis */
    static final double MAX_TURN = 0.35;

    /**
     * Share of the flat-cloth distance between a particle and a pin by which a hand can pull them apart.
     * Pulling the cloth taut against gravity would take infinite force, so the hand leaves some slack
     * which gravity pulls into a curve
     */
    static final double HAND_SLACK = 0.97;

    /** passes over the pins that bring a hand's target within their reach, to well below a millimeter */
    private static final int PIN_REACH_PASSES = 10;

    private final ParticleGrid grid;
    private final int particleCount;

    private final double spacing;

    /** the two corners the cloth hangs from, the ends of row 0 */
    private final int[] pins;

    /** x, y and z of one particle after another */
    private final double[] position;

    /** positions at the end of the previous substep, Verlet integration infers the velocities from them */
    private final double[] previous;

    /**
     * Inverse mass of each particle while nothing holds it. A particle weighs a quarter of every cell around
     * it, so particles along the edges weigh half as much - just as the wind presses on half as much cloth
     * around them
     */
    private final double[] freeInverseMass;

    /** 0 for pinned or held particles, nothing but a hand moves those */
    private final double[] inverseMass;

    private final double[] force;
    private final DistanceConstraints constraints;
    private final Tethers pinTethers;
    private final SmoothNoise gustNoise;
    private final SmoothNoise turnNoise;
    private final Map<Integer, Hold> holds = new HashMap<>();

    private double windSpeed;
    private double gusts;
    private double gravity;
    private double time;
    private double lastSubstep = MAX_SUBSTEP;

    /**
     * Creates a cloth at rest
     *
     * @param size particles along each edge, at least 3
     * @param seed determines the gusts
     * @param windSpeed mean wind speed in m/s
     * @param gusts amplitude in m/s by which the wind speed varies
     */
    ClothSimulation(int size, long seed, double windSpeed, double gusts, double gravity) {
        grid = new ParticleGrid(size);
        this.windSpeed = windSpeed;
        this.gusts = gusts;
        this.gravity = gravity;
        particleCount = grid.particleCount();
        pins = new int[] {0, size - 1};
        spacing = WIDTH / (size - 1);
        double cellMass = AREAL_DENSITY * spacing * spacing;

        position = new double[3 * particleCount];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int i = 3 * (row * size + column);
                position[i] = column * spacing - WIDTH / 2;
                position[i + 1] = PIN_HEIGHT;
                position[i + 2] = row * spacing;
            }
        }
        previous = position.clone();
        freeInverseMass = new double[particleCount];
        inverseMass = new double[particleCount];
        for (int particle = 0; particle < particleCount; particle++) {
            freeInverseMass[particle] = 4 / (cellMass * grid.cellsAround(particle));
            inverseMass[particle] = isPinned(particle) ? 0 : freeInverseMass[particle];
        }
        force = new double[3 * particleCount];

        constraints = grid.constraints(spacing, COMPLIANCE);
        pinTethers = grid.tethers(spacing, pins);
        gustNoise = new SmoothNoise(seed, GUST_PERIOD);
        // another seed makes the direction independent of the speed
        turnNoise = new SmoothNoise(seed + 1, TURN_PERIOD);
    }

    boolean isPinned(int particle) {
        return Arrays.stream(pins).anyMatch(pin -> pin == particle);
    }

    @Override
    public void apply(SimulationInput input) {
        switch (input) {
            case ParameterChange(String name, double value) -> setParameter(name, value);
            case Grab(int vertex, double x, double y, double z) -> grab(vertex, x, y, z);
            case Drag(int vertex, double x, double y, double z) -> drag(vertex, x, y, z);
            case Release(int vertex) -> release(vertex);
            case Selection _ -> throw new InvalidInputException("Particles of the cloth cannot be selected");
        }
    }

    @Override
    public void step(double dt) {
        if (!(dt >= 0)) {
            throw new IllegalArgumentException("Time span must not be negative, but was " + dt);
        }
        int substeps = (int) Math.ceil(dt / MAX_SUBSTEP);
        if (substeps == 0) {
            return;
        }
        double substep = dt / substeps;
        holds.values().forEach(hold -> hold.approach(MAX_HAND_SPEED * dt));
        for (int i = 1; i <= substeps; i++) {
            addWindForces();
            integrate(substep);
            moveHeldParticles((double) i / substeps);
            constraints.project(position, inverseMass, substep);
            pinTethers.project(position, inverseMass);
            holds.values().forEach(hold -> hold.tethers.project(position, inverseMass));
            collide(substep);
            lastSubstep = substep;
            time += substep;
        }
        holds.values().forEach(Hold::arrive);
    }

    @Override
    public double time() {
        return time;
    }

    /**
     * Writes x, y and z of one particle after another, then x, y and z of the sphere's center and its
     * radius, then the readouts, as the scene of {@link ClothDefinition} lays them out
     */
    @Override
    public void writeFrame(FloatBuffer frame) {
        for (double coordinate : position) {
            frame.put((float) coordinate);
        }
        frame.put((float) SPHERE_X).put((float) SPHERE_Y).put((float) SPHERE_Z).put((float) SPHERE_RADIUS);
        writeReadouts(frame);
    }

    /**
     * Writes the readouts: the wind that the next substep feels, its gust noise and speed, then its turn
     * noise and the angle by which it turns
     */
    private void writeReadouts(FloatBuffer frame) {
        frame.put((float) gustNoise.valueAt(time)).put((float) windSpeedAt(time));
        frame.put((float) turnNoise.valueAt(time)).put((float) windTurnAt(time));
    }

    /**
     * Writes the x-ray as the scene of {@link ClothDefinition} lays it out: the constraints along rows and
     * columns (they come first among the constraints), each from one particle to the other, with its strain
     * in percent
     */
    @Override
    public void writeXray(FloatBuffer frame) {
        int count = grid.stretchConstraintCount();
        frame.put(count);
        for (int constraint = 0; constraint < count; constraint++) {
            putPosition(frame, constraints.firstParticle(constraint));
            putPosition(frame, constraints.secondParticle(constraint));
            frame.put((float) (100 * constraints.strain(constraint, position)));
        }
    }

    private void putPosition(FloatBuffer frame, int particle) {
        int i = 3 * particle;
        frame.put((float) position[i]).put((float) position[i + 1]).put((float) position[i + 2]);
    }

    private void setParameter(String name, double value) {
        switch (name) {
            case ClothDefinition.WIND -> windSpeed = value;
            case ClothDefinition.GUSTS -> gusts = value;
            case ClothDefinition.GRAVITY -> gravity = value;
            default -> throw new InvalidInputException("Unknown parameter '%s'".formatted(name));
        }
    }

    /** Lets a hand hold a particle, which then ignores every force. Pinned corners stay where they are */
    private void grab(int particle, double x, double y, double z) {
        requireParticle(particle);
        if (isPinned(particle)) {
            return;
        }
        holds.computeIfAbsent(particle, Hold::new).moveTo(x, y, z);
        inverseMass[particle] = 0;
    }

    private void drag(int particle, double x, double y, double z) {
        requireParticle(particle);
        Hold hold = holds.get(particle);
        if (hold != null) {
            hold.moveTo(x, y, z);
        }
    }

    private void release(int particle) {
        requireParticle(particle);
        if (holds.remove(particle) != null) {
            inverseMass[particle] = freeInverseMass[particle];
        }
    }

    private void requireParticle(int particle) {
        if (particle < 0 || particle >= particleCount) {
            throw new InvalidInputException("There is no vertex %d".formatted(particle));
        }
    }

    /**
     * Sets the wind force on every particle. The wind blows along z, gusts change its speed and turn it
     * away from the axis, both smoothly at random
     */
    private void addWindForces() {
        Arrays.fill(force, 0);
        double speed = windSpeedAt(time);
        double turn = windTurnAt(time);
        double windX = speed * Math.sin(turn);
        double windZ = speed * Math.cos(turn);
        int size = grid.size();
        for (int row = 0; row < size - 1; row++) {
            for (int column = 0; column < size - 1; column++) {
                addWindForceOnCell(row * size + column, windX, windZ);
            }
        }
    }

    /** Wind speed at time t in m/s: the mean speed, changed by the gusts */
    private double windSpeedAt(double t) {
        return Math.max(0, windSpeed + gusts * gustNoise.valueAt(t));
    }

    /** Angle in radians by which the wind turns away from the z axis at time t */
    private double windTurnAt(double t) {
        return MAX_TURN * turnNoise.valueAt(t);
    }

    /**
     * Adds the wind force on a cell of the grid, spread evenly over its four corners (a quarter of the cell
     * each).
     *
     * <p>The wind presses on the cell with the dynamic pressure of the part of the wind that hits it
     * head-on, relative to the moving cell: {@code F = 1/2 ρ c A v|v| n}, where {@code v} is that part of
     * the speed, {@code A} the area of the cell and {@code n} its unit normal. Spreading the force of each
     * triangle over its corners instead would push every other particle twice as hard (the diagonals of
     * the triangles alternate) and make the cloth ripple in a zigzag
     *
     * @param corner particle at the top left corner of the cell
     */
    private void addWindForceOnCell(int corner, double windX, double windZ) {
        int topLeft = 3 * corner;
        int topRight = topLeft + 3;
        int bottomLeft = topLeft + 3 * grid.size();
        int bottomRight = bottomLeft + 3;
        double fallingX = position[bottomRight] - position[topLeft];
        double fallingY = position[bottomRight + 1] - position[topLeft + 1];
        double fallingZ = position[bottomRight + 2] - position[topLeft + 2];
        double risingX = position[topRight] - position[bottomLeft];
        double risingY = position[topRight + 1] - position[bottomLeft + 1];
        double risingZ = position[topRight + 2] - position[bottomLeft + 2];
        // cross product N of the diagonals is normal to the cell and twice as long as its area
        double normalX = fallingY * risingZ - fallingZ * risingY;
        double normalY = fallingZ * risingX - fallingX * risingZ;
        double normalZ = fallingX * risingY - fallingY * risingX;
        double normalSquared = normalX * normalX + normalY * normalY + normalZ * normalZ;
        if (normalSquared == 0) {
            return;
        }
        // the cell moves with the mean velocity of its corners
        double toVelocity = 1 / lastSubstep;
        double relativeX = windX - meanDisplacement(topLeft, topRight, bottomLeft, bottomRight) * toVelocity;
        double relativeY = -meanDisplacement(topLeft + 1, topRight + 1, bottomLeft + 1, bottomRight + 1) * toVelocity;
        double relativeZ =
                windZ - meanDisplacement(topLeft + 2, topRight + 2, bottomLeft + 2, bottomRight + 2) * toVelocity;
        // with v = N·w / |N| for the relative wind w: F = 1/4 ρ c (N·w) |N·w| N / |N|², a quarter per corner
        double flow = normalX * relativeX + normalY * relativeY + normalZ * relativeZ;
        double scale = AIR_DRAG / 16 * flow * Math.abs(flow) / normalSquared;
        addForce(topLeft, scale * normalX, scale * normalY, scale * normalZ);
        addForce(topRight, scale * normalX, scale * normalY, scale * normalZ);
        addForce(bottomLeft, scale * normalX, scale * normalY, scale * normalZ);
        addForce(bottomRight, scale * normalX, scale * normalY, scale * normalZ);
    }

    private double meanDisplacement(int a, int b, int c, int d) {
        return (displacement(a) + displacement(b) + displacement(c) + displacement(d)) / 4;
    }

    private double displacement(int coordinate) {
        return position[coordinate] - previous[coordinate];
    }

    private void addForce(int i, double x, double y, double z) {
        force[i] += x;
        force[i + 1] += y;
        force[i + 2] += z;
    }

    /**
     * Moves the free particles by Verlet integration: each keeps the velocity it had in the previous
     * substep (damped, limited and scaled to the length of this one) and accelerates by gravity and wind
     */
    private void integrate(double substep) {
        double velocityScale = Math.exp(-DAMPING * substep) * substep / lastSubstep;
        double maxStep = MAX_SPEED * substep;
        double substepSquared = substep * substep;
        for (int particle = 0; particle < particleCount; particle++) {
            double particleInverseMass = inverseMass[particle];
            if (particleInverseMass == 0) {
                continue;
            }
            int i = 3 * particle;
            double x = position[i];
            double y = position[i + 1];
            double z = position[i + 2];
            double stepX = (x - previous[i]) * velocityScale;
            double stepY = (y - previous[i + 1]) * velocityScale;
            double stepZ = (z - previous[i + 2]) * velocityScale;
            double stepSquared = stepX * stepX + stepY * stepY + stepZ * stepZ;
            if (stepSquared > maxStep * maxStep) {
                double limit = maxStep / Math.sqrt(stepSquared);
                stepX *= limit;
                stepY *= limit;
                stepZ *= limit;
            }
            position[i] = x + stepX + force[i] * particleInverseMass * substepSquared;
            position[i + 1] = y + stepY + (force[i + 1] * particleInverseMass - gravity) * substepSquared;
            position[i + 2] = z + stepZ + force[i + 2] * particleInverseMass * substepSquared;
            previous[i] = x;
            previous[i + 1] = y;
            previous[i + 2] = z;
        }
    }

    private void moveHeldParticles(double progress) {
        holds.forEach((particle, hold) -> {
            int i = 3 * particle;
            for (int axis = 0; axis < 3; axis++) {
                previous[i + axis] = position[i + axis];
                position[i + axis] = hold.from[axis] + (hold.to[axis] - hold.from[axis]) * progress;
            }
        });
    }

    /** Pushes free particles out of the sphere and the floor, where they lose velocity to friction */
    private void collide(double substep) {
        double friction = 1 - Math.exp(-FRICTION * substep);
        for (int particle = 0; particle < particleCount; particle++) {
            if (inverseMass[particle] == 0) {
                continue;
            }
            int i = 3 * particle;
            boolean touchesSphere = pushOutOfSphere(position, i);
            boolean touchesFloor = position[i + 1] < COLLISION_MARGIN;
            if (touchesFloor) {
                position[i + 1] = COLLISION_MARGIN;
            }
            if (touchesSphere || touchesFloor) {
                for (int axis = 0; axis < 3; axis++) {
                    previous[i + axis] += (position[i + axis] - previous[i + axis]) * friction;
                }
            }
        }
    }

    /**
     * Moves a point that's too close to the sphere onto its surface, plus the collision margin
     *
     * @param offset index of the point's x coordinate
     * @return whether the point was too close
     */
    private static boolean pushOutOfSphere(double[] coordinates, int offset) {
        double dx = coordinates[offset] - SPHERE_X;
        double dy = coordinates[offset + 1] - SPHERE_Y;
        double dz = coordinates[offset + 2] - SPHERE_Z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        double reach = SPHERE_RADIUS + COLLISION_MARGIN;
        if (distanceSquared >= reach * reach) {
            return false;
        }
        if (distanceSquared == 0) {
            // a point right at the center has no direction to leave in, so it leaves upwards
            coordinates[offset + 1] = SPHERE_Y + reach;
            return true;
        }
        double scale = reach / Math.sqrt(distanceSquared);
        coordinates[offset] = SPHERE_X + dx * scale;
        coordinates[offset + 1] = SPHERE_Y + dy * scale;
        coordinates[offset + 2] = SPHERE_Z + dz * scale;
        return true;
    }

    /**
     * Pulls a point back within the distance a particle has from each pin in the flat cloth, minus some
     * slack, so a hand pulls the particle no farther than the cloth reaches. Projecting onto the ball
     * around one pin after the other approaches the intersection of the balls
     */
    private void keepWithinReachOfPins(double[] point, int particle) {
        for (int pass = 0; pass < PIN_REACH_PASSES; pass++) {
            for (int pin : pins) {
                int i = 3 * pin;
                double reach = HAND_SLACK * spacing * grid.flatDistance(particle, pin);
                double dx = point[0] - position[i];
                double dy = point[1] - position[i + 1];
                double dz = point[2] - position[i + 2];
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance > reach) {
                    double scale = reach / distance;
                    point[0] = position[i] + dx * scale;
                    point[1] = position[i + 1] + dy * scale;
                    point[2] = position[i + 2] + dz * scale;
                }
            }
        }
    }

    /**
     * A particle in a hand, which moves it towards a target. Each step the hand moves the particle from
     * where the step starts to where it ends, at most a limited distance closer to the target.
     *
     * <p>Like a pin the hand carries part of the cloth's weight, so the other particles are tied to the
     * held one as to the pins
     */
    private final class Hold {

        private final int particle;
        private final Tethers tethers;
        private final double[] from;
        private final double[] to;
        private final double[] target;

        Hold(int particle) {
            this.particle = particle;
            tethers = grid.tethers(spacing, particle);
            from = Arrays.copyOfRange(position, 3 * particle, 3 * particle + 3);
            to = from.clone();
            target = from.clone();
        }

        /**
         * Sets the target: within reach of the hand and, as the cloth doesn't stretch, of the pins. Above
         * the floor and outside the sphere
         */
        void moveTo(double x, double y, double z) {
            target[0] = Math.clamp(x, -REACH, REACH);
            target[1] = Math.clamp(y, COLLISION_MARGIN, MAX_HEIGHT);
            target[2] = Math.clamp(z, -REACH, REACH);
            keepWithinReachOfPins(target, particle);
            pushOutOfSphere(target, 0);
        }

        void approach(double maxDistance) {
            double dx = target[0] - from[0];
            double dy = target[1] - from[1];
            double dz = target[2] - from[2];
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double share = distance > maxDistance ? maxDistance / distance : 1;
            to[0] = from[0] + dx * share;
            to[1] = from[1] + dy * share;
            to[2] = from[2] + dz * share;
            // on the way to a target behind the sphere the hand goes around it
            pushOutOfSphere(to, 0);
        }

        void arrive() {
            System.arraycopy(to, 0, from, 0, to.length);
        }
    }
}
