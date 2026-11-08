import { Layout, Menu } from 'antd'

import { content } from '../shared/content'
import { entry, type NavItem } from './app.menu'
import { chatKeyOf, filesKeyOf, projectKeyOf, projectSegment, siderRootKey } from './app.paths'
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
const chatUnitKeyOf = (project: Project): string => chatKeyOf(projectSegment(project))
const unitKeyOf = (project: Project): string => filesKeyOf(projectSegment(project))

/**
 * The frame's tree (R-WP-38): the list first, then one entry per project and, under the one the address
 * names, its units — the chat first and the file area last (UC-WP-77). Paco takes the place between them the
 * day his story is built; nothing here has to move for that.
 *
 * A unit is a **flat** item carrying its own class for the indent, so its showing stays a reading of
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
    const chat = chatUnitKeyOf(project)
    const unit = unitKeyOf(project)
    // INC-L1-LOW-1: no address names a project entry — since UC-WP-77 every chat address names one of its
    // units — so the entry is a handle (`project:<seg>` reaches `onOpen` below) and never a mark
    items.push(entry(key, project.name, false))
    // the units hang under the project the address names and under no other: entries beside a project the
    // viewer is not in would be marks beside the URL (R-WP-38). The unit is the finer handle, so it is the
    // one that marks (UC-WP-77)
    if (activeKey === chat || activeKey === unit) {
      items.push({
        ...entry(chat, content.nav.chat, activeKey === chat),
        className: 'app-sider-unit',
      })
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
      // the width the look names (R-WP-43) — the basis' own default is the same number, stated here so the
      // frame of the tree is a decision and not a coincidence
      width={200}
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
            const opened = projects.find(
              project => keyOf(project) === key || chatUnitKeyOf(project) === key,
            )
            if (opened !== undefined) {
              // the chat unit and the project entry are two handles of one address change (UC-WP-77): the
              // same call, so the same path is written and the open chat is never mounted again
              onOpen(opened)
            }
          }}
        />
      </nav>
    </Layout.Sider>
  )
}
