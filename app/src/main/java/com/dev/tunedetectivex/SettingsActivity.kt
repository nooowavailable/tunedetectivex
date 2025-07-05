package com.dev.tunedetectivex

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingsActivity : AppCompatActivity() {

    private lateinit var intervalInput: EditText
    private lateinit var releaseAgeSlider: Slider
    private lateinit var releaseAgeLabel: TextView
    private lateinit var delayInput: EditText
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var isNetworkRequestsAllowed = true
    private var ignoreNextToggleChange = false

    private val backupManager by lazy {
        val apiService = DeezerApiService.create()
        val savedArtistDao = AppDatabase.getDatabase(this).savedArtistDao()
        BackupManager(this, savedArtistDao, apiService)
    }


    private val createBackupLauncher =
        registerForActivityResult(CreateDocument("todo/todo")) { uri: Uri? ->
            uri?.let { backupManager.createBackup(it) }
        }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (isNetworkRequestsAllowed) {
                uri?.let {
                    lifecycleScope.launch {
                        backupManager.restoreBackup(it)

                        val db = AppDatabase.getDatabase(applicationContext)
                        val dao = db.savedArtistDao()
                        val allArtists = dao.getAll()

                        for (artist in allArtists) {
                            if (artist.deezerId == null || artist.deezerId == 0L) {
                                dao.updateDeezerId(artist.id, artist.id)
                                Log.d(
                                    "ImportDeezerFix",
                                    "✅ artist.id (${artist.id}) -> deezerId taken for '${artist.name}'"
                                )
                            }
                        }

                        val prefs = applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
                        val fetchIntervalMinutes = prefs.getInt("fetchInterval", 720)
                        val now = System.currentTimeMillis()
                        val nextFetchTimeMillis = now + fetchIntervalMinutes * 60_000L

                        prefs.edit {
                            putLong("lastWorkerRunTimestamp", now)
                            putLong("nextFetchTimeMillis", nextFetchTimeMillis)
                            putString("last_worker_status", "restored")
                        }

                        WorkManagerUtil.reEnqueueIfMissing(applicationContext)
                    }
                }
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.network_type_not_available),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }



    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        editor = sharedPreferences.edit()

        setContentView(R.layout.activity_settings)

        intervalInput = findViewById(R.id.intervalInput)
        releaseAgeSlider = findViewById(R.id.releaseAgeSlider)
        releaseAgeLabel = findViewById(R.id.releaseAgeLabel)
        delayInput = findViewById(R.id.delayInput)

        val itunesSwitch = findViewById<SwitchMaterial>(R.id.switch_itunes_support)
        itunesSwitch.isChecked = isItunesSupportEnabled()

        itunesSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreNextToggleChange) {
                ignoreNextToggleChange = false
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.itunes_support_title))
                    .setMessage(
                        HtmlCompat.fromHtml(
                            getString(R.string.itunes_support_message),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
                    .setPositiveButton(getString(R.string.dialog_button_understood)) { _, _ ->
                        enableItunesSupport(true)
                        itunesSwitch.isChecked = true
                    }
                    .setNegativeButton(getString(R.string.dialog_button_cancel)) { _, _ ->
                        ignoreNextToggleChange = true
                        itunesSwitch.isChecked = false
                    }
                    .show()
            } else {
                enableItunesSupport(false)
                Toast.makeText(
                    this,
                    getString(R.string.itunes_support_disabled_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val autoLoadSwitch = findViewById<SwitchMaterial>(R.id.switch_auto_load_releases)
        autoLoadSwitch.isChecked = isAutoLoadReleasesEnabled()

        autoLoadSwitch.setOnCheckedChangeListener { _, isChecked ->
            setAutoLoadReleasesEnabled(isChecked)
            Toast.makeText(
                this,
                if (isChecked) getString(R.string.auto_load_enabled_toast)
                else getString(R.string.auto_load_disabled_toast),
                Toast.LENGTH_SHORT
            ).show()
        }


        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        editor = sharedPreferences.edit()

        findViewById<MaterialButton>(R.id.button_backup).setOnClickListener {
            val fileName = backupManager.generateBackupFileName()
            createBackupLauncher.launch(fileName)
        }

        findViewById<MaterialButton>(R.id.button_restore).setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json"))
        }

        findViewById<MaterialButton>(R.id.button_select_network_type).setOnClickListener {
            showNetworkTypeDialog()
        }

        findViewById<MaterialButton>(R.id.button_request_battery_optimization).setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }

        checkNetworkTypeAndSetFlag()

        val currentInterval = loadFetchInterval()
        intervalInput.setText(intervalToLabel(currentInterval))
        val currentReleaseAge = loadReleaseAgePreference()
        val currentDelay = loadFetchDelay()

        intervalInput.apply {
            isFocusable = false
            isClickable = true
            setOnClickListener {
                showIntervalSelectionDialog()
            }
        }



        delayInput.setText(currentDelay.toString())
        releaseAgeSlider.value = currentReleaseAge.toFloat()
        updateReleaseAgeLabel(currentReleaseAge)


        releaseAgeSlider.addOnChangeListener { _, value, _ ->
            val releaseAgeInWeeks = value.toInt()
            Log.d("SettingsActivity", "Slider changed: $releaseAgeInWeeks")

            delayedUpdate {
                Log.d("SettingsActivity", "Saving slider value: $releaseAgeInWeeks")
                updateReleaseAgeLabel(releaseAgeInWeeks)
                saveReleaseAgePreference(releaseAgeInWeeks)
            }
        }

        delayInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                delayedUpdate {
                    val delay = delayInput.text.toString().toIntOrNull()
                    if (delay != null && delay in 1..60) {
                        saveFetchDelay(delay)
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.invalid_delay_value),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val isFirstRun = sharedPreferences.getBoolean("isFirstRunSettings", true)
        if (isFirstRun) {
            showSettingsTutorial()
            editor.putBoolean("isFirstRunSettings", false).apply()
        }

        findViewById<MaterialButton>(R.id.button_change_icon).setOnClickListener {
            showIconChooserDialog()
        }

        val buttonNotificationSettings: MaterialButton = findViewById(R.id.buttonNotificationSettings)
        buttonNotificationSettings.setOnClickListener {
            openDebugNotificationSettings()
        }
    }

    private fun openDebugNotificationSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, "debug_channel")
        }
        startActivity(intent)
    }

    private fun showIntervalSelectionDialog() {
        val intervals = arrayOf(15, 30, 60, 120, 240, 360, 720, 1440)
        val labels = arrayOf(
            getString(R.string.interval_15_min),
            getString(R.string.interval_30_min),
            getString(R.string.interval_1_h),
            getString(R.string.interval_2_h),
            getString(R.string.interval_4_h),
            getString(R.string.interval_6_h),
            getString(R.string.interval_12_h),
            getString(R.string.interval_1_day),
        )

        val currentInterval = loadFetchInterval()
        val currentIndex = intervals.indexOf(currentInterval).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.fetch_interval_title))
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedInterval = intervals[which]
                saveFetchInterval(selectedInterval)
                intervalInput.setText(labels[which])
                Toast.makeText(
                    this,
                    getString(R.string.interval_selected_toast, labels[which]),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun intervalToLabel(minutes: Int): String {
        return when (minutes) {
            15 -> getString(R.string.interval_15_min)
            30 -> getString(R.string.interval_30_min)
            60 -> getString(R.string.interval_1_h)
            120 -> getString(R.string.interval_2_h)
            240 -> getString(R.string.interval_4_h)
            360 -> getString(R.string.interval_6_h)
            720 -> getString(R.string.interval_12_h)
            1440 -> getString(R.string.interval_1_day)
            else -> getString(R.string.interval_fallback_minutes, minutes)
        }
    }


    private fun showIconChooserDialog() {
        val iconOptions = arrayOf("Standard", "Minimal")
        val aliasNames = arrayOf(
            "com.dev.tunedetectivex.AliasDefault",
            "com.dev.tunedetectivex.AliasMin",
        )

        val currentEnabled = aliasNames.indexOfFirst { alias ->
            packageManager.getComponentEnabledSetting(ComponentName(this, alias)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_app_icon))
            .setSingleChoiceItems(iconOptions, currentEnabled) { dialog, which ->
                setAppIcon(aliasNames, which)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.select_app_icon), null)
            .show()
    }

    private fun setAppIcon(aliasNames: Array<String>, selectedIndex: Int) {
        val packageManager = packageManager

        for ((i, alias) in aliasNames.withIndex()) {
            val state = if (i == selectedIndex)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            packageManager.setComponentEnabledSetting(
                ComponentName(this, alias),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }


    private fun enableItunesSupport(enabled: Boolean) {
        setItunesSupportEnabled(enabled)
        wipeEntireSearchHistory()
        Toast.makeText(
            this,
            if (enabled) getString(R.string.itunes_support_enabled_toast)
            else getString(R.string.itunes_support_disabled_toast),
            Toast.LENGTH_SHORT
        ).show()
    }


    private fun wipeEntireSearchHistory() {
        val dao = AppDatabase.getDatabase(this).searchHistoryDao()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.clearHistory()
            }
        }
    }


    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData("package:$packageName".toUri())
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                getString(R.string.battery_optimization_ignored),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkNetworkTypeAndSetFlag() {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        isNetworkRequestsAllowed = WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)
    }

    private fun showNetworkTypeDialog() {
        val networkTypes = arrayOf(
            "Wi-Fi Only",
            "Any"
        )

        val currentNetworkType = sharedPreferences.getString("networkType", "Any") ?: "Any"

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_network_type_title))
            .setSingleChoiceItems(
                arrayOf(
                    getString(R.string.network_type_wifi),
                    getString(R.string.network_type_any)
                ),
                networkTypes.indexOf(currentNetworkType)
            ) { dialog, which ->
                val selectedType = networkTypes[which]
                sharedPreferences.edit { putString("networkType", selectedType) }
                setupFetchReleasesWorker()
                checkNetworkTypeAndSetFlag()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun scrollToView(view: View) {
        view.parent.requestChildFocus(view, view)
    }

    private fun delayedUpdate(action: () -> Unit) {
        updateRunnable?.let {
            Log.d("SettingsActivity", "Cancelling previous runnable")
            updateHandler.removeCallbacks(it)
        }

        updateRunnable = Runnable {
            Log.d("SettingsActivity", "Executing delayed update")
            action()
        }

        Log.d("SettingsActivity", "Posting delayed update")
        updateHandler.postDelayed(updateRunnable!!, 1000)
    }


    @SuppressLint("SetTextI18n")
    private fun updateReleaseAgeLabel(weeks: Int) {
        releaseAgeLabel.text = getString(R.string.notify_releases_within_weeks, weeks)
    }

    private fun saveFetchInterval(minutes: Int) {
        Log.d("SettingsActivity", "saveFetchInterval called with: $minutes")
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sharedPreferences.edit { putInt("fetchInterval", minutes) }
        Log.d("SettingsActivity", "Fetch interval saved in SharedPreferences.")
        setupFetchReleasesWorker()
    }

    private fun saveFetchDelay(seconds: Int) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sharedPreferences.edit { putInt("fetchDelay", seconds) }
    }

    private fun loadFetchDelay(): Int {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getInt("fetchDelay", 1)
    }

    private fun saveReleaseAgePreference(weeks: Int) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sharedPreferences.edit { putInt("releaseAgeWeeks", weeks) }
    }

    private fun loadFetchInterval(): Int {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getInt("fetchInterval", 720)
    }

    private fun loadReleaseAgePreference(): Int {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getInt("releaseAgeWeeks", 4)
    }

    private fun setupFetchReleasesWorker() {
        WorkManagerUtil.reEnqueueIfMissing(this)
    }

    @SuppressLint("SetTextI18n")
    fun refreshSettingsUI() {
        intervalInput.setText(loadFetchInterval().toString())
        delayInput.setText(loadFetchDelay().toString())
        releaseAgeSlider.value = loadReleaseAgePreference().toFloat()
        updateReleaseAgeLabel(loadReleaseAgePreference())
    }

    private fun showSettingsTutorial() {
        scrollToView(intervalInput)
        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    intervalInput,
                    getString(R.string.fetch_interval_title),
                    getString(R.string.fetch_interval_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    findViewById<SwitchMaterial>(R.id.switch_auto_load_releases),
                    getString(R.string.auto_load_releases_title),
                    getString(R.string.auto_load_releases_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    releaseAgeSlider,
                    getString(R.string.release_age_title),
                    getString(R.string.release_age_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    delayInput,
                    getString(R.string.fetch_delay_title),
                    getString(R.string.fetch_delay_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    findViewById<MaterialButton>(R.id.button_request_battery_optimization),
                    getString(R.string.request_battery_optimization_title),
                    getString(R.string.request_battery_optimization_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    findViewById<MaterialButton>(R.id.button_select_network_type),
                    getString(R.string.select_network_type_title),
                    getString(R.string.select_network_type_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    findViewById<MaterialButton>(R.id.button_backup),
                    getString(R.string.create_backup_title),
                    getString(R.string.create_backup_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    findViewById<MaterialButton>(R.id.button_restore),
                    getString(R.string.restore_data_title),
                    getString(R.string.restore_data_description)
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_tutorial_completed),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {}

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_tutorial_aborted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .start()
    }

    private fun isItunesSupportEnabled(): Boolean {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return prefs.getBoolean("itunesSupportEnabled", false)
    }

    private fun setItunesSupportEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        prefs.edit { putBoolean("itunesSupportEnabled", enabled) }
    }

    private fun isAutoLoadReleasesEnabled(): Boolean {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return prefs.getBoolean("autoLoadReleases", true)
    }

    private fun setAutoLoadReleasesEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        prefs.edit { putBoolean("autoLoadReleases", enabled) }
    }

}