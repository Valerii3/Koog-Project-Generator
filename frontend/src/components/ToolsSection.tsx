import type { ToolOption } from '../types'
import { OptionCard } from './OptionCard'
import styles from './Section.module.css'

interface ToolsSectionProps {
  tools: ToolOption[]
  selected: string[]
  disabled: boolean
  onToggle: (id: string) => void
}

export function ToolsSection({ tools, selected, disabled, onToggle }: ToolsSectionProps) {
  if (disabled) {
    return (
      <div className={styles.disabledMessage}>
        Tools are not available for Planner agents.
      </div>
    )
  }

  return (
    <div className={styles.list}>
      {tools.map(tool => (
        <OptionCard
          key={tool.id}
          label={tool.label}
          description={tool.description}
          selected={selected.includes(tool.id)}
          layout="list"
          onClick={() => onToggle(tool.id)}
        />
      ))}
    </div>
  )
}
