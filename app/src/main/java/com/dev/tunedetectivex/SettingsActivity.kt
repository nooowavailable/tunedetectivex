package com.dev.tunedetectivex

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
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
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    private lateinit var intervalInput: EditText
    private lateinit var releaseAgeSlider: Slider
    private lateinit var releaseAgeLabel: TextView
    private lateinit var delayInput: EditText
    private lateinit var retryInput: EditText
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var isNetworkRequestsAllowed = true


    private val backupManager by lazy {
        val apiService = DeezerApiService.create()
        BackupManager(this, AppDatabase.getDatabase(this).savedArtistDao(), apiService)
    }


    private val createBackupLauncher =
        registerForActivityResult(CreateDocument("todo/todo")) { uri: Uri? ->
            uri?.let { backupManager.createBackup(it) }
        }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (isNetworkRequestsAllowed) {
                uri?.let { backupManager.restoreBackup(it) }
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.network_type_not_available),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    private val updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

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
        retryInput = findViewById(R.id.retryInput)

        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        editor = sharedPreferences.edit()

        findViewById<MaterialButton>(R.id.button_backup).setOnClickListener {
            createBackupLauncher.launch("TDX-backup.json")
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

        findViewById<MaterialButton>(R.id.button_toggle_folder_import).setOnClickListener {
            showFolderImportToggleDialog()
        }

        checkNetworkTypeAndSetFlag()


        val currentInterval = loadFetchInterval()
        val currentReleaseAge = loadReleaseAgePreference()
        val currentDelay = loadFetchDelay()
        val currentRetry = loadRetryAfterFailure()

        intervalInput.setText(currentInterval.toString())
        delayInput.setText(currentDelay.toString())
        retryInput.setText(currentRetry.toString())
        releaseAgeSlider.value = currentReleaseAge.toFloat()
        updateReleaseAgeLabel(currentReleaseAge)


        releaseAgeSlider.addOnChangeListener { _, value, _ ->
            delayedUpdate {
                val releaseAgeInWeeks = value.toInt()
                updateReleaseAgeLabel(releaseAgeInWeeks)
                saveReleaseAgePreference(releaseAgeInWeeks)
            }
        }

        intervalInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val interval = s?.toString()?.toIntOrNull()
                delayedUpdate {
                    if (interval != null && interval > 0) {
                        saveFetchInterval(interval)
                        showToast(getString(R.string.fetch_interval_set, interval))
                        Log.d(
                            "SettingsActivity",
                            "Fetch interval updated and saved: $interval minutes"
                        )
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.invalid_interval_value),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        delayInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val delay = s?.toString()?.toIntOrNull()
                delayedUpdate {
                    if (delay != null && delay > 0) {
                        saveFetchDelay(delay)
                        showToast(getString(R.string.fetch_delay_set, delay))
                        Log.d("SettingsActivity", "Fetch delay updated and saved: $delay seconds")
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.invalid_delay_value),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        retryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val retry = s?.toString()?.toIntOrNull()
                delayedUpdate {
                    if (retry != null && retry > 0) {
                        saveRetryAfterFailure(retry)
                        showToast(getString(R.string.retry_after_failure_set, retry))
                        Log.d(
                            "SettingsActivity",
                            "Retry after failure updated and saved: $retry minutes"
                        )
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.invalid_retry_value),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        val isFirstRun = sharedPreferences.getBoolean("isFirstRunSettings", true)
        if (isFirstRun) {
            showSettingsTutorial()
            editor.putBoolean("isFirstRunSettings", false).apply()
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
        val networkType = sharedPreferences.getString("networkType", "Any")
        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkType!!)
    }

    private fun showNetworkTypeDialog() {
        val networkTypes = arrayOf(
            "Wi-Fi Only",
            "Mobile Data Only",
            "Any"
        )

        val currentNetworkType = sharedPreferences.getString("networkType", "Any") ?: "Any"

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_network_type_title))
            .setSingleChoiceItems(
                arrayOf(
                    getString(R.string.network_type_wifi),
                    getString(R.string.network_type_mobile),
                    getString(R.string.network_type_any)
                ),
                networkTypes.indexOf(currentNetworkType)
            ) { dialog, which ->
                val selectedType = networkTypes[which]
                sharedPreferences.edit { putString("networkType", selectedType) }
                setupFetchReleasesWorker(loadFetchInterval())
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
        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        updateRunnable = Runnable { action() }
        updateHandler.postDelayed(updateRunnable!!, 1000)
    }

    @SuppressLint("SetTextI18n")
    private fun updateReleaseAgeLabel(weeks: Int) {
        releaseAgeLabel.text = getString(R.string.notify_releases_within_weeks, weeks)
    }

    private fun saveFetchInterval(minutes: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit { putInt("fetchInterval", minutes) }
        Log.d("SettingsActivity", "Fetch interval saved: $minutes minutes")
        setupFetchReleasesWorker(minutes)
    }

    private fun saveFetchDelay(seconds: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit { putInt("fetchDelay", seconds) }
    }

    private fun saveRetryAfterFailure(minutes: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit { putInt("retryAfterFailure", minutes) }
    }

    private fun saveReleaseAgePreference(weeks: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit { putInt("releaseAgeWeeks", weeks) }
    }

    private fun loadFetchInterval(): Int {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return sharedPreferences.getInt("fetchInterval", 90)
    }

    private fun loadFetchDelay(): Int {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return sharedPreferences.getInt("fetchDelay", 0)
    }

    private fun loadRetryAfterFailure(): Int {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return sharedPreferences.getInt("retryAfterFailure", 5)
    }

    private fun loadReleaseAgePreference(): Int {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return sharedPreferences.getInt("releaseAgeWeeks", 4)
    }

    private fun setupFetchReleasesWorker(intervalMinutes: Int) {
        WorkManagerUtil.setupFetchReleasesWorker(this, intervalMinutes)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    fun refreshSettingsUI() {
        intervalInput.setText(loadFetchInterval().toString())
        delayInput.setText(loadFetchDelay().toString())
        retryInput.setText(loadRetryAfterFailure().toString())
        releaseAgeSlider.value = loadReleaseAgePreference().toFloat()
        updateReleaseAgeLabel(loadReleaseAgePreference())
    }

    private fun showFolderImportToggleDialog() {
        val appPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFolderImportEnabled = appPreferences.getBoolean("isFolderImportEnabled", false)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.folder_import_feature_title)
            .setMessage(
                if (isFolderImportEnabled) {
                    getString(R.string.folder_import_feature_enabled_message)
                } else {
                    getString(R.string.folder_import_feature_disabled_message)
                }
            )
            .setPositiveButton(
                if (isFolderImportEnabled) {
                    R.string.disable
                } else {
                    R.string.enable
                }
            ) { _, _ ->
                appPreferences.edit { putBoolean("isFolderImportEnabled", !isFolderImportEnabled) }
                Toast.makeText(
                    this, if (isFolderImportEnabled) {
                        R.string.folder_import_feature_disabled_toast
                    } else {
                        R.string.folder_import_feature_enabled_toast
                    }, Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                    releaseAgeSlider,
                    getString(R.string.release_age_title),
                    getString(R.string.release_age_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    delayInput,
                    getString(R.string.delayed_fetching_title),
                    getString(R.string.delayed_fetching_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    retryInput,
                    getString(R.string.retry_attempt_title),
                    getString(R.string.retry_attempt_description)
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

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

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

}
