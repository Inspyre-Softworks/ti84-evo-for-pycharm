package com.inspyresoftworks.ti84evo.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import com.inspyresoftworks.ti84evo.ui.EvoBuildInfo
import com.inspyresoftworks.ti84evo.ui.EvoMarketplaceVersionChecker
import com.inspyresoftworks.ti84evo.ui.EvoVersionStatus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Application-wide source of truth for Marketplace validation and polling. */
@Service(Service.Level.APP)
internal class EvoMarketplaceService : Disposable {
    private val settings = ApplicationManager.getApplication().getService(EvoApplicationSettings::class.java)
    private val listeners = CopyOnWriteArrayList<(EvoVersionStatus) -> Unit>()
    private val checking = AtomicBoolean(false)
    @Volatile private var scheduledCheck: ScheduledFuture<*>? = null
    @Volatile var status: EvoVersionStatus = EvoVersionStatus.checking(EvoBuildInfo.version)
        private set

    init {
        reschedule(runImmediately = true)
    }

    fun addListener(parentDisposable: Disposable, listener: (EvoVersionStatus) -> Unit) {
        listeners += listener
        Disposer.register(parentDisposable) { listeners -= listener }
        listener(status)
    }

    fun retryNow() {
        AppExecutorUtil.getAppExecutorService().execute(::performCheck)
    }

    fun settingsChanged() {
        reschedule(runImmediately = false)
    }

    private fun reschedule(runImmediately: Boolean) {
        scheduledCheck?.cancel(false)
        val mischiefMode = EvoMischiefMode.isActive()
        val interval = settings.snapshot(mischiefMode).marketplaceCheckIntervalSeconds.toLong()
        scheduledCheck = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            ::performCheck,
            if (runImmediately) 0 else interval,
            interval,
            TimeUnit.SECONDS,
        )
        if (mischiefMode) LOG.info("[Mischief Mode] Marketplace polling interval: $interval seconds")
    }

    private fun performCheck() {
        if (!checking.compareAndSet(false, true)) return
        try {
            publish(EvoMarketplaceVersionChecker.check(EvoBuildInfo.version, PLUGIN_ID))
        } finally {
            checking.set(false)
        }
    }

    private fun publish(value: EvoVersionStatus) {
        status = value
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { listener -> listener(value) }
        }
    }

    override fun dispose() {
        scheduledCheck?.cancel(false)
        listeners.clear()
    }

    companion object {
        const val PLUGIN_ID = "com.inspyresoftworks.ti84evo"
        private val LOG = Logger.getInstance(EvoMarketplaceService::class.java)
    }
}
