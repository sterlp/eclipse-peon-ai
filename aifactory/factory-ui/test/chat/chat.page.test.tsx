import { afterEach, beforeEach, expect, test } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { renderApp } from '../shared/app.harness'
import { baseDeclarations, declarations, declares } from '../shared/sheets'
import { ChatPage } from '../../src/chat/chat.page'
import { project } from '../shared/fixtures'
import { content } from '../../src/shared/content'
import {
  ANSWER,
  closeChatStub,
  event,
  flush,
  framedLines,
  KEEP_ALIVE,
  mockScrollOf,
  QUESTION,
  resetChatStub,
  sentTexts,
  stubChat,
  tile,
  userLineTexts,
} from './chat.page.stub'

beforeEach(() => resetChatStub())

afterEach(async () => {
  await closeChatStub()
})

test('chatAsksForAProjectWhenNoneIsOpen', () => {
  render(<ChatPage project={null} />)

  expect(screen.getByText(content.chat.noProject)).toBeInTheDocument()
  expect(screen.queryByTestId('chat-input')).toBeNull()
})

// UC-WP-5
test('openProjectBindsWorkDir', async () => {
  const chat = stubChat({ messages: [QUESTION], projects: [project()] })
  renderApp()
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()

  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))

  // the address names the project now, so the chat appears with the read that resolves that address
  expect(await screen.findByTestId('chat-log')).toBeInTheDocument()
  await flush()
  expect(screen.getByText('wo liegen die Dateien?')).toBeInTheDocument()
  expect(screen.getByTestId('chat-input')).toHaveFocus()
  // the project list is gone — opening a project opens its chat, it does not only remember it
  expect(screen.queryByLabelText(content.projects.gitUrl)).toBeNull()
  const reads = chat.calls.filter(call => call.path.endsWith('/messages'))
  expect(reads.map(call => call.path)).toEqual(['/api/projects/demo-1a2b3c4/messages'])
})

test('theChatHeaderNamesTheProjectOnly', async () => {
  stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  const chat = await screen.findByRole('region', { name: content.chat.title })
  await flush()

  // the view IS the chat of a project, so its head names the project and nothing else — a second "Chat"
  // heading would only repeat the menu entry the user just clicked (R-WP-1)
  expect(within(chat).getByRole('heading', { name: 'demo' })).toBeInTheDocument()
  expect(within(chat).queryByRole('heading', { name: content.chat.title })).toBeNull()
  expect(within(chat).queryByText(/^Projekt:/)).toBeNull()
})

// UC-WP-6
test('chatAlwaysGoesToJon', async () => {
  const chat = stubChat({ messages: [] })
  const { container } = render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'fängst du mich auf?' } })
  fireEvent.click(screen.getByTestId('chat-send'))

  expect(chat.countOf('/messages', 'POST')).toBe(1)
  const post = chat.calls.find(call => call.method === 'POST')
  expect(post?.path).toBe('/api/projects/demo-1a2b3c4/messages')
  // one field only: nothing on this edge can name an addressee
  expect(JSON.parse(post?.body ?? '{}')).toEqual({ text: 'fängst du mich auf?' })
  expect(Object.keys(JSON.parse(post?.body ?? '{}'))).toEqual(['text'])
  // the own line is on the screen before the history carries it
  expect(screen.getByTestId('chat-line-user')).toHaveTextContent('fängst du mich auf?')
  // and nothing exists that could take the input away from Jon
  expect(screen.queryByTestId('agent-switcher')).toBeNull()
  expect(container.querySelector('select')).toBeNull()
  expect(screen.queryByRole('combobox')).toBeNull()
})

