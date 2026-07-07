package com.example.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.R

object ArtworkProcessor {
    /**
     * Processes the artwork source URL/URI based on album art settings in SharedPreferences:
     * - wifiOnly ("pref_wifi_only"): If enabled and not connected to Wi-Fi, returns a local fallback placeholder.
     * - hqArt ("pref_hq_art"): If disabled (low definition), downsamples Unsplash images (changes w=500/1000 parameter to w=120) to save bandwidth and load faster.
     */
    fun getProcessedArtworkSource(context: Context, url: String): Any {
        if (url.isEmpty()) {
            return R.drawable.ic_launcher_foreground
        }

        val sharedPrefs = context.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)
            ?: return url

        val wifiOnly = sharedPrefs.getBoolean("pref_wifi_only", false)
        val hqArt = sharedPrefs.getBoolean("pref_hq_art", true)

        // 1. Check Wi-Fi restriction if active
        if (wifiOnly && url.startsWith("http")) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val isWifi = if (connectivityManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                } else {
                    @Suppress("DEPRECATION")
                    val networkInfo = connectivityManager.activeNetworkInfo
                    @Suppress("DEPRECATION")
                    networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI
                }
            } else {
                false
            }

            if (!isWifi) {
                // Return fallback local resource instead of downloading from network
                return R.drawable.ic_launcher_foreground
            }
        }

        // 2. Downsample for low-definition setting to save local memory & bandwidth
        if (!hqArt && url.startsWith("http")) {
            if (url.contains("images.unsplash.com")) {
                // Replace Unsplash size w=XXX with w=120 and add lower quality parameter q=30
                var modifiedUrl = url
                modifiedUrl = modifiedUrl.replace(Regex("w=\\d+"), "w=120")
                modifiedUrl = if (modifiedUrl.contains("auto=format")) {
                    modifiedUrl.replace("auto=format", "auto=format&q=30")
                } else {
                    "$modifiedUrl&q=30"
                }
                return modifiedUrl
            }
        }

        return url
    }
}
