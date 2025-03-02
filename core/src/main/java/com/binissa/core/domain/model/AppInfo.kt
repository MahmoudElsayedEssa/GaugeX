package com.binissa.core.domain.model

data class AppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val firstInstallTime: Long,
    val lastUpdateTime: Long
)

fun AppInfo.toMap(): Map<String, Any> {
    return mapOf(
        "packageName" to packageName,
        "versionName" to versionName,
        "versionCode" to versionCode,
        "buildType" to buildType,
        "firstInstallTime" to firstInstallTime,
        "lastUpdateTime" to lastUpdateTime
    )
}