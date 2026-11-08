import { useState } from 'react'
import { Layout, Menu } from 'antd'
import { FolderOpenOutlined } from '@ant-design/icons'

import { content } from '../shared/content'
import { entry, type NavGroup } from './app.menu'
import { chatKeyOf, filesKeyOf, projectKeyOf, projectSegment } from './app.paths'
import type { Project } from '../shared/dto'

/**
 * Below this width the sider steps out of the chat's way (R-WP-38): the width of the tree plus the chat's own
 * measure plus the file panel (17–24 rem) need more than a `md` frame leaves, so the panel would be pushed
 * into the conversation. Stated as the basis' own name, never as a pixel measure.
 */
export const SIDER_BREAKPOINT = 'lg'

/** From this many projects on the tree keeps only its first group open (R-WP-44). */
const ALL_GROUPS_OPEN_BELOW = 4

interface Props {
  projects: Project[]
  /** `navKeyOfPathname(pathname)` — what the frame read from the address. */
  activeKey: string | null
  /** Folded away? The frame owns the answer, the band's handle and the breakpoint both write it (R-WP-44). */
  collapsed: boolean
  onCollapse: (collapsed: boolean) => void
  onOpen: (project: Project) => void
  /** The second handle of one address change: the file area of the project the address names. */
  onFiles: (project: Project) => void
}

const groupKeyOf = (project: Project): string => projectKeyOf(projectSegment(project))
const chatKeyFor = (project: Project): string => chatKeyOf(projectSegment(project))
const filesKeyFor = (project: Project): string => filesKeyOf(projectSegment(project))

/**
 * The group the address names, `null` when it names no unit of the tree. The one reading of "which group
 * stands under the mark" — the preset and the union of the effective keys both ask it (R-WP-1).
 */
function namedGroupOf(projects: Project[], activeKey: string | null): string | null {
  const named = projects.find(project => chatKeyFor(project) === activeKey || filesKeyFor(project) === activeKey)
  return named === undefined ? null : groupKeyOf(named)
}

/**
 * The groups standing open before the viewer has said anything: all of them while the list is short, the
 * first one only from {@link ALL_GROUPS_OPEN_BELOW} on (R-WP-44) — plus the group the address names, because
 * a mark inside a closed group would answer the URL while showing nothing (R-WP-1).
 */
function openGroupsOf(projects: Project[], activeKey: string | null): string[] {
  const keys = projects
    .slice(0, projects.length < ALL_GROUPS_OPEN_BELOW ? projects.length : 1)
    .map(groupKeyOf)
  const named = namedGroupOf(projects, activeKey)
  return named === null || keys.includes(named) ? keys : [...keys, named]
}

/**
 * The tree of the frame (R-WP-44): one collapsible group per project, named by the project, holding its
 * units — the chat first and the file area last, with Paco's place between them open (R-WP-43). The group is
 * never an address: its title only opens and closes, the children are the handles. No project, no tree —
 * neither a heading nor a placeholder stands in for one.
 *
 * The file area carries an icon; the chat needs none, and an icon of the basis is decoration: without
 * `aria-hidden` its `role="img"` label would join the entry's accessible name („folder Files") and the entry
 * would answer to no name the catalog states (R-WP-27).
 *
 * `collapsed` belongs to the frame (`app.shell.tsx`) and not to the tree: the handle that folds it away
 * stands in the band (R-WP-44), and the basis' responsive listener answers `onCollapse(value, 'responsive')`
 * beside a controlled `collapsed` too (`layout/Sider.js` calls `handleSetCollapsed(mql.matches,
 * 'responsive')`) — so the breakpoint keeps working with one owner of the answer. `trigger={null}` takes the
 * basis' own handle away in both states (`layout/Sider.js`: no trigger node when `trigger === null`), which
 * leaves the band's button the single way back.
 */
export function AppSider({ projects, activeKey, collapsed, onCollapse, onOpen, onFiles }: Props) {
  // `null` while the viewer has opened or closed nothing: until then the tree follows the reading above.
  // Derived **during rendering** — an effect would trip `react(set-state-in-effect)`, and a project arriving
  // later would otherwise rewrite what the viewer left open.
  const [touched, setTouched] = useState<string[] | null>(null)

  const items: NavGroup[] = projects.map(project => ({
    key: groupKeyOf(project),
    label: project.name,
    children: [
      entry(chatKeyFor(project), content.nav.chat, activeKey === chatKeyFor(project)),
      entry(filesKeyFor(project), content.nav.files, activeKey === filesKeyFor(project), <FolderOpenOutlined aria-hidden />),
    ],
  }))

  // the **union**, not the viewer's list: `touched` limits the preset of open groups and never the group the
  // address names, whose mark has to be seen (R-WP-44, R-WP-1). Accepted consequence — clicking the title of
  // exactly that group folds nothing, it snaps open again; no state and no loop behind it, `touched` changes
  // once and the union is arithmetic. Do not "repair" this into `touched` alone.
  const openKeys = touched ?? openGroupsOf(projects, activeKey)
  const named = namedGroupOf(projects, activeKey)
  const shownOpenKeys = named === null || openKeys.includes(named) ? openKeys : [...openKeys, named]

  return (
    <Layout.Sider
      className="app-sider"
      theme="light"
      // the width the look names (docs/ui.md, Look-Absatz: 250 px, so a project name fits the row) — the
      // basis' own default is 200 px, so the width of the tree is stated here and not left to a coincidence
      width={250}
      breakpoint={SIDER_BREAKPOINT}
      collapsedWidth={0}
      collapsible
      collapsed={collapsed}
      onCollapse={onCollapse}
      // the band's button is the handle of the tree (R-WP-44): the basis' own trigger — a text pill in its
      // corner — would be a second one, and the tree is folded from the band and not from its own edge
      trigger={null}
    >
      <nav aria-label={content.nav.views}>
        <Menu
          mode="inline"
          // folded, the tree stays **inline**: the basis would switch it to its popup form for the moment of
          // the fold and answer that switch with an empty key list (`onOpenChange([])`), which throws away
          // every group the viewer had open (measured: `menu.js:88` `inlineCollapsed ?? siderCollapsed`)
          inlineCollapsed={false}
          items={items}
          openKeys={shownOpenKeys}
          onOpenChange={setTouched}
          selectedKeys={activeKey === null ? [] : [activeKey]}
          onClick={({ key }) => {
            const opened = projects.find(project => chatKeyFor(project) === key)
            if (opened !== undefined) {
              onOpen(opened)
              return
            }
            const unit = projects.find(project => filesKeyFor(project) === key)
            if (unit !== undefined) {
              onFiles(unit)
            }
          }}
        />
      </nav>
    </Layout.Sider>
  )
}
