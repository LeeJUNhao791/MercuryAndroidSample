# Bug 修复 & 回归测试生成（眼镜 AR 项目版）

## 使用场景
发现眼镜端 Bug 或收到崩溃堆栈后，运行此 Prompt 进行根因分析和修复生成。

**触发词**：修复 Bug、崩溃分析、生成回归测试、Bug 修复、眼镜端崩溃

## 输入参数
- `$CRASH_STACK`：崩溃堆栈信息（logcat 或崩溃报告中的完整堆栈）
- `$SOURCE_FILES`：堆栈中涉及的所有源文件的**完整内容**（不能只提供堆栈中的几行）
- `$RELATED_TYPES`：相关的 Entity / DTO / Interface 定义（影响空安全判断）
- `$RECENT_CHANGES`：最近 7 天相关文件的 git diff（用于判断是否回归）
- `$PROJECT_RULES`：项目所有 Rules 内容

## Prompt 正文

---

你是一个 RayNeo AR 眼镜项目的崩溃分析专家。请严格按照以下框架分析崩溃，并生成修复代码和回归测试。

## 项目规则
$PROJECT_RULES

## 崩溃堆栈
$CRASH_STACK

## 涉及源文件的完整内容
$SOURCE_FILES

## 相关类型定义（Entity / DTO / Interface）
$RELATED_TYPES

## 最近 7 天相关文件的 Git 变更
$RECENT_CHANGES

---

## 第一阶段：崩溃分析

### 1. 崩溃分类
- **异常类型**：（NPE / OOM / ANR / ClassCast / IndexOutOfBounds / ...）
- **精确触发位置**：类名 + 方法名 + 行号（从堆栈提取）
- **影响范围**：（眼镜端 UI 线程 / 后台线程 / 全局）
- **眼镜端特有**：是否与 `mBindingPair`、`FixPosFocusTracker`、`TempleAction` 相关

### 2. 根因推理链（必须完整填写）

用"因为→所以"的链式推理：

```
- **直接原因**：哪个变量/状态在那一刻异常
  例：fixPosFocusTracker 为 null

- **为什么异常**：追溯赋值/初始化路径
  例：initFocusTarget() 在 onCreate 中未调用，或因异常提前退出

- **触发条件**：什么操作/时序/状态组合导致
  例：用户在眼镜端触摸板快速滑动后进入页面

- **眼镜端特有因素**：
  - 是否与焦点系统初始化顺序有关
  - 是否与 mBindingPair 的 setLeft/updateView 调用顺序有关
  - 是否与 repeatOnLifecycle 时机有关

- **是否回归**：对照 recent_changes 判断
  例：最近无相关提交，不是回归问题

- **置信度**：
  [ ] 高（>80%）：有完整代码上下文，触发路径明确
  [ ] 中（50-80%）：缺少部分上下文，建议补充后再次分析
  [ ] 低（<50%）：堆栈信息不足，暂不下结论，建议人工介入
```

### 3. 修复方案（必须给出两个方案）

**方案 A：修复根因**（消除导致异常的根本原因）
```
修复策略：
[描述如何从源头消除问题]

Unified Diff：
```diff
// 文件路径
[diff 内容]
```
```

**方案 B：防御性保护**（即使根因未修复，也不崩溃）
```
防御策略：
[描述如何在异常路径上做兜底保护]

Unified Diff：
```diff
// 文件路径
[diff 内容]
```
```

### 4. 眼镜 AR 专项检查

请特别检查以下眼镜端特有的崩溃场景：

- **焦点系统崩溃**：`FixPosFocusTracker?.handleFocusTargetEvent()` 是否做了空安全检查
- **mBindingPair 空指针**：`mBindingPair.setLeft { }` / `mBindingPair.updateView { }` 调用时机是否在 super.onCreate() 之后
- **TempleAction 空消费**：`action.consumed` 是否正确判断
- **ViewPair 左右眼不同步**：`updateView` 是否遗漏导致单眼显示异常
- **RecyclerView 崩溃**：`adapter` 是否在 RecyclerView 完全初始化后才使用
- **3D 效果崩溃**：`make3DEffectForSide` 传入的 `view` / `isLeft` 是否可能为 null
- **FToast/FLogger 崩溃**：SDK 工具类是否有空安全保护

