package PageObjects.LoginPageObjects

import android.util.Log
import androidx.test.uiautomator.UiSelector
import pageobjects.BasePageObject
import pageobjects.loginpageobjects.LOGIN_RESOURCE_ID
import pageobjects.loginpageobjects.PASSWORD_RESOURCE_ID
import pageobjects.loginpageobjects.USERNAME_RESOURCE_ID

class NativeLoginPageObject: BasePageObject() {
    fun setUsername(name: String) {
        val usernameField = device.findObject(UiSelector().resourceId(USERNAME_RESOURCE_ID))
        Log.i("uia", "Waiting for username filed to be present.")
        assert(usernameField.waitForExists(timeout))
        usernameField.setText(name)
    }

    fun setPassword(password: String) {
        val passwordField = device.findObject(UiSelector().resourceId(PASSWORD_RESOURCE_ID))
        Log.i("uia", "Waiting for password filed to be present.")
        assert(passwordField.waitForExists(timeout))
        passwordField.setText(password)
    }

    fun tapLogin() {
        val loginButton = device.findObject(UiSelector().resourceId(LOGIN_RESOURCE_ID))
        assert(loginButton.waitForExists(timeout))
        loginButton.click()
    }
}