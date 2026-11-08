import { expect, test } from 'vitest'

import {
  chatFilesPath,
  chatKeyOf,
  chatPath,
  chatRoot,
  filesKeyOf,
  navKeyOfPathname,
  pageKeyOfPathname,
  projectOfSegment,
  projectsPath,
  settingsPath,
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
  // the chat address names the **unit** of its project and not the project entry: the tree lists chat · files
  // under the project, and the unit is the finer handle the mark has to follow (UC-WP-77)
  expect(navKeyOfPathname(chatPath(project().name))).toBe(chatKeyOf(project().name))
  // an address that names no entry of the tree marks nothing — guessing the first would lie about the page
  expect(navKeyOfPathname('/settings')).toBeNull()
  expect(navKeyOfPathname('/chat')).toBeNull()
  expect(navKeyOfPathname('/no/such/route')).toBeNull()
  // an escaped name marks the unit whose chat opens: the chat resolves what `useParams` hands it — decoded
  expect(navKeyOfPathname('/chat/deep%20dive')).toBe(chatKeyOf('deep dive'))
  // an address that cannot be decoded names what it spells; reading it is no reason to throw
  expect(navKeyOfPathname('/chat/%E0%A4%A')).toBe(chatKeyOf('%E0%A4%A'))
})

/**
 * The file area has an address of its own and the reading tells the two apart (R-WP-38, UC-WP-70 G2): the
 * unit is read **before** the chat, so `/chat/x/files` names x's area and not x's chat. The edges are what
 * makes this a reading and not a prefix match — a trailing slash names the same place, a second segment
 * that is no unit names nothing (it is no page either, R-WP-1), and the unit keeps its own prefix so a
 * project may be called `files`.
 */
// R-WP-38
test('theUnitAddressIsItsOwnEntry', () => {
  expect(chatFilesPath('demo')).toBe('/chat/demo/files')
  expect(navKeyOfPathname(chatFilesPath('demo'))).toBe(filesKeyOf('demo'))
  expect(navKeyOfPathname('/chat/demo/files/')).toBe(filesKeyOf('demo'))
  expect(navKeyOfPathname('/chat/demo/')).toBe(chatKeyOf('demo'))
  expect(navKeyOfPathname('/chat/demo/unknown')).toBeNull()
  // the unit of an escaped name decodes like the chat does, and an undecodable one is read as it stands
  expect(navKeyOfPathname('/chat/deep%20dive/files')).toBe(filesKeyOf('deep dive'))
  expect(navKeyOfPathname('/chat/%E0%A4%A/files')).toBe(filesKeyOf('%E0%A4%A'))
  // the bare chat root answers with nothing: it is a question about the last project, not a unit
  expect(navKeyOfPathname('/chat')).toBeNull()
  // a project called `files` is still a project, and its chat unit and its file unit stay apart
  expect(navKeyOfPathname(chatPath('files'))).toBe(chatKeyOf('files'))
  expect(navKeyOfPathname(chatFilesPath('files'))).toBe(filesKeyOf('files'))
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

/**
 * The header answers another question than the tree (R-WP-43, UC-WP-76): which **page** an address is. Every
 * chat address is the chat page — a unit of it included, because the header names the page and only the tree
 * names the unit — and the answer is an address itself, which is what lets a menu entry navigate to its key
 * instead of carrying a second table of where entries lead.
 */
// R-WP-43
// UC-WP-76
test('theMenuReadsThePageOfTheAddress', () => {
  expect(pageKeyOfPathname(projectsPath)).toBe(projectsPath)
  // a trailing slash names the same page. The tree answers nothing there — that is its own, other question
  expect(pageKeyOfPathname(`${projectsPath}/`)).toBe(projectsPath)
  expect(pageKeyOfPathname(chatRoot)).toBe(chatRoot)
  expect(pageKeyOfPathname(chatPath('demo'))).toBe(chatRoot)
  expect(pageKeyOfPathname(chatFilesPath('demo'))).toBe(chatRoot)
  expect(pageKeyOfPathname(settingsPath)).toBe(settingsPath)
  // an address of no page names no page: guessing the first entry would mark a page that is not showing
  expect(pageKeyOfPathname('/no/such')).toBeNull()
  expect(pageKeyOfPathname('/')).toBeNull()
})
