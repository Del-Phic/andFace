package dev.andface.galaxy.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeIntegrityPolicyTest {
    @Test
    fun acceptsNormalGalaxyReleaseBuildSignals() {
        val signals = DeviceBuildSignals(
            tags = "release-keys",
            fingerprint = "samsung/dm3qxxx/dm3q:15/AP3A.240905.015.A2/12345678:user/release-keys",
            model = "SM-S918N",
            manufacturer = "samsung",
            brand = "samsung",
            device = "dm3q",
            product = "dm3qxxx",
            hardware = "qcom"
        )

        assertFalse(RuntimeIntegrityPolicy.hasHighRiskSignal(signals, highRiskFilePresent = false))
    }

    @Test
    fun rejectsTestKeysAndHighRiskFiles() {
        assertTrue(
            RuntimeIntegrityPolicy.hasHighRiskSignal(
                normalSignals(tags = "release-keys test-keys"),
                highRiskFilePresent = false
            )
        )
        assertTrue(RuntimeIntegrityPolicy.hasHighRiskSignal(normalSignals(), highRiskFilePresent = true))
    }

    @Test
    fun rejectsAdbEnabledFieldDevice() {
        assertTrue(
            RuntimeIntegrityPolicy.hasHighRiskSignal(
                normalSignals(),
                highRiskFilePresent = false,
                adbEnabled = true
            )
        )
    }

    @Test
    fun rejectsAndroidSdkEmulatorSignals() {
        assertTrue(
            RuntimeIntegrityPolicy.hasHighRiskSignal(
                normalSignals(
                    fingerprint = "google/sdk_gphone64_x86_64/emu64x:15/AP31:userdebug/dev-keys",
                    model = "sdk_gphone64_x86_64",
                    brand = "google",
                    device = "emu64x",
                    product = "sdk_gphone64",
                    hardware = "ranchu"
                ),
                highRiskFilePresent = false
            )
        )
    }

    @Test
    fun rejectsGenericAndGenymotionSignals() {
        assertTrue(
            RuntimeIntegrityPolicy.hasHighRiskSignal(
                normalSignals(
                    fingerprint = "generic/vbox86p/vbox86p:9/PQ3A:userdebug/test-keys",
                    model = "Android SDK built for x86",
                    manufacturer = "Genymotion",
                    brand = "generic",
                    device = "generic_x86",
                    product = "vbox86p",
                    hardware = "vbox86"
                ),
                highRiskFilePresent = false
            )
        )
    }

    private fun normalSignals(
        tags: String? = "release-keys",
        fingerprint: String? = "samsung/dm3qxxx/dm3q:15/AP3A.240905.015.A2/12345678:user/release-keys",
        model: String? = "SM-S918N",
        manufacturer: String? = "samsung",
        brand: String? = "samsung",
        device: String? = "dm3q",
        product: String? = "dm3qxxx",
        hardware: String? = "qcom"
    ): DeviceBuildSignals {
        return DeviceBuildSignals(
            tags = tags,
            fingerprint = fingerprint,
            model = model,
            manufacturer = manufacturer,
            brand = brand,
            device = device,
            product = product,
            hardware = hardware
        )
    }
}
