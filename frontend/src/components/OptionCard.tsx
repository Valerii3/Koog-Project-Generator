import styles from './OptionCard.module.css'

interface OptionCardProps {
  label: string
  description: string
  selected: boolean
  disabled?: boolean
  layout?: 'grid' | 'list'
  onClick: () => void
}

export function OptionCard({
  label,
  description,
  selected,
  disabled,
  layout = 'grid',
  onClick,
}: OptionCardProps) {
  return (
    <div
      className={[
        styles.card,
        layout === 'list' ? styles.listCard : '',
        selected ? styles.selected : '',
        disabled ? styles.disabled : '',
      ].join(' ')}
      onClick={disabled ? undefined : onClick}
      role="button"
      aria-pressed={selected}
    >
      <div className={styles.label}>{label}</div>
      <div className={styles.description}>{description}</div>
    </div>
  )
}
