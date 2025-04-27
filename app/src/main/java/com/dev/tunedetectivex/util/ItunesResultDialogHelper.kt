package com.dev.tunedetectivex.util

import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dev.tunedetectivex.ItunesArtistResultAdapter
import com.dev.tunedetectivex.R
import com.dev.tunedetectivex.SavedArtistItem
import com.dev.tunedetectivex.api.ITunesApiService
import com.dev.tunedetectivex.models.ItunesArtistDisplayItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ItunesResultDialogHelper {

    private const val PREF_KEY_HIDE_DIALOG = "hideItunesSummaryDialog"

    fun showResultSummaryDialog(
        context: Context,
        deezerUpdated: Int,
        itunesUpdated: Int,
        notMatchedArtists: List<SavedArtistItem>,
        coroutineScope: CoroutineScope,
        onDeleteArtist: (SavedArtistItem) -> Unit,
        onRescanArtist: suspend (SavedArtistItem, String?) -> Unit,
        onManualSelect: suspend (SavedArtistItem, Long) -> Unit
    ) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_KEY_HIDE_DIALOG, false)) return

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_itunes_summary, null)
        val messageText = view.findViewById<TextView>(R.id.textSummaryMessage)
        val checkbox = view.findViewById<CheckBox>(R.id.checkboxHideDialog)

        val summary = buildString {
            appendLine(context.getString(R.string.itunes_summary_deezer_ids, deezerUpdated))
            appendLine(context.getString(R.string.itunes_summary_itunes_ids, itunesUpdated))

            if (notMatchedArtists.isNotEmpty()) {
                appendLine()
                appendLine(context.getString(R.string.itunes_summary_missing_header))
                notMatchedArtists.forEach {
                    appendLine("• ${it.name}")
                }
            } else {
                appendLine()
                appendLine(context.getString(R.string.itunes_summary_no_matches))
            }
        }

        messageText.text = summary

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.itunes_summary_title))
            .setView(view)
            .setPositiveButton("OK") { dialog, _ ->
                if (checkbox.isChecked) {
                    prefs.edit { putBoolean(PREF_KEY_HIDE_DIALOG, true) }
                }
                dialog.dismiss()
            }
            .setNeutralButton(context.getString(R.string.itunes_summary_manage_missing)) { _, _ ->
                showMissingArtistListDialog(
                    context,
                    notMatchedArtists,
                    coroutineScope,
                    onDeleteArtist,
                    onRescanArtist,
                    onManualSelect
                )
            }
            .show()
    }

    private fun showMissingArtistListDialog(
        context: Context,
        missingArtists: List<SavedArtistItem>,
        coroutineScope: CoroutineScope,
        onDeleteArtist: (SavedArtistItem) -> Unit,
        onRescanArtist: suspend (SavedArtistItem, String?) -> Unit,
        onManualSelect: suspend (SavedArtistItem, Long) -> Unit
    ) {
        val artistNames = missingArtists.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.itunes_missing_artists_title))
            .setItems(artistNames) { _, which ->
                val selectedArtist = missingArtists[which]
                showArtistActionDialog(
                    context,
                    selectedArtist,
                    coroutineScope,
                    onDeleteArtist,
                    onRescanArtist,
                    onManualSelect
                )
            }
            .setNegativeButton(context.getString(R.string.back), null)
            .show()
    }

    private fun showArtistActionDialog(
        context: Context,
        artist: SavedArtistItem,
        coroutineScope: CoroutineScope,
        onDeleteArtist: (SavedArtistItem) -> Unit,
        onRescanArtist: suspend (SavedArtistItem, String?) -> Unit,
        onManualSelect: suspend (SavedArtistItem, Long) -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(artist.name)
            .setMessage(context.getString(R.string.itunes_artist_not_found_message))
            .setPositiveButton(context.getString(R.string.retry_search)) { _, _ ->
                coroutineScope.launch { onRescanArtist(artist, null) }
            }
            .setNegativeButton(context.getString(R.string.delete_artist)) { _, _ ->
                onDeleteArtist(artist)
            }
            .setNeutralButton(context.getString(R.string.manual_search)) { _, _ ->
                startManualSearchWithResults(context, artist, coroutineScope, onManualSelect)
            }
            .show()
    }

    fun startManualSearchWithResults(
        context: Context,
        artist: SavedArtistItem,
        coroutineScope: CoroutineScope,
        onArtistSelected: suspend (SavedArtistItem, Long) -> Unit
    ) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val disableItunesMatching = prefs.getBoolean("disableItunesMatching", false)

        if (disableItunesMatching) {
            Toast.makeText(
                context,
                context.getString(R.string.itunes_disabled_toast),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val input = EditText(context).apply {
            hint = context.getString(R.string.manual_itunes_search_hint)
            setText(artist.name)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.manual_itunes_search_title))
            .setView(input)
            .setPositiveButton(context.getString(R.string.search_button)) { _, _ ->
                val term = input.text.toString().trim()
                if (term.isNotEmpty()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val retrofit = Retrofit.Builder()
                            .baseUrl("https://itunes.apple.com/")
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                        val service = retrofit.create(ITunesApiService::class.java)

                        val response = service.searchArtist(term).execute()
                        val results = response.body()?.results.orEmpty()

                        val displayItems = results.mapNotNull { artistItem ->
                            val artistId = artistItem.artistId ?: return@mapNotNull null
                            val albumResponse = service.lookupArtistWithAlbums(artistId).execute()
                            val albums = albumResponse.body()?.results.orEmpty()
                                .filter { it.collectionType in listOf("Album", "EP", "Single") }

                            val latest = albums.maxByOrNull { it.releaseDate.orEmpty() }

                            ItunesArtistDisplayItem(
                                artist = artistItem.copy(albums = null),
                                latestTitle = latest?.collectionName,
                                latestDate = latest?.releaseDate?.take(10)
                            )
                        }


                        withContext(Dispatchers.Main) {
                            showItunesSearchResultsDialog(
                                context,
                                artist,
                                displayItems,
                                coroutineScope,
                                onArtistSelected
                            )
                        }
                    }
                }
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .show()
    }

    private fun showItunesSearchResultsDialog(
        context: Context,
        originalArtist: SavedArtistItem,
        items: List<ItunesArtistDisplayItem>,
        coroutineScope: CoroutineScope,
        onArtistSelected: suspend (SavedArtistItem, Long) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_itunes_artist_results, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewItunesResults)

        val adapter = ItunesArtistResultAdapter { selected ->
            MaterialAlertDialogBuilder(context)
                .setTitle(
                    context.getString(
                        R.string.confirm_artist_title,
                        selected.artist.artistName
                    )
                )
                .setMessage(
                    context.getString(
                        R.string.confirm_artist_message,
                        selected.latestDate ?: context.getString(R.string.no_date),
                        selected.latestTitle ?: context.getString(R.string.no_release)
                    )
                )
                .setPositiveButton(context.getString(R.string.save_button)) { _, _ ->
                    coroutineScope.launch(Dispatchers.IO) {
                        selected.artist.artistId?.let { onArtistSelected(originalArtist, it) }
                    }
                }
                .setNegativeButton(context.getString(R.string.cancel_button), null)
                .show()
        }


        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(context)
        adapter.submitList(items)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.itunes_artist_selection_title))
            .setView(view)
            .setNegativeButton(context.getString(R.string.close_button), null)
            .show()

    }
}
