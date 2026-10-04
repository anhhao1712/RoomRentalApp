package vn.edu.thuetro.data

import android.content.Context
import org.osmdroid.config.Configuration

object OsmConfig {

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext

        Configuration.getInstance().apply {
            load(
                appContext,
                appContext.getSharedPreferences(
                    "osmdroid",
                    Context.MODE_PRIVATE
                )
            )

            userAgentValue = appContext.packageName
        }

        initialized = true
    }
}