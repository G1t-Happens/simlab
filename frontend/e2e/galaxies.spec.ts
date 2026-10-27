import { expect, test } from '@playwright/test'

import { fakeServer, type FakeSimulation } from './fakeServer.ts'

/** stars in each of the two galaxies */
const STARS = 100

/**
 * Readouts of the real galaxies at the start: the pairs of their 35,200 particles, the
 * interactions of the tree, those of a selected star and the relative error of its acceleration in
 * percent, and the change of the energy since the start in percent
 */
const READOUTS = [619_502_400, 32_881_600, 1_034, 0.297, -0.0142]

/**
 * Two galaxies of glowing stars that turn around their centers 40 kpc apart, and the readouts of
 * the real galaxies. The x-ray shows a cube of the tree around the center of each galaxy
 */
const galaxies: FakeSimulation = {
  info: {
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
        name: 'openingAngle',
        label: 'θ',
        unit: '',
        min: 0.4,
        max: 1.5,
        defaultValue: 0.7,
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
    ],
  },
  scene: {
    camera: {
      position: { x: 0, y: -110, z: 70 },
      target: { x: 0, y: 0, z: 0 },
      up: { x: 0, y: 0, z: 1 },
      orbiting: true,
    },
    objects: [
      {
        kind: 'PointCloud',
        count: STARS,
        firstColor: { red: 227, green: 235, blue: 255 },
        lastColor: { red: 74, green: 120, blue: 255 },
        trailLength: 0,
        glowing: true,
        selectable: true,
      },
      {
        kind: 'PointCloud',
        count: STARS,
        firstColor: { red: 243, green: 232, blue: 255 },
        lastColor: { red: 154, green: 92, blue: 255 },
        trailLength: 0,
        glowing: true,
        selectable: true,
      },
      {
        kind: 'Readouts',
        readouts: [
          { name: 'pairs', label: 'N(N−1)/2', unit: '' },
          { name: 'interactions', label: 'I', unit: '' },
          { name: 'starInteractions', label: 'nᵢ', unit: '' },
          { name: 'starForceError', label: 'δᵢ', unit: '%' },
          { name: 'energyError', label: 'ΔE/E₀', unit: '%' },
        ],
      },
    ],
    xray: [
      {
        kind: 'Cubes',
        capacity: 2,
        label: 'Cubes of the tree that act on the selected star as a whole',
        color: { red: 63, green: 185, blue: 80 },
      },
    ],
  },
  frame: (tick, selected) => [
    ...[-20, 20].flatMap((center) =>
      Array.from({ length: STARS }, (_, star) => {
        const radius = 1 + star / 10
        const angle = 2.4 * star + tick / (10 * radius)
        return [center + radius * Math.cos(angle), radius * Math.sin(angle), 0]
      }).flat(),
    ),
    ...READOUTS.slice(0, 2),
    ...(selected === undefined ? [Number.NaN, Number.NaN] : READOUTS.slice(2, 4)),
    READOUTS[4] ?? Number.NaN,
  ],
  xray: () => [2, -20, 0, 0, 12, 20, 0, 0, 12],
  lesson: {
    symbols: [{ name: 'ratio', parameter: 'ratio', readout: null }],
    blocks: [
      {
        kind: 'Experiment',
        question: 'What happens if the second galaxy weighs a fifth of the first one?',
        values: { ratio: 0.2 },
        explanation: 'It loses the larger share of its disk, at $\\class{ratio}{q} = 0.2$.',
      },
    ],
  },
}

test('streams the glowing galaxies into the page, with the time in millions of years', async ({
  page,
}) => {
  const errors: Error[] = []
  page.on('pageerror', (error) => errors.push(error))
  await fakeServer(page, galaxies)
  await page.goto('/simulations/galaxies')

  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  await expect
    .poll(async () => {
      const time = /^t = (\d+\.\d)\u00a0Myr$/.exec(
        (await page.locator('.time').textContent()) ?? '',
      )
      return Number(time?.[1])
    })
    .toBeGreaterThan(1)
  expect(errors).toEqual([])
})

test('counts the pairs of a direct sum, the fewer interactions of the tree and its errors', async ({
  page,
}) => {
  await fakeServer(page, galaxies)
  await page.goto('/simulations/galaxies')

  const readouts = page.getByRole('region', { name: 'Readouts' })

  await expect(readouts.getByRole('term')).toHaveText([
    'N(N−1)/2 pairs',
    'I interactions',
    'nᵢ starInteractions',
    'δᵢ starForceError',
    'ΔE/E₀ energyError',
  ])
  await expect(readouts.getByRole('definition')).toHaveText([
    '6.20 · 10⁸',
    '3.29 · 10⁷',
    '–',
    '–',
    '−0.0142 %',
  ])
})

test('changes the pace and the opening angle of the running simulation', async ({ page }) => {
  const server = await fakeServer(page, galaxies)
  await page.goto('/simulations/galaxies')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()

  await page.getByRole('slider', { name: 'Δt pace' }).fill('30')
  await page.getByRole('slider', { name: 'θ openingAngle' }).fill('1.5')

  await expect
    .poll(() => server.messages)
    .toEqual([
      { type: 'param', name: 'pace', value: 30 },
      { type: 'param', name: 'openingAngle', value: 1.5 },
    ])
})

test('shows the cubes of the tree in the x-ray', async ({ page }) => {
  const errors: Error[] = []
  page.on('pageerror', (error) => errors.push(error))
  const server = await fakeServer(page, galaxies)
  await page.goto('/simulations/galaxies')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()

  await page.getByRole('checkbox', { name: 'X-ray' }).check()

  await expect(page.getByRole('list', { name: 'What the x-ray shows' })).toHaveText(
    'Cubes of the tree that act on the selected star as a whole',
  )
  await expect.poll(() => server.messages).toEqual([{ type: 'xray', shown: true }])
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  expect(errors).toEqual([])
})

test('sets up the next encounter, which starts over on restart', async ({ page }) => {
  const server = await fakeServer(page, galaxies)
  await page.goto('/simulations/galaxies')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  const restart = page.getByRole('button', { name: 'Restart' })

  await page
    .getByRole('region', { name: 'Applies on restart' })
    .getByRole('slider', { name: 'q ratio' })
    .fill('0.5')

  await expect(restart).toHaveAccessibleDescription('Restart to apply the changes.')

  await restart.click()

  await expect.poll(() => server.joinedRooms).toHaveLength(2)
  expect(server.openedRooms).toEqual([
    { type: 'galaxies', parameters: { pace: 15, openingAngle: 0.7, ratio: 1 } },
    { type: 'galaxies', parameters: { pace: 15, openingAngle: 0.7, ratio: 0.5 } },
  ])
  expect(server.messages).toEqual([])
  await expect(restart).not.toHaveAccessibleDescription('Restart to apply the changes.')
})

test('starts over for an experiment of the lesson that sets up another encounter', async ({
  page,
}) => {
  const server = await fakeServer(page, galaxies)
  await page.goto('/simulations/galaxies')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'How it works' }).click()

  await page.getByRole('button', { name: 'Try q = 0.2' }).click()

  await expect.poll(() => server.joinedRooms).toHaveLength(2)
  expect(server.openedRooms).toEqual([
    { type: 'galaxies', parameters: { pace: 15, openingAngle: 0.7, ratio: 1 } },
    { type: 'galaxies', parameters: { pace: 15, openingAngle: 0.7, ratio: 0.2 } },
  ])
  await expect(page.getByRole('button', { name: 'Restart' })).not.toHaveAccessibleDescription(
    'Restart to apply the changes.',
  )
})
