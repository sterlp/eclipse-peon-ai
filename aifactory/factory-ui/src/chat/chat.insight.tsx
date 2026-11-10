import { useCallback, useEffect, useRef, useState } from 'react'

import { readTranscript } from './chat.agents.client'
import { isTraceLine, type ChatMessage, type ChatStreamEvent } from './chat.client'
import { ChatLog } from './chat.log'
import {
  appendLiveText,
  appendLiveThink,
  appendLiveTool,
  appendLiveProblem,
  type LiveItem,
} from './chat.live'
import { useFollowBottom } from './chat.scroll'
import { openChatStream } from './chat.stream'
import { errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import { sseBackoff } from '../shared/sse.client'

interface Props {
  /** The project whose runtime holds this agent — the read is addressed by project and tile name. */
  projectId: string
  /** The tile's name as the roster sends it: the server resolves it, this component never maps a name. */
  name: string
  onClose: () => void
}

/**
 * The look into one member's own conversation (R-WP-26, R-WP-53, UC-WP-89): the slide-over beside the chat,
 * drawn by the chat's own {@link ChatLog} and growing while the member works. It is the same widget and not a
 * second markup world — Markdown answers, one timeline of his steps, the same follow-bottom rule — and it
 * stays read-only: no composer, no send, no stop, and no scroll-to-bottom button either, because the only
 * control of the look closes it (UC-WP-49).
 *
 * His wire carries his lines and nothing else — think, answer, tool (R-WP-53). It carries no turn end, so
 * `openTurn` stays null and no indicator claims work that the wire cannot end; it carries no diff, no roster,
 * no delegation, no failure, because those stay on Jon's channel — which is why opening the look reloads
 * neither the history nor the tiles (UC-WP-90).
 *
 * One rule covers the opening and every reconnect, the chat's own rule D-E: <em>read, then subscribe</em>.
 * The anchor of every live line is the number of voices the snapshot held, frozen while the look stands — so
 * nothing is drawn twice and nothing is read a second time while the look is open. A dropped wire heals the
 * way it opened — re-read and re-subscribe. A read the server has not come back for changes nothing about
 * that: the look keeps asking, because a wire that stopped asking never comes back at all.
 *
 * What the read refuses stays one line of text where the conversation would stand — no dialog, no retry
 * (R-WP-26), and no second request for a name the runtime refuses (R-WP-53). A task line names nobody: the
 * member was given that task, nobody typed it into this widget (R-WP-4). It is a region of the chat view and
 * no fourth tab (R-WP-1); only a tile offering TRANSCRIPT opens it (D-14).
 */
export function ChatInsight({ projectId, name, onClose }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [live, setLive] = useState<LiveItem[]>([])
  const [truncated, setTruncated] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [snapshot, setSnapshot] = useState(false)
  /**
   * The anchor every live line of this look shares: the VOICES the snapshot held — a step of his trace is not
   * one, because a read hands such a step back at any place (D-A). Frozen for the life of the look, which is
   * what lets a live line be placed without a second read (UC-WP-90).
   */
  const afterLines = useRef(0)
  /** The live lines as they stand, beside their state: a stream event continues the list that is really
   *  there, and the state of a render is one render behind it (D-B). */
  const liveItems = useRef<LiveItem[]>([])
  const log = useRef<HTMLUListElement>(null)
  const { onLogScroll, followIfFollowing } = useFollowBottom(log)

  const writeLive = useCallback((items: LiveItem[]) => {
    liveItems.current = items
    setLive(items)
  }, [])

  /**
   * The snapshot in the log's own model: his answer names itself with the core's name the read resolved, his
   * step joins the timeline of its place, his task carries no author. Resolves true when the conversation is
   * on the screen — which is the only state a subscription may anchor on.
   */
  const readSnapshot = useCallback((): Promise<boolean> => {
    return readTranscript(projectId, name)
      .then(loaded => {
        const lines = loaded.lines.map((line): ChatMessage => {
          if (line.kind === 'agent') {
            return { role: 'jon', text: line.text, origin: loaded.name }
          }
          return line.kind === 'tool'
            ? { role: 'tool', text: line.text }
            : { role: 'user', text: line.text }
        })
        afterLines.current = lines.filter(line => !isTraceLine(line)).length
        setMessages(lines)
        setTruncated(loaded.truncated)
        setSnapshot(true)
        setError(null)
        return true
      })
      // a refused name is one line of text where the conversation would stand — no dialog, no retry
      .catch(reason => {
        setError(errorMessage(reason))
        return false
      })
  }, [name, projectId])

  const applyEvent = useCallback(
    (event: ChatStreamEvent) => {
      // `diff`, `agents`, `delegation`, `done` and `error` never reach this wire (R-WP-53): the server keeps
      // them on Jon's channel, so no branch here would ever be taken. `partial`, `tool` and a refusal of a
      // tool do — the core reports one for his tool too, and his look shows it as the red entry it is
      // (R-WP-61)
      const anchor = afterLines.current
      switch (event.kind) {
        case 'partial':
          if (event.thinking) {
            // a Think delta is an entry of his trace and never a voice of his own conversation (R-WP-40);
            // its clock is the moment this browser received it, as it is on Jon's wire
            writeLive(appendLiveThink(liveItems.current, event.text, anchor, Date.now(), null))
            break
          }
          writeLive(appendLiveText(liveItems.current, event.text, anchor))
          break
        case 'tool':
          // the stamp of the step is the wire's own; the turn it belongs to is unknown here, so no step of
          // this look ever claims to be the one being worked on (R-WP-40)
          writeLive(
            appendLiveTool(liveItems.current, event.text, anchor, event.origin ?? null, Date.now(), null),
          )
          break
        case 'problem':
          // his refusal, drawn like his step and with the same stamp rule; of this look's entries none ever
          // works, since his stream ends no turn here (R-WP-53, R-WP-61)
          writeLive(
            appendLiveProblem(liveItems.current, event.text, anchor, event.origin ?? null, Date.now(), null),
          )
          break
      }
    },
    [writeLive],
  )

  useEffect(() => {
    let mounted = true
    const controller = new AbortController()
    const backoff = sseBackoff()
    let reconnect: ReturnType<typeof setTimeout> | undefined

    const connect = () => {
      void openChatStream(
        projectId,
        {
          onEvent: event => {
            backoff.reset()
            applyEvent(event)
          },
          onEnd: retry => {
            if (!mounted || !retry) {
              return
            }
            // a dropped wire loses the live lines and nothing else (R-WP-11): his snapshot is read again —
            // the stream brings nothing back — and the wire is asked for again in the same breath. A read the
            // server has not come back for must not end the asking: it would freeze the look until it closes
            writeLive([])
            void readSnapshot()
            reconnect = setTimeout(connect, backoff.next())
          },
        },
        controller.signal,
        name,
      )
    }

    // the stream opens only once the snapshot has been read: a line anchored before it would be drawn above
    // the whole conversation, and an anchor the read never gave would need a second read to be found (D-E)
    void readSnapshot().then(read => {
      if (mounted && read) {
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
  }, [applyEvent, name, projectId, readSnapshot, writeLive])

  useEffect(() => {
    followIfFollowing()
  }, [live, messages, followIfFollowing])

  return (
    <aside className="chat-insight" aria-label={content.insight.title(name)} data-testid="insight-panel">
      <header className="chat-insight-head">
        <span className="chat-insight-agent" data-testid="insight-agent">
          {name}
        </span>
        <span className="chat-insight-readonly">{content.insight.readOnly}</span>
        <button type="button" data-testid="insight-close" onClick={onClose}>
          {content.common.close}
        </button>
      </header>
      {error !== null && (
        <p className="chat-insight-error" role="alert" data-testid="insight-error">
          {error}
        </p>
      )}
      {!snapshot && error === null && <p className="hint">{content.common.loading}</p>}
      {snapshot && (
        <>
          <ChatLog
            messages={messages}
            outgoing={[]}
            live={live}
            // this wire says nothing about a turn ending, so no step may paint itself as working (R-WP-53)
            openTurn={null}
            testId="insight-log"
            // his task arrived as an order and not as input of this widget: the log names nobody for it
            userAuthor={null}
            logRef={log}
            onScroll={onLogScroll}
          />
          {truncated && (
            <p className="hint" data-testid="insight-truncated">
              {content.insight.truncated}
            </p>
          )}
        </>
      )}
    </aside>
  )
}
