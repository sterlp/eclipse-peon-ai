import { expect, test } from 'vitest'

import {
  acceptedAction,
  afterSnapshot,
  type ActionWatch,
} from '../../src/chat/chat.action.rebuild'
import type { AgentTile, AgentTileState, TileCapability } from '../../src/chat/chat.agents.client'

/**
 * The seats the server hands out (`factory-backend` AgentTiles.capabilitiesOf): the conversation's own agent
 * offers Clear and Compact but no insight into a conversation of his own, a member offers all four. That set
 * is the client's only handle on "whose conversation is this" — it knows no agent name (D-14).
 */
const OWN: TileCapability[] = ['CONTEXT', 'CLEAR', 'COMPACT']
const MEMBER: TileCapability[] = ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT']

function rosterOf(name: string, state: AgentTileState, capabilities: TileCapability[]): AgentTile[] {
  return [
    {
      name,
      context: '3k',
      tokens: 3_400,
      estimate: false,
      contextPercent: 3,
      budget: 100_000,
      state,
      capabilities,
    },
  ]
}

const MARK = 7

/** The one seat test of D-2: which accepted action is worth watching, and which is not. */
// UC-WP-100
test('onlyTheOwnConversationsActionIsWatched', () => {
  const own = rosterOf('Jon', 'IDLE', OWN)[0]

  // the seat decides, never a name: Compact watches COMPACTING, Clear watches WORKING
  expect(acceptedAction(own, 'compact', MARK)).toEqual({
    name: 'Jon',
    region: 'COMPACTING',
    mark: MARK,
    rising: false,
  })
  expect(acceptedAction(own, 'clear', MARK)?.region).toBe('WORKING')

  // a member's conversation is his own (R-WP-26): his action changes nothing here, and a name the roster
  // does not carry cannot be watched either
  const member = rosterOf('Dev', 'IDLE', MEMBER)[0]
  expect(acceptedAction(member, 'compact', MARK)).toBeNull()
  expect(acceptedAction(member, 'clear', MARK)).toBeNull()
  expect(acceptedAction(undefined, 'compact', MARK)).toBeNull()
})

/** The edge itself: rise, fall, one rebuild with the mark of the acceptance, watch spent. */
// UC-WP-100
test('theEndOfTheActionsRegionIsTheOneRebuild', () => {
  const rebuilds: number[] = []
  const rebuild = (mark: number) => {
    rebuilds.push(mark)
  }
  const watch = acceptedAction(rosterOf('Jon', 'IDLE', OWN)[0], 'compact', MARK)

  const risen = afterSnapshot(watch, rosterOf('Jon', 'COMPACTING', OWN), rebuild)
  expect(risen, 'the area was seen: from here the end is a falling edge').toEqual({
    name: 'Jon',
    region: 'COMPACTING',
    mark: MARK,
    rising: true,
  })
  expect(rebuilds, 'the area itself rebuilds nothing').toEqual([])

  const spent = afterSnapshot(risen, rosterOf('Jon', 'IDLE', OWN), rebuild)

  // the mark is the one the acceptance took and not the one the falling edge sees: the action's own live
  // lines stand behind it and stay as its traces
  expect(rebuilds, 'one read, with the mark of the acceptance').toEqual([MARK])
  expect(spent, 'the watch is spent — one rebuild per action').toBeNull()
  expect(afterSnapshot(spent, rosterOf('Jon', 'IDLE', OWN), rebuild), 'no second rebuild').toBeNull()
  expect(rebuilds).toEqual([MARK])

  // an action that ended in ERROR ended: its snapshot is the fall too, and no watch stays armed
  const clear = acceptedAction(rosterOf('Jon', 'IDLE', OWN)[0], 'clear', 2)
  const workingAgain = afterSnapshot(clear, rosterOf('Jon', 'WORKING', OWN), rebuild)
  expect(afterSnapshot(workingAgain, rosterOf('Jon', 'ERROR', OWN), rebuild)).toBeNull()
  expect(rebuilds, 'a failed action rebuilds as well — the Bestand is valid either way').toEqual([MARK, 2])
})

/** Nothing without a click: the core's own Compact, a too-early snapshot, a roster that names nobody. */
// UC-WP-100
test('withoutAnAcceptedActionASnapshotRebuildsNothing', () => {
  const rebuilds: number[] = []
  const rebuild = (mark: number) => {
    rebuilds.push(mark)
  }

  // a Compact the core started on its own (auto, pre-turn) reports the very same states and ends with the
  // very same edge — without a click behind it nothing may be re-read (Grenze 1 of R-WP-63)
  expect(afterSnapshot(null, rosterOf('Jon', 'COMPACTING', OWN), rebuild)).toBeNull()
  expect(afterSnapshot(null, rosterOf('Jon', 'IDLE', OWN), rebuild)).toBeNull()

  // a snapshot taken before the claim was published is not the end of anything: the edge may still come
  const watch: ActionWatch | null = acceptedAction(rosterOf('Jon', 'IDLE', OWN)[0], 'compact', MARK)
  const kept = afterSnapshot(watch, rosterOf('Jon', 'IDLE', OWN), rebuild)
  expect(kept, 'the watch stays armed — the rising edge can still arrive').toEqual(watch)
  expect(afterSnapshot(kept, rosterOf('Dev', 'IDLE', MEMBER), rebuild), 'a roster that names the tile not at all says nothing about its end').toEqual(
    watch,
  )
  expect(afterSnapshot(kept, rosterOf('Jon', 'COMPACTING', OWN), rebuild)?.rising, 'then the area').toBe(true)

  expect(rebuilds, 'no snapshot of any of these reads the conversation again').toEqual([])
})
