package com.mobvoi.wenet

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ffalcon.mercury.android.sdk.demo.R
import java.util.*

/**
 * 自定义的音频模拟条形图 Created by shize on 2016/9/5.
 * 迁移并转换为 Kotlin 代码
 */
class VoiceRectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 音频矩形的数量
    private var mRectCount: Int = 0
    // 音频矩形的画笔
    private var mRectPaint: Paint = Paint()
    // 渐变颜色的两种
    private var topColor: Int = 0
    private var downColor: Int = 0
    // 音频矩形的宽和高
    private var mRectWidth: Int = 0
    private var mRectHeight: Int = 0
    // 偏移量
    private var offset: Int = 0
    // 频率速度
    private var mSpeed: Int = 0

    private var mEnergyBuffer: DoubleArray? = null

    init {
        setPaint(context, attrs)
    }

    fun setPaint(context: Context, attrs: AttributeSet?) {
        // 将属性存储到TypedArray中
        val ta = context.obtainStyledAttributes(attrs, R.styleable.VoiceRect)
        
        // 添加矩形画笔的基础颜色
        mRectPaint.color = ta.getColor(
            R.styleable.VoiceRect_RectTopColor,
            ContextCompat.getColor(context, R.color.top_color)
        )
        // 添加矩形渐变色的上面部分
        topColor = ta.getColor(
            R.styleable.VoiceRect_RectTopColor,
            ContextCompat.getColor(context, R.color.top_color)
        )
        // 添加矩形渐变色的下面部分
        downColor = ta.getColor(
            R.styleable.VoiceRect_RectDownColor,
            ContextCompat.getColor(context, R.color.down_color)
        )
        // 设置矩形的数量
        mRectCount = ta.getInt(R.styleable.VoiceRect_RectCount, 10)
        mEnergyBuffer = DoubleArray(mRectCount)

        // 设置重绘的时间间隔，也就是变化速度
        mSpeed = ta.getInt(R.styleable.VoiceRect_RectSpeed, 300)
        // 每个矩形的间隔
        offset = ta.getInt(R.styleable.VoiceRect_RectOffset, 0)
        // 回收TypeArray
        ta.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        // 画布的宽
        val mWidth = width
        // 获取矩形的最大高度
        mRectHeight = height
        // 获取单个矩形的宽度(减去的部分为到右边界的间距)
        mRectWidth = (mWidth - offset) / mRectCount
        // 实例化一个线性渐变
        val mLinearGradient = LinearGradient(
            0f,
            0f,
            mRectWidth.toFloat(),
            mRectHeight.toFloat(),
            topColor,
            downColor,
            Shader.TileMode.CLAMP
        )
        // 添加进画笔的着色器
        mRectPaint.shader = mLinearGradient
    }

    fun add(energy: Double) {
        mEnergyBuffer?.let { buffer ->
            if (buffer.size - 1 >= 0) {
                System.arraycopy(buffer, 1, buffer, 0, buffer.size - 1)
            }
            buffer[buffer.size - 1] = energy
        }
    }

    fun zero() {
        mEnergyBuffer?.let { buffer ->
            Arrays.fill(buffer, 0.0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var currentHeight: Float
        val buffer = mEnergyBuffer ?: return
        
        for (i in 0 until mRectCount) {
            currentHeight = (mRectHeight * buffer[i]).toFloat()

            // 矩形的绘制是从左边开始到上、右、下边（左右边距离左边画布边界的距离，上下边距离上边画布边界的距离）
            canvas.drawRect(
                (mRectWidth * i + offset).toFloat(),
                (mRectHeight - currentHeight) / 2,
                (mRectWidth * (i + 1)).toFloat(),
                mRectHeight / 2 + currentHeight / 2,
                mRectPaint
            )
        }
        // 使得view延迟重绘
        postInvalidateDelayed(mSpeed.toLong())
    }
}
