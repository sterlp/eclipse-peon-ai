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
import { ChatTiles } from './chat.tiles'
import { openChatStream } from './chat.stream'
import { ApiRequestError, errorMessage } from '../shared/api.client'
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
 * The team's tiles ({@link ChatTiles}) follow the same rule in their own family: read on mount, replaced
 * by every `agents` snapshot, never merged — and re-read after an action, because 202 only said
 * <em>taken</em>. This component holds the state of both, and it knows no agent name: a tile says what it
 * offers (D-14).
 *
 * A tile offering TRANSCRIPT opens {@link ChatInsight}, the read-only look into that agent's own
 * conversation (UC-WP-49). It is drawn after the {@link files} slot and so lies above the file area, and
 * it stays a region of this view — no fourth tab, no router (R-WP-1).
 *
 * The composer's second control ends the running turn (UC-WP-50). After an accepted stop this view does
 * <em>not</em> re-read the history: the live lines the user was already shown are all that exists of the
 * stopped turn, and there is no second store to read them back from (R-WP-11).
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
  const [live, setLive] = useState('')
  const [toolLines, setToolLines] = useState<string[]>([])
  const [draft, setDraft] = useState('')
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
  const input = useRef<HTMLInputElement>(null)
  const projectId = project?.id ?? null

  /** The finished text arrives here, never as an event — so live lines give way to it (§3.3.3). */
  const applyHistory = useCallback((loaded: ChatMessage[]) => {
    setMessages(loaded)
    setLive('')
    setToolLines([])
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
            setLive(previous => previous + event.text)
          }
          break
        case 'tool':
          setToolLines(previous => [...previous, event.text])
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
          // the reason is a line in the chat, the composer stays usable (Q1)
          setError(event.message)
          setRunningTurn(null)
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

  async function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (text === '' || projectId === null) {
      return
    }
    // shown before the server has it: a turn writes the user line into the memory only when it starts,
    // so a queued input would vanish from the screen until the next history read
    setOutgoing(previous => [...previous, text])
    setDraft('')
    setError(null)
    // the send is what opens the turn, so the flag follows the intent and not the answer (D-13): 202 only
    // says "taken", and a stop that comes one moment later must already address this turn
    setRunningTurn(projectId)
    stoppedTurn.current = null
    try {
      // the name of this browser goes with it, and only when there is one (R-WP-25, UC-WP-48)
      await sendMessage(projectId, text, user)
    } catch (reason) {
      setOutgoing(previous => previous.slice(0, -1))
      setRunningTurn(null)
      setError(errorMessage(reason))
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
      <section className="view" aria-label="Chat">
        <p className="empty-state">Kein Projekt geöffnet</p>
      </section>
    )
  }

  return (
    <>
      <section className="view" aria-label="Chat">
        <header className="chat-head">
          <h1 className="chat-title">{project.name}</h1>
        </header>
        <ChatTiles tiles={tiles} onAction={act} onInspect={setInsight} />
        <ChatLog messages={messages} outgoing={outgoing} user={user} live={live} toolLines={toolLines} />
        {error !== null && (
          <p className="chat-error" data-testid="chat-error" role="alert">
            {error}
          </p>
        )}
        <ChatComposer
          draft={draft}
          inputRef={input}
          busy={busy}
          onDraftChange={setDraft}
          onSubmit={submit}
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
