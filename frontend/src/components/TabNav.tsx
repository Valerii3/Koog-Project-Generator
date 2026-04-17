import styles from './TabNav.module.css'

export type TabId = 'agents' | 'providers' | 'tools' | 'features'

interface Tab {
  id: TabId
  label: string
  disabled?: boolean
}

interface TabNavProps {
  activeTab: TabId
  toolsDisabled: boolean
  featuresDisabled: boolean
  onTabChange: (tab: TabId) => void
}

export function TabNav({ activeTab, toolsDisabled, featuresDisabled, onTabChange }: TabNavProps) {
  const tabs: Tab[] = [
    { id: 'agents', label: 'Agents' },
    { id: 'providers', label: 'Providers' },
    { id: 'tools', label: 'Tools', disabled: toolsDisabled },
    { id: 'features', label: 'Features', disabled: featuresDisabled },
  ]

  return (
    <nav className={styles.nav} role="tablist">
      {tabs.map(tab => (
        <button
          key={tab.id}
          role="tab"
          aria-selected={activeTab === tab.id}
          aria-disabled={tab.disabled}
          className={[
            styles.tab,
            activeTab === tab.id ? styles.active : '',
            tab.disabled ? styles.disabledTab : '',
          ].join(' ')}
          onClick={() => !tab.disabled && onTabChange(tab.id)}
        >
          {tab.label}
          {tab.disabled && <span className={styles.disabledHint}> (N/A)</span>}
        </button>
      ))}
    </nav>
  )
}
