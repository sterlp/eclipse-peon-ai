import type { MenuItemType } from 'antd/es/menu/interface'

/**
 * The item of a menu of the frame — the type of **both** of them, so the mark is stated once for the header
 * and the tree instead of twice (R-WP-38: mark and URL agree in one place).
 *
 * The basis carries a menu's selection as the item state of its entry and states no `aria-current` of its own
 * (ANTD-A1); it does pass an unknown item prop through to the `li` it renders, so the mark is provable to a
 * screen reader as well. Named deviation ANTD-A9.
 */
export type NavItem = MenuItemType & { 'aria-current'?: 'page' }

/**
 * One entry, marked only because the address names it — the caller passes what it read from the path, so
 * neither menu holds a state that could disagree with the URL (UC-WP-52).
 */
export function entry(key: string, label: string, active: boolean): NavItem {
  return active ? { key, label, 'aria-current': 'page' } : { key, label }
}
