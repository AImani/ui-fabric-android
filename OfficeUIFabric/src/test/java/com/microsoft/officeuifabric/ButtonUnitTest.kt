package com.microsoft.officeuifabric

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
public class ButtonUnitTest {
    @Test
    fun testSimpleButton() {
        val button = Button(RuntimeEnvironment.systemContext)
        Assert.assertNotNull(button)
    }
}