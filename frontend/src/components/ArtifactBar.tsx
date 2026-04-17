import { useState } from 'react'
import styles from './ArtifactBar.module.css'

interface ArtifactBarProps {
  artifact: string
  onArtifactChange: (value: string) => void
  onDownload: () => void
  downloading: boolean
}

function projectNameFromArtifact(artifact: string): string {
  const parts = artifact.split('.')
  return parts[parts.length - 1] || 'hello'
}

export function ArtifactBar({ artifact, onArtifactChange, onDownload, downloading }: ArtifactBarProps) {
  const [inputValue, setInputValue] = useState(artifact)

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setInputValue(val)
    onArtifactChange(val.trim() || 'com.example.hello')
  }

  const zipName = projectNameFromArtifact(artifact) + '.zip'

  return (
    <div className={styles.bar}>
      <div className={styles.fieldGroup}>
        <label className={styles.fieldLabel}>Project artifact</label>
        <input
          className={styles.input}
          type="text"
          value={inputValue}
          onChange={handleChange}
          placeholder="com.example.hello"
          aria-label="Project artifact"
        />
      </div>

      <button
        className={styles.downloadBtn}
        onClick={onDownload}
        disabled={downloading}
      >
        {downloading ? 'Generating…' : (
          <>
            <span>Download</span>
            <span className={styles.zipName}>{zipName}</span>
          </>
        )}
      </button>
    </div>
  )
}
