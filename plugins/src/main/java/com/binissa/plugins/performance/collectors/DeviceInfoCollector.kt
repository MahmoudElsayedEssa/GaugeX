package com.binissa.plugin.performance.collectors

import com.binissa.core.domain.model.toMap
import com.binissa.plugin.util.DeviceInfoHelper

class DeviceInfoCollector(
    private val deviceInfoHelper: DeviceInfoHelper
) : MetricsCollector {
    override suspend fun collect(): Map<String, Any> {
        val deviceInfo = deviceInfoHelper.getDeviceInfo().toMap()
        val appInfo = deviceInfoHelper.getAppInfo().toMap()
        return deviceInfo + appInfo
    }
}
