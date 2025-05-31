package com.dev.tunedetectivex

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupManager(private val context: Context, private val savedArtistDao: SavedArtistDao, private val apiService: DeezerApiService) {

    private val gson = Gson()

    fun createBackup(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val artists = savedArtistDao.getAll()
                val sharedPreferences =
                    context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                val fetchInterval = sharedPreferences.getInt("fetchInterval", 90)
                val releaseAgeWeeks = sharedPreferences.getInt("releaseAgeWeeks", 4)
                val fetchDelay = sharedPreferences.getInt("fetchDelay", 1)
                val autoLoadReleases = sharedPreferences.getBoolean("autoLoadReleases", true)
                val itunesSupportEnabled = sharedPreferences.getBoolean("itunesSupportEnabled", false)
                val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"

                val backupData = BackupData(
                    artists = artists,
                    fetchInterval = fetchInterval,
                    releaseAgeWeeks = releaseAgeWeeks,
                    fetchDelay = fetchDelay,
                    autoLoadReleases = autoLoadReleases,
                    itunesSupportEnabled = itunesSupportEnabled,
                    networkType = networkType
                )

                val json = gson.toJson(backupData)

                withContext(Dispatchers.Main) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray(Charsets.UTF_8))
                        Toast.makeText(
                            context,
                            "TDX backup successfully saved!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error saving the backup: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    fun restoreBackup(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Importing backup...", Toast.LENGTH_LONG).show()
            }

            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    val backupData = gson.fromJson(json, BackupData::class.java)

                    val artistsToInsert = backupData.artists?.map {
                        val fixedDeezerId = it.deezerId ?: it.id
                        it.copy(deezerId = fixedDeezerId)
                    } ?: emptyList()

                    if (artistsToInsert.isNotEmpty()) {
                        savedArtistDao.insertAll(artistsToInsert)

                        artistsToInsert.forEach { artist ->
                            savedArtistDao.setNotifyOnNewRelease(artist.id, true)
                        }
                    }

                    context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).edit {
                        backupData.fetchInterval?.let { putInt("fetchInterval", it) }
                        backupData.releaseAgeWeeks?.let { putInt("releaseAgeWeeks", it) }
                        backupData.fetchDelay?.let { putInt("fetchDelay", it) }
                        backupData.autoLoadReleases?.let { putBoolean("autoLoadReleases", it) }
                        backupData.itunesSupportEnabled?.let {
                            putBoolean(
                                "itunesSupportEnabled",
                                it
                            )
                        }
                        backupData.networkType?.let { putString("networkType", it) }
                    }

                    withContext(Dispatchers.Main) {
                        if (context is SettingsActivity) {
                            context.refreshSettingsUI()
                        }
                    }

                    val fetchJobs = artistsToInsert.map { artist ->
                        launch {
                            try {
                                val deezerId = artist.deezerId ?: return@launch
                                val artistDetailsResponse = apiService.getArtistDetails(deezerId).execute()
                                val artistDetails = artistDetailsResponse.body()
                                val profileImageUrl = artistDetails?.picture_xl ?: artist.profileImageUrl ?: ""

                                savedArtistDao.updateArtistDetails(artist.id, profileImageUrl)

                                val releasesResponse = apiService.getArtistReleases(deezerId, 0).execute()
                                val releases = releasesResponse.body()?.data ?: emptyList()

                                releases.forEach { release ->
                                    savedArtistDao.insertRelease(
                                        ReleaseItem(
                                            id = release.id,
                                            title = release.title,
                                            artistName = artist.name,
                                            albumArtUrl = release.getBestCoverUrl(),
                                            releaseDate = release.release_date,
                                            apiSource = "Deezer"
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    fetchJobs.forEach { it.join() }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Backup successfully restored and updated!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error while restoring the backup: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    data class BackupData(
        val artists: List<SavedArtist>? = null,
        val fetchInterval: Int? = null,
        val releaseAgeWeeks: Int? = null,
        val fetchDelay: Int? = null,
        val autoLoadReleases: Boolean? = null,
        val itunesSupportEnabled: Boolean? = null,
        val networkType: String? = null
    )
}