package com.dev.tunedetectivex

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
                    "Selected network type is not available. Please check your connection.",
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
                showToast("Saved!")
                Log.d("SettingsActivity", "Release age updated and saved: $releaseAgeInWeeks weeks")
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
                        showToast("Fetch interval set to $interval minutes")
                        Log.d(
                            "SettingsActivity",
                            "Fetch interval updated and saved: $interval minutes"
                        )
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Invalid interval value.",
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
                        showToast("Fetch delay set to $delay seconds")
                        Log.d("SettingsActivity", "Fetch delay updated and saved: $delay seconds")
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Invalid delay value.",
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
                        showToast("Retry after failure set to $retry minutes")
                        Log.d(
                            "SettingsActivity",
                            "Retry after failure updated and saved: $retry minutes"
                        )
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Invalid retry value.",
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

    private fun checkNetworkTypeAndSetFlag() {
        val networkType = sharedPreferences.getString("networkType", "Any")
        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkType!!)
    }

    private fun showNetworkTypeDialog() {
        val networkTypes = arrayOf("Wi-Fi Only", "Mobile Data Only", "Any")
        val currentNetworkType = sharedPreferences.getString("networkType", "Any")

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Network Type")
            .setSingleChoiceItems(
                networkTypes,
                networkTypes.indexOf(currentNetworkType)
            ) { dialog, which ->
                val selectedType = networkTypes[which]
                editor.putString("networkType", selectedType).apply()
                setupFetchReleasesWorker(loadFetchInterval())
                checkNetworkTypeAndSetFlag()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun scrollToView(view: View) {
        view.parent.requestChildFocus(view, view)
    }

    private fun delayedUpdate(action: () -> Unit) {
        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        updateRunnable = Runnable { action() }
        updateHandler.postDelayed(updateRunnable!!, 1500)
    }

    @SuppressLint("SetTextI18n")
    private fun updateReleaseAgeLabel(weeks: Int) {
        releaseAgeLabel.text = "Notify releases within the last $weeks weeks"
    }

    private fun saveFetchInterval(minutes: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit().putInt("fetchInterval", minutes).apply()
        setupFetchReleasesWorker(minutes)
    }

    private fun saveFetchDelay(seconds: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit().putInt("fetchDelay", seconds).apply()
    }

    private fun saveRetryAfterFailure(minutes: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit().putInt("retryAfterFailure", minutes).apply()
    }

    private fun saveReleaseAgePreference(weeks: Int) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit().putInt("releaseAgeWeeks", weeks).apply()
    }

    private fun loadFetchInterval(): Int {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return sharedPreferences.getInt("fetchInterval", 15)
    }

    private fun loadFetchDelay(): Int {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return sharedPreferences.getInt("fetchDelay", 10)
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

    private fun showSettingsTutorial() {
        scrollToView(intervalInput)
        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    intervalInput,
                    "Fetch interval",
                    "Here you can set how often new publications should be fetched (in minutes)."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    releaseAgeSlider,
                    "Release-Age",
                    "With this slider you can define how old releases can be to receive notifications (in weeks)."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    delayInput,
                    "Delayed fetching",
                    "Here you can set how many seconds should elapse between fetching."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    retryInput,
                    "Retry Attempt",
                    "Specify how long the app should wait after an error before it tries again (in minutes)."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    findViewById<MaterialButton>(R.id.button_backup),
                    "Create backup",
                    "Tap here to create a backup of your saved data."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    findViewById<MaterialButton>(R.id.button_restore),
                    "Restore data",
                    "Tap here to restore a previously created backup."
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Settings tutorial completed!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Settings tutorial aborted.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .start()
    }

}
