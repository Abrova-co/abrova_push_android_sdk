package ir.abrova.push.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ir.abrova.push.example.databinding.ActivitySettingsBinding
import ir.abrova.push.sdk.AbrovaPush
import ir.abrova.push.sdk.AbrovaPushConfig
import ir.abrova.push.sdk.AbrovaPushLogLevel

/**
 * Settings Activity for configuring the SDK.
 *
 * Features demonstrated:
 * - Runtime SDK configuration
 * - API key management
 * - Log level selection
 * - Device ID display
 * - Cache clearing
 *
 * Note: Server URL is managed internally by the SDK and not user-configurable.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadCurrentSettings()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun loadCurrentSettings() {
        val prefs = getSharedPreferences("abrova_push_example", Context.MODE_PRIVATE)

        // Load saved settings
        binding.etApiKey.setText(prefs.getString("api_key", BuildConfig.ABROVA_PUSH_API_KEY))

        // Display current device ID
        binding.tvDeviceId.text = AbrovaPush.getDeviceId() ?: "Not registered"

        // Display connection status
        binding.tvConnectionStatus.text = if (AbrovaPush.isConnected()) "Connected" else "Disconnected"

        // Log level spinner - set current selection
        val currentLogLevel = prefs.getInt("log_level", AbrovaPushLogLevel.DEBUG.ordinal)
        binding.spinnerLogLevel.setSelection(currentLogLevel)
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnApply.setOnClickListener {
            applySettings()
        }

        binding.btnClearCache.setOnClickListener {
            clearCache()
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllDialog()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnCopyDeviceId.setOnClickListener {
            copyDeviceId()
        }
    }

    private fun saveSettings() {
        val apiKey = binding.etApiKey.text.toString().trim()
        val logLevel = binding.spinnerLogLevel.selectedItemPosition

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API Key is required", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("abrova_push_example", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("api_key", apiKey)
            .putInt("log_level", logLevel)
            .apply()

        Toast.makeText(this, "Settings saved. Restart app to apply.", Toast.LENGTH_LONG).show()
    }

    private fun applySettings() {
        val apiKey = binding.etApiKey.text.toString().trim()
        val logLevel = binding.spinnerLogLevel.selectedItemPosition

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API Key is required", Toast.LENGTH_SHORT).show()
            return
        }

        // Save first
        saveSettings()

        // Apply log level immediately
        AbrovaPush.setLogLevel(AbrovaPushLogLevel.values()[logLevel])

        // Re-initialize SDK with new config
        val config = AbrovaPushConfig(
            apiKey = apiKey,
            notificationIcon = "ic_notification"
        )

        try {
            // Unregister first
            AbrovaPush.unregister()

            // Re-initialize
            AbrovaPush.init(applicationContext, config)

            // Re-register
            AbrovaPush.register()

            Toast.makeText(this, "Settings applied and reconnecting...", Toast.LENGTH_LONG).show()

            // Update UI
            binding.tvConnectionStatus.text = "Reconnecting..."

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearCache() {
        AbrovaPush.clearInboxCache()
        Toast.makeText(this, "Inbox cache cleared", Toast.LENGTH_SHORT).show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will clear all saved settings and unregister the device. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearAllData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllData() {
        // Unregister device
        AbrovaPush.unregister()

        // Clear preferences
        getSharedPreferences("abrova_push_example", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // Clear SDK cache
        AbrovaPush.clearInboxCache()

        Toast.makeText(this, "All data cleared. Restart app.", Toast.LENGTH_LONG).show()

        // Update UI
        binding.etApiKey.text?.clear()
        binding.tvDeviceId.text = "Not registered"
        binding.tvConnectionStatus.text = "Disconnected"
    }

    private fun testConnection() {
        binding.tvConnectionStatus.text = "Testing..."

        // Simple connection check
        if (AbrovaPush.isConnected()) {
            binding.tvConnectionStatus.text = "Connected"
            Toast.makeText(this, "Connection OK!", Toast.LENGTH_SHORT).show()
        } else {
            binding.tvConnectionStatus.text = "Disconnected"
            Toast.makeText(this, "Not connected. Check settings.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyDeviceId() {
        val deviceId = AbrovaPush.getDeviceId()
        if (deviceId != null) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Device ID", deviceId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Device ID copied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No device ID available", Toast.LENGTH_SHORT).show()
        }
    }
}
