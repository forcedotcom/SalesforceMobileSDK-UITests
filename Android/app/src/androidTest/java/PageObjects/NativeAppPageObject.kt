package PageObjects

import android.support.test.uiautomator.UiSelector
import org.junit.Assert

/**
 * Created by bpage on 2/24/18.
 */
class NativeAppPageObject(private val app: TestApplication) : BasePageObject() {

    fun assertAppLoads() {
        val titleBar = device.findObject(UiSelector().className("android.widget.TextView").index(0))
        titleBar.waitForExists(timeout * 10)
        Assert.assertEquals("App did not successfully testLogin.", app.name, titleBar.text)
    }
}