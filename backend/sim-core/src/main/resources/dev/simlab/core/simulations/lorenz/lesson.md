---
symbols:
    sigma: { parameter: sigma, code: [sigma] }
    rho: { parameter: rho, code: [rho] }
    beta: { parameter: beta, code: [beta] }
    order: { parameter: order, code: [order] }
    h: { parameter: step, code: [h, stepSize] }
    x: { readout: x, code: [px] }
    y: { readout: y, code: [py] }
    z: { readout: z, code: [pz] }
    xDot: { readout: xDot, code: [] }
    yDot: { readout: yDot, code: [] }
    zDot: { readout: zDot, code: [] }
    rates: { code: [dydt] }
    state: { code: [y] }
    f: { code: [derivative] }
    t: { code: [t] }
    halfStep: { code: [halfStep] }
    sixthStep: { code: [sixthStep] }
    k1: { code: [k1] }
    k2: { code: [k2] }
    k3: { code: [k3] }
    k4: { code: [k4] }
    timeScale: { code: [TIME_SCALE] }
    pendingTime: { code: [pendingTime] }
    speed: { code: [speed] }
    arrowLength: { code: [ARROW_LENGTH] }
---

## The code at a glance

| Method                           | Part       | What it does                                                                                                            |
| -------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------- |
| `LorenzDefinition#create`        | Simulation | starts 1,000 particles with the parameters $\class{sigma}{\sigma}$, $\class{rho}{\rho}$, $\class{beta}{\beta}$          |
| `LorenzSimulation#step`          | Simulation | advances the particles in real time, in steps of $\class{h}{h}$                                                         |
| `RungeKutta4#step`               | Toolkit    | advances any system $\class{rates}{\dot{\mathbf y}} = \class{f}{f}(\class{t}{t}, \class{state}{\mathbf y})$ by one step |
| `Euler#step`                     | Toolkit    | does so with Euler's method, which the controls can choose instead                                                      |
| `LorenzSystem#derivative`        | Simulation | evaluates the Lorenz equations, the $\class{f}{f}$ of this system                                                       |
| `LorenzSimulation#writeReadouts` | Simulation | reports the state of the selected particle and its rates of change                                                      |
| `LorenzSimulation#writeXray`     | Simulation | shows the flow at the points of a grid as arrows                                                                        |

## The Lorenz equations

Edward Lorenz (1963) reduced a model of convection in a layer of fluid that is heated from below
(Saltzman, 1962) to three ordinary differential equations:

$$
\begin{aligned}
\class{rates}{\dot x} &= \class{sigma}{\sigma} (\class{y}{y} - \class{x}{x}) \\
\class{rates}{\dot y} &= \class{x}{x} (\class{rho}{\rho} - \class{z}{z}) - \class{y}{y} \\
\class{rates}{\dot z} &= \class{x}{x} \class{y}{y} - \class{beta}{\beta} \class{z}{z}
\end{aligned}
$$

Here $x$ measures the intensity of the convective motion, $y$ the difference in temperature between
the rising and the falling currents, and $z$ how far the temperature profile from bottom to top
deviates from a straight line. $\class{sigma}{\sigma}$ is the Prandtl number of the fluid,
$\class{rho}{\rho}$ the Rayleigh number divided by the one at which convection sets in, and
$\class{beta}{\beta} = 4 / (1 + a^2)$ depends on the aspect ratio $a$ of the convection rolls.
Lorenz chose $\class{sigma}{\sigma} = 10$, $\class{beta}{\beta} = 8/3$ and $\class{rho}{\rho} = 28$,
the defaults here. Time and all three variables are dimensionless.

The particles do not interact: each one is a state $(x, y, z)$ of its own system. Their states form
a single vector $\class{state}{\mathbf y}$ of 3,000 components, which `LorenzSystem#derivative` maps
to its time derivative $\class{rates}{\dot{\mathbf y}}$ in one pass:

`LorenzSystem#derivative`

Click a particle in the scene, and the equations fill in with its state. The rates on the right are
those that `LorenzSystem#derivative` computes for it, which every frame reports. Both sides show
their values rounded to three significant digits, so they may differ in the last one. Pause the
simulation to read them, and step through it frame by frame:

