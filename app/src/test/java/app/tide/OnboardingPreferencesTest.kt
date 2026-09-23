package app.tide

import androidx.test.core.app.ApplicationProvider
import app.tide.onboarding.OnboardingPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingPreferencesTest {

    @Test
    fun `onboarding has not happened until it is marked done`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("tide.onboarding", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        val prefs = OnboardingPreferences(context)
        assertFalse("a fresh install has not onboarded", prefs.completed)

        prefs.completed = true
        assertTrue(OnboardingPreferences(context).completed)
    }
}
