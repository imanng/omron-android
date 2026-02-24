package com.example.heatsense_android.ble

import java.util.UUID

/**
 * Omron 2JCIE-BL01 BLE UUIDs.
 * Base UUID: 0C4CXXXX-7700-46F4-AA96D5E974E32A54
 */
object OmronConstants {
    private const val BASE = "0c4c%04x-7700-46f4-aa96-d5e974e32a54"

    val SENSOR_SERVICE_UUID: UUID = UUID.fromString(BASE.format(0x3000))
    val LATEST_DATA_CHAR_UUID: UUID = UUID.fromString(BASE.format(0x3001))
    val LATEST_PAGE_CHAR_UUID: UUID = UUID.fromString(BASE.format(0x3002))

    /** Device name prefixes for scan filter (Env, IM-BL01, EP-BL01) */
    val OMRON_DEVICE_NAME_PREFIXES = listOf("Env", "IM", "EP")
    const val OMRON_DEVICE_NAME_FULL = "EnvSensor-BL01"
}
