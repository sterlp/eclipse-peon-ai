import { useLocation } from 'react-router-dom'

/**
 * The probe a routed test reads the address from — a component of its own so the harness stays a file of
 * helpers (fast-refresh rule), and the claim it carries is unchanged: only the router knows the path.
 */
export function CurrentPath() {
  const { pathname } = useLocation()
  return (
    <span data-testid="current-path" hidden>
      {pathname}
    </span>
  )
}
