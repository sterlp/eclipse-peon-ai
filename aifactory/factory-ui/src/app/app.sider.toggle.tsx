import { Button } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons'

import { content } from '../shared/content'

interface Props {
  /** What the frame holds: is the tree folded away? */
  collapsed: boolean
  onToggle: () => void
}

/**
 * The one handle of the tree (R-WP-44): a text button of the basis in the band, because the tree is needed
 * briefly and the chat owns the area. The handle speaks in glyphs, so its name says what pressing it will do
 * (`content.nav.toggleSider`), and the icon answers which way it goes — the basis' own pattern for the
 * folding of a sider.
 *
 * The state is the frame's (`app.shell.tsx`) and not the sider's own: the responsive listener of the basis
 * asks the same question, and two owners of one answer disagree the moment the window changes its width.
 */
export function AppSiderToggle({ collapsed, onToggle }: Props) {
  return (
    <Button
      className="app-sider-toggle"
      type="text"
      data-testid="sider-toggle"
      aria-label={content.nav.toggleSider(collapsed)}
      icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
      onClick={onToggle}
    />
  )
}
