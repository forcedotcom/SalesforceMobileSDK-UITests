package PageObjects

import android.os.Build
import android.support.test.InstrumentationRegistry
import android.support.test.uiautomator.UiDevice

/**
 * Created by bpage on 2/24/18.
 */
open class BasePageObject {
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val isOldDevice: Boolean = (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
    val isArm = Build.SUPPORTED_ABIS.first().contains("armeabi")
    var timeout:Long = if (isArm) 30000 else 5000
}