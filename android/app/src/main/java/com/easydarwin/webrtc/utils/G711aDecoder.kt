package com.easydarwin.webrtc.utils

import android.util.Log

/**
 * G.711a 音频解码器工具类
 */
object G711aDecoder {
    private const val TAG = "G711aDecoder"

    // G.711a 解码表 (alaw)
    private val aLawDecompressTable = shortArrayOf(
        -5504, -5248, -6016, -5760, -4480, -4224, -4992, -4736,
        -7552, -7296, -8064, -7808, -6528, -6272, -7040, -6784,
        -2752, -2624, -3008, -2880, -2240, -2112, -2496, -2368,
        -3776, -3648, -4032, -3904, -3264, -3136, -3520, -3392,
        -22016, -20992, -24064, -23040, -17920, -16896, -19968, -18944,
        -30208, -29184, -32256, -31232, -26112, -25088, -28160, -27136,
        -11008, -10496, -12032, -11520, -8960, -8448, -9984, -9472,
        -15104, -14592, -16128, -15616, -13056, -12544, -14080, -13568,
        544, 512, 576, 544, 448, 416, 480, 448,
        736, 704, 768, 736, 672, 640, 704, 672,
        272, 256, 288, 272, 224, 208, 240, 224,
        368, 352, 384, 368, 336, 320, 352, 336,
        136, 128, 144, 136, 112, 104, 120, 112,
        184, 176, 192, 184, 168, 160, 176, 168,
        68, 64, 72, 68, 56, 52, 60, 56,
        92, 88, 96, 92, 84, 80, 88, 84,
        5504, 5248, 6016, 5760, 4480, 4224, 4992, 4736,
        7552, 7296, 8064, 7808, 6528, 6272, 7040, 6784,
        2752, 2624, 3008, 2880, 2240, 2112, 2496, 2368,
        3776, 3648, 4032, 3904, 3264, 3136, 3520, 3392,
        22016, 20992, 24064, 23040, 17920, 16896, 19968, 18944,
        30208, 29184, 32256, 31232, 26112, 25088, 28160, 27136,
        11008, 10496, 12032, 11520, 8960, 8448, 9984, 9472,
        15104, 14592, 16128, 15616, 13056, 12544, 14080, 13568,
        -544, -512, -576, -544, -448, -416, -480, -448,
        -736, -704, -768, -736, -672, -640, -704, -672,
        -272, -256, -288, -272, -224, -208, -240, -224,
        -368, -352, -384, -368, -336, -320, -352, -336,
        -136, -128, -144, -136, -112, -104, -120, -112,
        -184, -176, -192, -184, -168, -160, -176, -168,
        -68, -64, -72, -68, -56, -52, -60, -56,
        -92, -88, -96, -92, -84, -80, -88, -84
    )

    /**
     * 批量解码 G.711a 数据为 PCM short数组
     */
    fun bulkDecode(g711aData: ByteArray, srcPos: Int, length: Int, pcmOutput: ShortArray, destPos: Int) {
        if (length <= 0) return

        for (i in 0 until length) {
            val srcIndex = srcPos + i
            val destIndex = destPos + i

            if (srcIndex >= g711aData.size) {
                Log.w(TAG, "Source index out of bounds: $srcIndex >= ${g711aData.size}")
                break
            }

            pcmOutput[destIndex] = aLawDecompressTable[g711aData[srcIndex].toInt() and 0xFF]
        }
    }

    // 保留原有方法用于兼容
    fun decodeToBuffer(g711aData: ByteArray, pcmBuffer: ByteArray): Int {
        return decodeToBuffer(g711aData, 0, g711aData.size, pcmBuffer, 0)
    }

    fun decodeToBuffer(g711aData: ByteArray, srcPos: Int, length: Int, pcmBuffer: ByteArray, destPos: Int): Int {
        if (length <= 0) return 0

        val requiredSize = length * 2
        if (destPos + requiredSize > pcmBuffer.size) {
            Log.e(TAG, "PCM buffer too small: ${pcmBuffer.size} < ${destPos + requiredSize}")
            return 0
        }

        for (i in 0 until length) {
            val srcIndex = srcPos + i
            val destIndex = destPos + i * 2

            if (srcIndex >= g711aData.size) {
                Log.w(TAG, "Source index out of bounds: $srcIndex >= ${g711aData.size}")
                break
            }

            val sample = aLawDecompressTable[g711aData[srcIndex].toInt() and 0xFF]
            pcmBuffer[destIndex] = (sample.toInt() and 0xFF).toByte()
            pcmBuffer[destIndex + 1] = (sample.toInt() shr 8).toByte()
        }

        return length * 2
    }
}