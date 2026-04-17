export interface AgentTypeOption {
  id: string
  label: string
  description: string
}

export interface ProviderOption {
  id: string
  label: string
  description: string
  envVar: string | null
}

export interface ToolOption {
  id: string
  label: string
  description: string
}

export interface FeatureOption {
  id: string
  label: string
  description: string
  implemented: boolean
}

export interface OptionsResponse {
  agentTypes: AgentTypeOption[]
  providers: ProviderOption[]
  tools: ToolOption[]
  features: FeatureOption[]
}

export interface GenerateRequest {
  artifact: string
  agentType: string
  provider: string
  tools: string[]
  features: string[]
}
