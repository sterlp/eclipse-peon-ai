export type ViewName = 'projects' | 'chat' | 'settings'

const TABS: { id: ViewName; label: string }[] = [
  { id: 'projects', label: 'Projekte' },
  { id: 'chat', label: 'Chat' },
  { id: 'settings', label: 'Einstellungen' },
]

interface Props {
  active: ViewName
  onSelect: (view: ViewName) => void
}

export function ViewTabs({ active, onSelect }: Props) {
  return (
    <nav className="tabs" role="tablist" aria-label="Ansichten">
      {TABS.map(tab => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={active === tab.id}
          className={active === tab.id ? 'tab active' : 'tab'}
          onClick={() => onSelect(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </nav>
  )
}
