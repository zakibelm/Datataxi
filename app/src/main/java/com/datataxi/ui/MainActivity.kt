package com.datataxi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.datataxi.data.CaptureStore
import com.datataxi.data.JournalStore
import com.datataxi.data.ScreenLogStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureStore.init(applicationContext)
        JournalStore.init(applicationContext)
        ScreenLogStore.init(applicationContext)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DiagnosticScreen()
                }
            }
        }
    }
}
