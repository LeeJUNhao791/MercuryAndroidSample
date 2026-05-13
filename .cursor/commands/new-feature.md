# 新功能开发

## 使用场景
需要创建一个新的功能模块时运行此 Prompt。

**触发词**：创建新功能、开发新模块、增加 XX 功能、新功能开发

## 输入参数
- `$FEATURE_NAME`：功能名（PascalCase，如 `ProductDetail`、`Search`、`Checkout`）
- `$PACKAGE`：包路径（如 `com.example.app.feature.product`）

## Prompt 正文

---

根据以下参数创建新的功能模块：

- 功能名：`$FEATURE_NAME`
- 包路径：`$PACKAGE`

## 步骤

### 1. 创建目录结构

```
feature/$FEATURE_NAME/
├── ui/
│   ├── ${FEATURE_NAME}Screen.kt         # Compose 界面
│   ├── ${FEATURE_NAME}ViewModel.kt      # ViewModel
│   └── state/
│       ├── ${FEATURE_NAME}UiState.kt    # 状态 data class
│       ├── ${FEATURE_NAME}Intent.kt     # 用户意图 sealed interface
│       └── ${FEATURE_NAME}Effect.kt     # 一次性事件（导航、Toast）
├── domain/
│   ├── ${FEATURE_NAME}Repository.kt     # Repository 接口
│   └── usecase/
│       ├── Get${FEATURE_NAME}UseCase.kt
│       └── Save${FEATURE_NAME}UseCase.kt
└── data/
    ├── ${FEATURE_NAME}RepositoryImpl.kt # Repository 实现
    ├── remote/
    │   └── ${FEATURE_NAME}Api.kt         # Retrofit API 接口
    └── dto/
        └── ${FEATURE_NAME}Dto.kt        # 数据传输对象
```

### 2. 编码规范（必须遵守）

**ViewModel 规范：**
```kotlin
@HiltViewModel
class ${FEATURE_NAME}ViewModel @Inject constructor(
    private val getDataUseCase: Get${FEATURE_NAME}UseCase,
    private val saveDataUseCase: Save${FEATURE_NAME}UseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(${FEATURE_NAME}UiState())
    val uiState: StateFlow<${FEATURE_NAME}UiState> = _uiState.asStateFlow()

    private val _effect = Channel<${FEATURE_NAME}Effect>(Channel.BUFFERED)
    val effect: Flow<${FEATURE_NAME}Effect> = _effect.receiveAsFlow()

    fun onIntent(intent: ${FEATURE_NAME}Intent) {
        when (intent) {
            // 处理用户意图
        }
    }
}
```

**UiState 规范：**
```kotlin
data class ${FEATURE_NAME}UiState(
    val isLoading: Boolean = false,
    val data: Data? = null,
    val error: ${FEATURE_NAME}Error? = null
)

sealed interface ${FEATURE_NAME}Intent
sealed interface ${FEATURE_NAME}Effect
sealed interface ${FEATURE_NAME}Error
```

**Repository 接口（domain 层）：**
```kotlin
interface ${FEATURE_NAME}Repository {
    suspend fun getData(id: String): Result<Data>
    suspend fun saveData(data: Data): Result<Unit>
}
```

**Repository 实现（data 层）：**
```kotlin
class ${FEATURE_NAME}RepositoryImpl @Inject constructor(
    private val api: ${FEATURE_NAME}Api,
    private val dao: ${FEATURE_NAME}Dao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : ${FEATURE_NAME}Repository {
    override suspend fun getData(id: String): Result<Data> = runCatching {
        withContext(ioDispatcher) {
            // 先读缓存，再请求网络，缓存优先
        }
    }
}
```

### 3. 禁止事项（AI 绝对不能违反）

- ❌ ViewModel 不直接持有 Repository，必须通过 UseCase
- ❌ 禁止使用 GlobalScope，使用 viewModelScope
- ❌ 禁止硬编码 Dispatchers.IO，使用 @IODispatcher 注入
- ❌ 禁止暴露 MutableStateFlow，只暴露 StateFlow
- ❌ 禁止用 try-catch，Repository 返回 Result
- ❌ 禁止使用 !! 操作符
- ❌ Domain 层禁止使用 Context、Bundle

### 4. 参考现有模块

请参考以下现有模块的代码结构和风格：
- `feature/` 目录下已有的功能模块

### 5. 输出清单

创建完成后，请确认：
- [ ] 所有文件已创建在正确路径
- [ ] ViewModel 使用 @HiltViewModel + @Inject constructor
- [ ] UiState 是 data class，Intent/Error 是 sealed interface
- [ ] Repository 接口在 domain 层，实现类在 data 层
- [ ] UseCase 在 domain 层，使用 operator invoke
- [ ] 所有 suspend 函数返回 Result，不直接 throw
- [ ] IO 操作使用 @IODispatcher
- [ ] 一次性事件使用 Channel effect 模式

---

**然后请等待我确认功能需求的具体业务逻辑，再生成具体代码。**
