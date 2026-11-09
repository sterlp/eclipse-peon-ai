import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import {
  isTraceLine,
  listMessages,
  sendMessage,
  senderName,
  stopTurn,
  type ChatMessage,
  type ChatStreamEvent,
} from './chat.client'
import { agentAction, type AgentAction } from './chat.agents.client'
import { useTileBoard } from './chat.board'
import { ChatComposer } from './chat.composer'
import { useEdgeInsight } from './chat.edge'
import { ChatInsight } from './chat.insight'
import { ChatLog } from './chat.log'
import {
  appendLiveDelegation,
  appendLiveDiff,
  appendLiveError,
  appendLiveText,
  appendLiveThink,
  appendLiveTool,
  endLiveDelegation,
  liveLinesAfterHistory,
  livePreview,
  type LiveItem,
} from './chat.live'
import { lastVisibleUserText, withoutConfirmed, type PendingLine } from './chat.pending'
import { useFollowBottom } from './chat.scroll'
import { ScrollBottomButton } from './chat.scroll.bottom'
import { ChatTiles } from './chat.tiles'
import { ChatWaiting } from './chat.waiting'
import { openChatStream, RECONNECT_MS } from './chat.stream'
import { ApiRequestError, errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import type { Project } from '../shared/dto'

interface Props {
  project: Project | null
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
 * The team's tiles ({@link ChatTiles}) follow the same rule in their own family, kept by {@link useTileBoard}:
 * read once the stream stands, replaced whole by every `agents` snapshot — which stand of the roster is the
 * fresher one is the board's question, not this view's — and re-read after an action, because 202 only said
 * <em>taken</em>. This view knows no agent name: a tile says what it offers (D-14).
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
  const [error, setError] = useState<string | null>(null)
  // the one overlay this view may put on the right edge, held to one with the file area (R-WP-33,
  // UC-WP-60 G3); the rule and its measured reason live beside the state they write
  const { shown: insight, openInsight, closeInsight } = useEdgeInsight(filesOpen, onCloseFiles)
  /**
   * The project whose turn we started and have not seen end — held by id, so switching the project ends it
   * here without an effect writing state (UC-WP-50).
   */
  const [runningTurn, setRunningTurn] = useState<string | null>(null)
  /** The project whose turn the user asked to end, so its `done` must not read over his lines (D-16). */
  const stoppedTurn = useRef<string | null>(null)
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
  const turn = useRef<{ anchor: number; id: number } | null>(null)
  /** Hands out the ids of the turns, ever growing: a step of a past turn can never read as a step of the open one. */
  const turnSeq = useRef(0)
  /** The open turn as the screen sees it — no turn open, no step working (R-WP-40, UC-WP-72 GIVEN 2). */
  const [openTurn, setOpenTurn] = useState<number | null>(null)
  /**
   * How many history reads this view has asked for. A read is a network round trip over the whole memory, so
   * it is slowest exactly when the conversation is long, and a later read can answer first — the number is
   * what tells the two answers apart (D-C, R-WP-11).
   */
  const readSeq = useRef(0)
  /** The number of the read whose content is on the screen. An older answer never overwrites a newer one. */
  const appliedRead = useRef(0)
  /**
   * The scroll area of the chat. Where the user reads and what a new line owes him — follow him to its end,
   * or leave him reading (R-WP-35) — is `useFollowBottom`'s question; this view only says when the log has
   * new content, because that is the only thing here that knows it.
   */
  const log = useRef<HTMLUListElement>(null)
  const { following, onLogScroll, followNow, followIfFollowing } = useFollowBottom(log)
  const projectId = project?.id ?? null
  const showBoardFailure = useCallback((message: string) => setError(message), [])
  const { tiles, refreshTiles, applySnapshot } = useTileBoard(projectId, showBoardFailure)

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

  const writeLive = useCallback((next: LiveItem[]) => {
    liveItems.current = next
    setLive(next)
  }, [])

  const writeTurn = useCallback((next: { anchor: number; id: number } | null) => {
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
    const started = { anchor: historyCount.current.voices + pendingLines.current.length, id: ++turnSeq.current }
    writeTurn(started)
    return started
  }, [writeTurn])

  /**
   * The finished text arrives here, never as an event — so the live answer gives way to it (§3.3.3). What
   * gives way is nothing else: the tool lines of the turn exist on the wire only and stay where they were
   * placed, because a finished turn without its trace is not what UC-WP-54 asks for.
   *
   * Two things make a read a statement about one moment instead of about the conversation: `read` is the
   * number this view gave the request, and a read that answers after a newer one has been applied is
   * dropped — reads race, and the slower one would move the log back (R-WP-11). `mark` is how many live
   * lines stood when the request went out; text and error behind it belong to a turn the read never saw and
   * stay on the screen (UC-WP-7).
   */
  const applyHistory = useCallback(
    (loaded: ChatMessage[], mark: number, read: number) => {
      if (read < appliedRead.current) {
        return
      }
      appliedRead.current = read
      writeHistory(loaded)
      // his own lines are confirmed by their position in what the read brings, not by their text: a queued
      // input stands in the memory with the core's marker and would never be recognised again (R-WP-37)
      writePending(withoutConfirmed(pendingLines.current, loaded))
      writeLive(liveLinesAfterHistory(liveItems.current, mark))
    },
    [writeHistory, writePending, writeLive],
  )

  /**
   * One read of the conversation, numbered and stamped with the live list it has to meet. The stamp is taken
   * <em>before</em> the request: whatever the stream adds while the server is answering is not in the answer
   * and must not be given up for it (D-C, UC-WP-7).
   */
  const refresh = useCallback((): Promise<void> => {
    if (projectId === null) {
      return Promise.resolve()
    }
    const read = ++readSeq.current
    const mark = liveItems.current.length
    return listMessages(projectId)
      .then(loaded => applyHistory(loaded, mark, read))
      .catch(reason => setError(errorMessage(reason)))
  }, [applyHistory, projectId])

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
        case 'tool': {
          const step = beginTurn()
          // the step keeps the stamp the wire carried, the moment this browser received it and the turn that
          // reported it — each of them its own, so the cap of a long run cannot move a time and a step of a
          // finished turn cannot work for the next one (R-WP-40, UC-WP-72 GIVEN 2 and GIVEN 5)
          writeLive(
            appendLiveTool(
              liveItems.current,
              event.text,
              step.anchor,
              event.origin ?? null,
              Date.now(),
              step.id,
            ),
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
          // one reload per turn — the turn that drained a queued input closes that input too, and the
          // history that lands carries every finished line, the team's own answer among them
          if (stoppedTurn.current === projectId) {
            // UC-WP-50: after an accepted stop the lines he has seen stay on the screen — there is no
            // second store to read them back from, and a history read would throw them away (D-16, R-WP-11)
            stoppedTurn.current = null
          } else {
            void refresh()
          }
          // the end snapshot usually arrives on its own; reading again covers the reader who lost it
          void refreshTiles()
          setRunningTurn(null)
          break
        case 'error': {
          // shown where it happened — a line of the log, not a notice beside it — and the composer stays
          // usable (Q1). The read that follows brings the same line as a line of the conversation, so it
          // survives a reload and every other browser (R-WP-34, UC-WP-64)
          const at = beginTurn().anchor
          // the failure is an end of the answer, so no step of this turn works behind it
          writeTurn(null)
          writeLive(appendLiveError(liveItems.current, event.message, at))
          setRunningTurn(null)
          setError(null)
          void refresh()
          break
        }
        case 'agents':
          // replace, never merge — a snapshot that accumulated would become a second history (R-WP-7); as the
          // freshest stand it retires every roster read that began before it (UC-WP-84 GIVEN 3)
          applySnapshot(event.tiles)
          break
      }
    },
    [applySnapshot, beginTurn, projectId, refresh, refreshTiles, writeLive, writeTurn],
  )

  useEffect(() => {
    if (projectId === null) {
      return
    }
    const controller = new AbortController()
    let reconnect: ReturnType<typeof setTimeout> | undefined
    let mounted = true

    const connect = () => {
      void openChatStream(
        projectId,
        {
          onEvent: applyEvent,
          // the board is read on the answer, never before the subscription stands (R-WP-48)
          onOpen: () => void refreshTiles(),
          onEnd: retry => {
            if (!mounted || !retry) {
              return
            }
            // a dropped stream loses the live lines and nothing else (R-WP-11): the history that follows
            // is read again, and what the wire will not send back is dropped from the screen
            writeLive([])
            writeTurn(null)
            void refresh()
            reconnect = setTimeout(connect, RECONNECT_MS)
          },
        },
        controller.signal,
      )
    }

    // the stream opens only once the history has been read: an event that anchored before it would be drawn
    // above the whole history (D-E); the board is read on the stream's answer, not here (R-WP-48)
    void refresh().then(() => {
      if (mounted) {
        connect()
      }
    })
    return () => {
      mounted = false
      controller.abort()
      if (reconnect !== undefined) {
        clearTimeout(reconnect)
      }
    }
  }, [applyEvent, projectId, refresh, refreshTiles, writeLive, writeTurn])

  /**
   * A turn is running when we started one that has not ended, or when the roster shows an agent working —
   * another user's turn on the same project is a turn the user here may stop (UC-WP-22). COMPACTING is
   * deliberately not one of them: a half Compact is not something a turn's stop may cut (D-17). Read above
   * `send` as well: the empty send way asks it before anything goes out (UC-WP-57 GIVEN 2).
   */
  const busy =
    (runningTurn !== null && runningTurn === projectId) || tiles.some(tile => tile.state === 'WORKING')

  /**
   * One text into this project's conversation — whether the field wrote it or an empty send repeated the last
   * line the screen shows makes no difference on the wire (UC-WP-57): the same edge, the same queue while a
   * turn runs (R-WP-10). Never reached with a blank text: the backend answers 400, so the empty way ends in
   * `submit` before any request.
   */
  async function send(text: string) {
    if (projectId === null) {
      return
    }
    // his own input is always shown, wherever he had read up to (UC-WP-80) — before the line is written, so
    // the follow is re-armed when the effect that carries the new line runs
    followNow()
    // shown before the server has it: a turn writes the user line into the memory only when it starts,
    // so a queued input would vanish from the screen until the next history read. The number of history
    // lines standing now and this browser's sender name are what confirm the line later — the name is
    // normalised exactly as the wire carries it, or the history would never confirm it (R-WP-37, D-H)
    writePending([
      ...pendingLines.current,
      { text, afterHistory: historyCount.current.lines, origin: senderName(user) },
    ])
    setError(null)
    // the send is what opens the turn, so the flag follows the intent and not the answer (D-13): 202 only
    // says "taken", and a stop that comes one moment later must already address this turn
    setRunningTurn(projectId)
    stoppedTurn.current = null
    try {
      // the name of this browser goes with it, and only when there is one (R-WP-25, UC-WP-48)
      await sendMessage(projectId, text, user)
    } catch (reason) {
      writePending(pendingLines.current.slice(0, -1))
      setRunningTurn(null)
      setError(errorMessage(reason))
    }
  }

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

  /**
   * Stop the running turn (UC-WP-50). A 409 is not shown: it says the turn was already over, which is
   * what the click wanted, and the roster is read again so the tiles say so too (D-17). Any other
   * refusal is a line in the chat like any other error.
   */
  async function stop() {
    if (projectId === null) {
      return
    }
    setError(null)
    try {
      await stopTurn(projectId)
      // remembered for the `done` that follows: his lines stay, the history is not re-read (D-16)
      stoppedTurn.current = projectId
    } catch (reason) {
      if (!isAlreadyOver(reason)) {
        setError(errorMessage(reason))
        return
      }
    }
    // the end he asked for has been taken: the wait ends now, without waiting for a `done` that will not
    // read anything over it — the lines on the screen stay (R-WP-31, UC-WP-50, D-16)
    setRunningTurn(null)
    void refreshTiles()
  }

  /**
   * Clear or Compact one tile. 202 says <em>taken</em>, so both reads follow; a refusal (409 while a turn
   * runs, 400 for a tile that offers nothing) is a line in the chat like any other error and changes
   * nothing (UC-WP-12, UC-WP-13).
   */
  async function act(name: string, action: AgentAction) {
    if (projectId === null) {
      return
    }
    setError(null)
    try {
      await agentAction(projectId, name, action)
    } catch (reason) {
      setError(errorMessage(reason))
      return
    }
    // the history is re-read for every action, not only for the conversation's own agent: this component
    // knows no agent names, and a read of the single truth is cheaper than a rule about who owns it
    void refreshTiles()
    void refresh()
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
            whatever stands below — the wait, the notice, the input — and never on them. */}
        <div className="chat-scroll-bottom">
          {!following && <ScrollBottomButton onFollow={followNow} />}
        </div>
        {/* this notice is the page's own trouble only — a request that failed here: a send refused, a
            refused stop, a tile action that did not run. A failed turn is not announced here: it is a
            line of the log, read from the conversation like every other line (R-WP-34, UC-WP-64) */}
        {error !== null && (
          <p className="chat-error" data-testid="chat-error" role="alert">
            {error}
          </p>
        )}
        {/* one rule with the stop button: while a turn works on this project, the wait is on the screen
            (R-WP-31) — another browser's turn included, because this stream carries its lines anyway */}
        {busy && <ChatWaiting preview={livePreview(live)} />}
        <ChatComposer draft={draft} busy={busy} onDraftChange={setDraft} onSubmit={submit} onStop={stop} />
      </section>
      {files}
      {/* the look stands on the right edge only while the address leaves it free (R-WP-33, UC-WP-60 G3) */}
      {insight !== null && <ChatInsight projectId={project.id} name={insight} onClose={closeInsight} />}
    </>
  )
}

/**
 * The server's "no turn is running" (409) to a stop. The user got what he asked for either way, so this
 * one refusal is absorbed instead of printed (UC-WP-50, D-17).
 */
function isAlreadyOver(error: unknown): boolean {
  return error instanceof ApiRequestError && (error.apiError?.status ?? 0) === 409
}

