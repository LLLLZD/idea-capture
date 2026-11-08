package top.lzdrichard.idea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import top.lzdrichard.idea.ui.AppState
import top.lzdrichard.idea.ui.IdeaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = remember { AppState(applicationContext) }
            IdeaApp(state)
        }
    }
}
