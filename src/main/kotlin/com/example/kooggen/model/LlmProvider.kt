package com.example.kooggen.model

enum class LlmProvider(
    val id: String,
    val displayName: String,
    val description: String,
    val envVarName: String?,
    val modelExpression: String,
    val executorExpression: String,
    val importLines: List<String>,
    val executorSetupLines: List<String> = emptyList()
) {
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        description = "OpenAI models via API key.",
        envVarName = "OPENAI_API_KEY",
        modelExpression = "OpenAIModels.Chat.GPT4o",
        executorExpression = "simpleOpenAIExecutor(apiKey)",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.openai.OpenAIModels",
            "import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor"
        )
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic",
        description = "Claude models via Anthropic API key.",
        envVarName = "ANTHROPIC_API_KEY",
        modelExpression = "AnthropicModels.Opus_4_1",
        executorExpression = "simpleAnthropicExecutor(apiKey)",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.anthropic.AnthropicModels",
            "import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor"
        )
    ),
    GOOGLE(
        id = "google",
        displayName = "Google",
        description = "Gemini models via Google API key.",
        envVarName = "GOOGLE_API_KEY",
        modelExpression = "GoogleModels.Gemini2_5Pro",
        executorExpression = "simpleGoogleAIExecutor(apiKey)",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.google.GoogleModels",
            "import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor"
        )
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        description = "DeepSeek models via dedicated LLM client.",
        envVarName = "DEEPSEEK_API_KEY",
        modelExpression = "DeepSeekModels.DeepSeekChat",
        executorExpression = "MultiLLMPromptExecutor(deepSeekClient)",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient",
            "import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels",
            "import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor"
        ),
        executorSetupLines = listOf(
            "val deepSeekClient = DeepSeekLLMClient(apiKey)"
        )
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        description = "OpenRouter unified API access.",
        envVarName = "OPENROUTER_API_KEY",
        modelExpression = "OpenRouterModels.GPT4o",
        executorExpression = "simpleOpenRouterExecutor(apiKey)",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels",
            "import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor"
        )
    ),
    BEDROCK(
        id = "bedrock",
        displayName = "Bedrock",
        description = "Amazon Bedrock models via bearer-token executor.",
        envVarName = "BEDROCK_API_KEY",
        modelExpression = "BedrockModels.AnthropicClaude4_5Sonnet",
        executorExpression = "simpleBedrockExecutorWithBearerToken(apiKey)",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.bedrock.BedrockModels",
            "import ai.koog.prompt.executor.llms.all.simpleBedrockExecutorWithBearerToken"
        )
    ),
    MISTRAL(
        id = "mistral",
        displayName = "Mistral",
        description = "Mistral AI models via API key.",
        envVarName = "MISTRAL_API_KEY",
        modelExpression = "MistralAIModels.Chat.MistralMedium31",
        executorExpression = "simpleMistralAIExecutor(apiKey)",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.mistralai.MistralAIModels",
            "import ai.koog.prompt.executor.llms.all.simpleMistralAIExecutor"
        )
    ),
    OLLAMA(
        id = "ollama",
        displayName = "Ollama",
        description = "Local Ollama model, no API key required.",
        envVarName = null,
        modelExpression = "OllamaModels.Meta.LLAMA_3_2",
        executorExpression = "simpleOllamaAIExecutor()",
        importLines = listOf(
            "import ai.koog.prompt.executor.clients.ollama.OllamaModels",
            "import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor"
        )
    );

    val requiresApiKey: Boolean = envVarName != null
}
