import { ConfigProvider } from 'antd'

import { AppBrowser } from './app.routes'
import { factoryTheme } from '../shared/theme'
import { usePrefersDark } from '../shared/prefers.dark'

/**
 * The one design basis of the app (R-WP-39): the theme of the basis is derived from our tokens and wrapped
 * once around everything, so no page carries a colour of its own and no second sheet competes with it.
 *
 * Its own file because the theme is a component, and `main.tsx` is an entry that mounts and exports
 * nothing — a component living there would be outside every fast-refresh boundary.
 */
export function AppRoot() {
  return (
    <ConfigProvider theme={factoryTheme(usePrefersDark())}>
      <AppBrowser />
    </ConfigProvider>
  )
}
