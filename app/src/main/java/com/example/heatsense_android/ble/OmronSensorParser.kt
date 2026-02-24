package com.example.heatsense_android.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses Omron 2JCIE-BL01 "Latest data" characteristic (19 bytes, little-endian).
 * Byte layout: Row(1), Temp(2), RH(2), Light(2), UV(2), Pressure(2), Noise(2), Discomfort(2), Heatstroke(2), Battery(2).
 */
object OmronSensorParser {

    fun parseLatestData(data: ByteArray): OmronLatestData? {
        if (data.size < 19) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val row = buf.get().toInt() and 0xFF
        val temperatureC = buf.short.toInt() / 100.0
        val relativeHumidity = buf.short.toInt() / 100.0
        val lightLx = buf.short.toInt()
        val uvIndex = buf.short.toInt() / 100.0
        val pressureHpa = buf.short.toInt() / 10.0
        val soundNoiseDb = buf.short.toInt() / 100.0
        val discomfortIndex = buf.short.toInt() / 100.0
        val heatstrokeFactorC = buf.short.toInt() / 100.0
        val batteryMv = buf.short.toInt() and 0xFFFF
        val batteryVoltageV = batteryMv / 1000.0
        return OmronLatestData(
            rowOrSequence = row,
            temperatureC = temperatureC,
            relativeHumidity = relativeHumidity,
            lightLx = lightLx,
            uvIndex = uvIndex,
            pressureHpa = pressureHpa,
            soundNoiseDb = soundNoiseDb,
            discomfortIndex = discomfortIndex,
            heatstrokeFactorC = heatstrokeFactorC,
            batteryVoltageV = batteryVoltageV
        )
    }

    /**
     * Parses "Latest page" characteristic (9 bytes): UNIX time (4), measurement interval (2), latest page (2), latest row (1).
     * Memory is considered "On" when UNIX time is non-zero (time has been set and recording can progress).
     */
    fun parseLatestPage(data: ByteArray): OmronLatestPage? {
        if (data.size < 9) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val unixTime = buf.int.toLong() and 0xFFFFFFFFL
        val measurementIntervalSec = buf.short.toInt() and 0xFFFF
        val latestPage = buf.short.toInt() and 0xFFFF
        val latestRow = buf.get().toInt() and 0xFF
        return OmronLatestPage(
            unixTime = unixTime,
            measurementIntervalSec = measurementIntervalSec,
            latestPage = latestPage,
            latestRow = latestRow,
            memoryOn = unixTime > 0
        )
    }
}

data class OmronLatestPage(
    val unixTime: Long,
    val measurementIntervalSec: Int,
    val latestPage: Int,
    val latestRow: Int,
    val memoryOn: Boolean
)
