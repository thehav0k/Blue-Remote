package com.thehav0k.blueremote

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothService(private val context: Context, private val listener: ConnectionListener) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var connectThread: Thread? = null

    companion object {
        // Standard SPP UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        Log.d("BluetoothService", "Connecting to ${device.address}")
        connectThread?.interrupt()
        connectThread = thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket = socket
                socket.connect()
                Log.d("BluetoothService", "Connected to ${device.address}")
                listener.onConnected(device)
                // Start listening for incoming messages
                listenForMessages(socket)
            } catch (e: IOException) {
                Log.e("BluetoothService", "Connection failed", e)
                listener.onDisconnected()
                try {
                    bluetoothSocket?.close()
                } catch (_: IOException) {}
                bluetoothSocket = null
            }
        }
    }

    private fun listenForMessages(socket: BluetoothSocket) {
        thread {
            try {
                val input = socket.inputStream
                val buffer = ByteArray(1024)
                while (socket.isConnected) {
                    val bytes = input.read(buffer)
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        listener.onMessageReceived(message)
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothService", "Error reading from input stream", e)
            }
        }
    }

    fun sendSignal(signal: String) {
        try {
            val out = bluetoothSocket?.outputStream
            out?.write(signal.toByteArray())
            out?.flush()
            Log.d("BluetoothService", "Sent signal: $signal")
        } catch (e: IOException) {
            Log.e("BluetoothService", "Failed to send signal", e)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        try {
            bluetoothSocket?.close()
        } catch (_: IOException) {}
        bluetoothSocket = null
        connectThread?.interrupt()
        connectThread = null
    }

    interface ConnectionListener {
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected()
        fun onMessageReceived(message: String)
    }
}

