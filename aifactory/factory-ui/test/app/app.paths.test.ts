import { expect, test } from 'vitest'

import {
  chatPath,
  navKeyOfPathname,
  projectKeyOf,
  projectOfSegment,
  siderRootKey,
} from '../../src/app/app.paths'
import { project } from '../shared/fixtures'

/**
 * The frame holds no view of its own (UC-WP-52, R-WP-1): which entry of the tree shows is a reading of the
 * address, so a reload and a bookmark mark the same entry a click marks.
 */
// UC-WP-52
test('theAddressNamesTheEntryThatShows', () => {
  expect(navKeyOfPathname('/projects')).toBe(siderRootKey)
  expect(navKeyOfPathname(chatPath(project().name))).toBe(projectKeyOf(project().name))
  // an address that names no entry of the tree marks nothing — guessing the first would lie about the page
  expect(navKeyOfPathname('/settings')).toBeNull()
  expect(navKeyOfPathname('/chat')).toBeNull()
  expect(navKeyOfPathname('/no/such/route')).toBeNull()
})

/**
 * The address names a project by its name (C-3), so a name is what resolves and nothing else: the id reads
 * second, which is what keeps a bookmark written in ids working, and a segment that names no project is
 * `null` — the reading a gone project needs to land on the list (R-WP-1).
 */
// UC-WP-70
test('theSegmentResolvesNameOrId', () => {
  const demo = project()
  const other = project({ id: 'other-9f8e7d6', name: 'other' })
  const registry = [demo, other]

  expect(projectOfSegment(registry, 'demo')).toBe(demo)
  expect(projectOfSegment(registry, 'other')).toBe(other)
  expect(projectOfSegment(registry, other.id)).toBe(other)
  // the name is what answers, not a piece of the git url, which is free to repeat another project's name
  const elsewhere = project({
    id: 'else-4c5d6e7',
    name: 'elsewhere',
    gitUrl: 'https://git.example.com/acme/demo.git',
  })
  expect(projectOfSegment([elsewhere], 'demo')).toBeNull()
  expect(projectOfSegment(registry, 'gone')).toBeNull()
  expect(projectOfSegment(registry, '')).toBeNull()
})
