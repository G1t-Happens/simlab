import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('/actuator/health', (route) => route.fulfill({ json: { status: 'UP' } }))
  await page.route('/api/simulations', (route) =>
    route.fulfill({
      json: [
        {
          type: 'n-body',
          name: 'N bodies',
          description: 'Planets that attract each other.',
          timeUnit: 's',
          parameters: [],
        },
      ],
    }),
  )
  await page.route('/api/rooms', (route) => route.abort('connectionrefused'))
})

test('opens every simulation that the server offers from the start page', async ({ page }) => {
  await page.goto('/')

  await page.getByRole('link', { name: /N bodies/ }).click()

  await expect(page).toHaveURL('/simulations/n-body')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('N bodies')
  await expect(page.getByText('Planets that attract each other.')).toBeVisible()
})

test('shows a not found page for unknown addresses', async ({ page }) => {
  await page.goto('/does-not-exist')

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Page not found')

  await page.getByRole('link', { name: 'Go to the start page' }).click()

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Live physics simulations')
})
