---
symbols:
    pericenter: { parameter: pericenter, code: [pericenter] }
    ratio: { parameter: ratio, code: [] }
    inclination: { parameter: inclination, code: [] }
    myrPerTimeUnit: { code: [MYR_PER_TIME_UNIT] }
    acceleration: { code: [ax, ay, az, acceleration] }
    mass: { code: [masses, mass] }
    separation: { code: [dx, dy, dz] }
    softening: { code: [epsilonSquared, softeningSquared] }
    edge: { code: [edge] }
    distance: { code: [distanceSquared] }
    openingAngle: { parameter: openingAngle, code: [openingAngleSquared] }
    position: { code: [position] }
    velocity: { code: [velocity] }
    maxStep: { code: [MAX_STEP] }
    scaleLength: { code: [h] }
    radius: { code: [distance] }
    kappa: { code: [kappa] }
    toomreQ: { code: [TOOMRE_Q] }
    radialDispersion: { code: [radialSquared] }
    dispersionRatio: { code: [ratio] }
    meanVelocity: { code: [meanSquared] }
    density: { code: [density] }
    gravity: { code: [onSpheres] }
    dispersion: { code: [dispersionSquared] }
    anomaly: { code: [anomaly] }
    totalMass: { code: [totalMass] }
    speed: { code: [speed] }
    pairs: { readout: pairs, code: [pairs] }
    interactions: { readout: interactions, code: [] }
    starInteractions: { readout: starInteractions, code: [size] }
    starForceError: { readout: starForceError, code: [] }
    energyError: { readout: energyError, code: [] }
    initialEnergy: { code: [initialEnergy] }
    kinetic: { code: [kinetic] }
    potential: { code: [sortedPotential] }
---

## The code at a glance

| Method                           | Part       | What it does                                                  |
| -------------------------------- | ---------- | ------------------------------------------------------------- |
| `GalaxyDefinition#create`        | Simulation | sets up an encounter from the mass ratio, pericenter and tilt |
| `Galaxy#sample`                  | Simulation | draws the particles of a galaxy in equilibrium                |
| `GalaxySimulation#step`          | Simulation | advances both galaxies at the pace of the controls            |
| `Leapfrog#step`                  | Toolkit    | moves all particles by one step of drift, kick and drift      |
| `BarnesHut#accelerate`           | Toolkit    | computes the gravity of all particles with an octree          |
| `GalaxySimulation#writeReadouts` | Simulation | reports what the gravity costs and how much it errs           |
| `BarnesHut#relativeErrorOf`      | Toolkit    | compares the gravity on a star with the direct sum            |
| `GalaxySimulation#measureEnergy` | Simulation | measures the total energy every 30 steps                      |
| `BarnesHut#potentialEnergy`      | Toolkit    | sums up the potential energy with an octree                   |
| `GalaxySimulation#writeXray`     | Simulation | shows the cubes that act on the selected star                 |

## Units

Lengths are in kiloparsecs (kpc) and masses in $10^{10}$ solar masses. With the gravitational
constant $G = 4.3009 \cdot 10^{-6}\ \text{kpc}\, (\text{km/s})^2 / M_\odot$, the code sets $G = 1$
by measuring velocities in

$$
\sqrt{\frac{G \cdot 10^{10} M_\odot}{1\ \text{kpc}}} = 207.4\ \text{km/s}
$$

and times in $1\ \text{kpc} / (207.4\ \text{km/s}) = \class{myrPerTimeUnit}{4.715}$ million years
(Myr).

## Gravity

Each galaxy consists of 8,000 particles in its disk, 1,600 in its bulge and 8,000 in its halo of
dark matter, 35,200 particles in both. A particle of a disk weighs $5 \cdot 10^6$ solar masses: the
particles sample the smooth distribution of mass of a galaxy rather than single stars. Every
particle pulls on every other one, with gravity softened over $\varepsilon = 0.2$ kpc, about the
distance between neighboring particles of a disk:

$$
\class{acceleration}{\ddot x_i} = G \sum_{j \ne i} \class{mass}{m_j}\,
  \frac{\class{separation}{x_j - x_i}}
  {\left( \lVert \class{separation}{x_j - x_i} \rVert^2
  + \class{softening}{\varepsilon^2} \right)^{3/2}}
