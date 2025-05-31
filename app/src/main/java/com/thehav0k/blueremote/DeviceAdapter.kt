package com.thehav0k.blueremote

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thehav0k.blueremote.models.BluetoothDeviceModel

class DeviceAdapter(
    private val devices: List<BluetoothDeviceModel>,
    private val onClick: (BluetoothDeviceModel) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {
    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvAddress: TextView = view.findViewById(R.id.tvDeviceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_device_list_item, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.tvName.text = device.name
        holder.tvAddress.text = device.address
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount() = devices.size
}