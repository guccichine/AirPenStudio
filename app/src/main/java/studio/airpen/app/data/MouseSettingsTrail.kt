package studio.airpen.app.data

fun MouseSettings.withTrailDefaults(): MouseSettings = this

val MouseSettings.resolvedTrailStyle: String
    get() = try {
        val f = this::class.java.getDeclaredField("trailStyle")
        f.isAccessible = true
        (f.get(this) as? String) ?: "comet"
    } catch (_: Throwable) {
        "comet"
    }
