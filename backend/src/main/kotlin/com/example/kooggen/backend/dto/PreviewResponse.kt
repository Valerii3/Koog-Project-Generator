package com.example.kooggen.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class PreviewFileDto(val path: String, val content: String)

@Serializable
data class PreviewResponse(val files: List<PreviewFileDto>)
