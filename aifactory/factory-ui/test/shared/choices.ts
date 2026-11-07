import { fireEvent, screen, waitFor } from '@testing-library/react'

/**
 * The choices of the design basis (AntD 6) answer differently from a `<select>`: their entries are not
 * `<option>`s but a dropdown, and what the field shows is the catalog label — the wire value travels in the
 * request body.
 *
 * A dropdown is found through the field's own `id`: the base names its list `<id>_list`, and that list is in
 * the document from the first opening onwards — while `aria-expanded` and `aria-controls` come and go with
 * the popup, and jsdom decides both differently from a browser (nothing blurs, a mouse-down on an open
 * choice closes it). Naming the list is also what keeps a document with several choices honest: an entry is
 * never taken from whichever dropdown happens to be first.
 *
 * So a choice a test opens needs an `id` — which is what a labelled field carries anyway.
 */
function optionsOf(label: string): Element[] {
  const id = screen.getByLabelText(label).getAttribute('id')
  const list = id ? document.getElementById(`${id}_list`) : null
  const dropdown = list?.closest('.ant-select-dropdown')
  return Array.from(dropdown?.querySelectorAll('.ant-select-item-option-content') ?? [])
}

/** Opens the choice and answers with the labels of its entries, in the order the screen shows them. */
export async function openChoice(label: string): Promise<string[]> {
  if (optionsOf(label).length === 0) {
    fireEvent.mouseDown(screen.getByLabelText(label))
  }
  await waitFor(() => expect(optionsOf(label).length, `the choice of ${label} opens`).toBeGreaterThan(0))
  return optionsOf(label).map((entry) => entry.textContent ?? '')
}

/** Picks one entry of the choice by its label, as a click on the screen does. */
export async function chooseOption(label: string, text: string): Promise<void> {
  await openChoice(label)
  const option = optionsOf(label).find((entry) => entry.textContent === text)
  expect(option, `the choice of ${label} offers ${text}`).toBeDefined()
  fireEvent.click(option as Element)
}

/** What the choice shows as its own value — the label, not the wire value. */
export function shownChoice(label: string): string {
  const shown = screen.getByLabelText(label).closest('.ant-select')?.querySelector('.ant-select-content')
  return shown?.getAttribute('title') ?? shown?.textContent ?? ''
}
