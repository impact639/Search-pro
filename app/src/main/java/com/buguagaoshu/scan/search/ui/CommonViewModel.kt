package com.buguagaoshu.scan.search.ui

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.data.ConfigData
import com.buguagaoshu.scan.search.utils.PreferencesDataStoreUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class CommonViewModel @Inject constructor() : ViewModel() {
    companion object {
        // 默认服务器地址与模型名（新增配置时的初始值）
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/chat/completions"
        // 用 deepseek-chat（快速直出答案）而非 deepseek-reasoner（慢速推理模型）。
        // 本应用已不展示 reasoning_content，reasoner 会先内部生成大段推理再出答案，纯属白等。
        const val DEFAULT_MODEL_NAME = "deepseek-chat"

        // 解释模式（默认）：帮助理解选中的概念/文字
        val PROMPT_EXPLAIN = """
            【角色设定】
            你是一位耐心、知识渊博的学习导师。用户会从资料里选中一段文字或一个概念来问你，请帮助用户真正理解它，而不是只给一个干巴巴的答案。

            请只围绕用户给出的材料（选中的文字或本轮问题）展开，循序渐进地讲解：
            1. 先一句话点明这段内容在讲什么（结论先行）；
            2. 列出理解它所需的前置概念，若用户可能不熟悉，先用一两句补足；
            3. 分步骤拆解关键思路或推导过程，说明每一步为什么这样做；
            4. 点出常见难点或易混淆点，并用例子、类比帮助消化；
            5. 最后，另起一行先输出「【最终答案】」，紧接着用一句话给出最精简的结论，方便用户直接记住。
            如果涉及公式或推导，请用 LaTeX 语法书写：行内公式用 ${'$'}...${'$'} 包裹，单独成行的公式用 ${'$'}${'$'}...${'$'}${'$'} 包裹，界面会自动渲染成排版好的数学公式。
            如果用户没有明确问题，就默认讲解选中内容的要点；回答保持简洁，先讲清楚再追求完整。
        """.trimIndent()

        // 总结模式：基于当前「问题 + 回答」生成适合复习的总结
        val PROMPT_SUMMARIZE = """
            【角色设定】
            你是一位善于归纳总结的学习助手。用户会给你一份「问题 + 回答」内容，请基于它生成一份适合学习和复习的总结。

            请严格围绕用户给出的内容，按以下结构输出：
            1. 【关键知识点】：列出 3～5 个关键知识点，每个用一两句话说明；
            2. 【重要概念与关系】：点出核心概念及其相互关系；
            3. 【公式与符号】：若内容涉及数学公式、代码或计算机专业概念，保留准确的公式与符号，并说明适用条件；公式用 LaTeX 书写（行内 ${'$'}...${'$'}，块级 ${'$'}${'$'}...${'$'}${'$'}），代码用代码块；
            4. 【常见误区】：单独一段列出容易混淆或常犯错的地方；
            5. 最后另起一行输出「【最终答案】」，紧接着用一句话给出最精炼的总结。

            不要引申与当前内容无关的知识，不要编造内容中不存在的信息。
        """.trimIndent()

        // 出题模式：基于当前「问题 + 回答」出 3 道由浅入深的练习题，答案与解析默认隐藏
        val PROMPT_QUIZ = """
            【角色设定】
            你是一位出题老师。请基于用户给出的「问题 + 回答」内容，出 3 道由浅入深的练习题，帮助用户检验理解程度。

            请严格围绕当前知识点出题，按以下要求输出：
            1. 第 1 题考察基础理解，第 2 题考察应用，第 3 题稍有挑战（推理或变式）；
            2. 先只输出 3 道题目（依次编号 1、2、3），题目处不要写出答案或解析；
            3. 若涉及数学公式、代码或计算机专业概念，务必保证符号准确：公式用 LaTeX（行内 ${'$'}...${'$'}，块级 ${'$'}${'$'}...${'$'}${'$'}），代码用代码块；
            4. 3 道题目全部写完后，另起一行输出「【答案与解析】」，然后依次给出每道题的正确答案和逐步解析。

            不要出与当前内容无关的泛泛题目，不要编造内容中不存在的信息。
        """.trimIndent()

    }

    private val _apiMap = mutableStateMapOf<String, ConfigData>()

    private val _apiName = MutableStateFlow("")
    val apiName = _apiName.asStateFlow()
    private var _apiId: String = ""

    // ai 服务商列表
    private val _aiServerBaseUrl = MutableStateFlow(DEFAULT_BASE_URL)
    val aiServerBaseUrl = _aiServerBaseUrl.asStateFlow()

    // 存储 ai 服务商的 api key
    private val _apiKeyText = MutableStateFlow("")
    val apiKeyText = _apiKeyText.asStateFlow()

    // 模型名称
    private val  _modelName = MutableStateFlow(DEFAULT_MODEL_NAME)
    val modelName = _modelName.asStateFlow();

    // 存储需要提问的问题
    private val _questionText = MutableStateFlow("")
    val questionText = _questionText.asStateFlow()

    // 存储prompt（默认解释模式）
    private val _promptText = MutableStateFlow(PROMPT_EXPLAIN)
    val promptText = _promptText.asStateFlow()


    val apiMap: Map<String, ConfigData> get() = _apiMap

    // 向 Map 中添加元素的方法
    fun addToApiMap(key: String, value: ConfigData) {
        _apiMap[key] = value
    }

    fun addAllApiMap(map: Map<String, ConfigData>) {
        _apiMap.putAll(map)
    }

    // 从 Map 中移除元素
    fun removeFromApiMap(key: String) {
        _apiMap.remove(key)
    }

    // 更新配置文件
    fun updateConfig(context: Context) {
        viewModelScope.launch {
            val str = Json.encodeToString(apiMap)
            PreferencesDataStoreUtils.saveString(context, StaticVariableConfig.API_MAP, str)
        }
    }

    fun showUserSelectConfig(configData: ConfigData) {
        viewModelScope.launch {
            _apiId = configData.id
            _apiName.value = configData.name
            _aiServerBaseUrl.value = configData.api
            _modelName.value = configData.modelName
            _apiKeyText.value = configData.apiKey
            _promptText.value = configData.prompt
        }
    }


    fun updatePrompt(newPrompt: String) {
        viewModelScope.launch {
            _promptText.value = newPrompt
        }
    }

    // 新增配置前清空表单为默认值（名称/模型/Key 置空，地址恢复默认，提示词恢复解释模式）
    fun prepareNewConfig() {
        viewModelScope.launch {
            _apiName.value = ""
            _aiServerBaseUrl.value = DEFAULT_BASE_URL
            _modelName.value = ""
            _apiKeyText.value = ""
            _promptText.value = PROMPT_EXPLAIN
        }
    }


    fun updateModelName(newName: String) {
        viewModelScope.launch {
            _modelName.value = newName
        }
    }

    fun updateQuestionText(newText: String) {
        viewModelScope.launch {
            _questionText.value = newText
        }
    }

    // 清空「待发送输入」：仅在关闭悬浮窗时调用，不触碰历史回答等其他状态。
    // 放在 ViewModel 里而不是 Compose 的 remember 里，是因为 questionText 本来就是
    // ViewModel 作用域（随 MainActivity 存活），关闭浮窗不会销毁它，必须显式重置。
    fun clearQuestionText() {
        _questionText.value = ""
    }


    fun updateApiKeyText(newText: String) {
        viewModelScope.launch {
            _apiKeyText.value = newText
        }
    }


    fun updateApiName(newText: String) {
        viewModelScope.launch {
            _apiName.value = newText
        }
    }

    fun setBaseUrl(newUrl: String) {
        viewModelScope.launch {
            _aiServerBaseUrl.value = newUrl
        }
    }

    fun updateBaseUrl(newText: String) {
        viewModelScope.launch {
            _aiServerBaseUrl.value = newText
        }
    }
}
