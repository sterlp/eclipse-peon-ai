import type { ThemeMode } from './theme.mode'

/**
 * Every text the screen shows (R-WP-27, UC-WP-53): one catalog, no locale framework. A component holds no
 * literal of its own, so a test and a later i18n address the same keys instead of copy-pasted copy.
 *
 * Values are English (AGENTS.md: „UI: English"). A text that needs a value from the screen — a project
 * name, a byte count — is a function, because the sentence stays in the catalog and only the value comes
 * from the caller.
 */
export const content = {
  brand: 'Open AI Factory',

  nav: {
    /** The name of the frame's menu, for the screen reader. */
    views: 'Views',
    /** The name of the header's menu, for the screen reader (R-WP-43): its three pages. */
    pages: 'Pages',
    /** The list of projects: a page of the header, no place in the tree (R-WP-43, R-WP-44). */
    projects: 'Projects',
    /** The chat as one of the header's pages (R-WP-43, UC-WP-76) — the same word as `chat.title` (M15). */
    chat: 'Chat',
    /** The one unit a project has (R-WP-38): the file area, hung under its project in the tree. */
    files: 'Files',
    settings: 'Settings',
    /**
     * The handle that folds the tree away and brings it back (R-WP-44): the button of the header is two
     * glyphs and carries no word of its own, so the name says what pressing it will do — the same reading
     * as `theme.toggle`.
     */
    toggleSider: (collapsed: boolean): string =>
      collapsed ? 'Show the project tree' : 'Hide the project tree',
  },

  user: {
    /** The field of this browser's name (R-WP-17) — label and placeholder are the one phrase. */
    name: 'Your name',
  },

  theme: {
    /**
     * The switch between the two colour modes (UC-WP-71): it says what pressing it will do, since the
     * control itself is a moon and a sun and carries no word of its own.
     */
    toggle: (mode: ThemeMode): string =>
      mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode',
  },

  common: {
    loading: 'Loading…',
    close: 'Close',
    back: 'Back',
    seconds: 'seconds',
    /**
     * The two answers of a confirmation. The library's own dialog would speak its locale's words — the
     * catalog is the only source of copy here (R-WP-27), so both are named.
     */
    yes: 'Yes',
    no: 'No',
  },

  projects: {
    title: 'Projects',
    gitUrl: 'Git URL',
    gitUrlSample: 'https://git.example.com/org/repo.git',
    add: 'Add',
    cloning: 'Cloning…',
    empty: 'No project',
    remove: 'Remove',
    /** The row of a project (R-WP-24): a turn runs here, or nothing does. */
    working: 'working',
    free: 'free',
    /** Said beside "working" — an input only waits while a turn runs, so this never stands alone. */
    queued: (count: number): string => `${count} queued`,
    removeQuestion: (name: string): string =>
      `Remove ${name}? The clone folder is deleted with it.`,
  },

  chat: {
    /** The name of the chat region — the project's name is what the head shows (R-WP-1). */
    title: 'Chat',
    noProject: 'No project open',
    history: 'History',
    noMessages: 'No messages yet',
    /** The label of a user line the server left unnamed (R-WP-25) — a role, never an invented author. */
    you: 'YOU',
    /** The one word of a running delegation (R-WP-40): the entry names the member, never a task — the wire
     *  carries none, so there is nothing else this screen could say about it (UC-WP-72 GIVEN 3). */
    working: 'working',
    /** The word a Think entry names itself with (R-WP-40): the kind of the entry, never an author — the wire
     *  stamps none on a thought, so the screen has no name to show and says what it is instead. */
    thinking: 'Thinking',
    message: 'Message',
    messageToJon: 'Message to Jon',
    send: 'Send',
    stop: 'Stop',
    /** The wait of a turn that has delivered nothing yet — the words of the plugin (R-WP-31). */
    waitingForAi: 'Waiting for AI…',
    /** The one label of the way back to the end of the log (R-WP-35, UC-WP-80). */
    scrollBottom: 'Scroll bottom',
    /** The fallback of an `error` event that carries no reason — better than an empty line. */
    unknownError: 'unknown error',
  },

  tiles: {
    team: 'Team',
    state: {
      IDLE: 'ready',
      WORKING: 'working',
      COMPACTING: 'compacting',
      ERROR: 'error',
    },
    noContext: 'no own context',
    budget: (percent: number, budget: number): string => `${percent}% of budget ${budget}`,
    /** The tile's own door — the accessible name of a tile that opens its agent's conversation. */
    open: (name: string): string => `Open ${name}'s conversation`,
    clear: 'Clear',
    compact: 'Compact',
  },

  insight: {
    title: (name: string): string => `Insight into ${name}`,
    readOnly: 'read-only',
    truncated: 'Older lines fall away — the edge returns only the last ones.',
  },

  files: {
    open: 'Files',
    title: 'Files',
    openArea: 'Open the file area',
    collapseArea: 'Close the file area',
    root: 'Root',
    roots: {
      checkout: 'Checkout',
      checkoutHint: 'Project checkout',
      tmp: 'TMP',
      tmpHint: 'Temporary directory',
    },
    viewer: 'File',
    backToTree: 'Back to folder',
    linkSuffix: ' (link)',
    emptyFolder: 'Empty folder',
    binary: (size: number): string => `Binary file (${size} B) — its content is not shown.`,
    truncated: 'Cut short — the root holds more than this edge returns.',
  },

  config: {
    title: 'Settings',
    provider: 'Provider',
    model: 'Model',
    /** R-WP-50: the one word of the button that asks the connection again — the plugin's button says the same. */
    modelRefresh: 'Refresh',
    url: 'URL',
    tokenWindow: 'Token window',
    timeout: 'Timeout',
    sendThinking: 'Send thinking',
    yes: 'Yes',
    no: 'No',
    /**
     * R-WP-51: the advanced trio of the base level. The labels are the field names the server's refusal
     * repeats, so the message and the row on the screen name the same thing.
     */
    maxTokens: 'Max Tokens',
    queryParams: 'Query Params',
    headerParams: 'Header Params',
    /** The shape of both CSV fields — one rule, one sentence (R-WP-51). */
    paramsPlaceholder: 'k=v, k2=v2',
    key: 'Key',
    keySet: 'set',
    keyMissing: 'not set',
    keySource: 'source',
    source: {
      FILE: 'file',
      ENV: 'environment variable',
      NONE: 'none',
    },
    /**
     * The one field that may stay empty (R-WP-16): the key is write-only and never prefilled, so empty
     * cannot mean "delete" here. Every other field says it by being empty — that state has no words.
     */
    unchanged: 'empty = unchanged',
    /** UC-WP-63: without this a key set once could never be removed through the page. */
    removeKey: 'Remove key',
    removeKeyQueued: 'the stored key is removed when you save',
    /** Q3: the provider's own think form, said where the field can do nothing about it. */
    thinkUnsupported: 'this provider has no thinking input',
    saving: 'Saving…',
    save: 'Save',
  },
  /**
   * R-WP-23: the agents the factory runs, one row each. The key words of the key field are the default
   * level's (`config.keySet`, `config.unchanged`, `config.removeKey`) — one rule, one set of words.
   */
  agents: {
    title: 'Agents',
    /** The checkbox is no stored value: it says the slot's own file carries something. */
    override: 'Override',
    model: 'Model',
    url: 'URL',
    think: 'Think',
    /** R-THINK-10: Devs record carries the base think value every other agent inherits (R-WP-23). */
    thinkDefault: 'Think (Default)',
    /** The empty option of the think selection — the slot says nothing of its own. */
    inheritOption: 'inherits the default',
    /** R-WP-23: the base provider has no per-request think input, so no field could do anything. */
    thinkUnsupported: 'the base provider has no thinking input',
    temperature: 'Temperature',
    /**
     * R-WP-52: the two slot fields of the provider's body and of the tool allowlist. The label of the
     * body is the core's own name for the field, as the server's refusal repeats it.
     */
    extraBody: 'Extra Body',
    tools: 'Tools',
    /** The shape of the allowlist — the entry the core interprets, names and negations (D9). */
    toolsPlaceholder: 'read, grep, -write',
    /** UC-WP-88: the offer of what runs with the typed entry — it never saves and never warns. */
    preview: 'Preview',
    previewCount: (count: number): string => `${count} tools`,
    previewEmpty: 'no tool runs with that entry',
    previewHint: 'empty = every tool the agent has',
    save: 'Save',
    /** UC-WP-44 "ein leeres Feld erbt den Default-Feld für Feld" — the empty state of these fields. */
    inherits: 'empty = inherits the default',
  },
} as const
