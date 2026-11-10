import type { ReactNode } from 'react'

import type { MenuItemType, SubMenuType } from 'antd/es/menu/interface'

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
 * One collapsible group of the tree (R-WP-44): the title opens and closes, the entries under it are the
 * handles. A group answers **which project** and never **where**, so it carries no mark of its own.
 */
export type NavGroup = SubMenuType<NavItem>

/**
 * One entry, marked only because the address names it — the caller passes what it read from the path, so
 * neither menu holds a state that could disagree with the URL (UC-WP-52).
 */
export function entry(key: string, label: string, active: boolean, icon?: ReactNode): NavItem {
  const item: NavItem = icon === undefined ? { key, label } : { key, label, icon }
  return active ? { ...item, 'aria-current': 'page' } : item
}
