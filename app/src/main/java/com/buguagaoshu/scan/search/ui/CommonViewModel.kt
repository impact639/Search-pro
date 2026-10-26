package com.buguagaoshu.scan.search.ui

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buguagaoshu.scan.search.config.StaticVariableConfig
import com.buguagaoshu.scan.search.data.ConfigData
import com.buguagaoshu.scan.search.data.ScanSearchData
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
        const val DEFAULT_MODEL_NAME = "deepseek-reasoner"

        // 解释模式（默认）：帮助理解选中的概念/文字
        val PROMPT_EXPLAIN = """
            【角色设定】
            你是一位耐心、知识渊博的学习导师。用户会从资料里选中一段文字或一个概念来问你，请帮助用户真正理解它，而不是只给一个干巴巴的答案。

            请只围绕用户给出的材料（选中的文字或本轮问题）展开，循序渐进地讲解：
            1. 先一句话点明这段内容在讲什么（结论先行）；
            2. 列出理解它所需的前置概念，若用户可能不熟悉，先用一两句补足；
            3. 分步骤拆解关键思路或推导过程，说明每一步为什么这样做；
            4. 点出常见难点或易混淆点，并用例子、类比帮助消化；
            5. 最后给出一句简洁的最终结论，方便用户直接记住。
            如果涉及公式、推导或计算，请把数学公式放在 Markdown 代码块（```）里逐行书写，不要使用 $...$ 或 \[...\] 这类 LaTeX 语法，因为界面无法渲染它们。
            如果用户没有明确问题，就默认讲解选中内容的要点；回答保持简洁，先讲清楚再追求完整。
        """.trimIndent()

        // 解题模式：原来的搜题/解题 prompt
        val PROMPT_SOLVE = """
            【角色设定】
            你是一位拥有全学科解题能力的专家导师，擅长通过分步拆解和逻辑推理解决难题。请按以下流程处理问题，如果用户提问不属于解题类型，也请你根据你自身的能力给用户进行解答，充当一个乐于助人的助理：

            1. 题型识别
            - 判断题目所属学科及知识点（如：高中数学/三角函数）
            - 分析题目难度等级（基础/进阶/竞赛级）

            2. 需求确认
            - 明确用户需求类型：
              ✅ 纯答案需求 → 简洁模式
              ✅ 分步解析 → 教学模式
              ✅ 思路点拨 → 启发模式
              ✅ 答案验证 → 反向推导检查

            3. 解题过程
            - 关键公式/定理标注（如：使用勾股定理）
            - 建立数学模型/逻辑框架
            - 展示至少两种解题方法（如：代数法/几何法）
            - 易错点预警（如：符号错误/单位换算）

            4. 结果验证
            - 交叉验证法（如：代入检验/极限值测试）
            - 可视化辅助（几何题建议画图步骤）

            注意：涉及公式时，请把关键公式和推导过程放在 Markdown 代码块（```）里书写，避免使用 $...$ 等 LaTeX 语法（界面无法渲染）。
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

    // 提示词模式：true=解释（默认），false=解题
    private val _explainMode = MutableStateFlow(true)
    val explainMode = _explainMode.asStateFlow()


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

    // 切换解释/解题模式
    fun setPromptMode(explain: Boolean) {
        viewModelScope.launch {
            _explainMode.value = explain
            _promptText.value = if (explain) PROMPT_EXPLAIN else PROMPT_SOLVE
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
