import { Menu } from 'antd'

import { content } from '../shared/content'
import { entry, type NavItem } from './app.menu'
import { chatRoot, projectsPath, settingsPath } from './app.paths'

interface Props {
  /** `pageKeyOfPathname(pathname)` — what the frame read from the address; this component holds no state. */
  active: string | null
  onNavigate: (path: string) => void
}

/**
 * The header's pages (R-WP-43, UC-WP-76): Projects · Chat · Settings — dark, horizontal, and exactly those
 * three. The keys **are** the addresses, so a click navigates to the key and no second table of „where does
 * this entry lead" can drift from `app.paths.ts`.
 *
 * The chat is a page of the header (Paul, Runde 7) and needs no project of its own: `chatRoot` answers with
 * what this browser opened last (UC-WP-51). Every chat address names this key, so the mark follows the address
 * and the two menus say the same word — the header the page, the tree the unit (UC-WP-76 GIVEN 3, R-WP-38).
 *
 * The `<nav>` is ours and named of its own: the basis renders `ul[role=menu]` with no landmark, and a second
 * unnamed navigation would make the tree's helpers ambiguous — a screen reader and the tests both ask the two
 * menus by name.
 */
export function AppTop({ active, onNavigate }: Props) {
  const items: NavItem[] = [
    entry(projectsPath, content.nav.projects, active === projectsPath),
    entry(chatRoot, content.nav.chat, active === chatRoot),
    entry(settingsPath, content.nav.settings, active === settingsPath),
  ]

  return (
    <nav className="app-top" aria-label={content.nav.pages}>
      <Menu
        mode="horizontal"
        theme="dark"
        items={items}
        selectedKeys={active === null ? [] : [active]}
        onClick={({ key }) => onNavigate(key)}
      />
    </nav>
  )
}
