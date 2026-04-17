import { useEffect, useState } from 'react'
import type { OptionsResponse } from './types'
import { fetchOptions, generateProject, triggerDownload } from './api'
import { ArtifactBar } from './components/ArtifactBar'
import { TabNav } from './components/TabNav'
import type { TabId } from './components/TabNav'
import { AgentSection } from './components/AgentSection'
import { ProviderSection } from './components/ProviderSection'
import { ToolsSection } from './components/ToolsSection'
import { FeaturesSection } from './components/FeaturesSection'
import './App.css'

function projectName(artifact: string): string {
  const parts = artifact.split('.')
  return parts[parts.length - 1] || 'hello'
}

export default function App() {
  const [options, setOptions] = useState<OptionsResponse | null>(null)
  const [artifact, setArtifact] = useState('com.example.hello')
  const [agentType, setAgentType] = useState('BASIC')
  const [provider, setProvider] = useState('OPENAI')
  const [tools, setTools] = useState<string[]>([])
  const [features, setFeatures] = useState<string[]>([])
  const [activeTab, setActiveTab] = useState<TabId>('agents')
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchOptions()
      .then(setOptions)
      .catch(e => setError(String(e)))
  }, [])

  const isPlanner = agentType === 'PLANNER_SIMPLE' || agentType === 'PLANNER_CRITIC'
  const isBasic = agentType === 'BASIC'
  const toolsDisabled = isPlanner
  const featuresDisabled = !isBasic

  const handleAgentSelect = (id: string) => {
    setAgentType(id)
    const nowPlanner = id === 'PLANNER_SIMPLE' || id === 'PLANNER_CRITIC'
    const nowBasic = id === 'BASIC'
    if (nowPlanner) setTools([])
    if (!nowBasic) setFeatures([])
  }

  const toggleTool = (id: string) =>
    setTools(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])

  const toggleFeature = (id: string) =>
    setFeatures(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])

  const handleDownload = async () => {
    setDownloading(true)
    setError(null)
    try {
      const blob = await generateProject({
        artifact,
        agentType,
        provider,
        tools: toolsDisabled ? [] : tools,
        features: featuresDisabled ? [] : features,
      })
      triggerDownload(blob, projectName(artifact) + '.zip')
    } catch (e) {
      setError(String(e))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <div className="app">
      <header className="header">
        <div className="header-inner">
          <div className="logo">
            <span className="logo-icon">◆</span>
            <span className="logo-text">Koog Starter</span>
          </div>
          <ArtifactBar
            artifact={artifact}
            onArtifactChange={setArtifact}
            onDownload={handleDownload}
            downloading={downloading}
          />
        </div>
      </header>

      <main className="main">
        <div className="page-title">
          <h1>New Koog Project</h1>
          <p>Generate a Koog AI agent starter project with your chosen configuration.</p>
        </div>

        {error && (
          <div className="error-banner">{error}</div>
        )}

        {!options ? (
          <div className="loading">Loading options…</div>
        ) : (
          <div className="wizard">
            <TabNav
              activeTab={activeTab}
              toolsDisabled={toolsDisabled}
              featuresDisabled={featuresDisabled}
              onTabChange={setActiveTab}
            />

            <div className="tab-content">
              {activeTab === 'agents' && (
                <AgentSection
                  agents={options.agentTypes}
                  selected={agentType}
                  onSelect={handleAgentSelect}
                />
              )}
              {activeTab === 'providers' && (
                <ProviderSection
                  providers={options.providers}
                  selected={provider}
                  onSelect={setProvider}
                />
              )}
              {activeTab === 'tools' && (
                <ToolsSection
                  tools={options.tools}
                  selected={tools}
                  disabled={toolsDisabled}
                  onToggle={toggleTool}
                />
              )}
              {activeTab === 'features' && (
                <FeaturesSection
                  features={options.features}
                  selected={features}
                  disabled={featuresDisabled}
                  onToggle={toggleFeature}
                />
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
