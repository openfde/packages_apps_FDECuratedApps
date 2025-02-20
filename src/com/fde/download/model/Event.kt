package com.fde.download.model

data class Event(
    var eventType: EventType,
    var appName: String,
    var progress: Int = 0
)