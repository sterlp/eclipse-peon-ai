import { Menu } from 'antd'

import { content } from '../shared/content'
import { entry, type NavItem } from './app.menu'
import { projectsPath, settingsPath } from './app.paths'

interface Props {
  /** `pageKeyOfPathname(pathname)` — what the frame read from the address; this component holds no state. */
  active: string | null
  onNavigate: (path: string) => void
}

/**
 * The header's pages (R-WP-43, UC-WP-76): Projects · Settings — dark, horizontal, and exactly those two. The
 * keys **are** the addresses, so a click navigates to the key and no second table of „where does this entry
 * lead" can drift from `app.paths.ts`.
 *
 * The chat is no entry (Paul 2026-11-08): it belongs to a project and is opened from the tree (R-WP-44), so
 * on a chat address no item names it and the basis selection stays empty. That emptiness **is** the agreement
 * between the two menus (UC-WP-76 GIVEN 3) — marking „Projects" there would say the wrong page.
 *
 * The `<nav>` is ours and named of its own: the basis renders `ul[role=menu]` with no landmark, and a second
 * unnamed navigation would make the tree's helpers ambiguous — a screen reader and the tests both ask the two
 * menus by name.
 */
export function AppTop({ active, onNavigate }: Props) {
  const items: NavItem[] = [
    entry(projectsPath, content.nav.projects, active === projectsPath),
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
