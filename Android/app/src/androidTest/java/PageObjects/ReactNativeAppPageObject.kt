package PageObjects

import android.support.test.uiautomator.UiSelector
import android.util.Log
import org.junit.Assert

/**
 * Created by bpage on 3/6/18.
 */

class ReactNativeAppPageObject : BasePageObject() {

    init {
        timeout *= 3
    }

    fun assertAppLoads() {
        var alertWindow = device.findObject(UiSelector().resourceId("android:id/alertTitle"))
        if (alertWindow.exists()) {
            Log.i("uia", "React Native requesting overlay permission.")
            // Tap Continue Button
            device.findObject(UiSelector().resourceId("android:id/button1")).click()
            Thread.sleep(timeout)
        }

        val title = device.findObject(UiSelector().className("android.widget.TextView").index(0))
        title.waitForExists(timeout * 5)
        Assert.assertEquals("App did not successfully login.", "Mobile SDK Sample App", title.text)
    }
}