// UC-WP-56
test('enterSendsBothLinesInOneTurn', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  // Enter is the send (R-WP-30), and what the field holds goes with it — both lines, in their order, one turn
  const field = screen.getByTestId('chat-input') as HTMLTextAreaElement

  // the empty keystroke first, while nothing runs: here only the composer's guard can be the reason that no
  // request follows — the copy of this keystroke below is stopped by the running turn instead (UC-WP-56 GIVEN 1)
  expect(fireEvent.keyDown(field, { key: 'Enter' }), 'an empty Enter keeps its prevented default').toBe(false)
  await flush()
  expect(chat.countOf('/messages', 'POST'), 'an empty Enter opens no turn').toBe(0)

  fireEvent.change(field, { target: { value: 'erste Zeile' } })

  // the second line is set the way the user sets it, at the caret of the field, so the chain
  // Umbruch → two lines → one turn is walked and not modelled
  field.setSelectionRange(field.value.length, field.value.length)
  fireEvent.keyDown(field, { key: 'Enter', metaKey: true })
  expect(field, 'the line break came from the keyboard').toHaveValue('erste Zeile\n')
  fireEvent.change(field, { target: { value: `${field.value}zweite Zeile` } })

  fireEvent.keyDown(field, { key: 'Enter' })
  await flush()

  expect(sentTexts(chat)).toEqual(['erste Zeile\nzweite Zeile'])
  expect(field, 'the send took the text').toHaveValue('')

  // and again on a field the send has just emptied: here the **turn** is what stands in the way, its `done`
  // has not come — the guard's own proof is the probe above and `emptyEnterSendsNothing` (UC-WP-57 GIVEN 2)
  fireEvent.keyDown(field, { key: 'Enter' })
  await flush()
  expect(chat.countOf('/messages', 'POST'), 'an empty Enter behind a running turn opens nothing').toBe(1)
})

// UC-WP-75
test('anUrgentLineGoesToTheServerAsTyped', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  // the `!` is the core's marker, not the view's (R-WP-42): one POST with the text as typed, one line with
  // it as typed — whether this input is a prio message is decided by the server and by nothing here
  const field = screen.getByTestId('chat-input') as HTMLTextAreaElement
  fireEvent.change(field, { target: { value: '! stop now' } })
  fireEvent.keyDown(field, { key: 'Enter' })
  await flush()

  expect(sentTexts(chat)).toEqual(['! stop now'])
  expect(userLineTexts()).toEqual(['! stop now'])
})


