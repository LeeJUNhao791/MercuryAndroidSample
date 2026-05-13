package com.mobvoi.wenet

/**
 * WeNet 语音识别原生接口调用类
 */
object Recognize {

    init {
        // 加载原生动态库 libwenet.so
        System.loadLibrary("wenet")
    }

    /**
     * 初始化语音识别引擎
     * @param modelDir 模型文件所在的目录路径
     */
    external fun init(modelDir: String)

    /**
     * 重置识别器状态
     */
    external fun reset()

    /**
     * 接收音频波形数据
     * @param waveform PCM 16bit 音频数据
     */
    external fun acceptWaveform(waveform: ShortArray)

    /**
     * 设置输入完成，告知引擎已无后续音频
     */
    external fun setInputFinished()

    /**
     * 获取识别是否已完全结束
     * @return true 表示识别结束
     */
    external fun getFinished(): Boolean

    /**
     * 开始解码流程
     */
    external fun startDecode()

    /**
     * 获取当前的识别结果文本
     * @return 识别出的文字内容
     */
    external fun getResult(): String
}
