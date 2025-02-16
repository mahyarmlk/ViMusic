package it.vfsfitvnm.vimusic.models

import androidx.room.*


@Entity
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artistsText: String? = null,
    val durationText: String,
    val thumbnailUrl: String?,
    val lyrics: String? = null,
    val likedAt: Long? = null,
    val totalPlayTimeMs: Long = 0,
    val loudnessDb: Float? = null,
    val contentLength: Long? = null,
) {
    val formattedTotalPlayTime: String
        get() {
            val seconds = totalPlayTimeMs / 1000

            val hours = seconds / 3600

            return when  {
                hours == 0L -> "${seconds / 60}m"
                hours < 24L -> "${hours}h"
                else -> "${hours / 24}d"
            }
        }

    fun toggleLike(): Song {
        return copy(
            likedAt = if (likedAt == null) System.currentTimeMillis() else null
        )
    }
}