$$

That is the gravity of point masses smeared out into Plummer spheres of the scale radius
$\varepsilon$. It keeps close encounters between particles from flinging them apart, which real
galaxies of $10^{11}$ stars hardly know. Summed directly, the gravity of all particles takes
$N (N - 1) / 2 \approx 6.2 \cdot 10^8$ interactions of pairs.

## The Barnes-Hut tree

Barnes and Hut (1986) sort the particles into an octree: a cube around all of them splits into eight
cubes, and each of those again, until no cube holds more than 16 particles. Every cube knows its
mass $M$ and its center of mass $X$. Seen from afar, a cube pulls like a single particle of mass $M$
at $X$. The error of this monopole grows with the square of $l/d$, the edge of the cube over its
distance, as the dipole moment about the center of mass vanishes. A cube acts as a whole if

$$
\frac{\class{edge}{l}}{d} < \theta
  \iff \class{edge}{l}^2 < \class{openingAngle}{\theta^2}\, \class{distance}{d^2}
$$

with the opening angle $\class{openingAngle}{\theta}$, 0.7 unless the controls change it; otherwise
its children are examined. The code compares the squares, which spares it a square root. Every
particle then interacts with $O(\log N)$ cubes and particles, and the gravity of all of them takes
$O(N \log N)$ time instead of $O(N^2)$.

Particles close to each other walk the tree as a group of at most 64 (Barnes, 1990): the walk lists
the cubes and particles that act on the whole group, with $d$ measured from the center of mass of
the cube to the box around the group, and a cube that overlaps the box never acts as a whole.

`BarnesHut#collect`

Every particle of the group then sums up the softened gravity of the list. The groups are
independent of each other and run in parallel on all cores.

`BarnesHut#accelerateGroup`

On the particles of this simulation, the median error of the force is about 0.3 %. One evaluation
takes 7.2 ms on a 24-thread desktop processor, against 162 ms for a direct sum that runs the same
loop over all other particles, in parallel as well, 23 times as long. Walking the tree in groups,
through particles sorted in the order of the tree, is about 3.7 times as fast as walking it for
every particle.

The tree counts what it computes. Every frame reports the interactions $\class{interactions}{I}$ of
the last step: for every particle, the cubes and particles in the list of its group, the particle
itself among them, whose pull on itself is zero. The direct sum takes the
$\class{pairs}{N (N - 1) / 2}$ pairs of particles instead, and an interaction takes one inverse
square root, as a pair does. Click a star, and the frame reports the $\class{starInteractions}{n_i}$
entries of the list of its group as well, which the star sums up instead of the $N - 1 = 35{,}199$
other particles:

```calculation
\begin{aligned}
\class{interactions}{I} &\ll \class{pairs}{\tfrac{1}{2} N (N - 1)} \\
\class{starInteractions}{n_i} &\ll 35{,}199
\end{aligned}
```

`GalaxySimulation#writeReadouts`

At the start, the tree computes about $3.3 \cdot 10^7$ interactions per step, 19 times fewer than
the $6.2 \cdot 10^8$ pairs, and a star sums up about 1,000 entries, in the median. As the galaxies
merge into one dense remnant, the lists grow: after 1,000 Myr, the tree computes about
$4.0 \cdot 10^7$ interactions, 15.5 times fewer than the pairs, and a star sums up about 1,300
entries, between about 300 and 1,900.

## Accuracy against cost

The opening angle trades the accuracy of the gravity for the time it takes, and the controls change
it while the galaxies collide. On the particles of this simulation at the start, measured against
the direct sum for 2,000 of them drawn at random:

| $\class{openingAngle}{\theta}$ | Interactions      | Median error | 99th percentile | Energy drift   |
| ------------------------------ | ----------------- | ------------ | --------------- | -------------- |
| 0.4                            | $1.07 \cdot 10^8$ | 0.07 %       | 0.27 %          | 0.04 to 0.09 % |
| 0.7                            | $3.28 \cdot 10^7$ | 0.30 %       | 1.3 %           | 0.16 to 0.28 % |
| 1.0                            | $2.05 \cdot 10^7$ | 0.62 %       | 3.6 %           | 0.64 to 1.0 %  |
| 1.5                            | $1.01 \cdot 10^7$ | 2.4 %        | 15 %            | 2.7 to 4.4 %   |

