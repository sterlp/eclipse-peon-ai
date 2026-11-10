import { useCallback, useRef, useState } from 'react'
import type { RefObject } from 'react'

/** How far from the end of the log still counts as "the user reads the newest line" (R-WP-35). */
const SCROLL_END_SLOP = 24

/**
 * Where the user reads (R-WP-35, UC-WP-66, UC-WP-80): a new line takes him to the end of the log while he
 * stands there, and leaves him reading while he does not. Whoever has moved away from the end gets the way
 * back — the scroll-to-bottom button shows itself exactly while `following` is false, and both its click and
 * the user's own send take him to the end and re-arm the follow.
 *
 * `following` answers "may a new line move the screen", so it is state and not a ref: the button hangs on it.
 * The scroll handler writes it only when it changes, and React steps out on an equal value — otherwise every
 * scroll event would re-render the log for one boolean. The reading position itself stays a ref, because a new
 * line has to read it during the same event and not one render later.
 *
 * Knows the scroll area and nothing else: no transport, no conversation, no client.
 */
export function useFollowBottom(logRef: RefObject<HTMLElement | null>): {
  following: boolean
  /** the log's own scroll, read from the area and not from the event (R-WP-35) */
  onLogScroll: () => void
  /** to the end and re-armed — his own input, and the click on the button (UC-WP-80) */
  followNow(): void
  /** to the end only while he stands there: what a new line owes him (UC-WP-66) */
  followIfFollowing(): void
} {
  const atBottom = useRef(true)
  const [following, setFollowing] = useState(true)

  const keep = useCallback(
    (nearEnd: boolean) => {
      atBottom.current = nearEnd
      setFollowing(nearEnd)
    },
    // the state setter is stable; nothing here reads a render value
    [],
  )

  const jumpTo = useCallback(() => {
    const area = logRef.current
    if (area !== null) {
      // no `behavior: 'smooth'` (UC-WP-60): a streamed answer would arrive in a view still on its way
      area.scrollTop = area.scrollHeight
    }
  }, [logRef])

  const followNow = useCallback(() => {
    keep(true)
    jumpTo()
  }, [jumpTo, keep])

  const followIfFollowing = useCallback(() => {
    if (atBottom.current) {
      jumpTo()
    }
  }, [jumpTo])

  const onLogScroll = useCallback(() => {
    const area = logRef.current
    if (area !== null) {
      keep(area.scrollHeight - area.scrollTop - area.clientHeight < SCROLL_END_SLOP)
    }
  }, [keep, logRef])

  return { following, onLogScroll, followNow, followIfFollowing }
}
