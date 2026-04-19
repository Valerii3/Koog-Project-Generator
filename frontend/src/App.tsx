import { useEffect, useMemo, useState } from 'react'
import type { OptionsResponse, PreviewFile } from './types'
import { fetchOptions, generateProject, previewProject, triggerDownload } from './api'
import { ArtifactBar } from './components/ArtifactBar'
import { TabNav } from './components/TabNav'
import type { TabId } from './components/TabNav'
import { AgentSection } from './components/AgentSection'
import { ProviderSection } from './components/ProviderSection'
import { ToolsSection } from './components/ToolsSection'
import { FeaturesSection } from './components/FeaturesSection'
import { PreviewSection } from './components/PreviewSection'
import './App.css'

function projectName(artifact: string): string {
  const parts = artifact.split('.')
  return parts[parts.length - 1] || 'hello'
}

const DEFAULT_ARTIFACT = 'com.example.hello'
const DEFAULT_AGENT_TYPE = 'BASIC'
const DEFAULT_PROVIDER = 'OPENAI'

const AGENT_ID_TO_SLUG: Record<string, string> = {
  BASIC: 'basic',
  GRAPH: 'graph-based',
  FUNCTIONAL: 'functional',
  PLANNER_SIMPLE: 'planner',
  PLANNER_CRITIC: 'critic-planner',
}

const AGENT_SLUG_TO_ID: Record<string, string> = {
  basic: 'BASIC',
  'graph-based': 'GRAPH',
  graph: 'GRAPH',
  functional: 'FUNCTIONAL',
  planner: 'PLANNER_SIMPLE',
  'simple-planner': 'PLANNER_SIMPLE',
  'critic-planner': 'PLANNER_CRITIC',
  'planner-critic': 'PLANNER_CRITIC',
}

const PROVIDER_ID_TO_SLUG: Record<string, string> = {
  OPENAI: 'openai',
  ANTHROPIC: 'anthropic',
  GOOGLE: 'google',
  DEEPSEEK: 'deepseek',
  OPENROUTER: 'openrouter',
  BEDROCK: 'bedrock',
  MISTRAL: 'mistral',
  OLLAMA: 'llama',
}

const PROVIDER_SLUG_TO_ID: Record<string, string> = {
  openai: 'OPENAI',
  anthropic: 'ANTHROPIC',
  google: 'GOOGLE',
  deepseek: 'DEEPSEEK',
  openrouter: 'OPENROUTER',
  bedrock: 'BEDROCK',
  mistral: 'MISTRAL',
  llama: 'OLLAMA',
  ollama: 'OLLAMA',
}

const TOOL_ID_TO_SLUG: Record<string, string> = {
  BUILT_IN: 'built-in',
  ANNOTATION_BASED: 'annotation-based',
  AGENT_AS_TOOL: 'agent-as-tool',
  MCP: 'mcp',
}

const TOOL_SLUG_TO_ID: Record<string, string> = {
  'built-in': 'BUILT_IN',
  builtin: 'BUILT_IN',
  'annotation-based': 'ANNOTATION_BASED',
  annotation: 'ANNOTATION_BASED',
  'agent-as-tool': 'AGENT_AS_TOOL',
  agent: 'AGENT_AS_TOOL',
  mcp: 'MCP',
}

const FEATURE_ID_TO_SLUG: Record<string, string> = {
  EVENT_HANDLER: 'event-handler',
  CHAT_MEMORY: 'chat-memory',
  AGENT_PERSISTENCE: 'agent-persistence',
  TRACING: 'tracing',
  LONG_TERM_MEMORY: 'long-term-memory',
  OPEN_TELEMETRY_DATADOG: 'otel-datadog',
  OPEN_TELEMETRY_LANGFUSE: 'otel-langfuse',
  OPEN_TELEMETRY_WEAVE: 'otel-weave',
}

const FEATURE_SLUG_TO_ID: Record<string, string> = {
  'event-handler': 'EVENT_HANDLER',
  'chat-memory': 'CHAT_MEMORY',
  'agent-persistence': 'AGENT_PERSISTENCE',
  tracing: 'TRACING',
  'long-term-memory': 'LONG_TERM_MEMORY',
  'otel-datadog': 'OPEN_TELEMETRY_DATADOG',
  'otel-langfuse': 'OPEN_TELEMETRY_LANGFUSE',
  'otel-weave': 'OPEN_TELEMETRY_WEAVE',
}

interface UrlConfig {
  artifact: string
  agentType: string
  provider: string
  tools: string[]
  features: string[]
}

function parseCsv(value: string | null): string[] {
  if (!value) return []
  return value
    .split(',')
    .map(v => v.trim().toLowerCase())
    .filter(Boolean)
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values))
}

function isPlannerAgent(agentType: string): boolean {
  return agentType === 'PLANNER_SIMPLE' || agentType === 'PLANNER_CRITIC'
}

function normalizeSelections(agentType: string, tools: string[], features: string[]): Pick<UrlConfig, 'tools' | 'features'> {
  const normalizedTools = isPlannerAgent(agentType) ? [] : tools
  const normalizedFeatures = agentType === 'BASIC' ? features : []
  return { tools: normalizedTools, features: normalizedFeatures }
}

