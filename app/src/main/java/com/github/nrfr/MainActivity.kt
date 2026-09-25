package com.github.nrfr

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.nrfr.ui.screens.AboutScreen
import com.github.nrfr.ui.screens.MainScreen
import com.github.nrfr.ui.screens.ShizukuNotReadyScreen
import com.github.nrfr.ui.theme.NrfrTheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var isShizukuReady by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)

    // 持有监听器引用,onDestroy 里才能真正移除(匿名 lambda 每次创建都是新对象)
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            isShizukuReady = grantResult == PackageManager.PERMISSION_GRANTED
            if (!isShizukuReady) {
                Toast.makeText(this, R.string.shizuku_permission_toast, Toast.LENGTH_LONG).show()
            }
        }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizukuStatus()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isShizukuReady = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化 Hidden API 访问
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        // 检查 Shizuku 状态
        checkShizukuStatus()

        // 注册 Shizuku 监听器(含 binder 死亡监听,Shizuku 被杀时回到未就绪界面)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        setContent {
            NrfrTheme {
                if (showAbout) {
                    AboutScreen(onBack = { showAbout = false })
                } else if (isShizukuReady) {
                    MainScreen(onShowAbout = { showAbout = true })
                } else {
                    ShizukuNotReadyScreen()
                }
            }
        }
    }

    private fun checkShizukuStatus() {
        isShizukuReady = if (Shizuku.getBinder() == null) {
            Toast.makeText(this, R.string.shizuku_not_installed_toast, Toast.LENGTH_LONG).show()
            false
        } else {
            val hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Shizuku.requestPermission(0)
            }
            hasPermission
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }
}
