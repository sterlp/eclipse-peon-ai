import { expect, test } from 'vitest'

import { withoutConfirmed, type PendingLine } from '../../src/chat/chat.pending'
import type { ChatMessage } from '../../src/chat/chat.client'

function user(text: string): ChatMessage {
  return { role: 'user', text }
}

function jon(text: string): ChatMessage {
  return { role: 'jon', text }
}

// UC-WP-69
test('aLaterUserLineConfirmsAnEarlierPendingLine', () => {
  // the watermark is the number of history lines that stood when he sent — his own line cannot precede it
  const pending: PendingLine[] = [{ text: 'und die Tests?', afterHistory: 1 }]
  const loaded = [user('wo liegen die Dateien?'), user('und die Tests?'), jon('Vierhundert.')]

  expect(withoutConfirmed(pending, loaded)).toEqual([])
  // pure: the caller's list is not touched
  expect(pending).toHaveLength(1)
})

// UC-WP-69
test('aUserLineBeforeTheWatermarkConfirmsNothing', () => {
  // both user lines stood before he sent, so neither is his — an older input of his own or of another
  // browser confirms nothing, and the line is kept rather than lost (R-WP-37)
  const pending: PendingLine[] = [{ text: 'und die Tests?', afterHistory: 2 }]
  const loaded = [user('bitte X'), user('und ohne Namen'), jon('Erledigt.')]

  expect(withoutConfirmed(pending, loaded)).toBe(pending)
})

// UC-WP-69
test('aQueuedMarkerLineConfirmsItsPendingLine', () => {
  // the core's marker makes the history line say something else than what he typed; the confirmation
  // knows no format and parses nothing — position alone recognises it (UC-WP-69)
  const pending: PendingLine[] = [{ text: 'und die Tests?', afterHistory: 1 }]
  const loaded = [
    user('wo liegen die Dateien?'),
    user('[Queued Message] (14:32): und die Tests?'),
    jon('Vierhundert.'),
  ]

  expect(withoutConfirmed(pending, loaded)).toEqual([])
})

// UC-WP-69
test('aConfirmingLineConfirmsNoSecondPendingLine', () => {
  // two inputs, the history carries only the first one: the line that confirmed it is used, and the
  // kept line's watermark moves behind it — the next read of the same history confirms no second line
  const first: PendingLine = { text: 'zähl die Dateien', afterHistory: 0 }
  const second: PendingLine = { text: 'und die Tests?', afterHistory: 0 }
  const loaded = [user('zähl die Dateien'), jon('Im Root liegen pom.xml und README.md')]

  const rest = withoutConfirmed([first, second], loaded)
  expect(rest).toEqual([{ text: 'und die Tests?', afterHistory: 1 }])
  expect(withoutConfirmed(rest, loaded)).toBe(rest)
})