// R-WP-38
// UC-WP-70
test('aRunningTurnSurvivesTheUnitClick', async () => {
  // the file area is an address now, and an address change may not cost a running turn: a new mount would
  // read the history again and open the stream again, and the answer half way to the screen would be gone
  const chat = stubChat({ messages: [QUESTION], projects: [project()] })
  renderApp()
  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  await screen.findByTestId('chat-log')
  await flush()

  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')
  // the two wires of this project only — the list beside the chat has a work-state stream of its own, and
  // counting that one would answer a different question
  const readsBefore = chat.countOf(`/${project().id}/messages`, 'GET')
  const streamsBefore = chat.countOf(`/${project().id}/events`, 'GET')
  expect(streamsBefore, 'the open chat streams on exactly one wire').toBe(1)

  // the second handle of the same place: the tree names the file area while the answer is still arriving
  fireEvent.click(
    within(screen.getByRole('navigation', { name: content.nav.views })).getByRole('menuitem', {
      name: content.nav.files,
    }),
  )

  expect(await screen.findByTestId('files-panel')).toBeInTheDocument()
  // the chat beside it is the same instance — no read, no second wire, the live line still stands (G2)
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')
  expect(chat.countOf(`/${project().id}/messages`, 'GET'), 'the running chat is not read again').toBe(
    readsBefore,
  )
  expect(chat.countOf(`/${project().id}/events`, 'GET'), 'one live stream stays open').toBe(streamsBefore)

  // and the turn goes on: opening a folder is not the end of an answer
  await chat.push(event('partial', { kind: 'answer', text: ': pom.xml' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml')
})

// R-WP-38
// UC-WP-70
// UC-WP-77
test('aRunningTurnSurvivesTheChatUnitClick', async () => {
  // the way **back** from the file area, with a turn running: the tree's chat unit names the chat one address
  // up. An entry that answered with `/chat` instead of the project's own address would walk through the
  // last-project redirect, mount the chat again and cost the answer half way to the screen (UC-WP-77, G2).
  // INC-L1-BUG-2: started on the file area on purpose — started on the chat the click asks for the address the
  // view already has, so an entry wired to nothing would survive this test as neatly as the turn does.
  const chat = stubChat({ messages: [QUESTION], projects: [project()] })
  const app = renderApp(`/chat/${project().name}/files`)
  await screen.findByTestId('files-panel')
  await screen.findByTestId('chat-log')
  await flush()

  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')
  const readsBefore = chat.countOf(`/${project().id}/messages`, 'GET')
  const streamsBefore = chat.countOf(`/${project().id}/events`, 'GET')
  expect(streamsBefore, 'the open chat streams on exactly one wire').toBe(1)

  fireEvent.click(
    within(screen.getByRole('navigation', { name: content.nav.views })).getByRole('menuitem', {
      name: content.nav.chat,
    }),
  )

  // the click moved one address up: the area is gone, which is what makes „no new mount" a claim about a
  // changed address and not about a click that did nothing
  await waitFor(() => expect(app.path()).toBe(`/chat/${project().name}`))
  expect(screen.queryByTestId('files-panel'), 'the chat unit leaves the file area').toBeNull()

  // no third address and no new mount: the same chat instance keeps the live line and both wires (G2)
  expect(screen.getByTestId('chat-live'), 'the live line stands through the click').toHaveTextContent(
    'Im Root liegen',
  )
  expect(chat.countOf(`/${project().id}/messages`, 'GET'), 'the running chat is not read again').toBe(
    readsBefore,
  )
  expect(chat.countOf(`/${project().id}/events`, 'GET'), 'one live stream stays open').toBe(streamsBefore)

  // and the turn goes on: naming the unit is not the end of an answer
  await chat.push(event('partial', { kind: 'answer', text: ': pom.xml' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml')
})

// UC-WP-7
test('streamingUpdatesChatWithoutReload', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()
  expect(chat.countOf('/events', 'GET')).toBe(1)
  const readsBefore = chat.countOf('/messages', 'GET')

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  expect(chat.countOf('/messages', 'POST')).toBe(1)

  // the answer arrives in halves, the border cut through the middle of one event's JSON
  await chat.push('event:partial\ndata:{"kind":"answer","te')
  await chat.push('xt":"Im Root liegen"}\n\n' + KEEP_ALIVE)
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')

  await chat.push(event('partial', { kind: 'answer', text: ': pom.xml und README.md' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml und README.md')
  // growing text without a reload: no second history read while the text grows
  expect(chat.countOf('/messages', 'GET')).toBe(readsBefore)

  await chat.push(event('tool', { text: 'Running: `ls` in /home/me/.aifactory/projects/demo' }))
  expect(screen.getByTestId('chat-timeline')).toHaveTextContent('Running: `ls`')

  // thinking is streamed too, and since R-WP-40 it is an entry of the trace — an entry of the timeline and
  // never a line of the answer, which is what the live line still holds alone (UC-WP-72 GIVEN 1)
  await chat.push(event('partial', { kind: 'think', text: 'Ich überlege gerade…' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml und README.md')
  const thoughtEntries = () => [
    ...screen.getByTestId('chat-log').querySelectorAll('.chat-trace-item[data-trace="think"]'),
  ]
  expect(thoughtEntries(), 'the think delta is one entry beside the step').toHaveLength(1)
  expect(thoughtEntries()[0]).toHaveTextContent('Ich überlege gerade')
  expect(
    screen.getByTestId('chat-timeline').querySelectorAll('.chat-trace-item').length,
    'the thought joins the widget of the step instead of drawing a box of its own',
  ).toBe(2)

  // the finished text is no event: done means read the history once, and it replaces the live line
  // since R-WP-47 the read carries the step as well — name and excerpt out of the memory — so the live line
  // gives way to the Bestand's own account of it instead of standing twice (UC-WP-82)
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'tool', text: 'pom.xml README.md', origin: 'shellRunCommand' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 1234 }))

  expect(screen.getByTestId('chat-line-jon')).toHaveTextContent(ANSWER.text)
  expect(chat.countOf('/messages', 'GET')).toBe(readsBefore + 1)
  expect(screen.queryByTestId('chat-live')).toBeNull()
  // the trace of the turn is not lost by the read: the Bestand hands the step back, named and with its
  // excerpt, and the Think entry of the same place stays beside it in the ONE widget (R-WP-28, R-WP-40,
  // UC-WP-54, UC-WP-82)
  const widget = screen.getByTestId('chat-timeline')
  expect(widget, 'the step of the read stands in the log with the name the memory carries').toHaveTextContent(
    'shellRunCommand',
  )
  expect(widget).toHaveTextContent('pom.xml README.md')
  expect(
    thoughtEntries(),
    'a think entry is of the same family — it exists on the wire only, so the read may not drop it',
  ).toHaveLength(1)
  // the own input lives in the history only now — the pending copy is gone, no line twice
  expect(screen.getAllByTestId('chat-line-user')).toHaveLength(2)
})

// UC-WP-64
test('failedTurnIsALineInTheHistory', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  const reason = 'Es fehlt ein Modellschlüssel für OPEN_AI'
  // the server writes the failure into the conversation before it reports it, so the read that answers the
  // event is what makes the line outlive a reload and every other browser (R-WP-34)
  chat.setMessages([QUESTION, { role: 'error', text: reason }])

  await chat.push(event('error', { message: reason }))
  await flush()

  // the line stands in the log — once, never twice: the live notice gives way to the history that carries it
  const failures = screen.getAllByTestId('chat-line-error')
  expect(failures).toHaveLength(1)
  expect(screen.getByTestId('chat-log')).toContainElement(failures[0])
  expect(failures[0]).toHaveTextContent(reason)
  // no notice beside the log: a failed turn is a line of the conversation, not a message about it
  expect(screen.queryByTestId('error-panel')).toBeNull()
  // no alarm dialog, no dead input: the next turn is one line away (Q1)
  expect(screen.getByTestId('chat-input')).toBeEnabled()
  // the send stays clickable whatever happened (UC-WP-74 GIVEN 1) — an empty field is no reason to refuse it
  expect(screen.getByTestId('chat-send')).toBeEnabled()
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt mit Schlüssel' } })
  expect(screen.getByTestId('chat-send')).toBeEnabled()
})

// UC-WP-11
test('tileStateFollowsTheStream', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()

  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.IDLE)
  const historyReads = chat.countOf('/messages', 'GET')
  const rosterReads = chat.countOf('/agents', 'GET')

  // the snapshot arrives on the wire and replaces the list — no second read of anything (R-WP-7)
  await chat.push(event('agents', { tiles: [tile('Jon', 'WORKING'), tile('Dev', 'COMPACTING')] }))

  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.WORKING)
  expect(screen.getByTestId('tile-state-Dev')).toHaveTextContent(content.tiles.state.COMPACTING)
  expect(chat.countOf('/messages', 'GET')).toBe(historyReads)
  expect(chat.countOf('/agents', 'GET')).toBe(rosterReads)
  // and a tile that is not idle offers nothing any more (UC-WP-12's guard, seen from the screen)
  expect(screen.getByTestId('tile-clear-Jon')).toBeDisabled()
})

// UC-WP-12
test('clearOnATileNamesThatTileAndARefusalIsALine', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE')] })
  chat.refuseActions(409, 'a turn or another action is still running')
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-clear-Dev'))
  await waitFor(() => expect(screen.getByTestId('error-panel')).toHaveTextContent(/still running/))

  // one POST, and it addresses exactly the tile that was clicked — name and action, nothing else
  expect(chat.countOf('/agents', 'POST')).toBe(1)
  const post = chat.calls.find(call => call.method === 'POST')
  expect(post?.path).toBe('/api/projects/demo-1a2b3c4/agents')
  expect(JSON.parse(post?.body ?? '{}')).toEqual({ name: 'Dev', action: 'clear' })
  // the refusal changed nothing: no re-read of the chat, and no roster read beyond the two the view always
  // makes — its own first reading and the fresh read of the standing connection
  expect(chat.countOf('/messages', 'GET')).toBe(1)
  expect(chat.countOf('/agents', 'GET')).toBe(2)
  expect(screen.getByTestId('chat-log')).toBeInTheDocument()
})

// UC-WP-99
test('thePageErrorPanelStandsAboveTheTeamAndOutsideTheLog', async () => {
  const chat = stubChat({
    messages: [QUESTION],
    agents: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE')],
  })
  const REFUSAL = 'a turn or another action is still running'
  chat.refuseActions(409, REFUSAL)
  render(<ChatPage project={project()} />)
  await flush()

  // Paul's case, measured: a Compact refused because a turn runs is the answer the page has to show where
  // he is looking — not under what he is reading (R-WP-62)
  fireEvent.click(screen.getByTestId('tile-compact-Dev'))

  const panel = await screen.findByTestId('error-panel')
  expect(panel).toHaveTextContent(REFUSAL)
  // outside the scroll area, proven structurally: the log is the only thing in this column that runs past
  // its edges, and the panel stands nowhere inside it (UC-WP-99 GIVEN 1)
  expect(panel.closest('.chat-log')).toBeNull()
  // above the team — the adjacency is the proof jsdom cannot deliver as a style (memory #15)
  expect(panel.nextElementSibling).toBe(screen.getByTestId('chat-tiles'))
  expect(document.querySelectorAll('[role=alert]'), 'one announcement, not two').toHaveLength(1)

  // its own X closes it, and the next refused click shows it again (UC-WP-99 GIVEN 2)
  fireEvent.click(screen.getByTestId('error-panel-close'))
  expect(screen.queryByTestId('error-panel')).toBeNull()

  fireEvent.click(screen.getByTestId('tile-compact-Dev'))
  expect(await screen.findByTestId('error-panel')).toHaveTextContent(REFUSAL)
  expect(chat.countOf('/agents', 'POST')).toBe(2)

  // and a new interaction of this page closes the band by itself, with no click on the X in between
  // (UC-WP-99 GIVEN 2): the send is accepted here — only the tile action is refused — so the page begins a
  // fresh request and must not make the viewer dismiss the old answer first
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'noch eine frage' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await waitFor(() => expect(chat.countOf('/messages', 'POST')).toBe(1))
  expect(screen.queryByTestId('error-panel')).toBeNull()
  expect(document.querySelectorAll('[role=alert]'), 'a closed band announces nothing').toHaveLength(0)
})

// UC-WP-57
test('emptySendResendsTheLastUserMessage', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()
  const reads = chat.countOf('/messages', 'GET')

  // nothing typed and no turn running: the click alone repeats (UC-WP-57 GIVEN 1)
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  // the last line the screen shows, here the history's — nothing is pending — and it goes out as it stands,
  // on the same edge as a fresh one
  expect(chat.countOf('/messages', 'POST')).toBe(1)
  expect(sentTexts(chat)).toEqual([QUESTION.text])
  expect(chat.countOf('/messages', 'GET'), 'a repeat reads nothing again').toBe(reads)
  expect(userLineTexts(), 'the history line and the repeated one').toEqual([QUESTION.text, QUESTION.text])
  expect(screen.getByTestId('chat-input')).toHaveValue('')
})

// UC-WP-57
// UC-WP-74
test('emptySendDoesNothingWhileATurnRuns', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  // the turn this browser started runs until its `done`: the field stands empty and the button still sends
  expect(chat.countOf('/messages', 'POST')).toBe(1)
  expect(screen.getByTestId('chat-send')).toBeEnabled()

  // an empty click behind a running turn opens no second turn and queues no blank (UC-WP-57 GIVEN 2)
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  expect(chat.countOf('/messages', 'POST')).toBe(1)
  expect(sentTexts(chat)).toEqual(['zähl die Dateien'])
  expect(userLineTexts()).toEqual([QUESTION.text, 'zähl die Dateien'])
})

// UC-WP-57
test('emptySendWithoutMessagesDoesNothing', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  // nothing to repeat: the way ends before any request — a blank would reach the backend and be answered
  // with 400, and neither a line nor a notice may stand there afterwards (UC-WP-57 GIVEN 3)
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  expect(chat.countOf('/messages', 'POST')).toBe(0)
  expect(screen.queryAllByTestId('chat-line-user')).toEqual([])
  expect(screen.queryByTestId('error-panel')).toBeNull()
})

