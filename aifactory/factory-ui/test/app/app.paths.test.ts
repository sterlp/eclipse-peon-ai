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
  // an escaped name marks the entry whose chat opens: the chat resolves what `useParams` hands it — decoded
  expect(navKeyOfPathname('/chat/deep%20dive')).toBe(projectKeyOf('deep dive'))
  // an address that cannot be decoded names what it spells; reading it is no reason to throw
  expect(navKeyOfPathname('/chat/%E0%A4%A')).toBe(projectKeyOf('%E0%A4%A'))
})

/**
 * The address names a project by its name (C-3), so a name is what resolves and nothing else — not the
 * git url, which is free to repeat another project's name, not the database key either. Both readings end in
 * `null`, which is what sends an address that names no project to the list (R-WP-1).
 */
// UC-WP-70
test('theSegmentResolvesTheName', () => {
  const demo = project()
  const other = project({ id: 'other-9f8e7d6', name: 'other' })
  const registry = [demo, other]

  expect(projectOfSegment(registry, 'demo')).toBe(demo)
  expect(projectOfSegment(registry, 'other')).toBe(other)
  const elsewhere = project({
    id: 'else-4c5d6e7',
    name: 'elsewhere',
    gitUrl: 'https://git.example.com/acme/demo.git',
  })
  expect(projectOfSegment([elsewhere], 'demo')).toBeNull()
  expect(projectOfSegment(registry, other.id)).toBeNull()
  expect(projectOfSegment(registry, 'gone')).toBeNull()
  expect(projectOfSegment(registry, '')).toBeNull()
})
