import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { listMessages, sendMessage, type ChatMessage, type ChatStreamEvent } from './chat.client'
import { ChatComposer } from './chat.composer'
import { ChatLog } from './chat.log'
import { openChatStream } from './chat.stream'
import { errorMessage } from '../shared/api.client'
import type { Project } from '../shared/dto'

interface Props {
  project: Project | null
  /**
   * Anything the shell wants beside the chat — today the file area of `project`. It is drawn outside
   * the chat region and this component knows nothing about it, so `chat` keeps not importing
   * `project` (§4.3.4 of the increment).
   */
  files?: ReactNode
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
 * The page holds the state; {@link ChatLog} and {@link ChatComposer} only draw it.
 */
export function ChatPage({ project, files }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [outgoing, setOutgoing] = useState<string[]>([])
  const [live, setLive] = useState('')
  const [toolLines, setToolLines] = useState<string[]>([])
  const [draft, setDraft] = useState('')
  const [error, setError] = useState<string | null>(null)
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
          void refresh()
          break
        case 'error':
          // the reason is a line in the chat, the composer stays usable (Q1)
          setError(event.message)
          break
      }
    },
    [refresh],
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
    connect()
    input.current?.focus()
    return () => {
      mounted = false
      controller.abort()
      if (reconnect !== undefined) {
        clearTimeout(reconnect)
      }
    }
  }, [applyEvent, projectId, refresh])

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
    try {
      await sendMessage(projectId, text)
    } catch (reason) {
      setOutgoing(previous => previous.slice(0, -1))
      setError(errorMessage(reason))
    }
  }

  if (project === null) {
    return (
      <section className="view" aria-label="Chat">
        <h1>Chat</h1>
        <p className="empty-state">Kein Projekt geöffnet</p>
      </section>
    )
  }

  return (
    <>
      <section className="view" aria-label="Chat">
        <h1>Chat</h1>
        <p className="hint">Projekt: {project.name}</p>
        <ChatLog messages={messages} outgoing={outgoing} live={live} toolLines={toolLines} />
        {error !== null && (
          <p className="chat-error" data-testid="chat-error" role="alert">
            {error}
          </p>
        )}
        <ChatComposer draft={draft} inputRef={input} onDraftChange={setDraft} onSubmit={submit} />
      </section>
      {files}
    </>
  )
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