// UC-WP-58
test('waitingBarShowsTheLivePreview', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()
  // no turn, no wait
  expect(screen.queryByTestId('chat-waiting')).toBeNull()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  // GIVEN a turn that has delivered nothing THEN the words of the plugin
  expect(screen.getByTestId('chat-waiting')).toHaveTextContent(content.chat.waitingForAi)

  // GIVEN the stream delivers THEN the bar shows what it last reported, on one line — a preview beside
  // the log and never a second copy of the answer (R-WP-31)
  await chat.push(event('tool', { text: 'Running: `ls` in /home/me' }))
  expect(screen.getByTestId('chat-waiting')).toHaveTextContent('Running: `ls`')

  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen:\npom.xml' }))
  expect(screen.getByTestId('chat-waiting')).toHaveTextContent('pom.xml')
  expect(screen.getByTestId('chat-waiting').textContent).not.toContain('Im Root liegen')

  // and it is the plugin's line: mono, with a caret that blinks (R-WP-31). Where the bar stands is the
  // layout's call and not this rule's: the log scrolls in its own area, so the bar is a block directly
  // above the field and hangs on nothing any more (R-WP-35) — the `position: sticky` clause measured the
  // page scroll this increment took away, so it became its opposite.
  const painted = declarations('.chat-waiting')
  expect(painted, 'the wait keeps the plugin mono').toContain('font-family: var(--mono)')
  expect(painted, 'nothing sticks to a scroll the chat no longer has').not.toContain('position:')
  expect(declarations('.chat-waiting-caret')).toContain('animation')
  expect(declarations('.chat-log'), 'the log is the scroll area now').toContain('overflow-y: auto')
  expect(
    screen.getByTestId('chat-waiting').nextElementSibling?.className,
    'the wait sits directly above the field',
  ).toContain('chat-composer')
})

