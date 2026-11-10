import type { MouseEvent, ReactNode } from 'react'
import { Button, Tooltip } from 'antd'

interface Props {
  /**
   * The action's word out of the string catalog — it is the tooltip **and** the accessible name at once
   * (R-WP-59, R-WP-27): an icon without a name would be a defect, and two words would be two answers.
   */
  label: string
  icon: ReactNode
  /** The caller's action, event included: a tile's action has to be able to stop the tile's own click. */
  onClick?: (event: MouseEvent<HTMLElement>) => void
  /** `submit` keeps the form the one decider of what a send means (UC-WP-57). */
  htmlType?: 'button' | 'submit' | 'reset'
  size?: 'small' | 'middle' | 'large'
  disabled?: boolean
  /** Where an action stands is the host's question, so the class sits on the outermost node. */
  className?: string
  'data-testid'?: string
}

/**
 * The one shape of an action of this app (R-WP-59, R-WP-46): an icon that carries the shape and one catalog
 * word that carries the tooltip and the accessible name — never a text, and no page builds a second
 * icon-button path.
 *
 * It knows no action, no path and no meaning of a click: who acts and what that does stays with the page
 * (UC-WP-96 GIVEN 4 — only the drawing moves).
 *
 * One browser fact decides its shape: a `disabled` element takes no pointer events, so a tooltip hung on the
 * button alone would never open for a disabled action (`@rc-component/tooltip` reads a disabled child as
 * "tooltip off"). The plain node the tooltip wraps is therefore a `<span>` — hit-testing falls back to it, so
 * the disabled action still explains itself, as the plugin's icon buttons do. `className` goes to that span
 * (it is the grid or flex child), `aria-label` and `data-testid` to the `<button>`: the name belongs to the
 * control, and a screen reader or a test has to reach the element that is clicked.
 */
export function IconButton(props: Props) {
  const { label, icon, onClick, htmlType, size, disabled, className, ...rest } = props
  return (
    <Tooltip title={label}>
      <span className={className}>
        <Button
          aria-label={label}
          disabled={disabled}
          htmlType={htmlType}
          icon={icon}
          size={size}
          onClick={onClick}
          {...rest}
        />
      </span>
    </Tooltip>
  )
}
