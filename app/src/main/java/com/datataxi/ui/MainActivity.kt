package com.datataxi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.datataxi.data.CaptureStore
import com.datataxi.data.CrashLog
import com.datataxi.data.HistoryStore
import com.datataxi.data.JournalStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(applicationContext)

        // Après un plantage : on montre le rapport avant de relancer quoi que ce soit.
        val crash = CrashLog.read(applicationContext)
        if (crash != null) {
            setContent {
                DataTaxiTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        CrashScreen(crash) {
                            CrashLog.clear(applicationContext)
                            recreate()
                        }
                    }
                }
            }
            return
        }

        CaptureStore.init(applicationContext)
        JournalStore.init(applicationContext)
        HistoryStore.init(applicationContext)
        setContent {
            DataTaxiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxSize()) {
                        BrandHeader()
                        AppTabs(tab) { tab = it }
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

/** Bandeau noir avec le logo. */
@Composable
fun BrandHeader() {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(com.datataxi.R.drawable.logo_datataxi), contentDescription = null, modifier = Modifier.size(40.dp))
        Text("DataTaxi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Onglets de l'application (aussi utilisés par les captures d'écran). */
@Composable
fun AppTabs(selected: Int, onSelect: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selected,
        containerColor = Color(0xFF0A0A0A),
        contentColor = Color.White,
        indicator = { pos ->
            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[selected]), color = Color(0xFFFB0100))
        },
    ) {
        Tab(selected = selected == 0, onClick = { onSelect(0) }, text = { Text("Récurrences") })
        Tab(selected = selected == 1, onClick = { onSelect(1) }, text = { Text("Diagnostic") })
    }
}