```calculation
\begin{aligned}
\class{sigma}{\sigma} \cdot (\class{y}{y} - \class{x}{x}) &= \class{xDot}{\dot x} \\
\class{x}{x} \cdot (\class{rho}{\rho} - \class{z}{z}) - \class{y}{y} &= \class{yDot}{\dot y} \\
\class{x}{x} \cdot \class{y}{y} - \class{beta}{\beta} \cdot \class{z}{z} &= \class{zDot}{\dot z}
\end{aligned}
```

`LorenzSimulation#writeReadouts`

## The flow in the x-ray

The equations assign a velocity to every state, whether a particle is there or not: a flow through
the space of states. The x-ray shows it on a grid of $9 \times 9 \times 9$ points $g$, from $-20$ to
$20$ in $x$ and $y$ and from $5$ to $45$ in $z$, 5 apart. At every point, an arrow of the same
length $\class{arrowLength}{L} = 2.5$ points the way that the flow carries a state, and its color
tells the speed of the flow, from dim blue where it is slow to white where it is fast:

$$
g \;\longrightarrow\; g + \class{arrowLength}{L}\,
  \frac{\class{f}{f}(g)}{\class{speed}{\lVert \class{f}{f}(g) \rVert}}
$$

`LorenzSimulation#writeXray`

Along the $z$ axis, the arrows point straight down: with $\class{x}{x} = \class{y}{y} = 0$, the
equations leave $\class{rates}{\dot z} = -\class{beta}{\beta} \class{z}{z}$ alone. The flow is
fastest far out, where $\class{x}{x}$ and $\class{y}{y}$ differ the most; at a fifth of the points,
its speed exceeds 400.

## Why the particles spread

The flow contracts every volume of states at the same rate, since its divergence is constant:

$$
\begin{aligned}
\nabla \cdot \class{f}{f} &= \frac{\partial \dot x}{\partial x} + \frac{\partial \dot y}{\partial y}
  + \frac{\partial \dot z}{\partial z} \\
&= -(\class{sigma}{\sigma} + 1 + \class{beta}{\beta})
\end{aligned}
$$

A volume $V$ of states thus shrinks as
$V(t) = V(0)\, e^{-(\class{sigma}{\sigma} + 1 + \class{beta}{\beta}) t}$, by a factor of
$e^{-13.67}$ per unit of time for the default parameters. Every state approaches a set without
volume, the attractor, whose fractal dimension is about 2.06.

```experiment
question: >-
    What do the particles do for $\class{rho}{\rho} < 1$, where the fluid is heated too weakly to
    overturn?
values:
    rho: 0.5
explanation: >-
    They fall into the origin within a few seconds: the fluid conducts the heat without moving.
    The function $V = x^2 / \class{sigma}{\sigma} + y^2 + z^2$ decreases along every orbit for
    $\class{rho}{\rho} < 1$, so the origin attracts every state (Strogatz, 2018). Close to it, the
    particles approach it along the slowest direction of the linearized flow. In $x$ and $y$, its
    eigenvalues solve
    $\lambda^2 + (\class{sigma}{\sigma} + 1)\lambda = \class{sigma}{\sigma}(\class{rho}{\rho} - 1)$,
    and the slower one, $-0.475$, shrinks their distance from the origin by a factor of $e$ every
    2.1 units of time, or 2.6 seconds. Along $z$, the eigenvalue $-\class{beta}{\beta}$ shrinks it
    faster.
```

For $\class{rho}{\rho} < 1$, the origin is the only fixed point, and it attracts every state: the
fluid conducts the heat without moving. For $\class{rho}{\rho} > 1$, the origin turns unstable, and
two fixed points appear, steady convection rolls that turn one way or the other:

$$
C_\pm = \left( \pm\sqrt{\class{beta}{\beta} (\class{rho}{\rho} - 1)},\;
  \pm\sqrt{\class{beta}{\beta} (\class{rho}{\rho} - 1)},\; \class{rho}{\rho} - 1 \right)
$$

