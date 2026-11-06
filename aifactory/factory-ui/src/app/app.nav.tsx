import { Menu } from 'antd'

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
 * not a tab strip — the entries address a view instead of a panel.
 *
 * The mark is the basis' own selected state (its accent on the entry), not `aria-current`: a menu of the
 * design basis carries its selection as its item state, and bending that into `aria-current` would be a
 * style war with the library (R-WP-39). Named deviation ANTD-A1.
 */
export function AppNav({ active, onSelect }: Props) {
  return (
    <nav className="app-nav-bar" aria-label={content.nav.views}>
      <Menu
        className="app-nav"
        mode="horizontal"
        selectedKeys={[active]}
        onClick={entry => onSelect(entry.key as ViewName)}
        items={ENTRIES.map(entry => ({ key: entry.id, label: entry.label }))}
      />
    </nav>
  )
}
