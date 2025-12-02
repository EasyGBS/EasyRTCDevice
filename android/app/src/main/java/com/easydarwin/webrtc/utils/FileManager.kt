package com.easydarwin.webrtc.utils

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FileManager(private val context: Context) {

    private var outputStream: BufferedOutputStream? = null
    private var file: File? = null
    private var isAppend: Boolean = false

    /**
     * 初始化文件（默认保存到 app 沙盒路径 /Android/data/包名/files/）
     * @param fileName 文件名（如 "output.h264"）
     * @param subDir 子目录（如 "video"），可为 null
     * @param append 是否追加写入
     */
    fun init(fileName: String, subDir: String? = null, append: Boolean = false) {
        try {
            val baseDir = context.getExternalFilesDir(null)
            val dir = if (subDir.isNullOrEmpty()) baseDir else File(baseDir, subDir)
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
            }

            file = File(dir, fileName)
            outputStream = BufferedOutputStream(FileOutputStream(file, append))
            isAppend = append

            Log.i(TAG, "初始化文件成功: ${file?.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "初始化文件失败", e)
        }
    }

    /**
     * 写入 byte[] 数据
     */
    fun write(data: ByteArray, length: Int = data.size) {
        try {
            outputStream?.write(data, 0, length)
        } catch (e: IOException) {
            Log.e(TAG, "写入失败", e)
        }
    }

    fun write(data: ShortArray, length: Int = data.size) {
        try {
            val byteBuffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)  // 每个 Short 占 2 字节   // PCM 通常为小端序
            for (i in 0 until length)  byteBuffer.putShort(data[i])
            outputStream?.write(byteBuffer.array(), 0,byteBuffer.array().size )
        } catch (e: IOException) {
            Log.e(TAG, "写入失败", e)
        }
    }

    /**
     * 写入单个 byte
     */
    fun writeByte(b: Int) {
        try {
            outputStream?.write(b)
        } catch (e: IOException) {
            Log.e(TAG, "写入单字节失败", e)
        }
    }

    /**
     * 写入字符串（转为 UTF-8 字节）
     */
    fun writeString(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * 关闭流
     */
    fun close() {
        try {
            outputStream?.flush()
            outputStream?.close()
            Log.i(TAG, "保存完成：${file?.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "关闭文件失败", e)
        }
    }

    /**
     * 获取当前文件对象
     */
    fun getFile(): File? = file

    companion object {
        private const val TAG = "FileManager"
    }

}