package studio.airpen.app.data

enum class TrailStyle(val id: String, val label: String) {
    COMET("comet", "Comet"),
    SPARKLER("sparkler", "Sparkler"),
    RIBBON("ribbon", "Ribbon"),
    LASER("laser", "Laser"),
    EMBER("ember", "Ember"),
    ICE("ice", "Ice"),
    AURORA("aurora", "Aurora"),
    NEON("neon", "Neon"),
    STARDUST("stardust", "Stardust"),
    PLASMA("plasma", "Plasma"),
    LIGHTNING("lightning", "Lightning"),
    SMOKE("smoke", "Smoke"),
    RAINBOW("rainbow", "Rainbow"),
    PIXEL("pixel", "Pixel"),
    BUBBLES("bubbles", "Bubbles"),
    METEOR("meteor", "Meteor"),
    HALO("halo", "Halo"),
    CIRCUIT("circuit", "Circuit"),
    PETAL("petal", "Petal"),
    GLITCH("glitch", "Glitch"),
    CONSTELLATION("constellation", "Constellation"),
    PULSE("pulse", "Pulse"),
    FIREFLY("firefly", "Firefly"),
    PRISM("prism", "Prism"),
    VOID("void", "Void"),
    HELIX("helix", "Helix"),
    ORBIT("orbit", "Orbit"),
    DUST("dust", "Dust"),
    OFF("off", "Off");

    companion object {
        fun fromId(id: String): TrailStyle = entries.firstOrNull { it.id == id } ?: COMET
    }
}
