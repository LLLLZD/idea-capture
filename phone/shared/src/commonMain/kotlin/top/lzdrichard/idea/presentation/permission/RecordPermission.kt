package top.lzdrichard.idea.presentation.permission

import androidx.compose.runtime.Composable

/** 录音权限状态：未授予时 [request] 弹系统授权，授予后界面重绘。 */
class RecordPermissionState(val granted: Boolean, val request: () -> Unit)

/** 录音权限：Android 走运行时授权，iOS 走 AVAudioSession；平台差异只在 actual 里。 */
@Composable
expect fun rememberRecordPermission(): RecordPermissionState
