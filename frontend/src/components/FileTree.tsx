import { useState } from 'react'
import type { PreviewFile } from '../types'
import styles from './FileTree.module.css'
import kotlinIcon from '../assets/kotlin.svg'
import fileIcon from '../assets/file.svg'
import folderIcon from '../assets/folder.svg'

interface TreeNode {
  name: string
  path: string
  isFile: boolean
  children: TreeNode[]
}

function buildTree(files: PreviewFile[]): TreeNode[] {
  const roots: TreeNode[] = []

  for (const file of files) {
    const parts = file.path.split('/')
    let nodes = roots

    for (let i = 0; i < parts.length; i++) {
      const name = parts[i]
      const fullPath = parts.slice(0, i + 1).join('/')
      const isFile = i === parts.length - 1

      let node = nodes.find(n => n.name === name)
      if (!node) {
        node = { name, path: fullPath, isFile, children: [] }
        nodes.push(node)
      }

      if (!isFile) nodes = node.children
    }
  }

  return roots
}

function resolveFileIcon(name: string): string {
  if (name.endsWith('.kt')) return kotlinIcon
  return fileIcon
}

interface NodeViewProps {
  node: TreeNode
  selectedPath: string | null
  onSelect: (path: string) => void
  depth: number
}

function NodeView({ node, selectedPath, onSelect, depth }: NodeViewProps) {
  const [expanded, setExpanded] = useState(true)
  const indent = depth * 14

  if (node.isFile) {
    return (
      <div
        className={[
          styles.row,
          styles.file,
          node.name.endsWith('.kt') ? styles.kotlinFile : '',
          selectedPath === node.path ? styles.selected : '',
        ].join(' ')}
        style={{ paddingLeft: `${12 + indent}px` }}
        onClick={() => onSelect(node.path)}
      >
        <img src={resolveFileIcon(node.name)} className={styles.icon} alt="" />
        <span className={styles.name}>{node.name}</span>
      </div>
    )
  }

  return (
    <div>
      <div
        className={styles.row}
        style={{ paddingLeft: `${12 + indent}px` }}
        onClick={() => setExpanded(e => !e)}
      >
        <span className={styles.arrow}>{expanded ? '▾' : '▸'}</span>
        <img src={folderIcon} className={styles.icon} alt="" />
        <span className={styles.name}>{node.name}</span>
      </div>
      {expanded && node.children.map(child => (
        <NodeView
          key={child.path}
          node={child}
          selectedPath={selectedPath}
          onSelect={onSelect}
          depth={depth + 1}
        />
      ))}
    </div>
  )
}

interface FileTreeProps {
  files: PreviewFile[]
  selectedPath: string | null
  onSelect: (path: string) => void
}

export function FileTree({ files, selectedPath, onSelect }: FileTreeProps) {
  const tree = buildTree(files)
  return (
    <div className={styles.tree}>
      {tree.map(node => (
        <NodeView
          key={node.path}
          node={node}
          selectedPath={selectedPath}
          onSelect={onSelect}
          depth={0}
        />
      ))}
    </div>
  )
}
