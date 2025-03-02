package com.binissa.core.domain.model

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val apiLevel: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val screenDensity: Float,
    val totalRam: Long,
    val availableRam: Long,
    val batteryLevel: Float,
    val isCharging: Boolean,
    val networkType: String,
    val isRooted: Boolean
)

fun DeviceInfo.toMap(): Map<String, Any> {
    return mapOf(
        "manufacturer" to manufacturer,
        "model" to model,
        "osVersion" to osVersion,
        "apiLevel" to apiLevel,
        "screenWidth" to screenWidth,
        "screenHeight" to screenHeight,
        "screenDensity" to screenDensity,
        "totalRam" to totalRam,
        "availableRam" to availableRam,
        "batteryLevel" to batteryLevel,
        "isCharging" to isCharging,
        "networkType" to networkType,
        "isRooted" to isRooted
    )
}