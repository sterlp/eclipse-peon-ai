import Nav from 'react-bootstrap/Nav'
import NavItem from 'react-bootstrap/NavItem'
import NavLink from 'react-bootstrap/NavLink'

import { content } from '../shared/content'
import type { ViewName } from './app.paths'

const ENTRIES: { id: ViewName; label: string }[] = [
  { id: 'projects', label: content.nav.projects },
  { id: 'chat', label: content.nav.chat },
  { id: 'settings', label: content.nav.settings },
]

interface Props {
  active: ViewName
  onSelect: (view: ViewName) => void
}

/**
 * The frame's navigation (R-WP-1): exactly three entries, and the one showing is marked. It is a menu and
 * not a tab strip — the entries address a view instead of a panel, so the mark is `aria-current`, which is
 * also what a link carries once the view lives in the URL.
 */
export function AppNav({ active, onSelect }: Props) {
  return (
    <nav className="app-nav-bar" aria-label={content.nav.views}>
      <Nav as="ul" className="app-nav">
        {ENTRIES.map(entry => (
          <NavItem key={entry.id} as="li">
            <NavLink
              as="button"
              type="button"
              active={active === entry.id}
              aria-current={active === entry.id ? 'page' : undefined}
              onClick={() => onSelect(entry.id)}
            >
              {entry.label}
            </NavLink>
          </NavItem>
        ))}
      </Nav>
    </nav>
  )
}
