import { expect, test, type Page } from '@playwright/test'

import { fakeServer, type FakeSimulation } from './fakeServer.ts'

/** a particle at the center of the attractor, at the target of the camera */
const CENTER = [0, 0, 25]

/** readouts of the particle at the center: its coordinates and their rates of change */
const CENTER_READOUTS = [...CENTER, 0, 0, (-8 / 3) * 25]

/**
 * A particle at the center of the attractor and one that circles it, and the readouts of the
 * selected particle, which the center reports for either. The x-ray shows the flow at the center
 */
const lorenz: FakeSimulation = {
  info: {
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
        kind: 'Range',
        name: 'beta',
        label: 'β',
        unit: '',
        min: 0.1,
        max: 10,
        defaultValue: 8 / 3,
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
  },
  scene: {
    camera: {
      position: { x: 40, y: -80, z: 34 },
      target: { x: 0, y: 0, z: 25 },
      up: { x: 0, y: 0, z: 1 },
      orbiting: true,
    },
    objects: [
      {
        kind: 'PointCloud',
        count: 2,
        firstColor: { red: 139, green: 223, blue: 248 },
        lastColor: { red: 248, green: 139, blue: 214 },
        trailLength: 32,
        glowing: false,
        selectable: true,
      },
      {
        kind: 'Readouts',
        readouts: [
          { name: 'x', label: 'x', unit: '' },
          { name: 'y', label: 'y', unit: '' },
          { name: 'z', label: 'z', unit: '' },
          { name: 'xDot', label: 'ẋ', unit: '' },
          { name: 'yDot', label: 'ẏ', unit: '' },
          { name: 'zDot', label: 'ż', unit: '' },
        ],
      },
    ],
    xray: [
      {
        kind: 'Lines',
        capacity: 1,
        label: 'Direction of the flow at the points of a grid, colored by its speed',
        scale: {
          label: '‖f‖',
          unit: '',
          min: 0,
          max: 400,
          colors: [
            { red: 48, green: 68, blue: 92 },
            { red: 110, green: 143, blue: 181 },
            { red: 230, green: 237, blue: 243 },
          ],
        },
        directed: true,
      },
    ],
  },
  frame: (tick, selected) => [
    ...CENTER,
    10 * Math.cos(tick / 10),
    10 * Math.sin(tick / 10),
    25,
    ...(selected === undefined ? CENTER_READOUTS.map(() => Number.NaN) : CENTER_READOUTS),
  ],
  // at the center the flow points straight down
  xray: () => [1, ...CENTER, 0, 0, 22.5, (8 / 3) * 25],
  lesson: {
    symbols: [
      { name: 'sigma', parameter: 'sigma', readout: null },
      { name: 'rho', parameter: 'rho', readout: null },
      { name: 'beta', parameter: 'beta', readout: null },
      { name: 'x', parameter: null, readout: 'x' },
      { name: 'y', parameter: null, readout: 'y' },
      { name: 'z', parameter: null, readout: 'z' },
      { name: 'zDot', parameter: null, readout: 'zDot' },
    ],
    blocks: [
      {
        kind: 'Text',
        markdown: '## The equations\n\n$$\n\\dot x = \\class{sigma}{\\sigma} (y - x)\n$$',
      },
      {
        kind: 'Code',
        reference: 'LorenzSystem#derivative',
        path: 'dev/simlab/core/simulations/lorenz/LorenzSystem.java',
        firstLine: 23,
        lastLine: 25,
        code: [
          '    public void derivative(double t, double[] y, double[] dydt) {',
          '        dydt[0] = sigma * (y[1] - y[0]);',
          '    }',
        ].join('\n'),
        origin: 'SIMULATION',
        url: 'https://github.com/G1t-Happens/simlab/blob/f00d/LorenzSystem.java#L23-L25',
        occurrences: [{ symbol: 'sigma', line: 24, column: 19, length: 5 }],
      },
      {
        kind: 'Experiment',
        question: 'What do the particles do for $\\class{rho}{\\rho} < 1$?',
        values: { rho: 0.5 },
        explanation: 'They fall into the origin.',
      },
      {
        kind: 'Calculation',
        formula:
          '\\class{x}{x} \\cdot \\class{y}{y} - \\class{beta}{\\beta} \\cdot \\class{z}{z}' +
          ' = \\class{zDot}{\\dot z}',
      },
    ],
  },
}

