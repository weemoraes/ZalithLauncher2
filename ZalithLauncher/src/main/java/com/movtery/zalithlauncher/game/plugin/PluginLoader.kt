package com.movtery.zalithlauncher.game.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.game.plugin.ffmpeg.FFmpegPluginManager
import com.movtery.zalithlauncher.game.plugin.renderer.RendererPlugin
import com.movtery.zalithlauncher.game.plugin.renderer.RendererPluginManager
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.game.renderer.Renderers

/**
 * 统一插件的加载，保证仅获取一次应用列表
 */
object PluginLoader {
    private var isInitialized: Boolean = false
    private const val PACKAGE_FLAGS =
        PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES

    @JvmStatic
    @SuppressLint("QueryPermissionsNeeded")
    fun loadAllPlugins(context: Context, force: Boolean = false) {
        if (isInitialized && !force) return
        isInitialized = true

        DriverPluginManager.initDriver(context, force)
        if (force) RendererPluginManager.clearPlugin()

        val queryIntentActivities =
            context.packageManager.queryIntentActivities(
                Intent("android.intent.action.MAIN"),
                PACKAGE_FLAGS
            )
        queryIntentActivities.forEach {
            val applicationInfo = it.activityInfo.applicationInfo
            DriverPluginManager.parsePlugin(context, applicationInfo)
            RendererPluginManager.parseApkPlugin(context, applicationInfo)
            FFmpegPluginManager.parsePlugin(applicationInfo)
        }

        if (RendererPluginManager.isAvailable()) {
            val failedToLoadList: MutableList<RendererPlugin> = mutableListOf()
            RendererPluginManager.getRendererList().forEach { rendererPlugin ->
                val isSuccess = Renderers.addRenderer(
                    object : RendererInterface {
                        override fun getRendererId(): String = rendererPlugin.id

                        override fun getUniqueIdentifier(): String = rendererPlugin.uniqueIdentifier

                        override fun getRendererName(): String = rendererPlugin.displayName

                        override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { rendererPlugin.env }

                        override fun getDlopenLibrary(): Lazy<List<String>> = lazy { rendererPlugin.dlopen }

                        override fun getRendererLibrary(): String = rendererPlugin.glName

                        override fun getRendererEGL(): String = rendererPlugin.eglName
                    }
                )
                if (!isSuccess) failedToLoadList.add(rendererPlugin)
            }
            if (failedToLoadList.isNotEmpty()) RendererPluginManager.removeRenderer(failedToLoadList)
        }
    }
}