**X2/X3 设备差异相关崩溃：**
- **X3 独有事件崩溃**：`SlideUpwards`/`SlideDownwards` 在 X2 设备上是否被正确处理
- **VGA Camera 崩溃**：X2 设备访问 Camera ID 1（VGA）是否做了设备判断
- **MIC 模式崩溃**：X2 `setParameters` 在 X3 设备上是否可能失效
- **filterMode 崩溃**：X2 设备调用 X3 独有的 filterMode 是否导致异常

### 5. 修复硬约束检查

在生成修复之前，确认满足以下所有条件：
- [ ] 没有引入新依赖
- [ ] 没有改变公开 API 签名
- [ ] 没有破坏向后兼容性
- [ ] 没有使用 `!!` 作为唯一修复手段
- [ ] 没有添加 `Log.d` / `println` 调试代码
- [ ] 修复不破坏焦点系统的正常工作流程

---

## 第二阶段：回归测试生成

基于以上崩溃分析和修复，生成回归测试。

### 测试规则

1. **必须包含一个"修复前会失败"的测试**（证明 bug 确实存在）
2. **覆盖所有已知触发条件**（包括并发场景、生命周期变化）
3. **覆盖修复引入的新代码分支**
4. **测试必须调用 SUT（被测系统）的真实方法**，只 mock 外部依赖
5. **禁止 mock 被测对象本身**
6. 使用 **JUnit 5 + MockK + Turbine**

### 测试模板（ViewModel 层）

```kotlin
class ${Feature}ViewModelRegressionTest {

    private lateinit var viewModel: ${Feature}ViewModel
    private val repository: ${Feature}Repository = mockk()

    @BeforeEach
    fun setup() {
        viewModel = ${Feature}ViewModel(repository)
    }

    // ✅ 修复前会失败：证明 bug 存在
    @Test
    fun `场景_触发条件_预期结果`() = runTest {
        // Given：设置 mock
        coEvery { repository.getData() } returns Result.failure(NetworkError)

        // When：调用被测系统真实方法
        viewModel.onIntent(${Feature}Intent.LoadData)
        advanceUntilIdle()

        // Then：验证状态变化（不是验证 mock）
        assertIs<${Feature}UiState.Error>(viewModel.uiState.value)
    }

    // ✅ 覆盖眼镜端特有的 null 处理
    @Test
    fun `场景_数据为空_不崩溃`() = runTest {
        coEvery { repository.getData() } returns Result.success(null)

        viewModel.onIntent(${Feature}Intent.LoadData)
        advanceUntilIdle()

        // 验证有默认值，不 NPE
        assertEquals("默认值", viewModel.uiState.value.displayText)
    }
}
```

---

## 第三阶段：变更影响分析

基于修复代码，分析还应该运行哪些测试：

```json
{
  "must_run": ["必须跑的测试类（直接影响的模块）"],
  "should_run": ["建议跑的测试类（上游调用方）"],
  "skip": ["可跳过的测试类（确认无关联）"],
  "estimated_minutes": 3
}
```

分析维度：
1. **直接影响**：被改函数/类的测试
2. **上游影响**：调用了被改代码的模块
3. **眼镜端特有影响**：修复涉及焦点系统 → 检查所有使用 FixPosFocusTracker 的 Activity
4. **数据流**：改了 data class → 检查序列化 / API / DB

---

## 最终输出清单

- [ ] 崩溃分类（异常类型 + 触发位置）
- [ ] 根因推理链（4 步完整）
- [ ] 眼镜 AR 专项检查通过
- [ ] 置信度评估
- [ ] 修复方案 A（根因修复） + Diff
- [ ] 修复方案 B（防御性保护） + Diff
- [ ] 硬约束检查通过
- [ ] 回归测试代码（≥ 2 个测试，覆盖正常路径 + 异常路径）
- [ ] 变更影响分析 + 精准测试集推荐
