package com.example.kooggen.backend.routes

import com.example.kooggen.backend.dto.GenerateRequest
import com.example.kooggen.model.AgentFeatureType
import com.example.kooggen.model.AgentPersistenceFeatureConfig
import com.example.kooggen.model.AgentTemplateType
import com.example.kooggen.model.ChatMemoryFeatureConfig
import com.example.kooggen.model.EventHandlerFeatureConfig
import com.example.kooggen.model.LlmProvider
import com.example.kooggen.model.PlannerType
import com.example.kooggen.model.ProjectFeatureSpec
import com.example.kooggen.model.ProjectSpec
import com.example.kooggen.model.ProjectToolingSpec
import com.example.kooggen.model.ToolCapability
import com.example.kooggen.model.TracingFeatureConfig
import com.example.kooggen.template.TemplateRegistry
import com.example.kooggen.zip.ZipProjectStreamWriter
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.generateRoute() {
    post("/generate") {
        val req = call.receive<GenerateRequest>()

        val (templateType, plannerType) = when (req.agentType) {
            "BASIC"          -> AgentTemplateType.BASIC          to null
            "FUNCTIONAL"     -> AgentTemplateType.FUNCTIONAL     to null
            "PLANNER_SIMPLE" -> AgentTemplateType.PLANNER        to PlannerType.SIMPLE_LLM
            "PLANNER_CRITIC" -> AgentTemplateType.PLANNER        to PlannerType.SIMPLE_LLM_WITH_CRITIC
            else -> {
                call.respond(HttpStatusCode.BadRequest, "Unknown agentType: ${req.agentType}")
                return@post
            }
        }

        val provider = runCatching { LlmProvider.valueOf(req.provider) }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, "Unknown provider: ${req.provider}")
            return@post
        }

        val toolCapabilities = req.tools.mapNotNull { name ->
            runCatching { ToolCapability.valueOf(name) }.getOrNull()
        }.toSet()

        val selectedFeatures = req.features.mapNotNull { name ->
            runCatching { AgentFeatureType.valueOf(name) }.getOrNull()
        }.toSet()

        val features = ProjectFeatureSpec(
            selected = selectedFeatures,
            eventHandler = EventHandlerFeatureConfig(enabled = AgentFeatureType.EVENT_HANDLER in selectedFeatures),
            chatMemory = ChatMemoryFeatureConfig(enabled = AgentFeatureType.CHAT_MEMORY in selectedFeatures),
            agentPersistence = AgentPersistenceFeatureConfig(enabled = AgentFeatureType.AGENT_PERSISTENCE in selectedFeatures),
            tracing = TracingFeatureConfig(enabled = AgentFeatureType.TRACING in selectedFeatures)
        )

        val spec = ProjectSpec.fromArtifact(
            artifact = req.artifact,
            templateType = templateType,
            llmProvider = provider,
            tooling = ProjectToolingSpec(selectedCapabilities = toolCapabilities),
            features = features,
            plannerType = plannerType
        )

        val files = TemplateRegistry().resolve(spec.templateType).render(spec)

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, spec.archiveFileName
            ).toString()
        )

        call.respondOutputStream(contentType = ContentType.Application.Zip) {
            ZipProjectStreamWriter.write(this, files)
        }
    }
}
