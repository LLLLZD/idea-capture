package top.lzdrichard.idea.presentation.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.setActive

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberRecordPermission(): RecordPermissionState {
    var granted by remember {
        mutableStateOf(
            AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted,
        )
    }
    return RecordPermissionState(
        granted = granted,
        request = {
            AVAudioSession.sharedInstance().requestRecordPermission { ok ->
                granted = ok
            }
        },
    )
}
