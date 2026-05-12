package com.ffalcon.mercury.android.sdk.demo.ui.activity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.DialogTestBinding
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutDialogBinding
import com.ffalcon.mercury.android.sdk.ui.dialog.FDialog
import com.ffalcon.mercury.android.sdk.ui.dialog.FocusTracker
import com.ffalcon.mercury.android.sdk.ui.dialog.TrackInfo
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.util.FLogger
import kotlinx.coroutines.launch

class DialogActivity : BaseMirrorActivity<LayoutDialogBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化事件监听
        initEvent()
    }

    /**
     * 初始化眼镜端 Temple（镜腿）触摸事件监听
     */
    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect {
                    FLogger.i("DemoActivity", "action = $it")
                    when (it) {
                        // 双击镜腿：关闭当前 Activity
                        is TempleAction.DoubleClick -> {
                            finish()
                        }
                        // 单击镜腿：显示对话框
                        is TempleAction.Click -> {
                            showDialog()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * 显示自定义对话框
     */
    private fun showDialog() {
        FDialog.Builder<DialogTestBinding>(this)
            .setCancelable(true) // 设置对话框可取消
            .setCanceledOnTouchOutside(true) // 设置点击外部可取消
            .setOnShowListener {
                // 对话框显示时的回调
            }
            .setOnDismissListener {
                // 对话框消失时的回调
            }
            .setContentView(DialogTestBinding::class.java, initViewBlock = { pair, dialog ->
                // TODO: 在此处更新你的 UI 逻辑
            })
            .apply {
                // 处理焦点切换逻辑及焦点对象的事件响应
                val pair = mPair
                
                // 配置“确定”按钮的焦点追踪信息
                val btnOk = TrackInfo(pair.left.btnOk, eventHandler = { action, dialog ->
                    when (action) {
                        // 单击确定按钮：显示提示并关闭对话框
                        is TempleAction.Click -> {
                            FToast.show("Click Confirm")
                            dialog.dismiss()
                        }
                        else -> {
                            FLogger.i("btnOk action = $action")
                        }
                    }
                }, focusChangeHandler = { hasFocus ->
                    // 焦点变化时更新视图样式
                    pair.updateView {
                        triggerFocus(hasFocus, btnOk, pair.checkIsLeft(this))
                    }
                })

                // 配置“取消”按钮的焦点追踪信息
                val btnCancel = TrackInfo(pair.left.btnCancel, eventHandler = { action, dialog ->
                    when (action) {
                        // 单击取消按钮：显示提示并关闭对话框
                        is TempleAction.Click -> {
                            FToast.show("Click ")
                            dialog.dismiss()
                        }
                        else -> {
                            FLogger.i("btnCancel action = $action")
                        }
                    }
                }, focusChangeHandler = { hasFocus ->
                    // 焦点变化时更新视图样式
                    pair.updateView {
                        triggerFocus(hasFocus, btnCancel, pair.checkIsLeft(this))
                    }
                })

                // 设置无限焦点循环，添加可切换焦点的对象
                val tracker = FocusTracker(true).apply {
                    addFocusTarget(btnOk, btnCancel)
                }
                
                // 设置默认焦点位置为“取消”按钮
                tracker.currentFocus(pair.left.btnCancel)
                
                // 应用焦点追踪器
                setFocusTracker(tracker)
            }
            .setEventHandler { action, dialog ->
                // 对话框全局事件处理
                when (action) {
                    // 双击镜腿：关闭对话框
                    is TempleAction.DoubleClick -> {
                        dialog.dismiss()
                    }
                    else -> {}
                }
            }
            .build()
            .show()
    }

    /**
     * 触发焦点视觉效果
     * @param hasFocus 是否获得焦点
     * @param view 目标视图
     * @param isLeft 是否为左眼视图
     */
    private fun triggerFocus(hasFocus: Boolean, view: View, isLeft: Boolean) {
        // 根据焦点状态设置背景颜色
        view.setBackgroundColor(getColor(if (hasFocus) R.color.teal_700 else R.color.black))
        // 应用 3D 视觉效果
        make3DEffectForSide(view, isLeft, hasFocus)
    }
}