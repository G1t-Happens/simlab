import { expect, test } from '@playwright/test'

import { fakeServer, type FakeSimulation } from './fakeServer.ts'

/**
 * A square of four vertices, two meters wide, that hangs upright through the point the camera looks
 * at, above a sphere and a floor
 */
const cloth: FakeSimulation = {
  info: {
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
        defaultValue: 2,
        effect: 'LIVE',
      },
    ],
  },
  scene: {
    camera: {
      position: { x: 4.2, y: 2, z: 2.8 },
      target: { x: 0, y: 1.2, z: 0.6 },
      up: { x: 0, y: 1, z: 0 },
      orbiting: false,
    },
    objects: [
      {
        kind: 'Mesh',
        vertexCount: 4,
        triangles: [0, 1, 3, 0, 3, 2],
        material: { color: { red: 217, green: 81, blue: 60 }, finish: 'FABRIC' },
        grabbable: true,
      },
      {
        kind: 'Sphere',
        material: { color: { red: 201, green: 209, blue: 217 }, finish: 'GLOSSY' },
      },
      { kind: 'Floor', radius: 4 },
      {
        kind: 'Readouts',
        readouts: [
          { name: 'gustNoise', label: 'N₁', unit: '' },
          { name: 'speed', label: '|w|', unit: 'm/s' },
        ],
      },
    ],
    xray: [],
  },
  // the wind blows at the default speed of 2 m/s, the gusts add a fifth of 1 m/s
  frame: () => [
    -1, 0.2, 0.6, 1, 0.2, 0.6, -1, 2.2, 0.6, 1, 2.2, 0.6, 0.3, 0.85, 0.8, 0.4, 0.2, 2.2,
  ],
}

test('streams the cloth into the page, with the time in seconds', async ({ page }) => {
  await fakeServer(page, cloth)
  await page.goto('/simulations/cloth')

  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  await expect
    .poll(async () => {
      const seconds = /^t = (\d+\.\d)\u00a0s$/.exec(
        (await page.locator('.time').textContent()) ?? '',
      )
      return Number(seconds?.[1])
    })
    .toBeGreaterThan(1)
})

test('reports the gusty wind that blows', async ({ page }) => {
  await fakeServer(page, cloth)
  await page.goto('/simulations/cloth')

  const readouts = page.getByRole('region', { name: 'Readouts' })

  await expect(readouts.getByRole('term')).toHaveText(['N₁ gustNoise', '|w| speed'])
  await expect(readouts.getByRole('definition')).toHaveText(['0.200', '2.20 m/s'])
})

test('grabs the cloth, drags it and lets it go', async ({ page }) => {
  const server = await fakeServer(page, cloth)
  await page.goto('/simulations/cloth')
  await expect(page.getByText('Live', { exact: true })).toBeVisible()
  const scene = page.getByRole('img', { name: 'Cloth, live in 3D' })

  // the camera looks at the middle of the square
  await scene.hover()
  await page.mouse.down()
  await scene.hover({ position: { x: 100, y: 100 } })
  await page.mouse.up()

  await expect.poll(() => server.messages.map(({ type }) => type)).toContain('release')
  const types = server.messages.map(({ type }) => type)
  expect(types[0]).toBe('grab')
  expect(types).toContain('drag')
  expect(types.at(-1)).toBe('release')
  expect(new Set(server.messages.map(({ vertex }) => vertex)).size).toBe(1)
})
