@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package top.lzdrichard.idea.data

import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun randomId(): String = NSUUID().UUIDString()

actual object PlatformFile {

    private val manager = NSFileManager.defaultManager

    private fun filesDir(): String {
        val base = NSHomeDirectory() + "/Documents/pending"
        if (!manager.fileExistsAtPath(base)) manager.createDirectoryAtPath(base, true, null, null)
        return base
    }

    actual fun privatePath(fileName: String): String = filesDir() + "/" + fileName

    actual fun copyTo(sourcePath: String, destPath: String): String? {
        if (manager.fileExistsAtPath(destPath)) manager.removeItemAtPath(destPath, null)
        val ok = manager.copyItemAtPath(sourcePath, destPath, null)
        return if (ok) destPath else null
    }

    actual fun delete(path: String) {
        if (manager.fileExistsAtPath(path)) manager.removeItemAtPath(path, null)
    }

    actual fun exists(path: String): Boolean = manager.fileExistsAtPath(path)
}

actual fun newRecordingPath(): String {
    val dir = NSTemporaryDirectory() + "recordings"
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(dir)) manager.createDirectoryAtPath(dir, true, null, null)
    return dir + "/rec-" + nowMillis() + ".m4a"
}

actual class PlatformStore actual constructor() {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
