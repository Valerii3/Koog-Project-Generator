import { useEffect, useState } from 'react'
import type { PreviewFile } from '../types'
import { FileTree } from './FileTree'
import styles from './PreviewSection.module.css'

interface PreviewSectionProps {
  files: PreviewFile[] | null
  loading: boolean
}

function firstKtFile(files: PreviewFile[]): string | null {
  const main = files.find(f => f.path.endsWith('Main.kt'))
  if (main) return main.path
  const kt = files.find(f => f.path.endsWith('.kt'))
  return kt?.path ?? files[0]?.path ?? null
}

export function PreviewSection({ files, loading }: PreviewSectionProps) {
  const [selectedPath, setSelectedPath] = useState<string | null>(null)

  useEffect(() => {
    if (files && files.length > 0) {
      setSelectedPath(firstKtFile(files))
    }
  }, [files])

  const selectedFile = files?.find(f => f.path === selectedPath)

  if (loading) {
    return (
      <div className={styles.placeholder}>
        <div className={styles.spinner} />
        <span>Generating preview…</span>
      </div>
    )
  }

  if (!files) {
    return (
      <div className={styles.placeholder}>
        <span className={styles.placeholderIcon}>◆</span>
        <span>Switch to Preview to see the generated project</span>
      </div>
    )
  }

  return (
    <div className={styles.pane}>
      <FileTree
        files={files}
        selectedPath={selectedPath}
        onSelect={setSelectedPath}
      />
      <div className={styles.content}>
        {selectedFile ? (
          <>
            <div className={styles.pathBar}>
              {selectedFile.path}
            </div>
            <pre className={styles.code}><code>{selectedFile.content}</code></pre>
          </>
        ) : (
          <div className={styles.noFile}>Select a file to view its content</div>
        )}
      </div>
    </div>
  )
}
