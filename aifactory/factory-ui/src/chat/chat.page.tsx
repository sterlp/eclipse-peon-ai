import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { isTraceLine, type ChatMessage, type ChatStreamEvent } from './chat.client'
import { agentAction, type AgentAction } from './chat.agents.client'
import { acceptedAction, afterSnapshot, type ActionWatch } from './chat.action.rebuild'
import { useTileBoard } from './chat.board'
import { ChatComposer } from './chat.composer'
import { useEdgeInsight } from './chat.edge'
import { ChatInsight } from './chat.insight'
import { ChatLog } from './chat.log'
import {
  appendLiveDelegation,
  appendLiveDiff,
  appendLiveError,
  appendLiveProblem,
  appendLiveText,
  appendLiveThink,
  appendLiveTool,
  endLiveDelegation,
  livePreview,
  type LiveItem,
} from './chat.live'
import { lastVisibleUserText, type PendingLine } from './chat.pending'
import { useHistoryReads } from './chat.read'
import { useFollowBottom } from './chat.scroll'
import { ScrollBottomButton } from './chat.scroll.bottom'
import { ChatTiles } from './chat.tiles'
import { ChatWaiting } from './chat.waiting'
import { openChatStream } from './chat.stream'
import { useTurnControls } from './chat.turn.controls'
import { errorMessage } from '../shared/api.client'
import { ApiErrorPanel } from '../shared/api.error.panel'
import { content } from '../shared/content'
import type { ProjectDto } from '../shared/server-api'
import { sseBackoff } from '../shared/sse.client'

interface Props {
  project: ProjectDto | null
  /**
   * Anything the shell wants beside the chat — today the file area of `project`. It is drawn outside the
   * chat region and this component knows nothing about it: `chat` keeps not importing `project`, so no word
   * of the file area appears here (§4.3.4 of the increment).
   */
  files?: ReactNode
  /**
   * The frame's reading of the right edge — whether the address holds it for {@link files}, and the way back
   * from it (R-WP-38). No second "open" lives here and this view never navigates: it has no path of the file
   * area to name. Both are read by {@link useEdgeInsight} alone (R-WP-33, UC-WP-60 G3).
   */
  filesOpen?: boolean
  onCloseFiles?(): void
  /**
   * The name of this browser (R-WP-25), given by the shell that holds it — identity is not per project,
   * so this component neither stores nor interprets it: it labels the sends and stamps the own pending
   * lines with it (R-WP-37). Absent means no name: no field on the wire, no prefix (UC-WP-48).
   */
  user?: string | null
}

/**
 * The chat of one project (R-WP-4, R-WP-5): the history is the only truth for finished text, the
 * stream only adds live lines, and there is nothing here to switch — no agent picker, no second
 * conversation, no time on a restored line. A line is named by its author when the server names one.
 *
 * One rule covers connection and reload: <em>(re-)connect ⇒ read the history, live events only
 * append</em>. So a dropped stream and a browser reload lose exactly the live lines and nothing else
 * (R-WP-11), and the reconnect path needs no second buffer and no event memory. A read carries the number
 * this view gave it and gives up only the live lines that stood when it went out: reads are round trips and
 * answer out of order, so neither the older content nor the text of a turn that is already streaming may be
 * lost to one (UC-WP-7).
 *
 * What the turn reports while it runs lives in one list — the answer's text and the tool lines in the
 * order they arrived, each anchored at the rows that stood when the turn reported its first line (D-7, D-A).
 * The anchor is frozen per turn and is what lets both survive a history read: an input the user queues
 * mid-turn does not move the answer that is still streaming, and the trace of a finished turn keeps its
 * place while the answer gives way to the history that carries it (R-WP-28, UC-WP-54).
 *
 * The team's tiles ({@link ChatTiles}) follow the same rule in their own family, kept by {@link useTileBoard} —
 * which stand is the fresher one is the board's question, not this view's, and it knows no agent name (D-14).
 *
 * A tile offering TRANSCRIPT opens {@link ChatInsight} (UC-WP-49), held by {@link useEdgeInsight}. It shares
 * the right edge with the {@link files} slot and never stands with it: the edge carries exactly one overlay,
 * so opening one releases the other (R-WP-33, UC-WP-60 G3) — the drawer by its address, the insight without
 * a trace of its own. Either way it stays a region of this view: no fourth tab and no route, whatever the
 * address shows (R-WP-1).
 *
 * The composer's second control ends the running turn (UC-WP-50). After an accepted stop this view does
 * <em>not</em> re-read the history: the live lines the user was already shown are all that exists of the
 * stopped turn, and there is no second store to read them back from (R-WP-11).
 *
 * The send way of the composer is decided here and nowhere else (UC-WP-57): a text goes out as it stands, an
 * empty field repeats the last user line of the <em>loaded</em> history while no turn works, and does nothing
 * while one works or when the history holds no line. The keyboard keeps its pure text way — an empty Enter
 * never reaches this page (UC-WP-56). {@link ChatWaiting} sits above the composer while a turn works and
 * shows what the stream last reported (R-WP-31); it reads the live list the log already holds and is no
 * second memory.
 *
 * The page holds the state; {@link ChatLog} and {@link ChatComposer} only draw it.
 *
 * The head of the view names the project and nothing else: a second "Chat" heading would repeat the tab
 * the chat already sits in (R-WP-1).
 */
