package dev.andface.galaxy.access

data class DisplaySecuritySnapshot(
    val currentDisplayId: Int?,
    val defaultDisplayId: Int,
    val displays: List<DisplaySecurityInfo>,
    val multiWindow: Boolean,
    val pictureInPicture: Boolean
)

data class DisplaySecurityInfo(
    val id: Int,
    val secure: Boolean
)

object DisplayExposurePolicy {
    fun isSecureForAccess(snapshot: DisplaySecuritySnapshot): Boolean {
        if (snapshot.multiWindow || snapshot.pictureInPicture) return false
        val currentDisplayId = snapshot.currentDisplayId ?: return false
        if (currentDisplayId != snapshot.defaultDisplayId) return false
        val currentDisplay = snapshot.displays.firstOrNull { it.id == currentDisplayId } ?: return false
        if (!currentDisplay.secure) return false
        return snapshot.displays.none { it.id != snapshot.defaultDisplayId }
    }
}
