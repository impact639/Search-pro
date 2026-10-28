package com.buguagaoshu.scan.search.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class ChatResponse(
    val usage: Usage = Usage(0),
    val id: String?,
    val choices: List<Choice>
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class Usage(
    val completion_tokens: Int?
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class PromptTokensDetails(
    val cached_tokens: Int?
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class Choice(
    val finish_reason: String? = null,
    val index: Int?,
    val message: Message? = null,
    val delta: DeltaInfo? = null
)

// 注意：function_call / refusal / tool_calls / logprobs 等字段在不同服务商里可能是
// null、对象或数组，若声明为 String? 会因类型不匹配抛异常导致整块被丢弃。
// 已通过 @JsonIgnoreUnknownKeys 直接忽略这些未使用的字段，只保留真正需要的 content / reasoning_content。
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class DeltaInfo(
    // 思维链信息（不再展示，仅保留解析）
    val reasoning_content : String? = null,
    val content : String = "",
    val role: String? = null
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class Message(
    val role: String?,
    val content: String?,
    val reasoning_content: String? = null
)