From $\class{openingAngle}{\theta} = 0.4$ to 1.5, the interactions fall tenfold, but the median
error of the force grows 34-fold. Over the merger, the errors change little: at
$\class{openingAngle}{\theta} = 0.7$, the median error is 0.30 % at the start, 0.27 % after 500 Myr
and 0.28 % after 1,000 Myr. The last column gives the largest change of the total energy over the
first 1,000 Myr, for three sets of particles drawn at random; the section on the energy explains how
the simulation measures it.

Click a star, and the frame reports how far the acceleration $\tilde a_i$ that the tree gave it in
the last step deviates from the exact one, $\class{acceleration}{a_i}$, which the direct sum over
the 35,199 other particles gives at the same positions:

$$
\class{starForceError}{\delta_i}
  = \frac{\lVert \tilde a_i - \class{acceleration}{a_i} \rVert}
  {\lVert \class{acceleration}{a_i} \rVert}
$$

`BarnesHut#relativeErrorOf`

The direct sum for one star takes 35,199 interactions, about a thousandth of the interactions of a
step.

```experiment
question: >-
    What does the tree gain, and what does it lose, at $\class{openingAngle}{\theta} = 1.5$?
    Click a star to watch $\class{starForceError}{\delta_i}$.
values:
    openingAngle: 1.5
explanation: >-
    The tree computes about $1.0 \cdot 10^7$ interactions per step, a third of those at
    $\class{openingAngle}{\theta} = 0.7$, but the median error of the force grows eightfold, to
    2.4 %, and one star in a hundred errs by more than 10 %. Restart, and over the whole merger,
    the energy changes by up to 2.7 to 4.4 %, against 0.16 to 0.28 % at 0.7.
```

```experiment
question: >-
    And the other way round, at $\class{openingAngle}{\theta} = 0.4$?
values:
    openingAngle: 0.4
explanation: >-
    The tree computes about three times as many interactions, $1.1 \cdot 10^8$ per step, and a
    step takes 2.7 times as long, 19 ms instead of 7 ms. In return, the median error of the force
    falls to a quarter, 0.07 %. Restart, and over the whole merger, the energy changes by less
    than 0.1 %.
```

## The tree in the x-ray

Click a star and switch on the x-ray: it draws the cubes that act on the star as a whole, the
entries of the list of its group that the walk did not open. Near the star, the cubes are small;
farther away, they grow with their distance, as the opening angle lets them,
$\class{edge}{l} < \class{openingAngle}{\theta}\, \class{distance}{d}$. The particles of the cubes
that the walk opened act on the star one by one, and the x-ray leaves them out.

`BarnesHut#cubesActingOn`

`GalaxySimulation#writeXray`

At $\class{openingAngle}{\theta} = 0.7$, about 660 cubes act on a star at the start, in the median,
and about 910 after 1,000 Myr, in the dense remnant of the merger; at 0.4, about 1,640 and 2,570,
and at 1.5, about 190 and 260. At most, about 4,000 cubes act on a star, at
$\class{openingAngle}{\theta} = 0.4$ in the remnant, and a frame has room for half as many again,
6,144.

## The leapfrog

The leapfrog method moves the particles in three parts per step: half a step of drift at constant
velocity, a kick by the gravity at the positions halfway through, and another half step of drift:

$$
\begin{aligned}
\class{position}{x}_{n+1/2} &= \class{position}{x}_n
  + \tfrac{\Delta t}{2}\, \class{velocity}{v}_n \\
\class{velocity}{v}_{n+1} &= \class{velocity}{v}_n
  + \Delta t\, \class{acceleration}{a}(\class{position}{x}_{n+1/2}) \\
\class{position}{x}_{n+1} &= \class{position}{x}_{n+1/2}
  + \tfrac{\Delta t}{2}\, \class{velocity}{v}_{n+1}
\end{aligned}
$$

