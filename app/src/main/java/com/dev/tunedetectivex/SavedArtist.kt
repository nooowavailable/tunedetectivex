package com.dev.tunedetectivex

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_artist")
data class SavedArtist(
    @PrimaryKey val id: Long,
    val lastReleaseType: String? = null,
    val picture: String? = null,
    val picture_small: String? = null,
    val picture_medium: String? = null,
    val picture_big: String? = null,
    val picture_xl: String? = null,
    val notified: Boolean = false,
    val name: String,
    var lastReleaseDate: String? = null,
    var lastReleaseTitle: String? = null,
    val profileImageUrl: String? = null
)

@Entity(tableName = "sent_notification")
data class SentNotification(
    @PrimaryKey(autoGenerate = true) val notificationHash: Int,
    val date: Long
)
