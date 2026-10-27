---
symbols:
    wind: { parameter: wind, code: [windSpeed] }
    gusts: { parameter: gusts, code: [gusts] }
    gravity: { parameter: gravity, code: [gravity] }
    spacing: { code: [spacing] }
    substep: { code: [substep] }
    lastSubstep: { code: [lastSubstep] }
    damping: { code: [DAMPING] }
    force: { code: [force] }
    previous: { code: [previous] }
    restLength: { code: [restLength] }
    inverseMassA: { code: [inverseMassA] }
    inverseMassB: { code: [inverseMassB] }
    compliance: { code: [compliance] }
    reach: { code: [maxLength] }
    speed: { readout: speed, code: [speed] }
    turn: { readout: turn, code: [turn] }
    maxTurn: { code: [MAX_TURN] }
    gustNoise: { readout: gustNoise, code: [gustNoise] }
    turnNoise: { readout: turnNoise, code: [turnNoise] }
    relativeWind: { code: [relativeX, relativeY, relativeZ] }
    normal: { code: [normalX, normalY, normalZ] }
    flow: { code: [flow] }
    normalSquared: { code: [normalSquared] }
    airDrag: { code: [AIR_DRAG] }
    friction: { code: [FRICTION] }
---

## The code at a glance

| Method                          | Part       | What it does                                                |
| ------------------------------- | ---------- | ----------------------------------------------------------- |
| `ClothDefinition#create`        | Simulation | starts a cloth of 48 × 48 particles                         |
| `ClothSimulation#step`          | Simulation | splits every frame into substeps and runs the methods below |
| `ClothSimulation#addWindForces` | Simulation | computes the force of the gusty wind on every particle      |
| `ClothSimulation#integrate`     | Simulation | predicts the new positions with Verlet integration          |
| `DistanceConstraints#project`   | Toolkit    | corrects them, so that the cloth keeps its shape            |
| `Tethers#project`               | Toolkit    | keeps every particle within reach of the pins               |
| `ClothSimulation#collide`       | Simulation | pushes particles out of the sphere and the floor            |
| `ClothSimulation#writeReadouts` | Simulation | reports the wind with every frame                           |
| `ClothSimulation#writeXray`     | Simulation | shows the strain of the constraints in the x-ray            |

## A cloth of particles

The cloth is a square of $48 \times 48$ particles, 2 m wide, so neighbors lie
$\class{spacing}{s} = 2/47\ \text{m} \approx 4.3\ \text{cm}$ apart. With $0.3\ \text{kg/m}^2$, the
areal density of a heavy cotton fabric such as canvas, every cell weighs
$0.3\, \class{spacing}{s}^2 \approx 0.54\ \text{g}$. Every particle carries a quarter of each cell
around it: a whole cell inside the cloth, half a cell along its edges and a quarter in its corners.
The whole cloth weighs 1.2 kg.

Distance constraints hold the particles together: 4,512 between neighbors in a row or a column keep
the cloth from stretching, 4,418 along the diagonals of the cells from shearing, and 4,416 between
next-but-one neighbors in a row or a column from bending, 13,346 in all:

`ParticleGrid#constraints`

## Small steps

Position-based dynamics (Müller et al., 2007) moves particles in two phases: it predicts where the
forces take them, then moves the predicted positions until they satisfy the constraints; velocities
follow from the positions. Its extended form, XPBD (Macklin, Müller and Chentanez, 2016), gives
every constraint a compliance, the inverse of its stiffness, so that the cloth is equally stiff for
any length of step. Macklin et al. (2019) found that many substeps with a single pass over the
constraints beat one step with many passes. Here, every frame of $1/30$ s runs ten substeps of at
most $1/300$ s:

`ClothSimulation#step`

## Predicting the positions