It evaluates gravity once per step and is of second order: twice as many steps leave a quarter of
the error. It is time-reversible, as a step of $-\Delta t$ undoes a step of $\Delta t$. As a
symplectic method, it keeps the error in energy bounded for forces that derive from a potential, so
that orbits neither spiral in nor out over thousands of steps. And it conserves momentum if every
pair of particles pulls on each other equally hard, as in the direct sum (`LeapfrogTest`). The
forces of the tree meet both conditions only approximately; over the whole merger, the energy stays
within about 0.3 %.

`Leapfrog#step`

A step lasts at most $\class{maxStep}{0.16}$ units of time, 0.75 Myr. `GalaxySimulation#step` turns
the pace of the controls, in Myr per second, into as few equal steps as cover a frame:

`GalaxySimulation#step`

## The energy

The exact dynamics conserve the total energy: the kinetic energy of the particles plus the softened
potential energy of every pair,

$$
E = \class{kinetic}{K} + W
  = \sum_i \tfrac{1}{2}\, \class{mass}{m_i} \class{velocity}{v_i}^2
  - G \sum_{i < j} \frac{\class{mass}{m_i} \class{mass}{m_j}}
  {\sqrt{\lVert \class{separation}{x_j - x_i} \rVert^2 + \class{softening}{\varepsilon^2}}}
$$

Every frame reports how far the leapfrog and the tree have moved it since the start, as a share of
the energy $\class{initialEnergy}{E_0}$ at the start:

$$
\class{energyError}{\frac{\Delta E}{E_0}}
  = \frac{E - \class{initialEnergy}{E_0}}{\lvert \class{initialEnergy}{E_0} \rvert}
$$

The potential energy sums up as many pairs as the gravity does, so a tree sums it up as well. Every
particle sums up the potential of the list of its group, and half the sum over all particles of
their mass times their potential counts every pair once:

$$
\begin{gathered}
\class{potential}{\phi_i} = -G \sum_{j \ne i} \frac{\class{mass}{m_j}}
  {\sqrt{\lVert \class{separation}{x_j - x_i} \rVert^2 + \class{softening}{\varepsilon^2}}} \\
W = \tfrac{1}{2} \sum_i \class{mass}{m_i}\, \class{potential}{\phi_i}
\end{gathered}
$$

`BarnesHut#potentialEnergy`

`BarnesHut#sumPotentials`

Every 30 steps, at the end of a step, where the leapfrog leaves positions and velocities at the same
time, a tree of its own with the opening angle 0.4 measures the energy:

`GalaxySimulation#measureEnergy`

Its potential energy errs by about 0.01 %, whatever the opening angle of the gravity. The tree of
the gravity would add its own error to the measurement: at $\class{openingAngle}{\theta} = 1.5$, it
misses the potential energy by 0.4 to 0.7 %, so that changing $\class{openingAngle}{\theta}$ from
0.7 to 1.5 would make the energy seem to jump by 0.8 to 1.4 %, though no particle moved. A
measurement takes about as long as two steps at $\class{openingAngle}{\theta} = 0.7$, which adds
about 6 % to the time of the 30 steps.

## Galaxies in equilibrium

Each galaxy starts in equilibrium, built with the method of Hernquist (1993): the densities follow
exact profiles, and the velocities are drawn from Gaussian distributions whose moments satisfy the
Jeans equations, the moments of the collisionless Boltzmann equation.

The disk is exponential in radius and isothermal in height, with the mass $4 \cdot 10^{10} M_\odot$,
the scale length $\class{scaleLength}{h} = 3$ kpc and the scale height $z_0 = 0.3$ kpc, cut off at
18 kpc:

$$
\begin{gathered}
\Sigma(\class{radius}{R}) \propto e^{-\class{radius}{R}/\class{scaleLength}{h}} \\
\rho(\class{radius}{R}, z) \propto e^{-\class{radius}{R}/\class{scaleLength}{h}}
  \operatorname{sech}^2 \frac{z}{z_0}
\end{gathered}
$$

Bulge and halo follow the profile of Hernquist (1990), which holds the share $r^2 / (r + a)^2$ of
its mass within the radius $r$; inverting that share draws the radius of every particle:

$$
\begin{gathered}
\class{density}{\rho(r)} \propto \frac{1}{r (r + a)^3} \\
\frac{M(r)}{M} = \frac{r^2}{(r + a)^2}
\end{gathered}
$$