export function ChatPage({
  project,
  files,
  user,
  filesOpen = false,
  onCloseFiles = () => undefined,
}: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [outgoing, setOutgoing] = useState<PendingLine[]>([])
  const [live, setLive] = useState<LiveItem[]>([])
  const [draft, setDraft] = useState('')
  // the one overlay this view may put on the right edge, held to one with the file area (R-WP-33,
  // UC-WP-60 G3); the rule and its measured reason live beside the state they write
  const { shown: insight, openInsight, closeInsight } = useEdgeInsight(filesOpen, onCloseFiles)
  /**
   * What the last read holds, in the two units that read it: `lines` indexes a pending line's own position
   * (chat.pending.ts), `voices` is what a live anchor counts — a step of the trace is a line the read may add
   * at any place, so counting it would move every surviving entry of the turn above it (R-WP-47, A7-N1). A
   * ref because the handler reads the count <em>now</em>: a render's state is one render behind (D-B).
   */
  const historyCount = useRef({ lines: 0, voices: 0 })
  /**
   * The own inputs the history does not carry yet, each remembering how many history lines stood when it
   * was sent — the position its own line cannot precede, which is what confirms it (R-WP-37). Their count
   * is part of the anchor on purpose: an answer that started below them stays below them when one of them
   * becomes a history line (D-A).
   */
  const pendingLines = useRef<PendingLine[]>([])
  /**
   * The live lines on the screen, beside their state. The read of the history has to know how many of them
   * stood <em>when it was issued</em>, and a stream event has to continue the list that is really there —
   * the state of a render is one render behind both (D-B, UC-WP-7).
   */
  const liveItems = useRef<LiveItem[]>([])
  /**
   * The turn this view holds open: the rows that stood when it reported its first line, and its identity.
   * Recomputing the count per event would let an input queued mid-turn move every later chunk of the running
   * answer to a new anchor: the answer would break into blocks and stand below the question typed after it
   * (D-A, UC-WP-54). The identity is what a working step answers to — two turns can anchor at the same
   * number of rows, so the anchor alone cannot say which step is the one being worked on (R-WP-40).
   */
  const turn = useRef<{ anchor: number; id: number; from: number } | null>(null)
  /** Hands out the ids of the turns, ever growing: a step of a past turn can never read as a step of the open one. */
  const turnSeq = useRef(0)
  /** The open turn as the screen sees it — no turn open, no step working (R-WP-40, UC-WP-72 GIVEN 2). */
  const [openTurn, setOpenTurn] = useState<number | null>(null)
  /**
   * The scroll area of the chat. Where the user reads and what a new line owes him — follow him to its end,
   * or leave him reading (R-WP-35) — is `useFollowBottom`'s question; this view only says when the log has
   * new content, because that is the only thing here that knows it.
   */
  const log = useRef<HTMLUListElement>(null)
  const { following, onLogScroll, followNow, followIfFollowing } = useFollowBottom(log)
  const projectId = project?.id ?? null
  /** The action this browser asked for on its own tile, with the live mark its rebuild keeps (R-WP-63, UC-WP-100) */
  const actionWatch = useRef<ActionWatch | null>(null)

  useEffect(() => {
    followIfFollowing()
  }, [messages, live, outgoing, followIfFollowing])

  /** One write place per list: the ref and the state of a fact change together and nowhere else — the screen
   *  reads the state, the handler the ref, so the two never disagree about a count (D-B). */
  const writeHistory = useCallback((loaded: ChatMessage[]) => {
    historyCount.current = { lines: loaded.length, voices: loaded.filter(line => !isTraceLine(line)).length }
    setMessages(loaded)
  }, [])

  const writePending = useCallback((lines: PendingLine[]) => {
    pendingLines.current = lines
    setOutgoing(lines)
  }, [])

  /**
   * The turn's own controls, before the board: the board reports its failures into the same one panel. Read by
   * name on purpose — the callbacks below list these in their dependencies, and the hook keeps each of them
   * identity-stable, so the stream is not re-opened by a render.
   */
  const {
    ownTurn,
    error,
    setError,
    send,
    // the page's own `stop` stays the composer's handler: it adds the live row the hook must not know (R-WP-63)
    stop: stopTheTurn,
    endTurn,
    forgetStoppedFrom,
    stoppedFrom,
    takeStoppedTurn,
  } = useTurnControls({ projectId, user, historyCount, pendingLines, writePending, followNow })
  const { tiles, refreshTiles, applySnapshot } = useTileBoard(projectId, setError)

  const writeLive = useCallback((next: LiveItem[]) => {
    liveItems.current = next
    setLive(next)
  }, [])

  const writeTurn = useCallback((next: { anchor: number; id: number; from: number } | null) => {
    turn.current = next
    setOpenTurn(next?.id ?? null)
  }, [])

  /**
   * The open turn, opened by the first live line that needs it and frozen from then on. Its anchor counts
   * <em>voices</em> — the history lines a read cannot move, plus the own pending lines, which that read
   * turns into exactly those voices at the same indices — so the same number names the same place before
   * and after the read, which is what keeps one answer one block (D-A, UC-WP-54, R-WP-47). Its identity is
   * what a working step belongs to: a step of this turn can work, one of a finished turn cannot.
   */
  const beginTurn = useCallback(() => {
    if (turn.current !== null) {
      return turn.current
    }
    const voices = historyCount.current.voices + pendingLines.current.length
    const started = { anchor: voices, id: ++turnSeq.current, from: liveItems.current.length }
    writeTurn(started)
    return started
  }, [writeTurn])

  /**
   * One read of the conversation — its number and its live cut live in {@link useHistoryReads}, what it writes
   * stays here. The controls' forget of the stopped turn's row goes in as `afterRead`, not beside each call:
   * it is identity-stable, and only a read that really landed may clear that mark (R-WP-63).
   */
  const { refresh } = useHistoryReads({
    projectId,
    pendingLines,
    liveItems,
    writeHistory,
    writePending,
    writeLive,
    onFailure: setError,
    afterRead: forgetStoppedFrom,
  })

  const applyEvent = useCallback(
    (event: ChatStreamEvent) => {
      switch (event.kind) {
        case 'partial':
          if (event.thinking) {
            // a Think delta is no answer and no speech of its own: it is an entry of the trace, opened and
            // anchored like a step of this turn, and it carries the moment this browser received it — the
            // clock of a step and never a duration (R-WP-40, UC-WP-72 GIVEN 1)
            const step = beginTurn()
            writeLive(
              appendLiveThink(liveItems.current, event.text, step.anchor, Date.now(), step.id),
            )
            break
          }
          // the turn is opened outside the writer: its anchor is frozen on first use, and the list
          // to continue is the one that stands, not the one a past render showed (D-A, D-B)
          writeLive(appendLiveText(liveItems.current, event.text, beginTurn().anchor))
          break
        case 'tool':
        case 'problem': {
          // a step and a refusal of a tool are anchored, stamped and clocked by ONE rule: the wire's stamp,
          // the moment this browser received it, the turn that reported it — each its own, so the cap of a
          // long run cannot move a time and an entry of a finished turn cannot work for the next one
          // (R-WP-40, UC-WP-72 GIVEN 2 and 5). Only what they become differs: a refusal is no step, so the
          // screen paints it as the refusal it is (R-WP-61, UC-WP-98)
          const step = beginTurn()
          const stamp = event.origin ?? null
          const at = Date.now()
          writeLive(
            event.kind === 'problem'
              ? appendLiveProblem(liveItems.current, event.text, step.anchor, stamp, at, step.id)
              : appendLiveTool(liveItems.current, event.text, step.anchor, stamp, at, step.id),
          )
          break
        }
        case 'diff': {
          // the change itself, live, for every browser watching this project — the block names its own file,
          // so no author is needed and none is shown (R-WP-36, UC-WP-68)
          const at = beginTurn().anchor
          writeLive(appendLiveDiff(liveItems.current, event.file, event.diff, at))
          break
        }
        case 'delegation': {
          if (event.working) {
            // the entry is opened by its own event and anchored like a step of this turn — its identity is
            // what will decide whether the entry works, and the order stays in the tool line above
            // (R-WP-40, UC-WP-72 GIVEN 3)
            const step = beginTurn()
            writeLive(
              appendLiveDelegation(liveItems.current, event.member, step.anchor, Date.now(), step.id),
            )
            break
          }
          // an end that finds no open entry of this member leaves the list as it stands — the same reference.
          // It never opens a turn either: an end cannot give birth to the entry it was meant to close
          writeLive(endLiveDelegation(liveItems.current, event.member, turn.current?.id ?? null))
          break
        }
        case 'done':
          // the turn is over: the next one anchors at the rows that stand when it reports, not at these, and
          // nothing of this turn works any more — its steps stay on the screen without their indicator
          writeTurn(null)
          // no watch of a click survives a turn: an action never meets one here, and a stale watch would
          // read a later core Compact as ours — Grenze 1 of R-WP-63 (a running turn is refused anyway)
          actionWatch.current = null
          // one reload per turn — the turn that drained a queued input closes that input too, and the
          // history that lands carries every finished line, the team's own answer among them
          if (!takeStoppedTurn()) {
            void refresh()
          }
          // the end snapshot usually arrives on its own; reading again covers the reader who lost it
          void refreshTiles()
          endTurn()
          break
        case 'error': {
          // shown where it happened — a line of the log, not a notice beside it — and the composer stays
          // usable (Q1). The read that follows brings the same line as a line of the conversation, so it
          // survives a reload and every other browser (R-WP-34, UC-WP-64)
          const at = beginTurn().anchor
          // the failure is an end of the answer, so no step of this turn works behind it
          writeTurn(null)
          writeLive(appendLiveError(liveItems.current, event.message, at))
          endTurn()
          // the panel is left alone: a turn ending on its own is no answer to whatever the user asked a
          // moment before, and its word stays until he asks again or dismisses it (R-WP-66, UC-WP-103)
          void refresh()
          break
        }
        case 'agents':
          // replace, never merge — a snapshot that accumulated would become a second history (R-WP-7); as the
          // freshest stand it retires every roster read that began before it (UC-WP-84 GIVEN 3)
          applySnapshot(event.tiles)
          // the falling edge of the action this browser asked for rebuilds its conversation (R-WP-63)
          actionWatch.current = afterSnapshot(actionWatch.current, event.tiles, mark => {
            writeTurn(null)
            void refresh(mark)
            void refreshTiles()
          })
          break
      }
    },
    [applySnapshot, beginTurn, endTurn, refresh, refreshTiles, takeStoppedTurn, writeLive, writeTurn],
  )

  useEffect(() => {
    if (projectId === null) {
      return
    }
    const controller = new AbortController()
    const backoff = sseBackoff()
    let reconnect: ReturnType<typeof setTimeout> | undefined
    let mounted = true

    const connect = () => {
      void openChatStream(
        projectId,
        {
          onEvent: event => {
            backoff.reset()
            applyEvent(event)
          },
          // the fresh read once the subscription stands is what puts a finished turn above the view's own
          // first reading — that one opens the row, it is never the verdict of a turn (R-WP-48, UC-WP-84)
          onOpen: () => void refreshTiles(),
          onEnd: retry => {
            if (!mounted || !retry) {
              return
            }
            // a dropped stream loses the live lines and nothing else (R-WP-11): the history that follows
            // is read again, and what the wire will not send back is dropped from the screen
            writeLive([])
            writeTurn(null)
            actionWatch.current = null
            void refresh()
            reconnect = setTimeout(connect, backoff.next())
          },
        },
        controller.signal,
      )
    }

    // the stream opens only once the history has been read: an event that anchored before it would be drawn
    // above the whole history (D-E)
    // the roster is read as the view stands: a stream that never opens may not empty the tile row (R-WP-57, UC-WP-94)
    void refreshTiles()
    void refresh().then(() => {
      if (mounted) {
        connect()
      }
    })
    return () => {
      mounted = false
      controller.abort()
      actionWatch.current = null
      if (reconnect !== undefined) {
        clearTimeout(reconnect)
      }
    }
  }, [applyEvent, projectId, refresh, refreshTiles, writeLive, writeTurn])

  /**
   * A turn is running when we started one that has not ended, or when the roster shows an agent working —
   * another user's turn on the same project is a turn the user here may stop (UC-WP-22). COMPACTING is
   * deliberately not one of them: a half Compact is not something a turn's stop may cut (D-17). Read above
   * `submit` as well: the empty send way asks it before anything goes out (UC-WP-57 GIVEN 2).
   */
  const busy = ownTurn || tiles.some(tile => tile.state === 'WORKING')

  /**
   * Whether a Stop has something to stop (R-WP-65, UC-WP-102): the stand's own statement on any tile, plus this
   * browser's own send — between the click and the first WORKING snapshot the turn is already there and no
   * snapshot says it yet (D-13). Read as a statement and never as a state: `busy` above stays the wait's and the
   * empty send's question, and a name is never asked (R-WP-7, D-14).
   */
  const stoppable = ownTurn || tiles.some(tile => tile.stoppable)

  /**
   * The one decider of the send way (UC-WP-57), because the composer answers nothing on its own (UC-WP-74):
   * a text goes out as it stands; an empty field repeats the last user line the screen shows — the history's
   * or this browser's pending one, which is what makes "send it again after a stop" work — and does nothing
   * while a turn works or when no line has been shown at all. A repeat is not a second way on the wire — it
   * is `send` with the text the screen kept. The field keeps what he typed in the meantime: an empty send had
   * nothing to clear.
   */
  function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (text !== '') {
      setDraft('')
      void send(text)
      return
    }
    if (busy) {
      return // no empty turn and no queue line behind a running one (UC-WP-57 GIVEN 2)
    }
    const repeated = lastVisibleUserText(messages, pendingLines.current)
    if (repeated !== null) {
      void send(repeated)
    }
  }

  /** The composer's stop: the live row the stopped turn's lines began at is the anchor block's fact (R-WP-63). */
  function stop() {
    void stopTheTurn(turn.current?.from ?? null, refreshTiles)
  }

  /**
   * Clear or Compact one tile — the accepted action reads nothing here, its end is the falling edge of its
   * state area, watched with {@link afterSnapshot} on this conversation's tile only, never a member's
   * (R-WP-63, whose moment the module {@link acceptedAction} argues). Its mark is the row a stopped turn
   * began at, so what only ever lived on the screen stays (R-WP-63). A refusal resets the watch (UC-WP-12, UC-WP-13).
   */
  async function act(name: string, action: AgentAction) {
    if (projectId === null) {
      return
    }
    setError(null)
    const mark = stoppedFrom() ?? liveItems.current.length
    actionWatch.current = acceptedAction(tiles.find(tile => tile.name === name), action, mark)
    try {
      await agentAction(projectId, name, action)
    } catch (reason) {
      actionWatch.current = null
      setError(errorMessage(reason))
    }
  }

  if (project === null) {
    return (
      <section className="view" aria-label={content.chat.title}>
        <p className="empty-state">{content.chat.noProject}</p>
      </section>
    )
  }

  return (
    <>
      <section className="view" aria-label={content.chat.title}>
        <header className="chat-head">
          <h1 className="chat-title">{project.name}</h1>
        </header>
        {/* the page's own trouble, above the team and outside the one area that scrolls here (R-WP-62): a
            send refused, a refused stop, a tile action that did not run, a roster the read lost. A failed
            turn is not announced here: it is a line of the log, read from the conversation like every other
            line (R-WP-34, UC-WP-64), and it never closes this panel either (R-WP-66) */}
        <ApiErrorPanel error={error} onClose={() => setError(null)} />
        <ChatTiles tiles={tiles} onAction={act} onInspect={openInsight} />
        <ChatLog
          messages={messages}
          outgoing={outgoing}
          live={live}
          // the turn this view holds open — another browser's included, because its steps arrive on this
          // stream too: the indicator belongs to a step of THIS turn and to none that has ended (R-WP-40)
          openTurn={openTurn}
          logRef={log}
          onScroll={onLogScroll}
        />
        {/* the way back to the end, on the screen exactly while he reads away from it (UC-WP-80). The box
            carries no height of the column: the button hangs at its bottom edge, which is the top edge of
            whatever stands below — the wait and the input — and never on them. */}
        <div className="chat-scroll-bottom">
          {!following && <ScrollBottomButton onFollow={followNow} />}
        </div>
        {/* one rule with the stop button: while a turn works on this project, the wait is on the screen
            (R-WP-31) — another browser's turn included, because this stream carries its lines anyway */}
        {busy && <ChatWaiting preview={livePreview(live)} />}
        <ChatComposer
          draft={draft}
          busy={busy}
          stoppable={stoppable}
          onDraftChange={setDraft}
          onSubmit={submit}
          onStop={stop}
        />
      </section>
      {files}
      {/* the look stands on the right edge only while the address leaves it free (R-WP-33, UC-WP-60 G3);
          the member is its identity: another tile is another look and carries no line of the first one */}
      {insight !== null && <ChatInsight key={insight} projectId={project.id} name={insight} onClose={closeInsight} />}
    </>
  )
}

