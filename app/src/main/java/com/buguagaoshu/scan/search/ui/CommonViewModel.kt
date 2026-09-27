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
        // 解释模式（默认）：帮助理解选中的概念/文字
        val PROMPT_EXPLAIN = """
            【角色设定】
            你是一位耐心、知识渊博的学习导师。用户会从资料里选中一段文字或一个概念来问你，请帮助用户真正理解它。

            请按以下方式回答：
            1. 先用一句话点明这段话/这个概念在讲什么；
            2. 用通俗的语言解释它的含义、为什么这样、怎么来的；
            3. 尽量结合例子或类比帮助理解，必要时补充相关背景或前置知识；
            4. 如果用户没有明确问题，就默认讲解选中内容的要点；
            5. 回答保持简洁清晰，先给结论，再展开细节。
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
        """.trimIndent()
    }

    private val _apiMap = mutableStateMapOf<String, ConfigData>()

    private val _apiName = MutableStateFlow("")
    val apiName = _apiName.asStateFlow()
    private var _apiId: String = ""

    // ai 服务商列表
    private val _aiServerBaseUrl = MutableStateFlow("https://api.deepseek.com/chat/completions")
    val aiServerBaseUrl = _aiServerBaseUrl.asStateFlow()

    // 存储 ai 服务商的 api key
    private val _apiKeyText = MutableStateFlow("")
    val apiKeyText = _apiKeyText.asStateFlow()

    // 模型名称
    private val  _modelName = MutableStateFlow("deepseek-reasoner")
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
