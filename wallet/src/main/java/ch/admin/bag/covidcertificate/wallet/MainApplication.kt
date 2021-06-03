package ch.admin.bag.covidcertificate.wallet

import android.app.Application
import android.os.Build
import ch.admin.bag.covidcertificate.common.net.Config
import ch.admin.bag.covidcertificate.common.net.UserAgentInterceptor

class MainApplication : Application() {

	override fun onCreate() {
		super.onCreate()
		Config.userAgent =
			UserAgentInterceptor.UserAgentGenerator { "Android;${Build.VERSION.SDK_INT};${BuildConfig.VERSION_NAME};${BuildConfig.BUILD_TIME}" }
	}
}