```experiment
question: Where do the particles go at $\class{rho}{\rho} = 15$?
values:
    rho: 15
explanation: >-
    They settle on the fixed points $C_\pm$, about half of them on each, depending on where they
    were: steady convection rolls that turn one way or the other. Above
    $\class{rho}{\rho} \approx 13.93$, a particle may wander about chaotically before it settles,
    a transient chaos that lasts the longer the closer $\class{rho}{\rho}$ comes to 24.06 (Kaplan
    and Yorke, 1979; Sparrow, 1982). At $\class{rho}{\rho} = 15$, every particle has settled after
    about 20 seconds; at $\class{rho}{\rho} = 20$, some take more than a minute.
```

They lose their stability in a subcritical Hopf bifurcation at

$$
\class{rho}{\rho}_H = \frac{\class{sigma}{\sigma} (\class{sigma}{\sigma} + \class{beta}{\beta} + 3)}
  {\class{sigma}{\sigma} - \class{beta}{\beta} - 1} = \frac{470}{19} \approx 24.74
$$

for $\class{sigma}{\sigma} = 10$ and $\class{beta}{\beta} = 8/3$. Above it, as at
$\class{rho}{\rho} = 28$, a state circles $C_+$ and $C_-$ in turn and never repeats itself: the
strange attractor.

```experiment
question: >-
    Lorenz chose $\class{sigma}{\sigma} = 10$. What happens at $\class{sigma}{\sigma} = 3$, below
    $\class{beta}{\beta} + 1$?
values:
    sigma: 3
explanation: >-
    Every particle settles on $C_\pm$ within about 15 seconds. For
    $\class{sigma}{\sigma} < \class{beta}{\beta} + 1$, the denominator of $\class{rho}{\rho}_H$ is
    negative: the fixed points keep their stability however large $\class{rho}{\rho}$ grows. Set
    $\class{sigma}{\sigma}$ back to 10, and they turn unstable again; but distances from them grow
    only as $e^{0.094\, t}$ there, so the particles stay for about two minutes before the chaos
    returns. Restart, and they start over on the attractor.
```

On the attractor, neighboring states drift apart exponentially, on average as
$\delta(t) \approx \delta(0)\, e^{\lambda_1 t}$, with the largest Lyapunov exponent
$\lambda_1 \approx 0.906$. The other two exponents are $0$, along the flow, and $-14.57$; the three
add up to the divergence $-13.67$ (Sprott, 2003). The particles start $10^{-4}$ apart, so their
distances reach the size of the attractor, about 20, after

$$
t \approx \frac{1}{\lambda_1} \ln \frac{20}{10^{-4}} \approx 13.5
$$

units of time. In this simulation, the particles cover the whole attractor after about 12 units of
time, or 15 seconds.

## One step of Runge-Kutta

The classic Runge-Kutta method (Kutta, 1901) advances a system
$\class{rates}{\dot{\mathbf y}} = \class{f}{f}(\class{t}{t}, \class{state}{\mathbf y})$ by a step
$\class{h}{h}$ with four evaluations of $\class{f}{f}$:

$$
\begin{aligned}
\class{k1}{\mathbf k_1} &= \class{f}{f}(\class{t}{t},\, \class{state}{\mathbf y}) \\
\class{k2}{\mathbf k_2} &= \class{f}{f}\!\left(\class{t}{t} + \class{halfStep}{\tfrac{h}{2}},\,
  \class{state}{\mathbf y} + \class{halfStep}{\tfrac{h}{2}} \class{k1}{\mathbf k_1}\right) \\
\class{k3}{\mathbf k_3} &= \class{f}{f}\!\left(\class{t}{t} + \class{halfStep}{\tfrac{h}{2}},\,
  \class{state}{\mathbf y} + \class{halfStep}{\tfrac{h}{2}} \class{k2}{\mathbf k_2}\right) \\
\class{k4}{\mathbf k_4} &= \class{f}{f}(\class{t}{t} + \class{h}{h},\,
  \class{state}{\mathbf y} + \class{h}{h} \class{k3}{\mathbf k_3}) \\
\class{state}{\mathbf y}(\class{t}{t} + \class{h}{h}) &\approx \class{state}{\mathbf y}
  + \class{sixthStep}{\tfrac{h}{6}} \left(\class{k1}{\mathbf k_1} + 2 \class{k2}{\mathbf k_2}
  + 2 \class{k3}{\mathbf k_3} + \class{k4}{\mathbf k_4}\right)
