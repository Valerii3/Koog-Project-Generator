import type { ProviderOption } from '../types'
import { OptionCard } from './OptionCard'
import styles from './Section.module.css'

interface ProviderSectionProps {
  providers: ProviderOption[]
  selected: string
  onSelect: (id: string) => void
}

export function ProviderSection({ providers, selected, onSelect }: ProviderSectionProps) {
  return (
    <div className={styles.grid}>
      {providers.map(provider => (
        <OptionCard
          key={provider.id}
          label={provider.label}
          description={provider.envVar ? `${provider.description} (${provider.envVar})` : provider.description}
          selected={selected === provider.id}
          onClick={() => onSelect(provider.id)}
        />
      ))}
    </div>
  )
}
