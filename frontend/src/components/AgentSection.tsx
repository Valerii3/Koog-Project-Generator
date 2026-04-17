import type { AgentTypeOption } from '../types'
import { OptionCard } from './OptionCard'
import styles from './Section.module.css'

interface AgentSectionProps {
  agents: AgentTypeOption[]
  selected: string
  onSelect: (id: string) => void
}

export function AgentSection({ agents, selected, onSelect }: AgentSectionProps) {
  return (
    <div className={styles.grid}>
      {agents.map(agent => (
        <OptionCard
          key={agent.id}
          label={agent.label}
          description={agent.description}
          selected={selected === agent.id}
          onClick={() => onSelect(agent.id)}
        />
      ))}
    </div>
  )
}