async function simulatedTime(page: Page): Promise<number> {
  return Number((await page.locator('.time').textContent())?.replace('t = ', ''))
}

test('streams the simulation into the page, with its time', async ({ page }) => {
  await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')

  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  await expect.poll(() => simulatedTime(page)).toBeGreaterThan(1)
})

test('selects a particle with a click, and lists its readouts', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  const readouts = page.getByRole('region', { name: 'Readouts' })
  await expect(readouts.getByRole('definition')).toHaveText(['–', '–', '–', '–', '–', '–'])

  // the camera looks at the particle at the center of the attractor
  await page.getByRole('img', { name: 'Lorenz attractor, live in 3D' }).click()

  await expect.poll(() => server.messages).toContainEqual({ type: 'select', point: 0 })
  await expect(readouts.getByRole('definition')).toHaveText(['0', '0', '25', '0', '0', '−66.7'])
})

test('pauses the simulation, steps through it and slows it down', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  const clock = page.getByRole('group', { name: 'Time' })

  await clock.getByRole('button', { name: 'Pause' }).click()
  await clock.getByRole('button', { name: 'Step' }).click()
  await clock.getByLabel('Speed').selectOption('¼×')
  await clock.getByRole('button', { name: 'Play' }).click()

  await expect
    .poll(() => server.messages)
    .toEqual([
      { type: 'pause' },
      { type: 'step' },
      { type: 'speed', value: 0.25 },
      { type: 'play' },
    ])
  await expect(clock.getByRole('button', { name: 'Step' })).toBeDisabled()
})

test('calculates with the values of the selected particle in the lesson', async ({ page }) => {
  await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'How it works' }).click()
  const calculation = page.getByRole('figure', { name: 'Live calculation' })
  const rate = calculation.locator('.katex-html [data-symbol="zDot"]')

  await expect(calculation.locator('.katex-html [data-symbol="beta"]')).toHaveText('2.67')
  await expect(rate).toHaveText('z˙')

  await page.getByRole('img', { name: 'Lorenz attractor, live in 3D' }).click()

  // a negative value needs no parentheses on the other side of the equation
  await expect(rate).toHaveText('−66.7')
  await expect(calculation.locator('.katex-html [data-symbol="z"]')).toHaveText('25')
})

test('sends a changed parameter to the room', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()

  await page.getByRole('slider', { name: 'ρ rho' }).fill('20')

  await expect.poll(() => server.messages).toContainEqual({ type: 'param', name: 'rho', value: 20 })
})

test('chooses the numerical method of the room', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  const method = page.getByRole('group', { name: 'p order' })
  await expect(method.getByRole('radio', { name: 'Runge-Kutta 4' })).toBeChecked()

  await method.getByRole('radio', { name: 'Euler' }).check()

  await expect
    .poll(() => server.messages)
    .toContainEqual({ type: 'param', name: 'order', value: 1 })
})

test('shows the flow in the x-ray, with what its colors mean', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  const xray = page.getByRole('checkbox', { name: 'X-ray' })

  await xray.check()

  const legend = page.getByRole('list', { name: 'What the x-ray shows' })
  await expect(legend).toContainText('Direction of the flow at the points of a grid')
  await expect(legend).toContainText('0‖f‖400')
  await expect.poll(() => server.messages).toContainEqual({ type: 'xray', shown: true })
  // frames that hold the floats of the x-ray as well keep the room live
  const shownAt = await simulatedTime(page)
  await expect.poll(() => simulatedTime(page)).toBeGreaterThan(shownAt + 0.5)
  await expect(page.getByText('Live', { exact: true })).toBeVisible()

  await xray.uncheck()

  await expect(legend).toBeHidden()
  await expect.poll(() => server.messages).toContainEqual({ type: 'xray', shown: false })
})

test('resets the parameters to their defaults', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await page.getByRole('slider', { name: 'ρ rho' }).fill('20')

  await page.getByRole('button', { name: 'Reset parameters' }).click()

  await expect(page.getByRole('slider', { name: 'ρ rho' })).toHaveValue('28')
  await expect.poll(() => server.messages).toContainEqual({ type: 'param', name: 'rho', value: 28 })
})

