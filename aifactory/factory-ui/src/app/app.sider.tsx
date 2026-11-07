import { Layout, Menu } from 'antd'
import type { MenuItemType } from 'antd/es/menu/interface'

import { content } from '../shared/content'
import { filesKeyOf, projectKeyOf, projectSegment, siderRootKey } from './app.paths'
import type { Project } from '../shared/dto'

/**
 * Below this width the sider steps out of the chat's way (R-WP-38): 200 px of tree plus the chat's own
 * measure plus the file panel (17–24 rem) need more than a `md` frame leaves, so the panel would be pushed
 * into the conversation. Stated as the basis' own name, never as a pixel measure.
 */
export const SIDER_BREAKPOINT = 'lg'

/**
 * The basis carries a menu's selection as the item state of its entry and states no `aria-current` of its
 * own (ANTD-A1); it does pass an unknown item prop through to the `li` it renders, so R-WP-38's "markierung
 * und URL einig" is provable to a screen reader as well. Named deviation ANTD-A9.
 */
export type NavItem = MenuItemType & { 'aria-current'?: 'page' }

interface Props {
  projects: Project[]
  /** `navKeyOfPathname(pathname)` — what the frame read from the address; this component holds no state. */
  activeKey: string | null
  onOpen: (project: Project) => void
  onProjects: () => void
  /** The second handle of one address change: the file area of the project the address names. */
  onFiles: (project: Project) => void
}

function entry(key: string, label: string, active: boolean): NavItem {
  return active ? { key, label, 'aria-current': 'page' } : { key, label }
}

const keyOf = (project: Project): string => projectKeyOf(projectSegment(project))
const unitKeyOf = (project: Project): string => filesKeyOf(projectSegment(project))

/**
 * The frame's navigation (R-WP-38): the project list first, then one entry per project, and under the one
 * project the address names its file area — no agent entry, no second place that adds or removes a
 * project. The header's Settings stays out of here, because the tree answers "which project" and not
 * "which page".
 *
 * A unit is a flat item directly after its project and carries its own class for the indent: its openness
 * is a reading of `activeKey`, so there is no `openKeys` and nothing to keep in step. Only one entry of
 * the whole tree is marked — the one the address names, never both a project and its unit.
 *
 * The `<nav>` is ours: the basis renders `ul[role=menu]` and no landmark, and the tests and a screen reader
 * both ask for the navigation by name.
 *
 * `collapsed` is left to the basis on purpose. A controlled value would be answered by its responsive effect
 * once and ignored afterwards, so the trigger that R-WP-38 needs under the breakpoint — the only way back to
 * the tree at `collapsedWidth={0}` — would stop working.
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
