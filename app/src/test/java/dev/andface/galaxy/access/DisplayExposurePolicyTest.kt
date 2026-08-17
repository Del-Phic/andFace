package dev.andface.galaxy.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayExposurePolicyTest {
    @Test
    fun acceptsSingleSecureDefaultDisplay() {
        val snapshot = snapshot()

        assertTrue(DisplayExposurePolicy.isSecureForAccess(snapshot))
    }

    @Test
    fun rejectsMultiWindowAndPictureInPicture() {
        assertFalse(DisplayExposurePolicy.isSecureForAccess(snapshot(multiWindow = true)))
        assertFalse(DisplayExposurePolicy.isSecureForAccess(snapshot(pictureInPicture = true)))
    }

    @Test
    fun rejectsMissingOrNonDefaultCurrentDisplay() {
        assertFalse(DisplayExposurePolicy.isSecureForAccess(snapshot(currentDisplayId = null)))
        assertFalse(DisplayExposurePolicy.isSecureForAccess(snapshot(currentDisplayId = 4)))
    }

    @Test
    fun rejectsInsecureDefaultDisplay() {
        assertFalse(
            DisplayExposurePolicy.isSecureForAccess(
                snapshot(displays = listOf(DisplaySecurityInfo(id = DEFAULT_DISPLAY_ID, secure = false)))
            )
        )
    }

    @Test
    fun rejectsExternalDisplayPresence() {
        assertFalse(
            DisplayExposurePolicy.isSecureForAccess(
                snapshot(
                    displays = listOf(
                        DisplaySecurityInfo(id = DEFAULT_DISPLAY_ID, secure = true),
                        DisplaySecurityInfo(id = 8, secure = true)
                    )
                )
            )
        )
    }

    private fun snapshot(
        currentDisplayId: Int? = DEFAULT_DISPLAY_ID,
        displays: List<DisplaySecurityInfo> = listOf(DisplaySecurityInfo(id = DEFAULT_DISPLAY_ID, secure = true)),
        multiWindow: Boolean = false,
        pictureInPicture: Boolean = false
    ): DisplaySecuritySnapshot {
        return DisplaySecuritySnapshot(
            currentDisplayId = currentDisplayId,
            defaultDisplayId = DEFAULT_DISPLAY_ID,
            displays = displays,
            multiWindow = multiWindow,
            pictureInPicture = pictureInPicture
        )
    }

    private companion object {
        const val DEFAULT_DISPLAY_ID = 0
    }
}