\end{aligned}
$$

Its error is $O(h^5)$ per step and $O(h^4)$ over a fixed span of time: half the step leaves a
sixteenth of the error, as `RungeKutta4Test` confirms. The toolkit holds the method for any system,
and it allocates no memory per step:

`RungeKutta4#step`

## Euler's method

Euler's method, the simplest of all, follows the rates at the start of a step for the whole step:

$$
\class{state}{\mathbf y}(\class{t}{t} + \class{h}{h}) \approx \class{state}{\mathbf y}
  + \class{h}{h}\, \class{f}{f}(\class{t}{t}, \class{state}{\mathbf y})
$$

It evaluates $\class{f}{f}$ once per step instead of four times. Its error is $O(h^2)$ per step and
$O(h)$ over a fixed span of time: half the step leaves half the error (`EulerTest`).

`Euler#step`

The controls choose the method by its order $\class{order}{p}$, 1 for Euler's method and 4 for the
classic Runge-Kutta method, which for both also counts the evaluations of $\class{f}{f}$ per step.
Both methods implement `Integrator`, so the simulation simply swaps one for the other:

`LorenzSimulation#integratorOfOrder`

## Too large a step

On the linear equation $\dot u = \lambda u$, whose exact solution grows by $e^{h\lambda}$ in a step,
one step of Runge-Kutta multiplies $u$ by

$$
R(h\lambda) = 1 + h\lambda + \frac{(h\lambda)^2}{2}
  + \frac{(h\lambda)^3}{6} + \frac{(h\lambda)^4}{24},
$$

the Taylor series of $e^{h\lambda}$ up to the fourth power, and one step of Euler's method by
$1 + h\lambda$. Near a state, the Lorenz equations behave like their linearization, whose
eigenvalues take the place of $\lambda$. Where they have negative real parts, deviations should
shrink, and a method keeps them from growing only while $|R(h\lambda)| \le 1$: for real
$h\lambda$, from $-2.785$ to $0$ for Runge-Kutta and from $-2$ to $0$ for Euler's method. At the
origin, the fastest decaying direction of the Lorenz equations has the eigenvalue

$$
\lambda = -\frac{\class{sigma}{\sigma} + 1}{2}
  - \sqrt{\left(\frac{\class{sigma}{\sigma} - 1}{2}\right)^2
  + \class{sigma}{\sigma} \class{rho}{\rho}},
$$

$-22.8$ for the defaults and $-60.3$ for the largest $\class{sigma}{\sigma} = 30$ and
$\class{rho}{\rho} = 60$ that the controls allow. There, the default step $\class{h}{h} = 0.005$
keeps $|h\lambda|$ below $0.31$, far within the stable interval.

Around the fixed points $C_\pm$, the linearization has the eigenvalue $-13.85$ and the pair
$0.094 \pm 10.19\,i$: states spiral away from $C_\pm$ slowly, at the rate $0.094$, while they
circle it 1.6 times per unit of time. On that pair, Euler's method multiplies a deviation by
$|1 + h\lambda| = 1.0018$ per step of $\class{h}{h} = 0.005$, where the exact factor is
$e^{0.094 h} = 1.0005$: it lets the spirals grow at the rate $0.353$ instead of $0.094$. Euler's
method amplifies every oscillation, as $|1 + i\omega h| = \sqrt{1 + \omega^2 h^2} > 1$.

```experiment
question: What does Euler's method make of the attractor, at the same step?
values:
    order: 1
explanation: >-
    It swells, as the spirals around $C_\pm$ grow almost four times as fast as they should: the
    particles climb up to $z \approx 50.4$ instead of $47.5$. With $\class{h}{h} = 0.02$, they
    climb up to $61.5$.
```

```experiment
question: And with Euler's method at $\class{h}{h} = 0.04$?
values:
    order: 1
    step: 0.04
explanation: >-
    The particles fly off: the first within a second, half of them within three seconds, all
    within about half a minute. On the pair of $C_\pm$, $|1 + h\lambda| = 1.083$ lets the spirals
    grow at the rate $2.0$, so they carry the particles out to where the rates are larger, until
    the steps overshoot so far that the particles leave for infinity. Reset the parameters and
    restart to bring them back.
```

