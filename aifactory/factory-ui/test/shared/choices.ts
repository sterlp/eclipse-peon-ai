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
 * So a choice a test opens needs an `id` — which is what a labelled field carries anyway. Where the label
 * cannot resolve a field because the page shows it twice by rule („Think (Default)" is the default form's
 * field and the Dev slot's, R-WP-49), `id` names the field; without it the label stays the handle.
 */
function fieldOf(label: string, id?: string): Element | null {
  return id ? document.getElementById(id) : screen.getByLabelText(label)
}

function optionsOf(label: string, id?: string): Element[] {
  const fieldId = fieldOf(label, id)?.getAttribute('id')
  const list = fieldId ? document.getElementById(`${fieldId}_list`) : null
  const dropdown = list?.closest('.ant-select-dropdown')
  return Array.from(dropdown?.querySelectorAll('.ant-select-item-option-content') ?? [])
}

/** Opens the choice and answers with the labels of its entries, in the order the screen shows them. */
export async function openChoice(label: string, id?: string): Promise<string[]> {
  if (optionsOf(label, id).length === 0) {
    fireEvent.mouseDown(fieldOf(label, id) as Element)
  }
  await waitFor(() =>
    expect(optionsOf(label, id).length, `the choice of ${label} opens`).toBeGreaterThan(0),
  )
  return optionsOf(label, id).map((entry) => entry.textContent ?? '')
}

/** Picks one entry of the choice by its label, as a click on the screen does. */
export async function chooseOption(label: string, text: string, id?: string): Promise<void> {
  await openChoice(label, id)
  const option = optionsOf(label, id).find((entry) => entry.textContent === text)
  expect(option, `the choice of ${label} offers ${text}`).toBeDefined()
  fireEvent.click(option as Element)
}

/** What the choice shows as its own value — the label, not the wire value. */
export function shownChoice(label: string, id?: string): string {
  const shown = fieldOf(label, id)
    ?.closest('.ant-select')
    ?.querySelector('.ant-select-content')
  return shown?.getAttribute('title') ?? shown?.textContent ?? ''
}
