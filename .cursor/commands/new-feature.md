# 新功能开发（眼镜 AR 项目版）

## 使用场景
需要在 RayNeo AR 眼镜项目中创建一个新的功能模块时运行此 Prompt。

**触发词**：创建新功能、开发新模块、增加 XX 功能、新功能开发、眼镜端新页面

## 输入参数
- `$FEATURE_NAME`：功能名（PascalCase，如 `QrcodeScan`、`VoiceRecognition`、`ContactList`）
- `$PACKAGE`：包路径（如 `com.example.myapp.ui.activity.qrcode`）
- `$IS_GLASSES_SIDE`：是否为眼镜端页面（true/false）

## Prompt 正文

---

根据以下参数创建新的功能模块：

- 功能名：`$FEATURE_NAME`
- 包路径：`$PACKAGE`
- 眼镜端页面：`$IS_GLASSES_SIDE`

## 步骤

### 1. 创建目录结构

**眼镜端（$IS_GLASSES_SIDE = true）时使用：**

```
$PACKAGE/
├── ${FEATURE_NAME}Activity.kt    # BaseMirrorActivity（眼镜端）
├── ${FEATURE_NAME}ViewModel.kt   # ViewModel
├── state/
│   ├── ${FEATURE_NAME}UiState.kt
│   ├── ${FEATURE_NAME}Intent.kt
│   └── ${FEATURE_NAME}Effect.kt
└── adapter/
    └── ${FEATURE_NAME}Adapter.kt  # RecyclerView Adapter（如果需要列表）

domain/model/
└── ${FEATURE_NAME}Model.kt       # Domain 数据模型

data/
├── repository/
│   └── ${FEATURE_NAME}Repository.kt
└── repository/impl/
    └── ${FEATURE_NAME}RepositoryImpl.kt
```

**手机端（$IS_GLASSES_SIDE = false）时使用：**

```
$PACKAGE/
├── ${FEATURE_NAME}Activity.kt    # BaseEventActivity（手机端）
├── ${FEATURE_NAME}ViewModel.kt
└── state/
    └── ...
```

### 2. Activity 模板（眼镜端）

```kotlin
package $PACKAGE

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.util.FLogger
import kotlinx.coroutines.launch

class ${FEATURE_NAME}Activity : BaseMirrorActivity<Layout${FEATURE_NAME}Binding>() {

    private var fixPosFocusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFocusTarget()
        initEvent()
    }

    private fun initFocusTarget() {
        val focusHolder = FocusHolder()

        mBindingPair.setLeft {
            val focusTargets = mutableListOf<FocusInfo>()
            var firstFocusView: View? = null

            fun addFocusTarget(view: View, onClick: () -> Unit) {
                if (view.visibility != View.VISIBLE) return
                if (firstFocusView == null) firstFocusView = view

                focusTargets += FocusInfo(
                    view,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> onClick()
                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, view, mBindingPair.checkIsLeft(this))
                        }
                    }
                )
            }

            // 注册所有可交互元素
            // addFocusTarget(btnFirst) { handleFirst() }
            // addFocusTarget(btnSecond) { handleSecond() }

            if (focusTargets.isNotEmpty()) {
                focusHolder.addFocusTarget(*focusTargets.toTypedArray())
                firstFocusView?.let { focusHolder.currentFocus(it) }
            }
        }

        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            focusObj.reqFocus()
        }
    }

    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> finish()
                        else -> fixPosFocusTracker?.handleFocusTargetEvent(action)
                    }
                }
            }
        }
    }

    private fun triggerFocus(hasFocus: Boolean, view: View, isLeft: Boolean) {
        view.setBackgroundColor(
            getColor(if (hasFocus) R.color.color_rayneo_theme_0 else R.color.black)
        )
        make3DEffectForSide(view, isLeft, hasFocus)
    }
}
```

### 3. ViewModel 模板（通用）

