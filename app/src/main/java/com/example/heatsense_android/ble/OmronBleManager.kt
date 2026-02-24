package com.example.heatsense_android.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScannedDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Int
)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class SensorState(
    val latestData: OmronLatestData? = null,
    val latestPage: OmronLatestPage? = null,
    val rssi: Int? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected
)

@SuppressLint("MissingPermission")
class OmronBleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var pollingJob: Job? = null
    private var rssiJob: Job? = null

    private val _scanResults = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scanResults: StateFlow<List<ScannedDevice>> = _scanResults.asStateFlow()

    private val _sensorState = MutableStateFlow(SensorState())
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: ""
            val advertisesOmronService = result.scanRecord?.serviceUuids?.any { parcelUuid ->
                parcelUuid.getUuid() == OmronConstants.SENSOR_SERVICE_UUID
            } == true
            val nameMatches = name.isNotEmpty() &&
                OmronConstants.OMRON_DEVICE_NAME_PREFIXES.any { name.startsWith(it) }
            if (nameMatches || advertisesOmronService) {
                val displayName = when {
                    name.isNotEmpty() -> name
                    advertisesOmronService -> "Omron Sensor"
                    else -> "Unknown"
                }
                val existing = _scanResults.value
                val updated = existing.filter { it.address != device.address } +
                    ScannedDevice(device, displayName, device.address, result.rssi)
                _scanResults.value = updated.sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanResults.value = emptyList()
        }
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    fun startScan() {
        if (!isBluetoothAvailable() || bleScanner == null) return
        _scanResults.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            bleScanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            // no BLUETOOTH_SCAN permission
        }
    }

    fun stopScan() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) { }
    }

    fun connect(device: BluetoothDevice) {
        disconnect()
        _sensorState.value = _sensorState.value.copy(connectionState = ConnectionState.Connecting)
        try {
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            _sensorState.value = _sensorState.value.copy(
                connectionState = ConnectionState.Error("Permission denied")
            )
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
        pollingJob = null
        rssiJob?.cancel()
        rssiJob = null
        try {
            gatt?.close()
        } catch (_: Exception) { }
        gatt = null
        _sensorState.value = SensorState(connectionState = ConnectionState.Disconnected)
    }

    fun refresh() {
        readLatestData()
        readLatestPage()
        readRssi()
    }

    private fun readLatestData() {
        val g = gatt ?: return
        val service = g.getService(OmronConstants.SENSOR_SERVICE_UUID) ?: return
        val char = service.getCharacteristic(OmronConstants.LATEST_DATA_CHAR_UUID) ?: return
        try {
            g.readCharacteristic(char)
        } catch (_: SecurityException) { }
    }

    private fun readLatestPage() {
        val g = gatt ?: return
        val service = g.getService(OmronConstants.SENSOR_SERVICE_UUID) ?: return
        val char = service.getCharacteristic(OmronConstants.LATEST_PAGE_CHAR_UUID) ?: return
        try {
            g.readCharacteristic(char)
        } catch (_: SecurityException) { }
    }

    private fun readRssi() {
        try {
            gatt?.readRemoteRssi()
        } catch (_: SecurityException) { }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                withContext(Dispatchers.Main) {
                    readLatestData()
                    readLatestPage()
                }
            }
        }
        rssiJob?.cancel()
        rssiJob = scope.launch {
            while (isActive) {
                delay(RSSI_INTERVAL_MS)
                withContext(Dispatchers.Main) { readRssi() }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
                _sensorState.value = _sensorState.value.copy(connectionState = ConnectionState.Connected)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                this@OmronBleManager.gatt = null
                _sensorState.value = _sensorState.value.copy(
                    connectionState = if (status == BluetoothGatt.GATT_SUCCESS)
                        ConnectionState.Disconnected
                    else
                        ConnectionState.Error("Disconnected: $status")
                )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readLatestData()
                readLatestPage()
                readRssi()
                startPolling()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            when (characteristic.uuid) {
                OmronConstants.LATEST_DATA_CHAR_UUID -> {
                    characteristic.getValue()?.let { data ->
                        OmronSensorParser.parseLatestData(data)?.let { parsed ->
                            _sensorState.value = _sensorState.value.copy(latestData = parsed)
                        }
                    }
                }
                OmronConstants.LATEST_PAGE_CHAR_UUID -> {
                    characteristic.getValue()?.let { data ->
                        OmronSensorParser.parseLatestPage(data)?.let { parsed ->
                            _sensorState.value = _sensorState.value.copy(latestPage = parsed)
                        }
                    }
                }
                else -> { }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _sensorState.value = _sensorState.value.copy(rssi = rssi)
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L   // 1 minute
        private const val RSSI_INTERVAL_MS = 60_000L  // 1 minute
    }
}
