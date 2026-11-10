import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, test } from 'vitest'
import { DeleteOutlined, SendOutlined } from '@ant-design/icons'

import { IconButton } from '../../src/shared/icon.button'
import { content } from '../../src/shared/content'

beforeEach(() => {
  cleanup()
})

// UC-WP-96
test('actionsAreIconButtonsWithTooltips', async () => {
  let sends = 0
  render(
    <>
      <IconButton
        data-testid="probe-send"
        icon={<SendOutlined />}
        label={content.chat.send}
        onClick={() => sends++}
      />
      <IconButton data-testid="probe-clear" disabled icon={<DeleteOutlined />} label={content.tiles.clear} />
    </>,
  )

  // an action of the widget: the icon carries the shape, the catalog word carries the name and the tooltip,
  // and the word is the same in both (R-WP-59, R-WP-27 — „ein Icon ohne Namen ist ein Defekt")
  for (const label of [content.chat.send, content.tiles.clear]) {
    const button = screen.getByRole('button', { name: label })
    expect(button.textContent?.trim(), 'an icon button carries no text').toBe('')
    expect(button.querySelector('.anticon'), 'the icon sits on the button').not.toBeNull()

    fireEvent.mouseOver(button.parentElement as HTMLElement)
    expect(await screen.findByText(label), `the tooltip names ${label}`).toBeInTheDocument()
  }

  // the click stays the caller's action (UC-WP-96 GIVEN 4), the refusal to act stays the caller's state
  expect(screen.getByRole('button', { name: content.tiles.clear })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: content.chat.send }))
  expect(sends).toBe(1)
})
