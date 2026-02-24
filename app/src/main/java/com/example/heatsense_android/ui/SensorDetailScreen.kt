package com.example.heatsense_android.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.heatsense_android.ble.ConnectionState
import com.example.heatsense_android.ble.OmronLatestData
import com.example.heatsense_android.ble.SensorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailScreen(
    deviceAddress: String,
    viewModel: SensorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.sensorState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceAddress) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.rssi?.let { rssi ->
                        Text(
                            "RSSI: ${rssi}dBm",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(
                        onClick = {
                            state.latestData?.let { data ->
                                val body = formatShareText(data, state)
                                context.startActivity(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, body)
                                    }
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val cs = state.connectionState) {
                is ConnectionState.Connected -> { }
                is ConnectionState.Connecting -> {
                    Text("Connecting…", style = MaterialTheme.typography.bodyLarge)
                }
                is ConnectionState.Error -> {
                    Text("Error: ${cs.message}", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Retry")
                    }
                }
                ConnectionState.Disconnected -> {
                    Text("Disconnected", style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.latestPage?.let { page ->
                Text(
                    if (page.memoryOn) "Memory On" else "Memory Off",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            state.latestData?.let { data ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sensorDataItems(data)) { item ->
                        SensorDataCard(
                            label = item.label,
                            value = item.value,
                            unit = item.unit
                        )
                    }
                }
            } ?: run {
                if (state.connectionState is ConnectionState.Connected) {
                    Text("Reading data…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private data class SensorDataItem(val label: String, val value: String, val unit: String)

private fun sensorDataItems(data: OmronLatestData): List<SensorDataItem> = listOf(
    SensorDataItem("Temp", "%.2f".format(data.temperatureC), "°C"),
    SensorDataItem("RH", "%.2f".format(data.relativeHumidity), "%"),
    SensorDataItem("Luminosity", data.lightLx.toString(), "lx"),
    SensorDataItem("UV Index", data.uvIndexCategory, ""),
    SensorDataItem("Pressure", "%.1f".format(data.pressureHpa), "hPa"),
    SensorDataItem("Noise", "%.2f".format(data.soundNoiseDb), "dB"),
    SensorDataItem("Discomfort", data.discomfortCategory, ""),
    SensorDataItem("Heatstroke", data.heatstrokeCategory, ""),
    SensorDataItem("Battery", "%.3f".format(data.batteryVoltageV), "V")
)

@Composable
private fun SensorDataCard(
    label: String,
    value: String,
    unit: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatShareText(data: OmronLatestData, state: SensorState): String {
    return buildString {
        appendLine("Omron 2JCIE-BL01 Sensor Data")
        state.rssi?.let { appendLine("RSSI: $it dBm") }
        appendLine("Temperature: %.2f °C".format(data.temperatureC))
        appendLine("Relative Humidity: %.2f %%".format(data.relativeHumidity))
        appendLine("Luminosity: %d lx".format(data.lightLx))
        appendLine("UV Index: %s".format(data.uvIndexCategory))
        appendLine("Pressure: %.1f hPa".format(data.pressureHpa))
        appendLine("Noise: %.2f dB".format(data.soundNoiseDb))
        appendLine("Discomfort: %s".format(data.discomfortCategory))
        appendLine("Heatstroke: %s".format(data.heatstrokeCategory))
        appendLine("Battery: %.3f V".format(data.batteryVoltageV))
    }
}
