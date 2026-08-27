package dev.ayaan.tvremote.remote

/**
 * A handful of streaming apps launchable in one tap. The two adapters open
 * them by entirely different means -- Android TV via an app-link URL over
 * the wire protocol, Fire TV via its package name over an ADB shell -- so
 * each app carries both.
 */
enum class KnownApp(val label: String, val androidTvLink: String, val fireTvPackage: String) {
    YOUTUBE("YouTube", "https://www.youtube.com/tv", "com.amazon.firetv.youtube"),
    NETFLIX("Netflix", "https://www.netflix.com/title", "com.netflix.ninja"),
    PRIME_VIDEO("Prime Video", "https://app.primevideo.com", "com.amazon.firebat"),
    HOTSTAR("Hotstar", "https://www.hotstar.com", "in.startv.hotstar"),
}