function readUrlConfig(): UrlConfig {
  if (typeof window === 'undefined') {
    return {
      artifact: DEFAULT_ARTIFACT,
      agentType: DEFAULT_AGENT_TYPE,
      provider: DEFAULT_PROVIDER,
      tools: [],
      features: [],
    }
  }

  const params = new URLSearchParams(window.location.search)
  const artifact = params.get('artifact')?.trim() || DEFAULT_ARTIFACT
  const agentSlug = params.get('agent')?.trim().toLowerCase() || ''
  const providerSlug = (params.get('llm-provider') ?? params.get('provider') ?? '').trim().toLowerCase()

  const agentType = AGENT_SLUG_TO_ID[agentSlug] ?? DEFAULT_AGENT_TYPE
  const provider = PROVIDER_SLUG_TO_ID[providerSlug] ?? DEFAULT_PROVIDER
  const tools = unique(
    parseCsv(params.get('tools'))
      .map(slug => TOOL_SLUG_TO_ID[slug])
      .filter((id): id is string => Boolean(id))
  )
  const features = unique(
    parseCsv(params.get('features'))
      .map(slug => FEATURE_SLUG_TO_ID[slug])
      .filter((id): id is string => Boolean(id))
  )

  const normalized = normalizeSelections(agentType, tools, features)
  return { artifact, agentType, provider, tools: normalized.tools, features: normalized.features }
}

function writeUrlConfig(config: UrlConfig): void {
  if (typeof window === 'undefined') return

  const params = new URLSearchParams(window.location.search)
  params.set('artifact', config.artifact)
  params.set('agent', AGENT_ID_TO_SLUG[config.agentType] ?? config.agentType.toLowerCase())
  params.set('llm-provider', PROVIDER_ID_TO_SLUG[config.provider] ?? config.provider.toLowerCase())
  params.delete('provider')

  if (config.tools.length > 0) {
    params.set('tools', config.tools.map(id => TOOL_ID_TO_SLUG[id] ?? id.toLowerCase()).join(','))
  } else {
    params.delete('tools')
  }

  if (config.features.length > 0) {
    params.set('features', config.features.map(id => FEATURE_ID_TO_SLUG[id] ?? id.toLowerCase()).join(','))
  } else {
    params.delete('features')
  }

  const search = params.toString()
  const url = search
    ? `${window.location.pathname}?${search}${window.location.hash}`
    : `${window.location.pathname}${window.location.hash}`
  window.history.replaceState(null, '', url)
}

export default function App() {
  const initialConfig = useMemo(() => readUrlConfig(), [])
  const [options, setOptions] = useState<OptionsResponse | null>(null)
  const [artifact, setArtifact] = useState(initialConfig.artifact)
  const [agentType, setAgentType] = useState(initialConfig.agentType)
  const [provider, setProvider] = useState(initialConfig.provider)
  const [tools, setTools] = useState<string[]>(initialConfig.tools)
  const [features, setFeatures] = useState<string[]>(initialConfig.features)
  const [activeTab, setActiveTab] = useState<TabId>('agents')
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [previewFiles, setPreviewFiles] = useState<PreviewFile[] | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)

  useEffect(() => {
    fetchOptions()
      .then(setOptions)
      .catch(e => setError(String(e)))
  }, [])

  useEffect(() => {
    if (!options) return
    setAgentType(prev => options.agentTypes.some(a => a.id === prev) ? prev : (options.agentTypes[0]?.id ?? DEFAULT_AGENT_TYPE))
    setProvider(prev => options.providers.some(p => p.id === prev) ? prev : (options.providers[0]?.id ?? DEFAULT_PROVIDER))
    setTools(prev => prev.filter(id => options.tools.some(t => t.id === id)))
    setFeatures(prev => prev.filter(id => options.features.some(f => f.id === id && f.implemented)))
  }, [options])

  const isPlanner = isPlannerAgent(agentType)
  const isBasic = agentType === 'BASIC'
  const toolsDisabled = isPlanner
  const featuresDisabled = !isBasic
  const effectiveTools = useMemo(() => (toolsDisabled ? [] : tools), [toolsDisabled, tools])
  const effectiveFeatures = useMemo(() => (featuresDisabled ? [] : features), [featuresDisabled, features])

  useEffect(() => {
    writeUrlConfig({
      artifact,
      agentType,
      provider,
      tools: effectiveTools,
      features: effectiveFeatures,
    })
  }, [artifact, agentType, provider, effectiveTools, effectiveFeatures])

  const handleAgentSelect = (id: string) => {
    setAgentType(id)
    const nowPlanner = id === 'PLANNER_SIMPLE' || id === 'PLANNER_CRITIC'
    const nowBasic = id === 'BASIC'
    if (nowPlanner) setTools([])
    if (!nowBasic) setFeatures([])
    setPreviewFiles(null)
  }

  const handleTabChange = async (tab: TabId) => {
    setActiveTab(tab)
    if (tab === 'preview') {
      setPreviewLoading(true)
      setPreviewFiles(null)
      setError(null)
      try {
        const res = await previewProject({
          artifact,
          agentType,
          provider,
          tools: effectiveTools,
          features: effectiveFeatures,
        })
        setPreviewFiles(res.files)
      } catch (e) {
        setError(String(e))
      } finally {
        setPreviewLoading(false)
      }
    }
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
        tools: effectiveTools,
        features: effectiveFeatures,
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
              onTabChange={handleTabChange}
            />

            {activeTab !== 'preview' && (
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
            )}
            {activeTab === 'preview' && (
              <PreviewSection files={previewFiles} loading={previewLoading} />
            )}
          </div>
        )}
      </main>
    </div>
  )
}
