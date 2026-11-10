import { expect, test } from 'vitest'

import { sseBackoff } from '../../src/shared/sse.client'

// UC-WP-95
test('theReconnectBacksOffInsteadOfHammering', () => {
  const backoff = sseBackoff()

  expect([
    backoff.next(),
    backoff.next(),
    backoff.next(),
    backoff.next(),
    backoff.next(),
    backoff.next(),
    backoff.next(),
  ]).toEqual([500, 1_000, 2_000, 4_000, 8_000, 10_000, 10_000])

  backoff.reset()

  expect(backoff.next()).toBe(500)
})
