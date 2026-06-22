package com.flowfuel.app.core.observability

import android.util.Log
import io.sentry.Sentry
import timber.log.Timber

/** Encaminha logs de erro (Timber.e) ao Sentry, evitando chamadas duplicadas pelo app. */
class SentryTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.ERROR) return
        if (t != null) {
            Sentry.captureException(t)
        } else {
            Sentry.captureMessage(message)
        }
    }
}
