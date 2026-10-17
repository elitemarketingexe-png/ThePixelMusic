/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package unshoo.ianshulyadav.pixelmusic.innertube.models

import androidx.compose.runtime.Immutable

@Immutable
data class MoodAndGenreItemData(
    val title: String,
    val stripeColor: Long? = null,
    val browseId: String = "FEmusic_moods_and_genres_category",
    val params: String? = null,
    val thumbnailUrl: String? = null
)

@Immutable
data class MoodAndGenreSection(
    val title: String,
    val items: List<MoodAndGenreItemData>
)