Runge-Kutta stays stable at the origin up to $\class{h}{h} = 2.785 / 22.8 = 0.122$, but stability
is not yet accuracy. On the pair of $C_\pm$, $|R(h\lambda)|$ falls below 1 from
$\class{h}{h} \approx 0.106$ on, although the exact solution grows.

```experiment
question: What happens with Runge-Kutta at $\class{h}{h} = 0.12$, still stable at the origin?
values:
    step: 0.12
explanation: >-
    The chaos dies out: within a minute, 19 of 20 particles come to rest on $C_\pm$, and not one
    flies off. Every step multiplies their distance from $C_\pm$ by $|R(h\lambda)| = 0.992$, where
    the exact factor would be $e^{0.094 h} = 1.011$: the method damps the spirals that should
    grow, and so turns the unstable fixed points into stable ones.
```

```experiment
question: And with Runge-Kutta at $\class{h}{h} = 0.2$?
values:
    step: 0.2
explanation: >-
    Three quarters of the particles fly off within two seconds. Near the origin,
    $h\lambda = -4.57$ lies far outside the stable interval, and every step multiplies a deviation
    along the fastest direction by $R(h\lambda) = 9.1$. Nearly a tenth of the particles come to
    rest on $C_\pm$, where $h\lambda = -2.77$ just stays inside it. Reset the parameters and restart
    to bring them back.
```

## Steps in real time

`LorenzSimulation#step` advances the particles by $\class{timeScale}{0.8}$ units of time per second,
in steps of exactly $\class{h}{h}$, as the analysis above assumes. A frame of $\Delta t = 1/30$ s
adds $\class{timeScale}{0.8}\, \Delta t$ to the time $\class{pendingTime}{\tau}$ that no step has
covered yet, and the frame takes

$$
n = \left\lfloor \frac{\class{pendingTime}{\tau} + \class{timeScale}{0.8}\, \Delta t}
  {\class{h}{h}} \right\rfloor
$$

steps, which leave the rest of $\class{pendingTime}{\tau}$ for the next frame:

`LorenzSimulation#step`

With the default $\class{h}{h} = 0.005$, that is 5 or 6 steps per frame, 5.33 on average, and
$\class{order}{p} = 4$ evaluations of the equations per step for each of the 1,000 particles.
Steps longer than a frame move the particles only every few frames, so that you can watch every
step.

The particles start on the attractor. From $(1, 1, 1)$, a single point first circles one of the
fixed points, where differences between neighbors hardly grow, so it runs alone for 20 units of
time, with the classic Runge-Kutta method in steps of 0.005, whatever the controls say. The
particles then line up along $x$ from where it ended, $10^{-4}$ apart.

`LorenzSimulation#pointOnAttractor`

## References

- Kaplan, J. L., and Yorke, J. A. (1979).
  [Preturbulence: a regime observed in a fluid flow model of Lorenz](https://doi.org/10.1007/BF01221359).
  _Communications in Mathematical Physics_ 67, 93.
- Kutta, W. (1901). Beitrag zur näherungsweisen Integration totaler Differentialgleichungen.
  _Zeitschrift für Mathematik und Physik_ 46, 435.
- Lorenz, E. N. (1963).
  [Deterministic nonperiodic flow](<https://doi.org/10.1175/1520-0469(1963)020%3C0130:DNF%3E2.0.CO;2>).
  _Journal of the Atmospheric Sciences_ 20, 130.
- Saltzman, B. (1962).
  [Finite amplitude free convection as an initial value problem—I](<https://doi.org/10.1175/1520-0469(1962)019%3C0329:FAFCAA%3E2.0.CO;2>).
  _Journal of the Atmospheric Sciences_ 19, 329.
- Sparrow, C. (1982).
  [The Lorenz Equations: Bifurcations, Chaos, and Strange Attractors](https://doi.org/10.1007/978-1-4612-5767-7).
  Springer.
- Sprott, J. C. (2003).
  [Chaos and Time-Series Analysis](https://doi.org/10.1093/oso/9780198508397.001.0001). Oxford
  University Press.
- Strogatz, S. H. (2018). [Nonlinear Dynamics and Chaos](https://doi.org/10.1201/9780429492563), 2nd
  edition. CRC Press.
