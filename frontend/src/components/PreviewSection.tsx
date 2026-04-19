import { useEffect, useMemo, useState } from 'react'
import hljs from 'highlight.js/lib/core'
import kotlin from 'highlight.js/lib/languages/kotlin'
import 'highlight.js/styles/github.css'
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

hljs.registerLanguage('kotlin', kotlin)

function isKotlinFile(path: string): boolean {
  return path.endsWith('.kt') || path.endsWith('.kts')
}

export function PreviewSection({ files, loading }: PreviewSectionProps) {
  const [selectedPath, setSelectedPath] = useState<string | null>(null)

  useEffect(() => {
    if (files && files.length > 0) {
      setSelectedPath(firstKtFile(files))
    }
  }, [files])

  const selectedFile = files?.find(f => f.path === selectedPath)
  const highlightedKotlin = useMemo(() => {
    if (!selectedFile || !isKotlinFile(selectedFile.path)) {
      return null
    }
    return hljs.highlight(selectedFile.content, { language: 'kotlin' }).value
  }, [selectedFile])

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
            {highlightedKotlin ? (
              <pre className={styles.code}>
                <code
                  className={`hljs language-kotlin ${styles.highlightedCode}`}
                  dangerouslySetInnerHTML={{ __html: highlightedKotlin }}
                />
              </pre>
            ) : (
              <pre className={styles.code}>
                <code className={styles.plainCode}>{selectedFile.content}</code>
              </pre>
            )}
          </>
        ) : (
          <div className={styles.noFile}>Select a file to view its content</div>
        )}
      </div>
    </div>
  )
}