// UC-WP-58
test('waitingBarEndsWithTheTurn', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen: pom.xml' }))
  expect(screen.getByTestId('chat-waiting')).toBeInTheDocument()

  chat.setMessages([ANSWER])
  await chat.push(event('done', { millis: 12 }))

  // the answer stays, the wait goes with the turn (R-WP-31)
  expect(screen.queryByTestId('chat-waiting')).toBeNull()
  expect(screen.getByTestId('chat-log')).toHaveTextContent('Im Root liegen: pom.xml')
})

// UC-WP-58
test('waitingBarEndsWithAFailedTurn', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  expect(screen.getByTestId('chat-waiting')).toBeInTheDocument()

  // a failed turn is an ended turn: no wait line left standing above the reason (UC-WP-58), and the reason
  // is a line of the conversation, not a notice beside it (R-WP-34)
  chat.setMessages([{ role: 'error', text: 'Es fehlt ein Modellschlüssel für OPEN_AI' }])
  await chat.push(event('error', { message: 'Es fehlt ein Modellschlüssel für OPEN_AI' }))

  expect(screen.getByTestId('chat-line-error')).toHaveTextContent('Es fehlt ein Modellschlüssel')
  expect(screen.queryByTestId('error-panel')).toBeNull()
  expect(screen.queryByTestId('chat-waiting')).toBeNull()
})

