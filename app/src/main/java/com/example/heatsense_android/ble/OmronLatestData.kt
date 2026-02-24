package com.example.heatsense_android.ble

/**
 * Parsed "Latest data" from Omron 2JCIE-BL01 characteristic 0x3001 (19 bytes).
 * Scales per communication manual: Temperature 0.01°C, RH 0.01%, etc.
 */
data class OmronLatestData(
    val rowOrSequence: Int,
    val temperatureC: Double,
    val relativeHumidity: Double,
    val lightLx: Int,
    val uvIndex: Double,
    val pressureHpa: Double,
    val soundNoiseDb: Double,
    val discomfortIndex: Double,
    val heatstrokeFactorC: Double,
    val batteryVoltageV: Double
) {
    /** UV Index category (WHO/EPA: 0-2 Low, 3-5 Moderate, 6-7 High, 8-10 Very High, 11+ Extreme) */
    val uvIndexCategory: String get() = uvIndexToCategory(uvIndex)

    /** Discomfort category (typical index ranges: &lt;55 Cold, 55-60 Comfortable, 60-75 Warm, 75+ Hot) */
    val discomfortCategory: String get() = discomfortToCategory(discomfortIndex)

    /** Heatstroke/WBGT category (Safe &lt;25°C, Caution 25-28, Warning 28-31, Danger 31+) */
    val heatstrokeCategory: String get() = heatstrokeToCategory(heatstrokeFactorC)
}

/** WHO/EPA UV Index ranges → category label */
fun uvIndexToCategory(uvIndex: Double): String = when {
    uvIndex < 3 -> "Low"
    uvIndex < 6 -> "Medium"
    uvIndex < 8 -> "High"
    uvIndex < 11 -> "Very High"
    else -> "Extreme"
}

/** Discomfort index (55–85 typical) → category */
fun discomfortToCategory(index: Double): String = when {
    index < 55 -> "Cold"
    index < 60 -> "Comfortable"
    index < 75 -> "Warm"
    else -> "Hot"
}

/** WBGT-approx heatstroke factor (°C) → category */
fun heatstrokeToCategory(wbgtC: Double): String = when {
    wbgtC < 25 -> "Safe"
    wbgtC < 28 -> "Caution"
    wbgtC < 31 -> "Warning"
    else -> "Danger"
}
