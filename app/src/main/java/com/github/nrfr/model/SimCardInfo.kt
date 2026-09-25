package com.github.nrfr.model

/**
 * 当前覆盖配置条目。key 取 [Key] 常量,展示文案由 UI 层通过资源解析,
 * 数据层不再携带中文文案。
 */
data class SimCardInfo(
    val slot: Int,
    val subId: Int,
    val carrierName: String,
    val currentConfig: Map<String, String> = emptyMap(),
    val hasError: Boolean = false
) {
    object Key {
        const val COUNTRY_CODE = "country_code"
        const val CARRIER_NAME = "carrier_name"
    }
}