```kotlin
@HiltViewModel
class ${FEATURE_NAME}ViewModel @Inject constructor(
    private val getDataUseCase: Get${FEATURE_NAME}UseCase,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
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

data class ${FEATURE_NAME}UiState(
    val isLoading: Boolean = false,
    val data: ${FEATURE_NAME}Model? = null,
    val error: ${FEATURE_NAME}Error? = null
)

sealed interface ${FEATURE_NAME}Intent
sealed interface ${FEATURE_NAME}Effect
sealed interface ${FEATURE_NAME}Error
```

### 4. 编码规范（必须遵守）

**眼镜端 Activity：**
- ✅ 必须继承 `BaseMirrorActivity<Binding>`
- ✅ 所有可交互视图必须在 `initFocusTarget()` 中注册 `FocusInfo`
- ✅ 视觉更新用 `mBindingPair.updateView { }` 同步双眼
- ✅ 双击映射为 `finish()`
- ✅ 事件收集在 `repeatOnLifecycle(Lifecycle.State.RESUMED)` 中
- ✅ 使用 `FToast.show()` 而非 `Toast`
- ✅ 使用 `FLogger` 而非 `Log`

**ViewModel：**
- ✅ 必须通过 UseCase，不直接持有 Repository
- ✅ 只暴露 `StateFlow`，不暴露 `MutableStateFlow`
- ✅ 使用 `@IODispatcher` 注入，不硬编码 `Dispatchers.IO`
- ✅ Repository 返回 `Result`，不用 `throw`
- ✅ 一次性事件用 `Channel effect` 模式

### 5. 禁止事项

- ❌ 眼镜端页面使用 `AppCompatActivity`（必须用 `BaseMirrorActivity`）
- ❌ 使用 `View.OnClickListener`（触摸板不触发点击事件）
- ❌ 只更新左眼或右眼的视觉效果（必须用 `updateView` 同步）
- ❌ ViewModel 直接持有 Repository（必须通过 UseCase）
- ❌ 使用 `GlobalScope` / 硬编码 `Dispatchers.IO`
- ❌ 暴露 `MutableStateFlow`
- ❌ Repository 方法直接 `throw`
- ❌ Domain 层使用 `Context`、`Bundle`
- ❌ 使用 `!!` 作为唯一修复手段

### 6. 布局文件注意事项

布局文件需要创建在 `res/layout/` 下，约定：
- 眼镜端布局使用黑色背景：`android:background="@color/black"`
- 颜色使用 SDK 资源：`@color/color_rayneo_theme_0`
- 禁止全大写：`android:textAllCaps="false"`

### 7. Manifest 注册

每个新 Activity 必须在 AndroidManifest.xml 中声明，且眼镜端 Activity 必须声明横屏方向：

```xml
<activity
    android:name=".$FEATURE_NAME}Activity"
    android:screenOrientation="landscape"
    android:exported="false" />
```

### 8. X2/X3 设备差异处理

如果功能涉及以下模块，需要添加设备检测：

**TP 触摸板事件差异：**
```kotlin
private fun handleTempleAction(action: TempleAction) {
    when (action) {
        is TempleAction.DoubleClick -> finish()
        is TempleAction.SlideForward -> navigateNext()
        is TempleAction.SlideBackward -> navigatePrev()
        // X3 独有事件，需要判断设备
        is TempleAction.SlideUpwards -> {
            if (DeviceUtil.isX3Device()) scrollUp()
        }
        is TempleAction.SlideDownwards -> {
            if (DeviceUtil.isX3Device()) scrollDown()
        }
        else -> fixPosFocusTracker?.handleFocusTargetEvent(action)
    }
}
```

**Camera 差异（X3 VGA 摄像头）：**
```kotlin
if (DeviceUtil.isX3Device()) {
    // X3: 可以访问 VGA 摄像头 (Camera ID: 1) 进行空间定位
    val vgaCameraId = "1"
    val characteristics = cameraManager.getCameraCharacteristics(vgaCameraId)
}
```

