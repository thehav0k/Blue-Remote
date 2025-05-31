package com.thehav0k.blueremote

data class CarMessage(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

