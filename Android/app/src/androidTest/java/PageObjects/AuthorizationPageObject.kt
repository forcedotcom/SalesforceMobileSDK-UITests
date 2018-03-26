package PageObjects

import android.support.test.uiautomator.*
import android.util.Log

/**
 * Created by bpage on 2/23/18.
 */

class AuthorizationPageObject : BasePageObject() {

    init {
        if (isArm) {
            Log.i("uia", "Sleeping a while to let auth page load.")
            Thread.sleep(timeout)
        }
    }

    fun tapAllow() {
        val allowButton = if (isOldDevice) {
            device.findObject(UiSelector().className("android.widget.Button").index(0))
        }
        else {
            device.findObject(UiSelector().resourceId("oaapprove"))
        }

        Log.i("uia", "Waiting for allow button to be present.")
        assert(allowButton.waitForExists(timeout * 5))
        if (isArm) {
            Thread.sleep(timeout)
        }

        val webview2 = device.wait(Until.findObject(By.clazz("android.webkit.WebView")), timeout)
        Log.i("uia", "Scrolling webview.")
        webview2.scroll(Direction.DOWN, 0.5f)
        allowButton.click()

        if (isOldDevice) {
            Thread.sleep(timeout)
        }
    }
}