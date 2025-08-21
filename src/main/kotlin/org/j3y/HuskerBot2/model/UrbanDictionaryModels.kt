package org.j3y.HuskerBot2.model

data class UrbanDictionaryResponse(
    val list: List<UrbanDefinition>
)

data class UrbanDefinition(
    val definition: String,
    val permalink: String,
    val thumbsUp: Int,
    val author: String,
    val word: String,
    val defid: Long,
    val currentVote: String?,
    val writtenOn: String,
    val example: String,
    val thumbsDown: Int
)

data class PaginatedUrbanResult(
    val definitions: List<UrbanDefinition>,
    val currentPage: Int,
    val totalPages: Int,
    val searchTerm: String
)