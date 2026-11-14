package io.github.staninna.warosso.data

import android.content.Context
import android.util.Log
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.offset
import io.github.staninna.warosso.domain.rounded
import io.github.staninna.warosso.ui.map.STYLE_DARK
import io.github.staninna.warosso.ui.map.STYLE_LIGHT
import io.github.staninna.warosso.ui.map.initMapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.geometry.LatLng as MapLatLng

/**
 * Keeps the complex's map tiles on the phone, for both the light and the
 * dark style. One region per style; a new polygon replaces the old regions.
 */
class OfflineTiles(private val context: Context) {

    /**
     * Call on the main thread. Does nothing when the regions for this polygon are already there,
     * so starting the app offline never throws away tiles it cannot fetch again.
     */
    fun cache(polygon: List<LatLng>) {
        if (polygon.size < 3) return
        initMapLibre(context)
        val manager = OfflineManager.getInstance(context)

        // Some air around the polygon, so panning a little does not hit a blank edge.
        val northEast = LatLng(polygon.maxOf { it.lat }, polygon.maxOf { it.lon }).offset(MARGIN_M, 45.0).rounded()
        val southWest = LatLng(polygon.minOf { it.lat }, polygon.minOf { it.lon }).offset(MARGIN_M, 225.0).rounded()
        val bounds = LatLngBounds.Builder()
            .include(MapLatLng(northEast.lat, northEast.lon))
            .include(MapLatLng(southWest.lat, southWest.lon))
            .build()
        // A region's metadata names the style and the area it was made for.
        val wanted = listOf(STYLE_LIGHT, STYLE_DARK).associateBy { style -> "$style $northEast $southWest" }
        val pixelRatio = context.resources.displayMetrics.density

        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val existing = offlineRegions.orEmpty()
                if (existing.map { String(it.metadata) }.toSet() == wanted.keys) return
                existing.forEach { it.delete(LogDeleteError) }
                wanted.forEach { (key, style) ->
                    val definition = OfflineTilePyramidRegionDefinition(style, bounds, MIN_ZOOM, MAX_ZOOM, pixelRatio)
                    manager.createOfflineRegion(definition, key.toByteArray(), StartDownload)
                }
            }

            override fun onError(error: String) {
                Log.w(TAG, "Could not list offline regions: $error")
            }
        })
    }

    private object StartDownload : OfflineManager.CreateOfflineRegionCallback {
        override fun onCreate(offlineRegion: OfflineRegion) {
            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
        }

        override fun onError(error: String) {
            Log.w(TAG, "Could not create an offline region: $error")
        }
    }

    private object LogDeleteError : OfflineRegion.OfflineRegionDeleteCallback {
        override fun onDelete() = Unit

        override fun onError(error: String) {
            Log.w(TAG, "Could not delete an offline region: $error")
        }
    }

    private companion object {
        const val TAG = "OfflineTiles"
        const val MARGIN_M = 150.0
        const val MIN_ZOOM = 12.0
        const val MAX_ZOOM = 20.0
    }
}
