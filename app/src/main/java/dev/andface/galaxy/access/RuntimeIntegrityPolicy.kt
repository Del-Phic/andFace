package dev.andface.galaxy.access

data class DeviceBuildSignals(
    val tags: String?,
    val fingerprint: String?,
    val model: String?,
    val manufacturer: String?,
    val brand: String?,
    val device: String?,
    val product: String?,
    val hardware: String?
)

object RuntimeIntegrityPolicy {
    fun hasHighRiskSignal(
        signals: DeviceBuildSignals,
        highRiskFilePresent: Boolean,
        adbEnabled: Boolean = false
    ): Boolean {
        if (signals.tags?.contains("test-keys", ignoreCase = true) == true) return true
        if (highRiskFilePresent) return true
        if (adbEnabled) return true
        return hasEmulatorBuildSignal(signals)
    }

    private fun hasEmulatorBuildSignal(signals: DeviceBuildSignals): Boolean {
        val fingerprint = signals.fingerprint.orEmpty().lowercase()
        val model = signals.model.orEmpty().lowercase()
        val manufacturer = signals.manufacturer.orEmpty().lowercase()
        val brand = signals.brand.orEmpty().lowercase()
        val device = signals.device.orEmpty().lowercase()
        val product = signals.product.orEmpty().lowercase()
        val hardware = signals.hardware.orEmpty().lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("sdk_gphone") ||
            model.contains("google_sdk") ||
            model.contains("android sdk built for") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            manufacturer.contains("genymotion") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("vbox86") ||
            hardware.contains("qemu") ||
            product in EMULATOR_PRODUCTS ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    private val EMULATOR_PRODUCTS = setOf(
        "google_sdk",
        "sdk",
        "sdk_x86",
        "sdk_gphone",
        "sdk_gphone64",
        "vbox86p"
    )
}
