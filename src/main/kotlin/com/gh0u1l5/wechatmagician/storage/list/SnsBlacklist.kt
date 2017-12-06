package com.gh0u1l5.wechatmagician.storage.list

object SnsBlacklist : BaseList<String?>() {
    override operator fun plusAssign(value: String?) {
        if (value == null) {
            return
        }
        super.plusAssign(value.padStart(20, '0'))
    }
}