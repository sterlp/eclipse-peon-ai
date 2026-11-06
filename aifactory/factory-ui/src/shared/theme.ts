import { theme, type ThemeConfig } from 'antd'

import baseSheet from '../index.css?raw'

/**
 * The one place a design token of `index.css` becomes a token of the design basis (R-WP-39). Reading the
 * sheet rather than repeating its colours is what keeps the claim of R-WP-27 true — one table of colours,
 * and a changed colour cannot pass one of the two readers by.
 *
 * `getComputedStyle` is not the reader: jsdom resolves no custom property, so a test would measure an
 * empty string and call that a theme. The sheet as written answers in both worlds, and it is the artifact
 * the browser paints from anyway.
 */

/** The gutter of the frame (`.app` in `styles.css`) — the header holds its content on the same line. */
const FRAME_GUTTER = '0 1rem'

/** The light values, as `:root` states them. */
const LIGHT = blockOf(baseSheet, ':root,')

/** The dark values, as an explicit `data-theme="dark"` states them — the same table the media query repeats. */
const DARK = blockOf(baseSheet, "[data-theme='dark'] {")

/** One declaration block of the sheet, from its selector to its closing brace. */
function blockOf(sheet: string, selector: string): string {
  const at = sheet.indexOf(selector)
  if (at < 0) {
    throw new Error(`${selector} is not stated in the base sheet`)
  }
  const end = sheet.indexOf('}', at)
  return sheet.slice(at, end === -1 ? sheet.length : end)
}

/** The value a sheet block gives one custom property, or nothing — a token nobody states is invented nowhere. */
function tokenValue(block: string, name: string): string | undefined {
  const found = new RegExp(`${name}:\\s*([^;]+);`).exec(block)
  return found === null ? undefined : found[1].trim()
}

/**
 * The theme of the design basis for one colour mode. Every colour is read from the base sheet; a colour
 * the sheet does not name is left to the library rather than guessed here.
 */
export function factoryTheme(dark: boolean): ThemeConfig {
  const tokens = dark ? DARK : LIGHT
  const read = (name: string): string | undefined => tokenValue(tokens, name)

  return {
    algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: read('--accent'),
      colorBgBase: read('--bg'),
      colorTextHeading: read('--text-h'),
      colorText: read('--text'),
      colorBorder: read('--border'),
      // one border colour is stated in the sheet, so no component may invent a lighter second one
      colorBorderSecondary: read('--border'),
      colorSuccess: read('--agent'),
      colorError: read('--problem'),
      colorWarning: read('--question'),
      fontFamily: read('--sans'),
      fontFamilyCode: read('--mono'),
    },
    /**
     * The three surfaces the library paints on its own. Left alone they are the library's greys and its
     * dark-header white — which on our page reads as a second theme. The card stays without a background
     * because the look says the border names the block, not a fill (`docs/ui.md`).
     */
    components: {
      Card: { colorBgContainer: 'transparent' },
      Layout: {
        bodyBg: read('--bg'),
        headerBg: read('--bg'),
        headerColor: read('--text-h'),
        headerPadding: FRAME_GUTTER,
      },
    },
  }
}
