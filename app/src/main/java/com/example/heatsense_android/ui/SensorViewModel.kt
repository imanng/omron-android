package com.example.heatsense_android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heatsense_android.ble.OmronBleManager
import com.example.heatsense_android.ble.ScannedDevice
import com.example.heatsense_android.ble.SensorState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = OmronBleManager(application)

    val scanResults = bleManager.scanResults.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val sensorState = bleManager.sensorState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SensorState()
    )

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(device: ScannedDevice) {
        viewModelScope.launch {
            bleManager.connect(device.device)
        }
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun refresh() {
        bleManager.refresh()
    }

    override fun onCleared() {
        bleManager.disconnect()
        bleManager.stopScan()
        super.onCleared()
    }
}