The bulge has the scale length $a = 0.6$ kpc and the mass $10^{10} M_\odot$ within 6 kpc, where it
ends, the halo $a = 8$ kpc and the mass $1.2 \cdot 10^{11} M_\odot$ within 40 kpc.

The stars of the disk circle its axis. The gravity of the particles themselves, measured with test
particles, gives the speed $v_c(R)$ of circular orbits, their angular velocity $\Omega = v_c / R$,
and the epicyclic frequency $\class{kappa}{\kappa}$ of small radial oscillations around them:

$$
\class{kappa}{\kappa}^2 = \frac{1}{\class{radius}{R}^3}
  \frac{d \left( \class{radius}{R}^2 v_c^2 \right)}{d\class{radius}{R}}
$$

The radial velocity dispersion falls off as
$\sigma_R \propto e^{-\class{radius}{R}/2\class{scaleLength}{h}}$, like $\sqrt{\Sigma}$, and its
scale sets Toomre's stability parameter of a disk of stars,

$$
\class{toomreQ}{Q} = \frac{\sigma_R\, \class{kappa}{\kappa}}{3.36\, G\, \Sigma},
$$

to 1.2 at $\class{radius}{R} = 2.5\, \class{scaleLength}{h}$: above 1, the disk is stable against
clumping (Toomre, 1964). The epicyclic approximation sets the azimuthal dispersion,
$\class{dispersionRatio}{\sigma_\phi^2 / \sigma_R^2} = \class{kappa}{\kappa}^2 / 4\Omega^2$, and the
thickness of the disk the vertical one, $\sigma_z^2 = \pi G \Sigma z_0$, as in an isothermal sheet
(Spitzer, 1942). The Jeans equation then gives the mean azimuthal velocity, which lags behind $v_c$
by the asymmetric drift (Binney and Tremaine, 2008):

$$
\class{meanVelocity}{\bar v_\phi^2} = v_c^2 + \class{radialDispersion}{\sigma_R^2} \left( 1
  - \class{dispersionRatio}{\frac{\sigma_\phi^2}{\sigma_R^2}}
  + \frac{d \ln \left( \Sigma \class{radialDispersion}{\sigma_R^2} \right)}{d \ln \class{radius}{R}}
  \right)
$$

Here $\Sigma \class{radialDispersion}{\sigma_R^2} \propto e^{-2R/h}$, so the last term is
$-2\class{radius}{R}/\class{scaleLength}{h}$.

`Galaxy#setDiskVelocities`

Bulge and halo do not rotate. Their random motions are alike in all directions, with the dispersion
that balances the inward gravity $\class{gravity}{g(r)}$ in the Jeans equation of a spherical
system:

$$
\class{dispersion}{\sigma^2(r)} = \frac{1}{\class{density}{\rho(r)}}
  \int_r^\infty \class{density}{\rho(r')}\, \class{gravity}{g(r')}\, dr'
$$

The integral runs over the logarithm of the radius and takes the logarithmic mean of the integrand
at neighboring radii, which is exact for powers of the radius. No particle starts faster than 95 %
of the speed of escape, which the tails of the Gaussian distribution would exceed.

`RadialGravity#jeansDispersionSquared`

## The encounter

The galaxies fall towards each other on the parabolic orbit of two points of their masses, starting
60 kpc apart. On a parabola with the pericenter $\class{pericenter}{r_p}$ around the total mass
$\class{totalMass}{M}$, distance and velocity at the true anomaly $\class{anomaly}{\nu}$ are

$$
\begin{gathered}
r = \frac{2 \class{pericenter}{r_p}}{1 + \cos\class{anomaly}{\nu}} \\
v = \class{speed}{\sqrt{\frac{G \class{totalMass}{M}}{2 \class{pericenter}{r_p}}}}\,
  \left( -\sin\class{anomaly}{\nu},\ 1 + \cos\class{anomaly}{\nu} \right)
\end{gathered}
$$

with the $x$ axis pointing to the pericenter. Each galaxy takes its share of the separation, so that
their center of mass rests at the origin.

`GalaxySimulation#putOnParabolicOrbit`