**MIC 录音差异：**
```kotlin
// X2 使用 setParameters
fun setMicModeX2(mode: String) {
    audioManager.setParameters("audio_source_record=$mode")
}

// X3 使用新的 MIC API（参考 IPC SDK）
```

### 9. 传感器开发（IMU）

如果需要使用传感器：

```kotlin
class IMUActivity : BaseMirrorActivity<LayoutImuBinding>(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gameRotationVectorSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gameRotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    }

    override fun onResume() {
        super.onResume()
        gameRotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)  // 必须取消注册
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val w = event.values[3]
            // 四元数转欧拉角处理...
        }
    }
}
```

### 10. 视频合目开发（SurfaceView/MirroringView）

如果需要视频播放：

```kotlin
class VideoPlayActivity : BaseMirrorActivity<LayoutVideoBinding>() {

    private var mPlayer: MediaPlayer? = null

    private fun initPlayer() {
        binding.textureView.let { textureView ->
            mPlayer = MediaPlayer().apply {
                setSurface(Surface(textureView.surfaceTexture))
                prepare()
                start()
            }
            // 设置镜像
            binding.mirrorView.setSource(textureView)
            binding.mirrorView.startMirroring()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mirrorView.stopMirroring()  // 必须停止
        mPlayer?.release()
    }
}
```

### 11. BLE/GPS 功能开发

如果需要与手机通信：

```kotlin
// BLE 连接状态监听
private fun collectBleStatus() {
    MobileState.isMobileConnected()
        .onEach { isConnected ->
            mBindingPair.updateView {
                tvStatus.text = if (isConnected) "已连接" else "未连接"
            }
        }
        .launchIn(lifecycleScope)
}

// GPS 推流（需要 IPC SDK）
// onResponse 回调在异步线程，需要切换到主线程更新 UI
```

### 12. 动态焦点开发

如果需要运行时动态添加焦点：

```kotlin
private fun addDynamicFocus() {
    var handle: FocusViewHandle<View>? = null
    handle = mBindingPair.addFocusView(
        parent = mBindingPair.left.container,
        viewFactory = { Button(context).apply { text = "Dynamic" } },
        focusHolder = focusHolder,
        focusConfig = {
            eventHandler = { action ->
                when (action) { is TempleAction.Click -> FToast.show("Clicked") }
            }
            onFocusChange = { view, hasFocus, isLeft ->
                triggerFocus(hasFocus, view, isLeft)
            }
        }
    )
    focusHandles.add(handle!!)
}

// 移除
currentDynamicFocus?.clearFocusView()
```

### 8. 输出清单

创建完成后，请确认：
- [ ] Activity 继承 `BaseMirrorActivity`（眼镜端）或 `BaseEventActivity`（手机端）
- [ ] `initFocusTarget()` 中注册了所有可交互视图
- [ ] `mBindingPair.updateView { }` 用于所有双眼视觉同步
- [ ] 双击映射为 `finish()`
- [ ] 事件收集在 `repeatOnLifecycle(Lifecycle.State.RESUMED)` 中
- [ ] ViewModel 使用 `@HiltViewModel` + `@Inject constructor`
- [ ] UiState 是 `data class`，Intent/Error 是 `sealed interface`
- [ ] Repository 接口在 `domain/` 层，实现类在 `data/` 层
- [ ] UseCase 在 `domain/` 层，使用 `operator invoke`
- [ ] 所有 suspend 函数返回 `Result`，不直接 `throw`
- [ ] IO 操作使用 `@IODispatcher`
- [ ] 一次性事件使用 `Channel effect` 模式
- [ ] Manifest 中声明了 `screenOrientation="landscape"`
- [ ] 布局文件背景为黑色，文字非全大写

---

**然后请等待我确认功能需求的具体业务逻辑，再生成具体代码。**
