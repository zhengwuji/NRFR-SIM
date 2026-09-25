package com.github.nrfr.manager

import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import android.telephony.TelephonyManager
import com.android.internal.telephony.ICarrierConfigLoader
import com.github.nrfr.model.SimCardInfo
import rikka.shizuku.ShizukuBinderWrapper

object CarrierConfigManager {

    /**
     * 枚举所有激活的 SIM 卡。用免权限的 activeModemCount 动态获取槽位数,
     * 逐槽位经隐藏 API getSubId 探测(HiddenApiBypass 已放行),
     * 不再假设设备只有两个卡槽。读取失败时对应卡槽 hasError = true,
     * UI 据此区分「无覆盖配置」与「读取失败」。
     */
    fun getSimCards(context: Context): List<SimCardInfo> {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return emptyList()
        val simCards = mutableListOf<SimCardInfo>()

        val modemCount = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.phoneCount
            }
        } catch (_: Exception) {
            2
        }.coerceAtLeast(1)

        for (slotIndex in 0 until modemCount) {
            val subId = try {
                SubscriptionManager.getSubId(slotIndex)?.firstOrNull() ?: continue
            } catch (_: Exception) {
                continue
            }
            val (config, hasError) = getCurrentConfig(subId)
            simCards.add(
                SimCardInfo(
                    slot = slotIndex + 1,
                    subId = subId,
                    carrierName = getCarrierNameBySubId(telephonyManager, subId),
                    currentConfig = config,
                    hasError = hasError
                )
            )
        }
        return simCards
    }

    /** 返回 (覆盖配置, 是否读取失败)。 */
    private fun getCurrentConfig(subId: Int): Pair<Map<String, String>, Boolean> {
        try {
            val config = carrierConfigLoader().getConfigForSubId(subId, "com.github.nrfr")
                ?: return emptyMap<String, String>() to false

            val result = mutableMapOf<String, String>()
            config.getString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)?.let {
                result[SimCardInfo.Key.COUNTRY_CODE] = it
            }
            if (config.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
                config.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)?.let {
                    result[SimCardInfo.Key.CARRIER_NAME] = it
                }
            }
            return result to false
        } catch (e: Exception) {
            return emptyMap<String, String>() to true
        }
    }

    private fun carrierConfigLoader(): ICarrierConfigLoader = ICarrierConfigLoader.Stub.asInterface(
        ShizukuBinderWrapper(
            TelephonyFrameworkInitializer
                .getTelephonyServiceManager()
                .carrierConfigServiceRegisterer
                .get()
        )
    )

    private fun getCarrierNameBySubId(telephonyManager: TelephonyManager?, subId: Int): String {
        telephonyManager ?: return ""
        return try {
            val subTelephonyManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.createForSubscriptionId(subId)
            } else {
                TelephonyManager::class.java.getMethod(
                    "createForSubscriptionId",
                    Int::class.javaPrimitiveType
                ).invoke(telephonyManager, subId) as TelephonyManager
            }
            subTelephonyManager.networkOperatorName
        } catch (e: Exception) {
            telephonyManager.networkOperatorName
        }
    }

    fun setCarrierConfig(
        context: Context,
        subId: Int,
        countryCode: String?,
        carrierName: String? = null
    ): Boolean {
        val bundle = PersistableBundle()

        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            bundle.putString(
                CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                countryCode.lowercase()
            )
        }

        if (!carrierName.isNullOrEmpty()) {
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
        }

        return overrideCarrierConfig(context, subId, bundle)
    }

    fun resetCarrierConfig(context: Context, subId: Int): Boolean {
        return overrideCarrierConfig(context, subId, null)
    }

    private fun overrideCarrierConfig(context: Context, subId: Int, bundle: PersistableBundle?): Boolean {
        return try {
            carrierConfigLoader().overrideConfig(subId, bundle, true)
            false
        } catch (e: SecurityException) {
            if (e.message?.contains("cannot be invoked by shell") == true) {
                PrivilegedCarrierConfigRunner.overrideConfig(context, subId, bundle)
                true
            } else {
                throw e
            }
        }
    }
}
