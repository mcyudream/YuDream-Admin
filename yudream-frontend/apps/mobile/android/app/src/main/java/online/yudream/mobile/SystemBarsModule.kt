package online.yudream.mobile

import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * 系统栏桥（导航栏）：底部导航栏颜色/图标亮度由 RN 主题侧驱动，
 * 实现深浅模式（含 App 内手动切换）下跟随页面底色。
 * 状态栏由 react-native-screens 的 Screen trait 管理（见 RootNavigator screenOptions），
 * 此处不再直接写 statusBarColor——会被 RNS 挂载后的 trait 回落覆盖。
 */
class SystemBarsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "SystemBars"

    @ReactMethod
    fun applyNavColor(navColor: String, navLightIcons: Boolean) {
        val activity = currentActivity ?: return
        // WindowInsetsController 底层走 View.setSystemUiVisibility，只能在 UI 线程触碰视图
        activity.runOnUiThread {
            val window = activity.window
            try {
                window.navigationBarColor = Color.parseColor(navColor)
            } catch (ignored: IllegalArgumentException) {
                return@runOnUiThread
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                controller.isAppearanceLightNavigationBars = navLightIcons
            }
        }
    }
}
