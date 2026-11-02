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
    projects: 'Projects',
    chat: 'Chat',
    settings: 'Settings',
  },

  user: {
    /** The field of this browser's name (R-WP-17) — label and placeholder are the one phrase. */
    name: 'Your name',
  },

  common: {
    loading: 'Loading…',
    close: 'Close',
    back: 'Back',
    seconds: 'seconds',
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
    /** The label of a merged tool box — the word `chat.html` paints above the trace (R-WP-28). */
    toolActivity: 'Tool',
    message: 'Message',
    messageToJon: 'Message to Jon',
    send: 'Send',
    stop: 'Stop',
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
    insight: 'Insight',
    clear: 'Clear',
    compact: 'Compact',
  },

  insight: {
    title: (name: string): string => `Insight into ${name}`,
    readOnly: 'read-only',
    kind: {
      user: 'Input',
      agent: 'Answer',
      tool: 'Tool',
    },
    empty: 'No conversation — this agent has none in this run.',
    truncated: 'Older lines fall away — the edge returns only the last ones.',
  },

  files: {
    open: 'Files',
    title: 'Files',
    root: 'Root',
    roots: {
      checkout: 'Checkout',
      tmp: 'TMP',
    },
    linkSuffix: ' (link)',
    emptyFolder: 'Empty folder',
    binary: (size: number): string => `Binary file (${size} B) — its content is not shown.`,
    truncated: 'Cut short — the root holds more than this edge returns.',
  },

  config: {
    title: 'Settings',
    provider: 'Provider',
    model: 'Model',
    url: 'URL',
    tokenWindow: 'Token window',
    timeout: 'Timeout',
    sendThinking: 'Send thinking',
    yes: 'Yes',
    no: 'No',
    key: 'Key',
    keySet: 'set',
    keyMissing: 'not set',
    keySource: 'source',
    source: {
      FILE: 'file',
      ENV: 'environment variable',
      NONE: 'none',
    },
    /** Said at every field that may stay empty (R-WP-16) — until Inc 7 narrows it to the key. */
    unchanged: 'empty = unchanged',
    saving: 'Saving…',
    save: 'Save',
  },
} as const
