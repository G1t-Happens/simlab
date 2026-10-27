import type { SimulationInfo } from '@/api/simulations'

/**
 * The Lorenz attractor as the server describes it, with two of its parameters and the choice of its
 * numerical method
 */
export const lorenz: SimulationInfo = {
  type: 'lorenz',
  name: 'Lorenz attractor',
  description: 'A thousand particles start almost at the same point.',
  timeUnit: '',
  parameters: [
    {
      kind: 'Range',
      name: 'sigma',
      label: 'σ',
      unit: '',
      min: 0.1,
      max: 30,
      defaultValue: 10,
      effect: 'LIVE',
    },
    {
      kind: 'Range',
      name: 'rho',
      label: 'ρ',
      unit: '',
      min: 0,
      max: 60,
      defaultValue: 28,
      effect: 'LIVE',
    },
    {
      kind: 'Choice',
      name: 'order',
      label: 'p',
      options: [
        { value: 1, label: 'Euler' },
        { value: 4, label: 'Runge-Kutta 4' },
      ],
      defaultValue: 4,
      effect: 'LIVE',
    },
  ],
}

/** the cloth as the server describes it, with two of its parameters */
export const cloth: SimulationInfo = {
  type: 'cloth',
  name: 'Cloth',
  description: 'A cloth hangs from two corners.',
  timeUnit: 's',
  parameters: [
    {
      kind: 'Range',
      name: 'wind',
      label: 'v',
      unit: 'm/s',
      min: 0,
      max: 8,
      defaultValue: 3,
      effect: 'LIVE',
    },
    {
      kind: 'Range',
      name: 'gravity',
      label: 'g',
      unit: 'm/s²',
      min: 0,
      max: 20,
      defaultValue: 9.81,
      effect: 'LIVE',
    },
  ],
}

/**
 * The galaxy collision as the server describes it, with three of its parameters: one that changes
 * the running simulation, and two that take effect on restart
 */
export const galaxies: SimulationInfo = {
  type: 'galaxies',
  name: 'Galaxy collision',
  description: 'Two spiral galaxies fall towards each other.',
  timeUnit: 'Myr',
  parameters: [
    {
      kind: 'Range',
      name: 'pace',
      label: 'Δt',
      unit: 'Myr/s',
      min: 1,
      max: 40,
      defaultValue: 15,
      effect: 'LIVE',
    },
    {
      kind: 'Range',
      name: 'ratio',
      label: 'q',
      unit: '',
      min: 0.2,
      max: 1,
      defaultValue: 1,
      effect: 'RESTART',
    },
    {
      kind: 'Range',
      name: 'inclination',
      label: 'i',
      unit: '°',
      min: 0,
      max: 180,
      defaultValue: 60,
      effect: 'RESTART',
    },
  ],
}
