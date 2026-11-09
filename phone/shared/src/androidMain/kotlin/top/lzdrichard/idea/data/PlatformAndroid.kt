package top.lzdrichard.idea.data

import android.content.Context
import top.lzdrichard.idea.AndroidApp
import java.io.File
import java.util.UUID

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun randomId(): String = UUID.randomUUID().toString()

actual object PlatformFile {

    private fun filesDir(): File = File(AndroidApp.context.filesDir, "pending").apply { mkdirs() }

    actual fun privatePath(fileName: String): String = File(filesDir(), fileName).absolutePath

    actual fun copyTo(sourcePath: String, destPath: String): String? = try {
        val source = File(sourcePath)
        val target = File(destPath)
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        if (target.exists() && target.length() > 0) target.absolutePath else null
    } catch (e: Exception) {
        null
    }

    actual fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    actual fun exists(path: String): Boolean = File(path).let { it.exists() && it.length() > 0 }
}

actual fun newRecordingPath(): String {
    val dir = File(AndroidApp.context.cacheDir, "recordings").apply { mkdirs() }
    return File(dir, "rec-" + System.currentTimeMillis() + ".m4a").absolutePath
}

actual class PlatformStore actual constructor() {

    private val sp = AndroidApp.context.getSharedPreferences("idea", Context.MODE_PRIVATE)

    actual fun getString(key: String): String? = sp.getString(key, null)

    actual fun putString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    actual fun remove(key: String) {
        sp.edit().remove(key).apply()
    }
}