test('starts over in a new room', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Restart' }).click()

  await expect.poll(() => server.joinedRooms).toHaveLength(2)
  expect(server.joinedRooms[1]).toMatch(/\/ws\/rooms\/room-2$/)
})

test('starts over by itself once the server is back from a restart', async ({ page }) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()

  // the outputs of the sliders are status elements too
  const backendStatus = page.getByRole('status').filter({ hasText: 'Backend' })

  server.shutDown()

  await expect(page.getByText('Connection lost')).toBeVisible()
  await expect(backendStatus).toHaveText('Backend unreachable')

  server.up = true

  await expect(backendStatus).toHaveText('Backend UP')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  expect(server.joinedRooms).toHaveLength(2)
})

test('opens the lesson with its formula and its code, and closes it with Escape', async ({
  page,
}) => {
  await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  const button = page.getByRole('button', { name: 'How it works' })

  await button.click()

  const lesson = page.getByRole('dialog', { name: 'How it works' })
  await expect(lesson).toBeFocused()
  await expect(lesson.getByRole('heading', { name: 'The equations' })).toBeVisible()
  // KaTeX writes the formula as MathML as well, which screen readers read out
  await expect(lesson.locator('.text math[display="block"]')).toHaveCount(1)
  const code = lesson.getByRole('figure', { name: /LorenzSystem#derivative/ })
  await expect(code).toContainText('LorenzSystem#derivative')
  await expect(code).toContainText('dydt[0] = sigma * (y[1] - y[0]);')
  await expect(code.getByRole('link', { name: /LorenzSystem\.java:23–25/ })).toHaveAttribute(
    'href',
    'https://github.com/G1t-Happens/simlab/blob/f00d/LorenzSystem.java#L23-L25',
  )

  await page.keyboard.press('Escape')

  await expect(lesson).toBeHidden()
  await expect(button).toBeFocused()
})

test('lights up a symbol in the formula, in the code and among the controls together', async ({
  page,
}) => {
  await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await page.getByRole('button', { name: 'How it works' }).click()
  const lesson = page.getByRole('dialog', { name: 'How it works' })
  const sigma = page
    .locator('.parameter')
    .filter({ has: page.getByRole('slider', { name: 'σ sigma' }) })
  const rho = page
    .locator('.parameter')
    .filter({ has: page.getByRole('slider', { name: 'ρ rho' }) })

  await lesson.locator('.katex-html [data-symbol="sigma"]').hover()

  await expect(
    lesson
      .getByRole('figure', { name: /LorenzSystem#derivative/ })
      .locator('[data-symbol="sigma"]'),
  ).toHaveClass(/highlighted/)
  await expect(sigma).toHaveClass(/highlighted/)

  await rho.hover()

  await expect(lesson.locator('.experiment [data-symbol="rho"]')).toHaveClass(/highlighted/)
  await expect(sigma).not.toHaveClass(/highlighted/)
  await expect(lesson.locator('[data-symbol="sigma"].highlighted')).toHaveCount(0)
})

test('runs an experiment of the lesson with its values and the defaults of the others', async ({
  page,
}) => {
  const server = await fakeServer(page, lorenz)
  await page.goto('/simulations/lorenz')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  await page.getByRole('slider', { name: 'σ sigma' }).fill('20')
  await page.getByRole('button', { name: 'How it works' }).click()
  const experiment = page.getByRole('group', { name: 'What do the particles do for' })

  await experiment.getByRole('button', { name: 'Try ρ = 0.5' }).click()

  await expect(page.getByRole('slider', { name: 'ρ rho' })).toHaveValue('0.5')
  await expect(page.getByRole('slider', { name: 'σ sigma' })).toHaveValue('10')
  await expect
    .poll(() => server.messages)
    .toContainEqual({ type: 'param', name: 'rho', value: 0.5 })
  expect(server.joinedRooms).toHaveLength(1)

  await experiment.getByText('What happens', { exact: true }).click()

  await expect(experiment.getByText('They fall into the origin.')).toBeVisible()
})
