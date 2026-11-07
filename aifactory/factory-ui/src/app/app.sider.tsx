import { Layout, Menu } from 'antd'

import { content } from '../shared/content'
import { entry, type NavItem } from './app.menu'
import { filesKeyOf, projectKeyOf, projectSegment, siderRootKey } from './app.paths'
import type { Project } from '../shared/dto'

/**
 * Below this width the sider steps out of the chat's way (R-WP-38): 200 px of tree plus the chat's own
 * measure plus the file panel (17–24 rem) need more than a `md` frame leaves, so the panel would be pushed
 * into the conversation. Stated as the basis' own name, never as a pixel measure.
 */
export const SIDER_BREAKPOINT = 'lg'

interface Props {
  projects: Project[]
  /** `navKeyOfPathname(pathname)` — what the frame read from the address; this component holds no state. */
  activeKey: string | null
  onOpen: (project: Project) => void
  onProjects: () => void
  /** The second handle of one address change: the file area of the project the address names. */
  onFiles: (project: Project) => void
}

const keyOf = (project: Project): string => projectKeyOf(projectSegment(project))
const unitKeyOf = (project: Project): string => filesKeyOf(projectSegment(project))

/**
 * The frame's navigation (R-WP-38): the list first, then one entry per project and, under the one the
 * address names, its file area.
 *
 * A unit is a **flat** item carrying its own class for the indent, so its openness stays a reading of
 * `activeKey` — no `SubMenu`, no `openKeys`, nothing to keep in step. The `<nav>` is ours because the
 * basis renders `ul[role=menu]` with no landmark, and the tests and a screen reader both ask for the
 * navigation by name.
 *
 * `collapsed` is left to the basis on purpose: its responsive `matchMedia` effect answers a controlled
 * value once, at mount, and ignores it afterwards — the trigger R-WP-38 needs under the breakpoint, the
 * only way back at `collapsedWidth={0}`, would stop working.
 */
export function AppSider({ projects, activeKey, onOpen, onProjects, onFiles }: Props) {
  const items: NavItem[] = [entry(siderRootKey, content.nav.projects, activeKey === siderRootKey)]
  for (const project of projects) {
    const key = keyOf(project)
    const unit = unitKeyOf(project)
    items.push(entry(key, project.name, activeKey === key))
    // the unit hangs under the project the address names and under no other: an entry beside a project the
    // viewer is not in would be a second mark beside the URL (R-WP-38)
    if (activeKey === key || activeKey === unit) {
      items.push({
        ...entry(unit, content.nav.files, activeKey === unit),
        className: 'app-sider-unit',
      })
    }
  }

  return (
    <Layout.Sider
      className="app-sider"
      theme="light"
      breakpoint={SIDER_BREAKPOINT}
      collapsedWidth={0}
    >
      <nav aria-label={content.nav.views}>
        <Menu
          mode="inline"
          items={items}
          selectedKeys={activeKey === null ? [] : [activeKey]}
          onClick={({ key }) => {
            if (key === siderRootKey) {
              onProjects()
              return
            }
            const unit = projects.find(project => unitKeyOf(project) === key)
            if (unit !== undefined) {
              onFiles(unit)
              return
            }
            const opened = projects.find(project => keyOf(project) === key)
            if (opened !== undefined) {
              onOpen(opened)
            }
          }}
        />
      </nav>
    </Layout.Sider>
  )
}
