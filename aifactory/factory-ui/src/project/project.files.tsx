import { useCallback, useEffect, useState } from 'react'
import {
  CloseOutlined,
  FileOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons'
import { Drawer } from 'antd'



import { readFiles, type DirectoryResponse, type FileEntry, type FileResponse, type FileRoot } from './files.client'
import { errorMessage } from '../shared/api.client'
import { CodeView } from '../shared/code'
import { languageOf } from '../shared/code.language'
import { content } from '../shared/content'
import type { ProjectDto } from '../shared/server-api'

interface Props {
  /**
   * The addressed project — only its id is used. The panel never names the project, because the chat head
   * beside it already does (R-WP-33). The area owns its state and resets none of it on its own, so the
   * caller keys this component per project (`key={project.id}`): unkeyed, the second project would open
   * inside the tree of the one before it.
   */
  project: ProjectDto
  /** Whether the address names this area (`chat/:project/files`). Read by the route, never kept here. */
  open: boolean
  /** The one toggle there is: tab and the drawer's own close both go through it, and its owner navigates. */
  onToggle(): void
}

/** One directory read as it stands: asked for, answered, or refused. */
type Branch =
  | { status: 'loading' }
  | { status: 'ready'; read: DirectoryResponse | FileResponse }
  | { status: 'error'; message: string }

/** What the viewer holds: one file of one root, read once and shown until it is closed. */
interface Viewed {
  root: FileRoot
  path: string
  file: FileResponse
}

/** The two roots as cards — a card says what it is, which a switch between two words would not. */
const ROOTS: { value: FileRoot; label: string; hint: string }[] = [
  { value: 'checkout', label: content.files.roots.checkout, hint: content.files.roots.checkoutHint },
  { value: 'tmp', label: content.files.roots.tmp, hint: content.files.roots.tmpHint },
]

/** A branch is told apart by root and relative path — the pair the edge is asked with. */
function branchKey(root: FileRoot, path: string): string {
  return `${root}|${path}`
}

/** A directory below the root of the tree, or the root itself when the path is still empty. */
function childPathOf(path: string, name: string): string {
  return path === '' ? name : `${path}/${name}`
}

/**
 * The suffix behind a name. A symlink is a name and nothing else — the server sends no metadata of its
 * target, so `link` is the honest label and `0 B` would read as an empty file (UC-WP-42).
 */
function suffixOf(entry: FileEntry): string {
  if (entry.directory) {
    return '/'
  }
  return entry.symlink ? content.files.linkSuffix : ` (${entry.size} B)`
}

/** The icon of a row, by the name the row uses. An icon is decoration: the row keeps its name as label. */
const ICONS = {
  'folder2-open': FolderOpenOutlined,
  folder: FolderOutlined,
  'file-earmark': FileOutlined,
  'x-lg': CloseOutlined,
  'chevron-right': RightOutlined,
  'chevron-left': LeftOutlined,
} as const

function Icon({ name }: { name: keyof typeof ICONS }) {
  const Glyph = ICONS[name]
  return <Glyph aria-hidden="true" />
}

interface LevelProps {
  root: FileRoot
  path: string
  branches: Record<string, Branch>
  expanded: string[]
  onToggle(root: FileRoot, path: string): void
  onOpenFile(root: FileRoot, path: string): void
}

/**
 * One level of the tree: the rows of one listing, every directory carrying its own level while it is
 * open. A level is asked for when it is opened and never before — the tree is a directory at a time
 * (UC-WP-19), and a refused level prints its line where it stands (UC-WP-42).
 */
function FilesLevel({ root, path, branches, expanded, onToggle, onOpenFile }: LevelProps) {
  const branch = branches[branchKey(root, path)]

  if (branch === undefined || branch.status === 'loading') {
    return <p className="hint">{content.common.loading}</p>
  }
  if (branch.status === 'error') {
    return (
      <p className="files-error" role="alert" data-testid="files-error">
        {branch.message}
      </p>
    )
  }
  if (branch.read.kind !== 'directory') {
    // the listing called this a directory and the read disagrees: the row stays closed and asks again
    return null
  }

  return (
    <ul className="files-level">
      {branch.read.entries.map(entry => {
        const childPath = childPathOf(path, entry.name)
        // a row is open by its own key, never by the key of the level it stands in
        const open = expanded.includes(branchKey(root, childPath))
        return (
          <li key={childPath}>
            {entry.directory ? (
              <button
                type="button"
                className="files-row directory"
                data-testid="files-entry"
                aria-expanded={open}
                onClick={() => onToggle(root, childPath)}
              >
                <Icon name={open ? 'folder2-open' : 'folder'} />
                {entry.name}
                {suffixOf(entry)}
              </button>
            ) : (
              <button
                type="button"
                className="files-row"
                data-testid="files-entry"
                onClick={() => onOpenFile(root, childPath)}
              >
                <Icon name="file-earmark" />
                {entry.name}
                {suffixOf(entry)}
              </button>
            )}
            {entry.directory && open && (
              <FilesLevel
                root={root}
                path={childPath}
                branches={branches}
                expanded={expanded}
                onToggle={onToggle}
                onOpenFile={onOpenFile}
              />
            )}
          </li>
        )
      })}
      {branch.read.entries.length === 0 && <li className="empty-state">{content.files.emptyFolder}</li>}
      {branch.read.truncated && <li className="hint">{content.files.truncated}</li>}
    </ul>
  )
}

interface ViewerProps {
  viewed: Viewed
  onClose(): void
}

/**
 * The opened file (R-WP-33): highlighted, left aligned, and closable — closing it returns to the tree of
 * the root it came from, so the way back is one named control and not a guess.
 */
function FileViewer({ viewed, onClose }: ViewerProps) {
  return (
    <section className="files-viewer" aria-label={content.files.viewer} data-testid="files-viewer">
      <header className="files-viewer-head">
        <span className="files-viewer-path" data-testid="files-viewer-path">
          {viewed.root} · /{viewed.path}
        </span>
        <button type="button" className="files-viewer-close" data-testid="files-viewer-close" onClick={onClose}>
          <Icon name="x-lg" />
          {content.files.backToTree}
        </button>
      </header>
      {viewed.file.binary ? (
        <p className="hint" data-testid="files-binary">
          {content.files.binary(viewed.file.size)}
        </p>
      ) : (
        <CodeView
          className="files-viewer-code"
          testId="files-text"
          language={languageOf(viewed.path)}
          code={viewed.file.text ?? ''}
        />
      )}
      {viewed.file.truncated && (
        <p className="hint" data-testid="files-viewer-truncated">
          {content.files.truncated}
        </p>
      )}
    </section>
  )
}

/**
 * The file area (R-WP-33, R-WP-14, UC-WP-60/61): the vertical tab at the right edge, and the basis'
 * `Drawer` lying over that edge instead of pushing a column into the row. The chat stays on the screen
 * beside it and stays typable in — which is why the drawer renders inline (`getContainer={false}`: header
 * and tree keep their place, and tab and panel share one top edge) and carries neither mask nor focus trap.
 *
 * The tab is always mounted, so it keeps its identity across an open and a close. The area goes away with
 * the drawer — `destroyOnHidden` of the basis, no guard of ours — which is how closing forgets the position
 * in it, with no reset state of its own (R-WP-38).
 */
export function ProjectFiles({ project, open, onToggle }: Props) {
  return (
    <>
      <Drawer
        // the tab already says what this is, so the panel gets its name without a second visible word
        aria-label={content.files.title}
        // not modal: no mask, no trap, the chat beside it stays reachable — the basis states `true` on its
        // own, so this is the one place that has to say otherwise
        aria-modal={false}
        classNames={{ root: 'files-drawer', wrapper: 'files-drawer-wrapper', body: 'files-drawer-body' }}
        closeIcon={<CloseOutlined data-testid="files-close" aria-label={content.files.collapseArea} />}
        destroyOnHidden
        getContainer={false}
        mask={false}
        // closing is the address stepping back: the area keeps no "open" of its own (R-WP-38)
        onClose={onToggle}
        open={open}
        placement="right"
        // the wide measure, clamped by the sheet while no file is open: `wide` is the area's own state and a
        // copy of it up here would outlive the close
        size="var(--files-wide)"
        // its layer is the line "file drawer" in the list over `.chat-workspace` (`styles.css`): under the
        // tab, above the content of the row, which carries no number of its own
        zIndex={2}
      >
        <FilesArea project={project} />
      </Drawer>
      <button
        type="button"
        className="files-tab"
        data-testid="files-open"
        data-open={open}
        aria-expanded={open}
        aria-label={open ? content.files.collapseArea : content.files.openArea}
        onClick={onToggle}
      >
        <Icon name={open ? 'chevron-right' : 'chevron-left'} />
        <span className="files-tab-label">{content.files.open}</span>
      </button>
    </>
  )
}

/**
 * The open panel (UC-WP-42): the two roots the project may write to as cards, their contents as a tree
 * that is read one level per click, and the opened file as a highlighted viewer. Nothing here can write,
 * upload or create, because the edge behind it has no such method. It is born with the address that names
 * it and dies with the one that does not, so its own state never has to be reset.
 */
function FilesArea({ project }: { project: ProjectDto }) {
  const [shownRoot, setShownRoot] = useState<FileRoot>('checkout')
  const [branches, setBranches] = useState<Record<string, Branch>>({})
  const [expanded, setExpanded] = useState<string[]>([])
  const [viewed, setViewed] = useState<Viewed | null>(null)
  /** A refused file read: a line where the viewer would have stood, no dialog, no retry. */
  const [fileError, setFileError] = useState<string | null>(null)

  const remember = useCallback((key: string, branch: Branch) => {
    setBranches(previous => ({ ...previous, [key]: branch }))
  }, [])

  /**
   * A read that only records its answer. It skips the `loading` entry because its one caller before an
   * answer exists is the mount below, and a level with no entry yet is already rendered as loading by
   * `FilesLevel` — anything a click asks for goes through `loadBranch`, which does record it.
   */
  const readBranch = useCallback(
    (root: FileRoot, path: string) => {
      const key = branchKey(root, path)
      readFiles(project.id, root, path)
        .then(read => remember(key, { status: 'ready', read }))
        // a rejected path is the server's own sentence, printed where the level would have been
        .catch(reason => remember(key, { status: 'error', message: errorMessage(reason) }))
    },
    [project.id, remember],
  )

  const loadBranch = useCallback(
    (root: FileRoot, path: string) => {
      remember(branchKey(root, path), { status: 'loading' })
      readBranch(root, path)
    },
    [readBranch, remember],
  )

  // the root level comes with the mount, not with a click: the panel is born together with its address
  useEffect(() => {
    readBranch('checkout', '')
  }, [readBranch])

  /** A root is asked for once and then kept: clicking back to it shows the tree that was already read. */
  const showRoot = useCallback(
    (next: FileRoot) => {
      setShownRoot(next)
      setExpanded([])
      setViewed(null)
      setFileError(null)
      if (branches[branchKey(next, '')]?.status !== 'ready') {
        loadBranch(next, '')
      }
    },
    [branches, loadBranch],
  )

  function toggleDirectory(root: FileRoot, path: string): void {
    const key = branchKey(root, path)
    if (expanded.includes(key)) {
      setExpanded(previous => previous.filter(item => item !== key))
      return
    }
    setExpanded(previous => [...previous, key])
    // a level is read once; a refused one is asked again, because the refusal is a line to read and not
    // a verdict the area has to keep
    if (branches[key]?.status !== 'ready') {
      loadBranch(root, path)
    }
  }

  function openFile(root: FileRoot, path: string): void {
    readFiles(project.id, root, path)
      .then(answer => {
        if (answer.kind === 'file') {
          setViewed({ root, path, file: answer })
          setFileError(null)
        }
      })
      .catch(reason => {
        setViewed(null)
        setFileError(errorMessage(reason))
      })
  }

  return (
    <aside
      className="files-panel"
      aria-label={content.files.title}
      data-testid="files-panel"
      data-wide={viewed !== null}
    >
      <header className="files-head">
        <div className="files-roots" role="group" aria-label={content.files.root}>
          {ROOTS.map(card => (
            <button
              key={card.value}
              type="button"
              className="files-root-card"
              data-testid={`files-root-${card.value}`}
              aria-pressed={shownRoot === card.value}
              onClick={() => showRoot(card.value)}
            >
              <span className="files-root-name">{card.label}</span>
              <span className="files-root-hint">{card.hint}</span>
            </button>
          ))}
        </div>
      </header>
      <div className="files-tree" data-testid="files-tree">
        <p className="files-path" data-testid="files-path">
          {shownRoot} · /
        </p>
        <FilesLevel
          root={shownRoot}
          path=""
          branches={branches}
          expanded={expanded}
          onToggle={toggleDirectory}
          onOpenFile={openFile}
        />
      </div>
      {fileError !== null && (
        <p className="files-error" role="alert" data-testid="files-file-error">
          {fileError}
        </p>
      )}
      {viewed !== null && <FileViewer viewed={viewed} onClose={() => setViewed(null)} />}
    </aside>
  )
}
