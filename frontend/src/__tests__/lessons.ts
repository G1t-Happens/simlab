import type { CalculationBlock, CodeBlock, ExperimentBlock, Lesson } from '@/api/lessons'

/** method of the simulation with a link to its lines on GitHub, the symbols σ and y occur in it */
export const derivative: CodeBlock = {
  kind: 'Code',
  reference: 'LorenzSystem#derivative',
  path: 'dev/simlab/core/simulations/lorenz/LorenzSystem.java',
  firstLine: 23,
  lastLine: 26,
  code: [
    '    @Override',
    '    public void derivative(double t, double[] y, double[] dydt) {',
    '        dydt[0] = sigma * (y[1] - y[0]);',
    '    }',
  ].join('\n'),
  origin: 'SIMULATION',
  url:
    'https://github.com/G1t-Happens/simlab/blob/f00d/backend/sim-core/src/main/java' +
    '/dev/simlab/core/simulations/lorenz/LorenzSystem.java#L23-L26',
  occurrences: [
    { symbol: 'y', line: 24, column: 47, length: 1 },
    { symbol: 'sigma', line: 25, column: 19, length: 5 },
    { symbol: 'y', line: 25, column: 28, length: 1 },
    { symbol: 'y', line: 25, column: 35, length: 1 },
  ],
}

/** method of the toolkit without a link, as from a server built without Git */
export const system: CodeBlock = {
  kind: 'Code',
  reference: 'OdeSystem#derivative',
  path: 'dev/simlab/core/toolkit/ode/OdeSystem.java',
  firstLine: 8,
  lastLine: 8,
  code: '    void derivative(double t, double[] y, double[] dydt);',
  origin: 'TOOLKIT',
  url: null,
  occurrences: [],
}

export const conduction: ExperimentBlock = {
  kind: 'Experiment',
  question: 'What do the particles do for $\\class{rho}{\\rho} < 1$?',
  values: { rho: 0.5 },
  explanation: 'They fall into the origin, as the fluid conducts the heat.',
}

/**
 * The lesson of the Lorenz attractor, cut short: a heading, a formula, two methods and an
 * experiment, with the symbols σ and ρ of parameters and the symbol y of the state
 */
export const lorenzLesson: Lesson = {
  symbols: [
    { name: 'sigma', parameter: 'sigma', readout: null },
    { name: 'rho', parameter: 'rho', readout: null },
    { name: 'y', parameter: null, readout: null },
  ],
  blocks: [
    {
      kind: 'Text',
      markdown:
        '## The equations\n\n$$\n\\dot x = \\class{sigma}{\\sigma} (\\class{y}{y} - x)\n$$\n\n' +
        'With $\\class{sigma}{\\sigma} = 10$:',
    },
    derivative,
    { kind: 'Text', markdown: 'Any system $\\dot y = f(t, y)$ implements' },
    system,
    conduction,
  ],
}

/** calculation of the rate ẋ of the selected particle of the Lorenz attractor, from σ, x and y */
export const rateCalculation: CalculationBlock = {
  kind: 'Calculation',
  formula:
    '\\class{sigma}{\\sigma} \\cdot (\\class{y}{y} - \\class{x}{x}) = \\class{xDot}{\\dot x}',
}

/**
 * A lesson that calculates the rate ẋ of the selected particle, with the symbol σ of a parameter
 * and the symbols x, y and ẋ of readouts
 */
export const calculatingLesson: Lesson = {
  symbols: [
    { name: 'sigma', parameter: 'sigma', readout: null },
    { name: 'x', parameter: null, readout: 'x' },
    { name: 'y', parameter: null, readout: 'y' },
    { name: 'xDot', parameter: null, readout: 'xDot' },
  ],
  blocks: [{ kind: 'Text', markdown: 'The rate of $\\class{x}{x}$:' }, rateCalculation],
}
