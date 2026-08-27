package studio.airpen.app.data

object TrailPrefs {
    @Volatile var style: String = "comet"
    @Volatile var thickness: Float = 1f
    @Volatile var length: Float = 1f
    @Volatile var intensity: Float = 1f
    @Volatile var show: Boolean = true
}
