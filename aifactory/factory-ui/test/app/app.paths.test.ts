import { expect, test } from 'vitest'

import { chatPath, navKeyOfPathname, projectKeyOf, siderRootKey } from '../../src/app/app.paths'

/**
 * The frame holds no view of its own (UC-WP-52, R-WP-1): which entry of the tree shows is a reading of the
 * address, so a reload and a bookmark mark the same entry a click marks.
 */
// UC-WP-52
test('theAddressNamesTheEntryThatShows', () => {
  expect(navKeyOfPathname('/projects')).toBe(siderRootKey)
  expect(navKeyOfPathname(chatPath('demo-1a2b3c4'))).toBe(projectKeyOf('demo-1a2b3c4'))
  // an address that names no entry of the tree marks nothing — guessing the first would lie about the page
  expect(navKeyOfPathname('/settings')).toBeNull()
  expect(navKeyOfPathname('/chat')).toBeNull()
  expect(navKeyOfPathname('/no/such/route')).toBeNull()
})
