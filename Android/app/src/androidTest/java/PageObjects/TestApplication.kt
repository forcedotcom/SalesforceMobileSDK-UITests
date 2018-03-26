package PageObjects

import android.support.test.InstrumentationRegistry
import android.content.Intent

/**
 * Created by bpage on 2/21/18.
 */

class TestApplication {
    private var packageName = InstrumentationRegistry.getArguments().get("packageName") as String
    var name = packageName.split(".").last().replace("_java", "") + "_androidApp"
    var type:AppType = when (packageName.split(".").last()) {
            "native_java" -> AppType.NATIVE_JAVA
            "native_kotlin" -> AppType.NATIVE_KOTLIN
            "hybrid_local" -> AppType.HYBRID_LOCAL
            "hybrid_remote" -> AppType.HYBRID_REMOTE
            "react_native" -> AppType.REACT_NATIVE
            else -> {
                throw IllegalArgumentException("Unknown App Type")
            }
    }

    fun launch() {
        val context = InstrumentationRegistry.getContext()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}