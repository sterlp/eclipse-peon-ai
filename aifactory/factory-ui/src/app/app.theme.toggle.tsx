import { Switch } from 'antd'
import { MoonOutlined, SunOutlined } from '@ant-design/icons'

import { content } from '../shared/content'
import { useThemeMode } from '../shared/theme.mode'

/**
 * The switch between the two colour modes (UC-WP-71): a Switch of the basis, so it carries its own colours on
 * the dark band instead of asking for a token of its own. Which mode a browser shows is owned by
 * `shared/theme.mode.ts`, where the switch stands by the header — this component only connects the two.
 */
export function AppThemeToggle() {
  const { mode, choose } = useThemeMode()
  const dark = mode === 'dark'

  return (
    <Switch
      className="app-theme"
      data-testid="theme-toggle"
      aria-label={content.theme.toggle(mode)}
      checked={dark}
      checkedChildren={<MoonOutlined />}
      unCheckedChildren={<SunOutlined />}
      onChange={(checked: boolean) => choose(checked ? 'dark' : 'light')}
    />
  )
}
