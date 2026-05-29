package com.wmods.wppenhacer

import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.content.res.XModuleResources
import android.view.Window
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.activities.MainActivity
import com.wmods.wppenhacer.xposed.AntiUpdater
import com.wmods.wppenhacer.xposed.bridge.ScopeHook
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.downgrade.Patch
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage

class WppXposed : IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {

    private var MODULE_PATH: String? = null

    companion object {
        private var pref: XSharedPreferences? = null

        @JvmStatic
        var ResParam: XC_InitPackageResources.InitPackageResourcesParam? = null

        @JvmStatic
        fun getPref(): XSharedPreferences {
            return pref ?: XSharedPreferences(
                BuildConfig.APPLICATION_ID,
                BuildConfig.APPLICATION_ID + "_preferences"
            ).apply {
                makeWorldReadable()
                reload()
                pref = this
            }
        }
    }

    @SuppressLint("WorldReadableFiles")
    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val classLoader = lpparam.classLoader

        if (packageName == BuildConfig.APPLICATION_ID) {
            XposedHelpers.findAndHookMethod(
                MainActivity::class.java.name,
                classLoader,
                "isXposedEnabled",
                XC_MethodReplacement.returnConstant(true)
            )
            @Suppress("DEPRECATION")
            XposedHelpers.findAndHookMethod(
                PreferenceManager::class.java.name,
                classLoader,
                "getDefaultSharedPreferencesMode",
                XC_MethodReplacement.returnConstant(ContextWrapper.MODE_WORLD_READABLE)
            )
            return
        }

        AntiUpdater.hookSession(lpparam)

        Patch.handleLoadPackage(lpparam, getPref())

        ScopeHook.hook(lpparam)

        if ((packageName == FeatureLoader.PACKAGE_WPP && App.isOriginalPackage()) || packageName == FeatureLoader.PACKAGE_BUSINESS) {
            XposedBridge.log("[•] This package: ${lpparam.packageName}")

            // Load features
            FeatureLoader.start(classLoader, getPref(), lpparam.appInfo.sourceDir)

            disableSecureFlag()
        }
    }

    @Throws(Throwable::class)
    override fun handleInitPackageResources(resparam: InitPackageResourcesParam) {
        val packageName = resparam.packageName

        if (packageName != FeatureLoader.PACKAGE_WPP && packageName != FeatureLoader.PACKAGE_BUSINESS) {
            return
        }

        val modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res)
        ResParam = resparam
        val resourceClasses = listOf(
            R.array::class.java,
            R.string::class.java,
            R.drawable::class.java
        )
        resourceClasses.forEach {
            injectResources(it, modRes, resparam)
        }

    }

    private fun injectResources(
        clazz: Class<*>,
        modRes: XModuleResources?,
        resparam: InitPackageResourcesParam
    ) {
        var count = 0
        for (field in clazz.declaredFields) {
            try {
                field.isAccessible = true

                if (field.type === Int::class.javaPrimitiveType) {
                    val resId = field.getInt(null)
                    if (resId > 0x7f000000) {
                        count++
                        val replacementId = resparam.res.addResource(modRes, resId)
                        field.set(null, replacementId)
                    }
                } else if (field.type === IntArray::class.java) {
                    val resIds = field.get(null) as IntArray?
                    if (resIds != null) {
                        for (i in resIds.indices) {
                            if (resIds[i] > 0x7f000000) {
                                count++
                                resIds[i] = resparam.res.addResource(modRes, resIds[i])
                            }
                        }
                    }
                }
            } catch (_: Exception) {

            }
        }
        XposedBridge.log("Injected " + count + " resources for " + clazz.getSimpleName())
    }


    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        MODULE_PATH = startupParam.modulePath

        // Hook to bypass/suppress EPERM exceptions during close of files/file descriptors globally at Zygote level
        try {
            XposedHelpers.findAndHookMethod(
                "libcore.io.IoBridge",
                null,
                "closeAndSignalBlockedThreads",
                java.io.FileDescriptor::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val throwable = param.throwable
                        if (throwable is java.io.IOException) {
                            val message = throwable.message
                            if (message != null && (message.contains("EPERM") || message.contains("Operation not permitted"))) {
                                val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
                                    android.app.Application.getProcessName()
                                } else {
                                    try {
                                        Class.forName("android.app.ActivityThread")
                                            .getDeclaredMethod("currentProcessName")
                                            .invoke(null) as? String
                                    } catch (_: Throwable) {
                                        null
                                    }
                                }
                                if (processName != null && (processName.startsWith("com.whatsapp") || processName.startsWith("com.whatsapp.w4b"))) {
                                    XposedBridge.log("WaEnhancer: Suppressed EPERM IOException during close in WhatsApp ($processName)")
                                    param.throwable = null
                                }
                            }
                        }
                    }
                }
            )
            XposedBridge.log("WaEnhancer: Successfully hooked IoBridge in initZygote")
        } catch (t: Throwable) {
            XposedBridge.log("WaEnhancer: Failed to hook IoBridge in initZygote: " + t.message)
        }
    }

    fun disableSecureFlag() {
        XposedHelpers.findAndHookMethod(
            Window::class.java,
            "setFlags",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val flags = param.args[0] as Int
                    val mask = param.args[1] as Int
                    param.args[0] = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    param.args[1] = mask and WindowManager.LayoutParams.FLAG_SECURE.inv()
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            Window::class.java,
            "addFlags",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val flags = param.args[0] as Int
                    val newFlags = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    param.args[0] = newFlags
                    if (newFlags == 0) {
                        param.result = null
                    }
                }
            }
        )
    }
}