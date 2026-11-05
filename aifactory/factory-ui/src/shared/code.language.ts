/**
 * The suffix of a path to the language the viewer highlights with. Only what the loaded Shiki bundle
 * carries is named (checked against `shiki/bundle/web` of react-shiki 0.11.2, AGENTS-DEV Inc 3b): a
 * suffix outside it becomes `text`, which is what it renders as anyway — naming a grammar the bundle
 * does not hold would only promise a colour that never arrives.
 */
const BY_SUFFIX: Record<string, string> = {
  bash: 'bash',
  c: 'c',
  css: 'css',
  h: 'c',
  htm: 'html',
  html: 'html',
  java: 'java',
  js: 'javascript',
  json: 'json',
  jsonc: 'jsonc',
  jsx: 'jsx',
  md: 'markdown',
  mjs: 'javascript',
  py: 'python',
  scss: 'scss',
  sh: 'bash',
  sql: 'sql',
  ts: 'typescript',
  tsx: 'tsx',
  txt: 'text',
  xml: 'xml',
  yaml: 'yaml',
  yml: 'yaml',
  zsh: 'bash',
}

/** The language of a path: its suffix, or `text` for everything without one. */
export function languageOf(path: string): string {
  const name = path.slice(path.lastIndexOf('/') + 1)
  const dot = name.lastIndexOf('.')
  if (dot < 1) {
    return 'text'
  }
  return BY_SUFFIX[name.slice(dot + 1).toLowerCase()] ?? 'text'
}
