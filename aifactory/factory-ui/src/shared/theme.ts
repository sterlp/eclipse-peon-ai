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

/**
 * The dark values, as the table the document states them: `app/app.root.tsx` names the colour mode the app
 * is in on the root element (`data-theme`), so this is the dark mode a running app paints.
 */
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
  // What the dark table does not state differently keeps the light value: an undefined token would answer
  // with the library's own default, and in the dark that is a second look (R-WP-39). `--sans` and `--mono`
  // are stated once in `:root`, for both modes — the sheet's cascade does the same, and the basis has to.
  const read = (name: string): string | undefined =>
    tokenValue(tokens, name) ?? (dark ? tokenValue(LIGHT, name) : undefined)

  return {
    algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: read('--accent'),
      // the surface, not the page: `colorBgBase` seeds the basis' whole derivation (a card's fill, a field's
      // fill), so the grey page would paint every input grey (R-WP-39)
      colorBgBase: read('--surface'),
      colorBgLayout: read('--bg'),
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
     * The surfaces the library paints on its own. Left alone they are the library's greys and its
     * dark-header white — which on our page reads as a second theme (R-WP-39).
     */
    components: {
      Layout: {
        bodyBg: read('--bg'),
        // the header is a band of its own (R-WP-43): its colours are the band's, not the page's
        headerBg: read('--top-bg'),
        headerColor: read('--top-text'),
        headerPadding: FRAME_GUTTER,
        // the light sider paints this one, not `siderBg` — that token belongs to the dark variant and
        // would leave the tree in a second grey beside our own (R-WP-39). The tree is a surface beside the
        // page, not part of it: it stands on `--surface`
        lightSiderBg: read('--surface'),
      },
      /**
       * The dark menu of the header sits **on** that band, so it paints the band's own colour and not the
       * library's stock `#001529` — two dark tones in one surface would be two looks in one place (R-WP-39).
       * The selection is left to the basis: it derives it from `colorPrimary`, which is our `--accent`.
       */
      Menu: {
        darkItemBg: read('--top-bg'),
        darkItemColor: read('--top-text'),
        darkItemHoverColor: read('--top-text'),
        darkSubMenuItemBg: read('--top-bg'),
      },
      /**
       * The trace of a turn is read beside the answer and not as a list of its own: its dots carry the line
       * and its gaps stay small, which is the measure R-WP-40 of `docs/chat.md` asks for and not this
       * file's taste. The dots are tokens and stay tokens. The GAPS are not: the basis builds the timeline
       * on its Steps and reads `itemPaddingBottom` only in its alternate layout, which the trace does not
       * use — the small gap is therefore a rule of the sheet inside `.chat-line.trace` (`styles.css`), and
       * setting the inert token here would only look like a decision.
       */
      Timeline: {
        dotSize: 12,
        dotBorderWidth: 2,
      },
    },
  }
}
