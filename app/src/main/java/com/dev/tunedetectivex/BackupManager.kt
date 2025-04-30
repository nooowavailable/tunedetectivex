package com.dev.tunedetectivex

import android.content.Context
import android.net.Uri
import android.widget.Toast
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
                    context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                val fetchInterval = sharedPreferences.getInt("fetchInterval", 90)
                val releaseAgeWeeks = sharedPreferences.getInt("releaseAgeWeeks", 4)
                val fetchDelay = sharedPreferences.getInt("fetchDelay", 1)
                val backupData = BackupData(
                    artists = artists,
                    fetchInterval = fetchInterval,
                    releaseAgeWeeks = releaseAgeWeeks,
                    fetchDelay = fetchDelay
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

                    val fixedArtists = backupData.artists.map {
                        val fixedDeezerId = it.deezerId ?: it.id
                        it.copy(deezerId = fixedDeezerId)
                    }

                    savedArtistDao.insertAll(fixedArtists)

                    fixedArtists.forEach { artist ->
                        savedArtistDao.setNotifyOnNewRelease(artist.id, true)
                    }

                    val sharedPreferences =
                        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                    with(sharedPreferences.edit()) {
                        putInt("fetchInterval", backupData.fetchInterval)
                        putInt("releaseAgeWeeks", backupData.releaseAgeWeeks)
                        putInt("fetchDelay", backupData.fetchDelay)
                        apply()
                    }

                    withContext(Dispatchers.Main) {
                        if (context is SettingsActivity) {
                            context.refreshSettingsUI()
                        }
                    }

                    val fetchJobs = fixedArtists.map { artist ->
                        launch {
                            try {
                                val artistDetails =
                                    apiService.getArtistDetails(artist.deezerId ?: return@launch)
                                        .execute().body()
                                val profileImageUrl =
                                    artistDetails?.picture_xl ?: artist.profileImageUrl ?: ""

                                savedArtistDao.updateArtistDetails(artist.id, profileImageUrl)

                                val releases = apiService.getArtistReleases(
                                    artist.deezerId ?: return@launch,
                                    0
                                ).execute().body()?.data ?: emptyList()

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
        val artists: List<SavedArtist>,
        val fetchInterval: Int,
        val releaseAgeWeeks: Int,
        val fetchDelay: Int
    )
}