package com.salesforce.mobilesdk.mobilesdkuitest.Login

import PageObjects.*
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiSelector
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Created by bpage on 2/2/18.
 */

@RunWith(AndroidJUnit4::class)
class LoginTests {

    var app = TestApplication()
    private var device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    var timeout:Long = 30000
    var failedLoginMessage = "App did not successfully login."
    var username = "circleci@mobilesdk.com"
    var password = "test1234"

    @Before
    fun setupTestApp() {
        app.launch()
    }

    @Test
    fun testLogin() {
        val loginPage = LoginPageObject()
        loginPage.setUsername(username)
        loginPage.setPassword(password)
        loginPage.tapLogin()
        AuthorizationPageObject().tapAllow()
        Thread.sleep(timeout * 2)

        when (app.type) {
            AppType.NATIVE_JAVA, AppType.NATIVE_KOTLIN ->
                NativeAppPageObject(app).assertAppLoads()
            AppType.HYBRID_LOCAL -> {
                val title = device.findObject(UiSelector().className("android.view.View").descriptionContains("Users"))
                title.waitForExists(timeout)
                Assert.assertEquals(failedLoginMessage, "Users", title.contentDescription)
            }
            AppType.HYBRID_REMOTE -> {
                Thread.sleep(timeout)
                val title = device.findObject(UiSelector().className("android.view.View").descriptionContains("Salesforce Mobile SDK Test"))
                title.waitForExists(timeout)
                Thread.sleep(timeout / 2)
                Assert.assertEquals(failedLoginMessage, "Salesforce Mobile SDK Test", title.contentDescription)
            }
            AppType.REACT_NATIVE -> {
                ReactNativeAppPageObject().assertAppLoads()
            }
        }
    }
}