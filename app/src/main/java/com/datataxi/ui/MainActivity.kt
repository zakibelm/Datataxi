package com.datataxi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.datataxi.data.CaptureStore
import com.datataxi.data.JournalStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureStore.init(applicationContext)
        JournalStore.init(applicationContext)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Prédictions") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Diagnostic") })
                        }
                        when (tab) {
                            0 -> PredictionScreen()
                            else -> DiagnosticScreen()
                        }
                    }
                }
            }
        }
    }
}