// UC-WP-65
test('noWaitingFrameOnAnyLineAtAnyTime', async () => {
  // R-WP-31 no longer draws a frame at all. Asked of the sheet first, because a DOM question alone would
  // pass vacuously: paint the rule back into styles.css and this line is the one that goes red (memory #4 —
  // the old positive proof narrowed here, its live half kept below)
  expect(declares('.chat-line.pending'), 'the sheet states no waiting frame anymore').toBe(false)

  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'schlaf drei sekunden' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  await chat.push(event('partial', { kind: 'answer', text: 'Ich schlafe gerade' }))
  expect(screen.getByTestId('chat-waiting'), 'the turn is still waited for').toBeInTheDocument()
  expect(framedLines()).toHaveLength(0)

  fireEvent.click(screen.getByTestId('chat-stop'))
  await flush()

  // the accepted stop IS the end of the wait — it goes with the click, not with a later read, and
  // everything he was shown stays on the screen (UC-WP-50, D-16)
  expect(framedLines()).toHaveLength(0)
  expect(screen.queryByTestId('chat-waiting')).toBeNull()
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Ich schlafe gerade')
  expect(screen.getByTestId('chat-log')).toHaveTextContent('schlaf drei sekunden')

  chat.setMessages([QUESTION])
  await chat.push(event('done', { millis: 5 }))

  expect(framedLines()).toHaveLength(0)
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Ich schlafe gerade')
})

