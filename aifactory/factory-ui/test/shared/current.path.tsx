import { useLocation } from 'react-router-dom'

/**
 * The probe a routed test reads the address from — a component of its own so the harness stays a file of
 * helpers (fast-refresh rule), and the claim it carries is unchanged: only the router knows the path.
 *
 * `data-entry` carries the router's own key for the entry: two entries of the same path differ in it, which
 * is the only way a test can tell a navigation from a navigation that changed nothing (R-WP-38).
 */
export function CurrentPath() {
  const { pathname, key } = useLocation()
  return (
    <span data-testid="current-path" data-entry={key} hidden>
      {pathname}
    </span>
  )
}
