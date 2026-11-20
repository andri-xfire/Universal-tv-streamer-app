package com.universalstream.tv

import android.app.Application
import com.universalstream.tv.core.DeviceProfile
import com.universalstream.tv.core.NetworkMonitor
import com.universalstream.tv.core.UserAgentProvider
import com.universalstream.tv.data.AppDatabase
import com.universalstream.tv.data.BrowserRepository
import com.universalstream.tv.data.SettingsRepository
import com.universalstream.tv.data.settingsDataStore
import com.universalstream.tv.resolver.ShortUrlResolver
import com.universalstream.tv.resolver.UrlResolutionService
import com.universalstream.tv.resolver.WebViewRedirectResolver
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container: small, explicit, and free of reflection (good for 1 GB devices). */
class AppContainer(private val app: Application) {

    val deviceProfile: DeviceProfile by lazy { DeviceProfile.detect(app) }

    private val database: AppDatabase by lazy { AppDatabase.create(app) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(app.settingsDataStore) }

    val browserRepository: BrowserRepository by lazy {
        BrowserRepository(
            historyDao = database.historyDao(),
            favoriteDao = database.favoriteDao(),
            historyLimit = if (deviceProfile.isLowPerformance) 100 else 300,
        )
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(app) }

    val userAgentProvider: UserAgentProvider by lazy { UserAgentProvider(app, settingsRepository) }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(2, 1, TimeUnit.MINUTES))
            .build()
    }

    val urlResolutionService: UrlResolutionService by lazy {
        UrlResolutionService(
            httpResolver = ShortUrlResolver(
                baseClient = okHttpClient,
                userAgent = { userAgentProvider.effectiveUserAgent() },
                isNetworkAvailable = { networkMonitor.isCurrentlyOnline() },
            ),
            browserResolver = WebViewRedirectResolver(app),
            userAgentProvider = userAgentProvider,
        )
    }
}
