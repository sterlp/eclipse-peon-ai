import { expect, test } from 'vitest'

import { withoutConfirmed, type PendingLine } from '../../src/chat/chat.pending'
import type { ChatMessage } from '../../src/chat/chat.client'

function user(text: string, origin?: string): ChatMessage {
  return { role: 'user', text, origin: origin ?? null }
}

function jon(text: string): ChatMessage {
  return { role: 'jon', text }
}

function pending(afterHistory: number, origin: string | null, text = 'und die Tests?'): PendingLine {
  return { text, afterHistory, origin }
}

// UC-WP-69
test('aLaterUserLineConfirmsAnEarlierPendingLine', () => {
  // the watermark is the number of history lines that stood when he sent — his own line cannot precede it
  const waiting = [pending(1, 'Paul')]
  const loaded = [user('wo liegen die Dateien?', 'Paul'), user('und die Tests?', 'Paul'), jon('Vierhundert.')]

  expect(withoutConfirmed(waiting, loaded)).toEqual([])
  // pure: the caller's list is not touched
  expect(waiting).toHaveLength(1)
})

// UC-WP-69
test('aUserLineBeforeTheWatermarkConfirmsNothing', () => {
  // the sender matches and his last line still stands behind the watermark: his input waits in the
  // queue, and an older line of his own confirms nothing (R-WP-37)
  const waiting = [pending(2, 'Paul')]
  const loaded = [user('bitte X', 'Paul'), user('und ohne Namen', 'Paul'), jon('Erledigt.')]

  expect(withoutConfirmed(waiting, loaded)).toBe(waiting)
})

// UC-WP-69
test('aQueuedMarkerLineConfirmsItsPendingLine', () => {
  // the core's marker makes the history line say something else than what he typed; the confirmation
  // knows no format and parses nothing — sender and position alone recognise it (UC-WP-69)
  const waiting = [pending(1, 'Paul')]
  const loaded = [
    user('wo liegen die Dateien?', 'Paul'),
    user('[Queued Message] (14:32): und die Tests?', 'Paul'),
    jon('Vierhundert.'),
  ]

  expect(withoutConfirmed(waiting, loaded)).toEqual([])
})

// UC-WP-69
test('oneHistoryLineConfirmsEveryPendingLineOfItsSenderSentBeforeIt', () => {
  // the core bundles two queued inputs of one sender into ONE memory line (R-WP-37, Bündel-Fall):
  // that single line confirms both — 1:1 consumption would leave the second one standing forever
  const waiting = [pending(0, 'Paul', 'zähl die Dateien'), pending(0, 'Paul', 'und die Tests?')]
  const loaded = [
    user('[Queued Message] (14:32): [User Paul]: zähl die Dateien\n[User Paul]: und die Tests?', 'Paul'),
    jon('Im Root liegen pom.xml und README.md'),
  ]

  expect(withoutConfirmed(waiting, loaded)).toEqual([])
})

// UC-WP-69
test('aLineOfAnotherSenderConfirmsNothing', () => {
  // a shared chat speaks in one coordinate system (R-WP-15): Anna's line behind the watermark is
  // hers alone — Paul's input still waits in the queue and keeps its provisional line
  const waiting = [pending(1, 'Paul')]
  const loaded = [user('wo liegen die Dateien?', 'Paul'), user('Annas Frage', 'Anna'), jon('Erledigt.')]

  expect(withoutConfirmed(waiting, loaded)).toBe(waiting)
})

// UC-WP-69
test('aNamelessLineConfirmsTheNamelessPendingLine', () => {
  // without a name the server writes no prefix and the history line carries no origin (UC-WP-48):
  // null is one sender class of its own, and the nameless line confirms the nameless pending one
  const waiting = [pending(0, null)]
  const loaded = [user('und die Tests?'), jon('Vierhundert.')]

  expect(withoutConfirmed(waiting, loaded)).toEqual([])
})
