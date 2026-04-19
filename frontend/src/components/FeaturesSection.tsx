import type { FeatureOption } from '../types'
import { OptionCard } from './OptionCard'
import styles from './Section.module.css'

interface FeaturesSectionProps {
  features: FeatureOption[]
  selected: string[]
  disabled: boolean
  onToggle: (id: string) => void
}

export function FeaturesSection({ features, selected, disabled, onToggle }: FeaturesSectionProps) {
  if (disabled) {
    return (
      <div className={styles.disabledMessage}>
        Features are only available for Basic agents.
      </div>
    )
  }

  return (
    <div className={styles.list}>
      {features.filter(f => f.implemented).map(feature => (
        <OptionCard
          key={feature.id}
          label={feature.label}
          description={feature.description}
          selected={selected.includes(feature.id)}
          layout="list"
          onClick={() => onToggle(feature.id)}
        />
      ))}
    </div>
  )
}