// UC-WP-64
test('failedTurnKeepsTheWaitingInputAndOneErrorLine', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und die Ordner' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  // the turn fails with one input confirmed by the conversation and the other still waiting on it: the
  // waiting one keeps its text where it was shown, and the reason is exactly one line of the log (R-WP-34)
  chat.setMessages([
    { role: 'user', text: 'zähl die Dateien', origin: 'Paul' },
    { role: 'error', text: 'Es fehlt ein Modellschlüssel für OPEN_AI' },
  ])
  await chat.push(event('error', { message: 'Es fehlt ein Modellschlüssel für OPEN_AI' }))

  expect(screen.getByTestId('chat-log')).toHaveTextContent('und die Ordner')
  expect(screen.getAllByTestId('chat-line-error')).toHaveLength(1)
})
// UC-WP-66
test('theLogIsTheScrollArea', () => {
  // jsdom lays out nothing, so "the chat scrolls in its own area" is read where it is written: the log owns
  // the scroll, the row owns the position, and the shell is a frame that never scrolls as a whole (R-WP-35)
  expect(declarations('.chat-log'), 'the log carries the scroll').toContain('overflow-y: auto')
  expect(declarations('.chat-log'), 'and it is the flexible part of the column').toContain('flex: 1')
  // without the zeroed floor a flex item keeps its content height and never scrolls — the line that looks
  // like a leftover is the one that makes the scroll work
  expect(declarations('.chat-log'), 'and may shrink below its own content').toContain('min-height: 0')
  // the rows of the grid may not share the leftover height of the flexed log — that is what makes a bubble
  // as high as its text and not as high as the screen (R-WP-29); the height itself stays a browser measurement
  expect(declarations('.chat-log'), 'and stacks its lines from the top').toContain('align-content: start')
  expect(declarations('.chat-workspace'), 'the row is the box the file tab hangs on').toContain(
    'position: relative',
  )
  expect(declarations('.chat-workspace'), 'the row gives the chat its height, head to input').toContain(
    'align-items: stretch',
  )
  expect(declarations('.chat-workspace > .view'), 'the chat is a column').toContain(
    'flex-direction: column',
  )
  expect(declarations('.chat-workspace > .view'), 'and only its log scrolls').toContain(
    'overflow: hidden',
  )
  expect(declarations('.view'), 'every view scrolls for itself, so none of them is clipped').toContain(
    'overflow-y: auto',
  )
  expect(baseDeclarations('#root'), 'the shell is the fixed frame').toContain('height: 100dvh')
  expect(baseDeclarations('#root'), 'and it never scrolls as a whole').toContain('overflow: hidden')
})