Verlet integration keeps the position of the previous substep instead of a velocity. After a substep
of length $\class{lastSubstep}{\Delta t'}$, a particle of mass $m$ under the force
$\class{force}{F}$ moves in a substep of length $\class{substep}{\Delta t}$ to

$$
\begin{aligned}
x_{n+1} = x_n &+ e^{-\class{damping}{\gamma} \class{substep}{\Delta t}}\,
  \frac{\class{substep}{\Delta t}}{\class{lastSubstep}{\Delta t'}}\,
  (x_n - \class{previous}{x_{n-1}}) \\
&+ \class{substep}{\Delta t}^2 \left( \frac{\class{force}{F}}{m} - \class{gravity}{g}\, e_y \right)
\end{aligned}
$$

The factor $\class{substep}{\Delta t} / \class{lastSubstep}{\Delta t'}$ carries the velocity over
when the length of the substeps changes, and
$e^{-\class{damping}{\gamma} \class{substep}{\Delta t}}$, with
$\class{damping}{\gamma} = 0.2\ \text{s}^{-1}$, damps it a little, in addition to the drag of the
wind. The velocity carried over is limited to 30 m/s, three times as fast as the strongest gusts:
large corrections by the constraints would otherwise build up into large velocities.

`ClothSimulation#integrate`

## Satisfying the constraints

A distance constraint between the particles $a$ and $b$ with the rest length
$\class{restLength}{l_0}$ holds when

$$
\begin{gathered}
C = \lVert x_b - x_a \rVert - \class{restLength}{l_0} = 0 \\
\nabla_{x_a} C = -n, \qquad \nabla_{x_b} C = n \\
n = \frac{x_b - x_a}{\lVert x_b - x_a \rVert}
\end{gathered}
$$

XPBD moves both particles along these gradients, weighted by their inverse masses $w = 1/m$, by the
change $\Delta\lambda$ of the Lagrange multiplier $\lambda$ of the constraint, with the compliance
$\class{compliance}{\alpha}$ scaled to the substep:

$$
\begin{gathered}
\Delta\lambda = \frac{-C - \tilde\alpha\, \lambda}
  {\class{inverseMassA}{w_a} + \class{inverseMassB}{w_b} + \tilde\alpha}, \qquad
\tilde\alpha = \frac{\class{compliance}{\alpha}}{\class{substep}{\Delta t}^2} \\
\Delta x_a = -\class{inverseMassA}{w_a}\, \Delta\lambda\, n, \qquad
\Delta x_b = \class{inverseMassB}{w_b}\, \Delta\lambda\, n
\end{gathered}
$$

With a single pass per substep, $\lambda$ starts at zero, so
$\Delta\lambda = -C / (\class{inverseMassA}{w_a} + \class{inverseMassB}{w_b} + \tilde\alpha)$. A
rigid constraint, $\class{compliance}{\alpha} = 0$, moves the two particles all the way to their
rest length, the heavier one less. The constraints are corrected one after another (Gauss-Seidel),
each on the positions that the ones before it left; the constraints against stretching come first,
row by row from the pins, so that a single pass carries corrections all the way down the cloth.

`DistanceConstraints#project`

The compliances are $0$ against stretching, $10^{-4}$ m/N against shearing and $2 \cdot 10^{-2}$ m/N
against bending, stiffnesses of $\infty$, $10^4$ and 50 N/m. In a substep of $1/300$ s,
$\tilde\alpha$ is 9 and 1,800 per kilogram, against
$\class{inverseMassA}{w_a} + \class{inverseMassB}{w_b} \approx 3{,}700$ per kilogram for two
particles inside the cloth: shearing gives way a little, bending a lot, so the cloth falls into
folds.

## Tethers

A single pass leaves a hanging cloth stretched, most of all next to its pins, because the
constraints carry its weight from particle to particle. Long range attachments (Kim, Chentanez and
Müller-Fischer, 2012) carry it to the pins in one go: no particle may move farther from a pin than
it lies in the flat cloth, $\lVert x - x_\text{pin} \rVert \le \class{reach}{L}$. A particle that
lies farther away moves straight back onto the sphere of radius $\class{reach}{L}$ around the pin;
closer ones stay where they are.

`Tethers#project`

## The strain in the x-ray

The x-ray shows how well a single pass per substep keeps the cloth from stretching: each of the
4,512 constraints along the rows and columns, colored by its strain, the change of its length
relative to its rest length:

$$
\varepsilon = \frac{\lVert x_b - x_a \rVert}{\class{restLength}{l_0}} - 1
$$

Gray constraints keep their length, blue ones are compressed and red ones stretched, in full color
from 2 % on. The surface of the cloth turns translucent meanwhile, so that its constraints show
through.

`DistanceConstraints#strain`

`ClothSimulation#writeXray`

Rigid as they are, these constraints do not keep their length exactly, as a single pass corrects
every constraint once, on positions that the constraints after it move again. In the wind, 98 % of
them stay between about $-1$ % and $+3$ % of their rest length, and the longest are stretched by up
to 5 %. Grab the cloth and pull: next to the hand, which moves the particle it holds wherever it
goes, single constraints are stretched by 20 to 65 %.

## Gusty wind

The wind blows along $z$. With the speed $\class{wind}{v}$ and the gusts $\class{gusts}{\Delta v}$
of the controls, its velocity $w$ has the magnitude $\class{speed}{\lvert w \rvert}$ and turns away
from the axis by the angle $\class{turn}{\varphi}$ in radians:

$$
\begin{aligned}
\class{speed}{\lvert w \rvert} &= \max(0,\ \class{wind}{v}
  + \class{gusts}{\Delta v}\, \class{gustNoise}{N_1}(t)) \\
\class{turn}{\varphi} &= \class{maxTurn}{0.35}\, \class{turnNoise}{N_2}(t) \\
w &= \class{speed}{\lvert w \rvert}\, (\sin \class{turn}{\varphi},\ 0,\ \cos \class{turn}{\varphi})
\end{aligned}
$$

$\class{gustNoise}{N_1}$ and $\class{turnNoise}{N_2}$ are smooth random noise between $-1$ and $1$
that changes over 1.2 s and 3.5 s (`SmoothNoise#valueAt`).

`ClothSimulation#windSpeedAt`

`ClothSimulation#windTurnAt`

Every frame reports the wind that the next substep feels, so the formulas fill in as the gusts come
and go. Pause the simulation to read them, or slow it down:

```calculation
\begin{aligned}
\max(0,\ \class{wind}{v} + \class{gusts}{\Delta v} \cdot \class{gustNoise}{N_1})
  &= \class{speed}{\lvert w \rvert} \\
0.35 \cdot \class{turnNoise}{N_2} &= \class{turn}{\varphi}
\end{aligned}
```

`ClothSimulation#writeReadouts`

Every substep starts with the force of this wind on every particle:

`ClothSimulation#addWindForces`

Air presses on a cell with the dynamic pressure of the part of the wind that hits it head-on: the
component $v_n = \class{relativeWind}{w_r} \cdot n$ along the unit normal $n$ of the cell, where
$\class{relativeWind}{w_r} = w - u$ is the wind relative to the cell, which moves with the mean
velocity $u$ of its corners. On a cell of area $A$, the force is

$$
\begin{aligned}
F &= \tfrac{1}{2}\, \class{airDrag}{\rho\, c}\, A\, v_n \lvert v_n \rvert\, n \\
  &= \tfrac{1}{4}\, \class{airDrag}{\rho\, c}\,
    \frac{(\class{flow}{N \cdot w_r})\, \lvert \class{flow}{N \cdot w_r} \rvert}
    {\class{normalSquared}{\lVert N \rVert^2}}\, \class{normal}{N}
\end{aligned}
$$

with the density of air $\rho = 1.2\ \text{kg/m}^3$ and the drag coefficient $c = 1$, a little below
that of a solid plate facing the wind, as some air passes through cloth. The cross product
$\class{normal}{N} = d_1 \times d_2$ of the diagonals of the cell is normal to it and twice as long
as its area, $\lVert \class{normal}{N} \rVert = 2A$. The model leaves out the friction of the air
along the cloth. Each corner takes a quarter of the force.

`ClothSimulation#addWindForceOnCell`

```experiment
question: >-
    The wind presses with the square of its speed. How far does a steady wind of
    $\class{wind}{v} = 8$ m/s, four times as fast as the default, lift the cloth?
values:
    wind: 8
    gusts: 0
explanation: >-
    Within a few seconds, the cloth rises to about 75° from the vertical, clear of the sphere, and
    flaps around that angle. A flat sheet that hangs from its upper edge at the angle $\theta$
    from the vertical feels the pressure
    $\tfrac{1}{2} \class{airDrag}{\rho c} (\class{wind}{v} \cos\theta)^2$ of the head-on part of
    the wind, against $\sigma \class{gravity}{g} \sin\theta$, the part of its weight across it,
    with the areal density $\sigma = 0.3$ kg/m². The two balance where
    $\tan\theta \sec\theta = \class{airDrag}{\rho c} \class{wind}{v}^2 / 2\sigma\class{gravity}{g}$:
    at 74° for 8 m/s, and at 34° for 2 m/s, where the cloth hangs at 35° without gusts.
```

```experiment
question: What does the cloth do without its weight, at $\class{gravity}{g} = 0$?
values:
    gravity: 0
explanation: >-
    It swings up into the wind and streams out behind its pins, nearly flat, flapping a little.
    The wind presses only along the normal of the cloth, on the part of the wind that hits it
    head-on: it turns the cloth until the cloth lies along the wind, where no part of it faces the
    wind any more. Only the friction of the air along the cloth, which the model leaves out, would
    still pull on it.
```

## Collisions

Last in every substep, particles inside the sphere move out onto its surface, and particles below
the floor up onto it, both with a margin of 1 cm, so that the triangles between the particles stay
outside as well. While a particle touches either of them, its velocity decays as
$e^{-\class{friction}{20}\, t}$ with $t$ in seconds: a simple friction, which keeps the cloth from
sliding off the sphere at once.

`ClothSimulation#collide`

## References

- Kim, T.-Y., Chentanez, N., and Müller-Fischer, M. (2012).
  [Long range attachments: a method to simulate inextensible clothing in computer games](https://doi.org/10.2312/SCA/SCA12/305-310).
  _Eurographics/ACM SIGGRAPH Symposium on Computer Animation_, 305.
- Macklin, M., Müller, M., and Chentanez, N. (2016).
  [XPBD: position-based simulation of compliant constrained dynamics](https://doi.org/10.1145/2994258.2994272).
  _Proceedings of the 9th International Conference on Motion in Games_, 49.
- Macklin, M., Storey, K., Lu, M., Terdiman, P., Chentanez, N., Jeschke, S., and Müller, M. (2019).
  [Small steps in physics simulation](https://doi.org/10.1145/3309486.3340247). _Proceedings of the
  18th Annual ACM SIGGRAPH/Eurographics Symposium on Computer Animation_, 1.
- Müller, M., Heidelberger, B., Hennix, M., and Ratcliff, J. (2007).
  [Position based dynamics](https://doi.org/10.1016/j.jvcir.2007.01.005). _Journal of Visual
  Communication and Image Representation_ 18, 109.