For points, Barker's equation gives the time to the pericenter,
$t = \tfrac{1}{2} \sqrt{p^3 / G \class{totalMass}{M}}\, \left( D + D^3 / 3 \right)$ with
$p = 2 \class{pericenter}{r_p}$ and $D = \lvert \tan(\class{anomaly}{\nu}/2) \rvert$: for two equal
galaxies of $3.4 \cdot 10^{11} M_\odot$ in all and $\class{pericenter}{r_p} = 10$ kpc, $t = 45.7$
units of time, or 216 Myr. The extended galaxies pass each other after about 200 Myr. Their tides
tear long tails out of the disks, and their halos brake them by dynamical friction (Chandrasekhar,
1943), until they fall back together and merge into one after about 700 to 800 Myr.

```experiment
question: >-
    The first disk turns the way the galaxies circle each other. Which disk loses more stars to
    its tails if the second one turns the other way, at $\class{inclination}{i} = 180^\circ$?
values:
    inclination: 180
explanation: >-
    The first one, by far: 500 Myr in, about 40 % of the stars of its disk lie more than 30 kpc
    from its center, against 2 % of the disk that turns against the orbit. Toomre and Toomre
    (1972) explained why. The stars of a disk that turns with the orbit keep pace with the other
    galaxy for a while, so that its tides pull them the same way for long, while those of a disk
    that turns against it feel the pull change its direction quickly. With both disks turning with
    the orbit, at $\class{inclination}{i} = 0^\circ$, each loses about 27 %, and the galaxies merge
    after 600 to 700 Myr instead of 800 to 1,000 Myr.
```

```experiment
question: >-
    What happens if the second galaxy weighs a fifth of the first one, at
    $\class{ratio}{q} = 0.2$?
values:
    ratio: 0.2
explanation: >-
    The small galaxy loses the larger share of its disk: 700 Myr in, about 28 % of its stars lie
    more than 30 kpc from its center, against 13 % of the big one. The two merge only after 1,200
    to 1,500 Myr: dynamical friction brakes a body in proportion to its mass (Chandrasekhar, 1943),
    so the halo of the big galaxy brakes the small one slowly.
```

```experiment
question: >-
    Do the galaxies merge if they pass farther apart, at $\class{pericenter}{r_p} = 30$ kpc?
values:
    pericenter: 30
explanation: >-
    No. Tides fall off with the cube of the distance, so the disks keep all but a few percent of
    their stars, and the outer halos, which the galaxies pass through, brake them too weakly to
    bind them: 1,500 Myr in, they are more than 150 kpc apart and still moving apart at about
    75 km/s.
```

## References

- Barnes, J., and Hut, P. (1986).
  [A hierarchical O(N log N) force-calculation algorithm](https://doi.org/10.1038/324446a0).
  _Nature_ 324, 446.
- Barnes, J. E. (1990).
  [A modified tree code: don't laugh; it runs](<https://doi.org/10.1016/0021-9991(90)90232-P>).
  _Journal of Computational Physics_ 87, 161.
- Binney, J., and Tremaine, S. (2008). [Galactic Dynamics](https://doi.org/10.1515/9781400828722),
  2nd edition. Princeton University Press.
- Chandrasekhar, S. (1943).
  [Dynamical friction. I. General considerations: the coefficient of dynamical friction](https://doi.org/10.1086/144517).
  _The Astrophysical Journal_ 97, 255.
- Hernquist, L. (1990).
  [An analytical model for spherical galaxies and bulges](https://doi.org/10.1086/168845). _The
  Astrophysical Journal_ 356, 359.
- Hernquist, L. (1993). [N-body realizations of compound galaxies](https://doi.org/10.1086/191784).
  _The Astrophysical Journal Supplement Series_ 86, 389.
- Spitzer, L., Jr. (1942).
  [The dynamics of the interstellar medium. III. Galactic distribution](https://doi.org/10.1086/144407).
  _The Astrophysical Journal_ 95, 329.
- Toomre, A. (1964).
  [On the gravitational stability of a disk of stars](https://doi.org/10.1086/147861). _The
  Astrophysical Journal_ 139, 1217.
- Toomre, A., and Toomre, J. (1972). [Galactic bridges and tails](https://doi.org/10.1086/151823).
  _The Astrophysical Journal_ 178, 623.