// UC-WP-66
test('newLinesScrollToTheEnd', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const log = screen.getByTestId('chat-log')
  mockScrollOf(log, 1_000, 300)
  expect(log.scrollTop, 'the test starts at the top of a long log').toBe(0)

  // GIVEN he reads the newest line WHEN the stream adds one THEN the log follows it to its end
  await chat.push(event('partial', { kind: 'answer', text: 'noch eine Zeile' }))
  expect(log.scrollTop, 'a log at its end keeps following the stream').toBe(1_000)
})

// UC-WP-66
test('readingUpIsNotRippedDown', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const log = screen.getByTestId('chat-log')
  mockScrollOf(log, 1_000, 300)

  // GIVEN he has read up WHEN the next line arrives THEN the log stays where he left it
  log.scrollTop = 120
  fireEvent.scroll(log)
  await chat.push(event('partial', { kind: 'answer', text: 'noch eine Zeile' }))
  expect(log.scrollTop, 'a line he reads above the end is his place, not the stream\'s').toBe(120)

  // GIVEN he stands within the last few pixels of the end WHEN the stream adds THEN it follows again —
  // those pixels still count as "reading the newest line", so the log does not stop a hair too early
  log.scrollTop = 990
  fireEvent.scroll(log)
  await chat.push(event('partial', { kind: 'answer', text: 'und noch eine' }))
  expect(log.scrollTop, 'the end of the log is where the stream keeps him').toBe(1_000)
})

/**
 * The way back to the end (UC-WP-80): the button stands while the reading position is away from it, takes the
 * view there and re-arms the follow that gave up on it. All three GIVENs of the rule in one mount, because
 * each one is what makes the next one mean something.
 */
// UC-WP-80
test('scrollBottomButtonAppearsAboveAndReArmsAutoScroll', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const log = screen.getByTestId('chat-log')
  mockScrollOf(log, 1_000, 300)
  expect(screen.queryByTestId('scroll-bottom'), 'at the end there is nothing to come back to').toBeNull()

  // GIVEN he has read up WHEN a new line arrives THEN the view stays his and the way back appears
  log.scrollTop = 120
  fireEvent.scroll(log)
  await chat.push(event('partial', { kind: 'answer', text: 'noch eine Zeile' }))
  expect(log.scrollTop, 'the place he read up to stays his').toBe(120)
  const backToTheEnd = await screen.findByTestId('scroll-bottom')

  // GIVEN he clicks it WHEN the view moves THEN he stands at the end and the button has nothing left to offer
  fireEvent.click(backToTheEnd)
  expect(log.scrollTop, 'the click takes him to the end').toBe(1_000)
  expect(screen.queryByTestId('scroll-bottom'), 'at the end again: no button').toBeNull()

  // GIVEN the button brought him back WHEN the next line arrives THEN the follow works again
  await chat.push(event('partial', { kind: 'answer', text: 'und noch eine' }))
  expect(log.scrollTop, 'the click armed the follow again').toBe(1_000)
})

/**
 * His own input is the one line he always wants to see (UC-WP-80): the send answers to the send, not to the
 * reading position. The button is gone in the same breath — he is at the end.
 */
// UC-WP-80
test('ownInputAlwaysShowsWhenSentFromTheTop', async () => {
  stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const log = screen.getByTestId('chat-log')
  mockScrollOf(log, 1_000, 300)
  log.scrollTop = 120
  fireEvent.scroll(log)
  expect(screen.getByTestId('scroll-bottom'), 'read up: the way back stands').toBeInTheDocument()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  expect(log.scrollTop, 'his own input is shown wherever he had read up to').toBe(1_000)
  expect(screen.queryByTestId('scroll-bottom'), 'and the way back is on no screen').toBeNull()
})
