import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import {
  listMessages,
  sendMessage,
  stopTurn,
  type ChatMessage,
  type ChatStreamEvent,
} from './chat.client'
import { agentAction, listAgents, type AgentAction, type AgentTile } from './chat.agents.client'
import { ChatComposer } from './chat.composer'
import { ChatInsight } from './chat.insight'
import { ChatLog } from './chat.log'
import {
  appendLiveError,
  appendLiveText,
  appendLiveTool,
  liveLinesAfterHistory,
  livePreview,
  type LiveItem,
} from './chat.live'
import { ChatTiles } from './chat.tiles'
import { ChatWaiting } from './chat.waiting'
import { openChatStream } from './chat.stream'
import { ApiRequestError, errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import type { Project } from '../shared/dto'

interface Props {
  project: Project | null
  /**
   * Anything the shell wants beside the chat — today the file area of `project`. It is drawn outside
   * the chat region and this component knows nothing about it, so `chat` keeps not importing
   * `project` (§4.3.4 of the increment).
   */
  files?: ReactNode
  /**
   * The name of this browser (R-WP-25), given by the shell that holds it — identity is not per project,
   * so this component neither stores nor interprets it: it labels the sends and the own pending lines.
   * Absent means no name: no field on the wire, no prefix, the role label on the screen (UC-WP-48).
   */
  user?: string | null
}

/** How long the view waits before it asks again — long enough not to hammer a restarting server. */
const RECONNECT_MS = 500

/**
 * The chat of one project (R-WP-4, R-WP-5): the history is the only truth for finished text, the
 * stream only adds live lines, and there is nothing here to switch — no agent picker, no second
 * conversation, no time on a restored line. A line is named by its author when the server names one.
 *
 * One rule covers connection and reload: <em>(re-)connect ⇒ read the history, live events only
 * append</em>. So a dropped stream and a browser reload lose exactly the live lines and nothing else
 * (R-WP-11), and the reconnect path needs no second buffer and no event memory.
 *
 * What the turn reports while it runs lives in one list — the answer's text and the tool lines in the
 * order they arrived, each anchored at the rows already shown (D-7). The anchor is what lets the tool
 * lines of a finished turn survive the history read of {@code done}: the answer gives way to the history,
 * the trace has no place there and keeps its place on the screen (R-WP-28, UC-WP-54).
 *
 * The team's tiles ({@link ChatTiles}) follow the same rule in their own family: read on mount, replaced
 * by every `agents` snapshot, never merged — and re-read after an action, because 202 only said
 * <em>taken</em>. This component holds the state of both, and it knows no agent name: a tile says what it
 * offers (D-14).
 *
 * A tile offering TRANSCRIPT opens {@link ChatInsight}, the read-only look into that agent's own
 * conversation (UC-WP-49). It is drawn after the {@link files} slot and so lies above the file area, and
 * it stays a region of this view — no fourth tab and no route of its own, whatever the address shows (R-WP-1).
 *
 * The composer's second control ends the running turn (UC-WP-50). After an accepted stop this view does
 * <em>not</em> re-read the history: the live lines the user was already shown are all that exists of the
 * stopped turn, and there is no second store to read them back from (R-WP-11).
 *
 * Two things follow the turn itself: the text of an <em>accepted</em> send is kept for "Resend" (UC-WP-57),
 * and {@link ChatWaiting} sits above the composer while a turn works and shows what the stream last
 * reported (R-WP-31). Neither is a second memory: the first is one string of this browser's session, the
 * second reads the live list the log already holds.
 *
 * The page holds the state; {@link ChatLog} and {@link ChatComposer} only draw it.
 *
 * The head of the view names the project and nothing else: a second "Chat" heading would repeat the tab
 * the chat already sits in (R-WP-1).
 */
export function ChatPage({ project, files, user }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [tiles, setTiles] = useState<AgentTile[]>([])
  const [outgoing, setOutgoing] = useState<string[]>([])
  const [live, setLive] = useState<LiveItem[]>([])
  const [draft, setDraft] = useState('')
  /**
   * The last text the server took (202) — the one "Resend" sends again (UC-WP-57). Held only after an
   * accepted send: a text that never got through is not something the user should be offering twice.
   */
  const [lastSent, setLastSent] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** The tile whose insight is open — a name, not a conversation: there is still exactly one of those. */
  const [insight, setInsight] = useState<string | null>(null)
  /**
   * The project whose turn we started and have not seen end — held by id, so switching the project ends it
   * here without an effect writing state (UC-WP-50).
   */
  const [runningTurn, setRunningTurn] = useState<string | null>(null)
  /** The project whose turn the user asked to end, so its `done` must not read over his lines (D-16). */
  const stoppedTurn = useRef<string | null>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  /**
   * The rows the log shows — a live line is anchored at this count, so it keeps the place it happened at
   * even when the history read of `done` replaces the lines around it (D-7, UC-WP-54).
   */
  const shownRows = useRef(0)
  const projectId = project?.id ?? null

  useEffect(() => {
    shownRows.current = messages.length + outgoing.length
  }, [messages, outgoing])

  /**
   * The finished text arrives here, never as an event — so the live answer gives way to it (§3.3.3). What
   * gives way is nothing else: the tool lines of the turn exist on the wire only and stay where they were
   * placed, because a finished turn without its trace is not what UC-WP-54 asks for.
   */
  const applyHistory = useCallback((loaded: ChatMessage[]) => {
    setMessages(loaded)
    setLive(liveLinesAfterHistory)
    setOutgoing(previous => withoutSent(previous, loaded))
  }, [])

  const refresh = useCallback((): Promise<void> => {
    if (projectId === null) {
      return Promise.resolve()
    }
    return listMessages(projectId).then(applyHistory).catch(reason => setError(errorMessage(reason)))
  }, [applyHistory, projectId])

  /** The roster of tiles is read, not kept: an `agents` snapshot may have been dropped by a slow reader. */
  const refreshTiles = useCallback((): Promise<void> => {
    if (projectId === null) {
      return Promise.resolve()
    }
    return listAgents(projectId)
      .then(setTiles)
      .catch(reason => setError(errorMessage(reason)))
  }, [projectId])

  const applyEvent = useCallback(
    (event: ChatStreamEvent) => {
      switch (event.kind) {
        case 'partial':
          // thinking deltas stay invisible: the chat shows the answer, and R-WP-5 promises that text
          if (!event.thinking) {
            setLive(previous => appendLiveText(previous, event.text, shownRows.current))
          }
          break
        case 'tool':
          setLive(previous => appendLiveTool(previous, event.text, shownRows.current))
          break
        case 'done':
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
        case 'error':
          // shown where it happened — a line of the log, not a notice beside it — and the composer stays
          // usable (Q1). The read that follows brings the same line as a line of the conversation, so it
          // survives a reload and every other browser (R-WP-34, UC-WP-64)
          setLive(previous => appendLiveError(previous, event.message, shownRows.current))
          setRunningTurn(null)
          setError(null)
          void refresh()
          break
        case 'agents':
          // replace, never merge — a snapshot that accumulated would become a second history (R-WP-7)
          setTiles(event.tiles)
          break
      }
    },
    [projectId, refresh, refreshTiles],
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
          onEnd: retry => {
            if (!mounted || !retry) {
              return
            }
            // a dropped stream loses the live lines and nothing else (R-WP-11): the history that follows
            // is read again, and what the wire will not send back is dropped from the screen
            setLive([])
            void refresh()
            reconnect = setTimeout(connect, RECONNECT_MS)
          },
        },
        controller.signal,
      )
    }

    void refresh()
    void refreshTiles()
    connect()
    input.current?.focus()
    return () => {
      mounted = false
      controller.abort()
      if (reconnect !== undefined) {
        clearTimeout(reconnect)
      }
    }
  }, [applyEvent, projectId, refresh, refreshTiles])

  /**
   * One text into this project's conversation — whether it came from the field or from "Resend" makes no
   * difference on the wire (UC-WP-57): the same edge, the same queue while a turn runs (R-WP-10).
   */
  async function send(text: string) {
    if (projectId === null) {
      return
    }
    // shown before the server has it: a turn writes the user line into the memory only when it starts,
    // so a queued input would vanish from the screen until the next history read
    setOutgoing(previous => [...previous, text])
    setError(null)
    // the send is what opens the turn, so the flag follows the intent and not the answer (D-13): 202 only
    // says "taken", and a stop that comes one moment later must already address this turn
    setRunningTurn(projectId)
    stoppedTurn.current = null
    try {
      // the name of this browser goes with it, and only when there is one (R-WP-25, UC-WP-48)
      await sendMessage(projectId, text, user)
      // 202 is the moment this text becomes "the last one": a refusal never earns the Resend button
      setLastSent(text)
    } catch (reason) {
      setOutgoing(previous => previous.slice(0, -1))
      setRunningTurn(null)
      setError(errorMessage(reason))
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (text === '') {
      return
    }
    setDraft('')
    void send(text)
  }

  /**
   * The last accepted text again, without the user typing it (UC-WP-57). What he has typed in the meantime
   * stays in the field — a resend answers the failed turn, it does not empty what he is writing now.
   */
  function resend() {
    if (lastSent !== null) {
      void send(lastSent)
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
    void refreshTiles()
  }

  /**
   * A turn is running when we started one that has not ended, or when the roster shows an agent working —
   * another user's turn on the same project is a turn the user here may stop (UC-WP-22). COMPACTING is
   * deliberately not one of them: a half Compact is not something a turn's stop may cut (D-17).
   */
  const busy =
    (runningTurn !== null && runningTurn === projectId) || tiles.some(tile => tile.state === 'WORKING')

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
        <ChatTiles tiles={tiles} onAction={act} onInspect={setInsight} />
        <ChatLog messages={messages} outgoing={outgoing} user={user} live={live} />
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
        <ChatComposer
          draft={draft}
          inputRef={input}
          busy={busy}
          lastSent={lastSent}
          onDraftChange={setDraft}
          onSubmit={submit}
          onResend={resend}
          onStop={stop}
        />
      </section>
      {files}
      {insight !== null && (
        <ChatInsight projectId={project.id} name={insight} onClose={() => setInsight(null)} />
      )}
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

/**
 * A sent text is dropped as soon as the history carries a user line with it — the screen never shows
 * the same input twice. Repeating an identical text twice in a row is the known limit: the second
 * one appears with the history that contains it, not before.
 */
function withoutSent(pending: string[], history: ChatMessage[]): string[] {
  if (pending.length === 0) {
    return pending
  }
  const rest = [...pending]
  for (const message of history) {
    if (message.role !== 'user') {
      continue
    }
    const at = rest.indexOf(message.text)
    if (at >= 0) {
      rest.splice(at, 1)
    }
  }
  return rest.length === pending.length ? pending : rest
}
