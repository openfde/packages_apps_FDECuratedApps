package com.fde.download.utils

import org.greenrobot.eventbus.EventBus

object  EventBusUtils {
    fun register(subscriber: Any) {
        EventBus.getDefault().register(subscriber)
    }

    fun unregister(subscriber: Any) {
        EventBus.getDefault().unregister(subscriber)
    }

    fun sendEvent(event: Any) {
        EventBus.getDefault().post(event)
    }

    fun sendStickyEvent(event: Any) {
        EventBus.getDefault().postSticky(event)
    }

    fun sendButtonTextEvent(buttonTextEvent: Any) {
        EventBus.getDefault().post(buttonTextEvent)
    }
}