package com.thehav0k.blueremote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.thehav0k.blueremote.databinding.ActivityMainBinding
import com.thehav0k.blueremote.models.BluetoothDeviceModel
import com.thehav0k.blueremote.CarMessage

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothService: BluetoothService
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var isAutoMode: Boolean = false
    private val reservedButtons = mutableMapOf<Int, String>() // btnId -> signal

    private val deviceList = mutableListOf<BluetoothDeviceModel>()
    private lateinit var deviceAdapter: DeviceAdapter
    private var scanning = false

    private val messageList = mutableListOf<CarMessage>()
    private lateinit var messageAdapter: MessageAdapter

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                onPermissionsGranted()
            } else {
                Toast.makeText(this, "Bluetooth permission is required to use this feature.", Toast.LENGTH_LONG).show()
            }
        }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Listen for discovered devices
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    val deviceModel = BluetoothDeviceModel(
                        name = try {
                            if (hasBluetoothPermission()) it.name ?: "Unknown Device" else "Unknown Device"
                        } catch (_: SecurityException) {
                            "Unknown Device"
                        },
                        address = it.address
                    )
                    if (!deviceList.any { d -> d.address == deviceModel.address }) {
                        deviceList.add(deviceModel)
                        deviceAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private val PREFS_NAME = "settings"
    private val KEY_NIGHT_MODE = "night_mode"
    private val KEY_LAST_DEVICE = "last_connected_device"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set theme based on saved preference (default: follow system)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nightMode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize message adapter for displaying car messages
        messageAdapter = MessageAdapter(messageList)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messageAdapter
        }

        // Initialize BluetoothService with connection callbacks
        bluetoothService = BluetoothService(this, object : BluetoothService.ConnectionListener {
            override fun onConnected(device: BluetoothDevice) {
                runOnUiThread {
                    binding.progressOverlay.visibility = android.view.View.GONE
                    binding.tvConnectionStatus.text = getString(R.string.status_connected)
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.statusConnected))
                    val deviceName = try {
                        if (hasBluetoothPermission()) {
                            device.name ?: device.address
                        } else {
                            device.address
                        }
                    } catch (_: SecurityException) {
                        device.address
                    }
                    binding.tvDeviceName.text = deviceName
                    binding.btnReconnect.visibility = android.view.View.GONE
                    // Save connected device address
                    prefs.edit().putString(KEY_LAST_DEVICE, device.address).apply()
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    binding.progressOverlay.visibility = android.view.View.GONE
                    binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.statusDisconnected))
                    binding.tvDeviceName.text = getString(R.string.no_device)
                    binding.btnReconnect.visibility = android.view.View.VISIBLE
                    // Clear saved device address
                    prefs.edit().remove(KEY_LAST_DEVICE).apply()
                }
            }

            override fun onMessageReceived(message: String) {
                runOnUiThread {
                    messageList.add(0, CarMessage(message.trim()))
                    messageAdapter.notifyItemInserted(0)
                    binding.rvMessages.scrollToPosition(0)
                }
            }
        })

        initializeBluetoothAdapter()
        checkAndRequestPermissions()

        // Initialize device adapter for listing discovered devices
        deviceAdapter = DeviceAdapter(deviceList) { device ->
            if (hasBluetoothPermission()) {
                try {
                    connectToDevice(device)
                } catch (_: SecurityException) {
                    Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
            }
        }

        // Register Bluetooth discovery receiver for ACTION_FOUND
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryReceiver, filter)

        // Add dark mode toggle
        binding.btnToggleTheme.setOnClickListener {
            val currentMode = AppCompatDelegate.getDefaultNightMode()
            val newMode = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
                AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(newMode)
            prefs.edit().putInt(KEY_NIGHT_MODE, newMode).apply()

            // Update theme toggle icon
            updateThemeToggleIcon(newMode)
            // Update text colors for mode and device name
            updateModeAndDeviceNameColors(newMode)
        }

        // Try to reconnect to last device if available
        val lastDeviceAddress = prefs.getString(KEY_LAST_DEVICE, null)
        if (lastDeviceAddress != null) {
            val lastDevice = bluetoothAdapter.getRemoteDevice(lastDeviceAddress)
            if (hasBluetoothPermission()) {
                try {
                    bluetoothService.connect(lastDevice)
                } catch (_: SecurityException) {
                    Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
            }
        }

        // Show user manual dialog when info icon is tapped
        binding.btnInfo.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_user_manual, null, false)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            dialogView.findViewById<Button>(R.id.btnCloseManual).setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure text colors and theme icon are updated when returning to the activity
        val mode = AppCompatDelegate.getDefaultNightMode()
        updateModeAndDeviceNameColors(mode)
        updateThemeToggleIcon(mode)
    }

    private fun initializeBluetoothAdapter() {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return
            }
        }
        onPermissionsGranted()
    }

    private fun onPermissionsGranted() {
        setupUi()
        // Additional initialization if needed
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun setupUi() {
        binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
        binding.tvDeviceName.text = getString(R.string.no_device)
        binding.btnReconnect.setOnClickListener {
            if (hasBluetoothPermission()) {
                try {
                    scanDevices()
                } catch (_: SecurityException) {
                    Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnScan.setOnClickListener {
            if (hasBluetoothPermission()) {
                try {
                    scanDevices()
                } catch (_: SecurityException) {
                    Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchMode.setOnCheckedChangeListener { _, isChecked ->
            isAutoMode = isChecked
            binding.tvMode.text = if (isChecked) getString(R.string.mode_auto) else getString(R.string.mode_manual)
            // Send 'A' for Auto mode, 'M' for Manual mode
            if (isChecked) {
                bluetoothService.sendSignal("A")
            } else {
                bluetoothService.sendSignal("M")
            }
        }

        setButton(binding.btnForward, "F")
        setButton(binding.btnBackward, "B")
        setButton(binding.btnLeft, "L")
        setButton(binding.btnRight, "R")
        setButton(binding.btnStop, "S")
        setButton(binding.btnPlus, "+")
        setButton(binding.btnMinus, "-")

        setReservedButton(binding.btnReserved1)
        setReservedButton(binding.btnReserved2)
    }

    private fun setButton(button: Button, signal: String) {
        button.setOnClickListener {
            bluetoothService.sendSignal(signal)
        }
    }

    private fun setReservedButton(button: Button) {
        button.setOnClickListener {
            val input = EditText(this)
            input.hint = getString(R.string.enter_signal_character)
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_reserved_button))
                .setView(input)
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    val signal = input.text.toString().trim()
                    if (signal.isNotEmpty() && signal.length == 1) {
                        reservedButtons[button.id] = signal
                        button.text = signal
                    } else {
                        Toast.makeText(this, getString(R.string.enter_one_character), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        button.setOnLongClickListener {
            reservedButtons[button.id]?.let { signal ->
                bluetoothService.sendSignal(signal)
                true
            } ?: false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun scanDevices() {
        // Clear the current device list and update the adapter.
        deviceList.clear()
        deviceAdapter.notifyDataSetChanged()
        scanning = true

        // Ensure Bluetooth is enabled.
        if (!bluetoothAdapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        // Start device discovery.
        try {
            bluetoothAdapter.startDiscovery()
        } catch (_: SecurityException) {
            Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
            return
        }

        // Show device selection dialog.
        showDeviceDialog()

        // Cancel discovery after a set timeout (e.g., 10 seconds).
        Handler(Looper.getMainLooper()).postDelayed({
            if (scanning) {
                try {
                    bluetoothAdapter.cancelDiscovery()
                } catch (_: SecurityException) {
                    Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
                }
                scanning = false
            }
        }, 10000)
    }

    private fun showDeviceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_list, null, false)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_bluetooth_device))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(model: BluetoothDeviceModel) {
        val device = bluetoothAdapter.bondedDevices.firstOrNull { it.address == model.address }
        if (device != null) {
            Log.d("MainActivity", "Attempting to connect to device: ${device.name}")
            try {
                bluetoothService.connect(device)
                binding.progressOverlay.visibility = android.view.View.VISIBLE
            } catch (_: SecurityException) {
                Toast.makeText(this, getString(R.string.permission_denied_bluetooth_access), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.please_pair_device_first), Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(discoveryReceiver)
        bluetoothService.close()
    }

    /**
     * Updates the text color of the mode and device name based on the current theme mode.
     */
    private fun updateModeAndDeviceNameColors(mode: Int) {
        val isDark = when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val modeTextColor = if (isDark) getColor(R.color.onBackground) else getColor(R.color.primary)
        val deviceNameColor = if (isDark) getColor(R.color.secondary) else getColor(R.color.primaryVariant)
        binding.tvMode.setTextColor(modeTextColor)
        binding.tvDeviceName.setTextColor(deviceNameColor)
    }

    private fun updateThemeToggleIcon(mode: Int) {
        val isDark = when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val iconRes = if (isDark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode
        binding.btnToggleTheme.setImageResource(iconRes)
    }
}
