package com.example.hello

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools

private val apiKey: String = System.getenv("OPENAI_API_KEY")
    ?: error("Environment variable OPENAI_API_KEY is not set.")

fun createBuiltInToolRegistry(): ToolRegistry = ToolRegistry {
    tool(SayToUser)
    tool(AskUser)
    tool(ExitTool)
    tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
    tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
    tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
}

@LLMDescription("User-defined annotation-based tools")
class UserToolSet : ToolSet {
    @Tool("sample_tool")
    @LLMDescription("Sample tool description")
    fun sampleTool(
        @LLMDescription("Sample tool input")
        input: String
    ): String {
        TODO("Not yet implemented")
    }
}

fun createUserToolRegistry(): ToolRegistry = ToolRegistry {
    tools(UserToolSet().asTools())
}

fun main() = runBlocking {
    val toolRegistry = createBuiltInToolRegistry() + createUserToolRegistry()

    val agent = AIAgent<String, String>(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant",
        temperature = 0.7,
        maxIterations = 10,
        toolRegistry = toolRegistry,
    )

    val result = agent.run("Hello! Introduce yourself in one sentence.")
    println(result)
}
