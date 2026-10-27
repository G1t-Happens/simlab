import { expect, test, type Page } from '@playwright/test'

/** the health endpoint answers with UP while the returned switch is on, and fails otherwise */
async function switchableBackend(page: Page, up: boolean) {
  const backend = { up }
  await page.route('/actuator/health', (route) =>
    backend.up ? route.fulfill({ json: { status: 'UP' } }) : route.abort('connectionrefused'),
  )
  return backend
}

test.describe('backend status', () => {
  test.beforeEach(async ({ page }) => {
    // the start page lists the simulations, there are none here
    await page.route('/api/simulations', (route) => route.fulfill({ json: [] }))
  })

  test('shows that the backend is up', async ({ page }) => {
    await switchableBackend(page, true)

    await page.goto('/')

    await expect(page.getByRole('status')).toHaveText('Backend UP')
  })

  test('shows that the backend is down', async ({ page }) => {
    await page.route('/actuator/health', (route) =>
      route.fulfill({ status: 503, json: { status: 'DOWN' } }),
    )

    await page.goto('/')

    await expect(page.getByRole('status')).toHaveText('Backend DOWN')
  })

  test('shows that the backend is unreachable', async ({ page }) => {
    await switchableBackend(page, false)

    await page.goto('/')

    await expect(page.getByRole('status')).toHaveText('Backend unreachable')
  })

  test('shows that the backend went away', async ({ page }) => {
    const backend = await switchableBackend(page, true)
    await page.goto('/')
    await expect(page.getByRole('status')).toHaveText('Backend UP')

    backend.up = false

    await expect(page.getByRole('status')).toHaveText('Backend unreachable')
  })

  test('shows within a moment that the backend is back', async ({ page }) => {
    const backend = await switchableBackend(page, false)
    await page.goto('/')
    await expect(page.getByRole('status')).toHaveText('Backend unreachable')

    backend.up = true

    await expect(page.getByRole('status')).toHaveText('Backend UP', { timeout: 2_000 })
  })
})
