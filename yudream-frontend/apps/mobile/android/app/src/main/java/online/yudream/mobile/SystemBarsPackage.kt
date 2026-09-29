package online.yudream.mobile

import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager

/** 宿主手动注册的原生桥（禁止 autolink）：见 SystemBarsModule。 */
class SystemBarsPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(SystemBarsModule(reactContext))

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<out View, out ReactShadowNode<*>>> =
        emptyList()
}
