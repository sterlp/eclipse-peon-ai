import { DownOutlined } from '@ant-design/icons'

/**
 * The one arrow of a field that offers a list (R-WP-67, R-WP-46): AntD's `AutoComplete` paints none of its own
 * (6.7.0 — `suffix`/`suffixIcon` have no default, unlike `Select`'s `<DownOutlined aria-hidden />`), so every
 * offer field hands this definition to the base. It lives on its own because no field module owns the look of
 * another field. Hidden from assistive tech as the base's own arrow is: the field's label says what it is, the
 * arrow only says that a list can be opened. A field without a list (FREE, UNKNOWN, NONE) never gets it.
 */
export function ChoiceArrow() {
  return <DownOutlined aria-hidden="true" />
}
