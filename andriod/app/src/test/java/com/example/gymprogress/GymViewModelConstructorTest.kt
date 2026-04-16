package com.example.gymprogress

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Test

class GymViewModelConstructorTest {
    @Test
    fun exposesSingleApplicationConstructorForDefaultViewModelFactory() {
        val constructor = GymViewModel::class.java.getConstructor(Application::class.java)

        assertNotNull(constructor)
    }
}
