package com.gh0u1l5.wechatmagician

class WaitChannel {
    @Volatile var done = false
    private val channel = java.lang.Object()

    fun wait(): Boolean {
        synchronized(channel) {
            if (!done) {
                channel.wait()
                return true
            }
            return false
        }
    }

    fun wait(millis: Long): Boolean {
        synchronized(channel) {
            if (!done) {
                channel.wait(millis)
                return true
            }
            return false
        }
    }

    fun done() {
        synchronized(channel) {
            done = true
            channel.notifyAll()
        }
    }
}