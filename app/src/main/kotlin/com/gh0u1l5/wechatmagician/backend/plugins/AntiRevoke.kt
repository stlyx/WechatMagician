package com.gh0u1l5.wechatmagician.backend.plugins

import android.content.ContentValues
import com.gh0u1l5.wechatmagician.Global.SETTINGS_CHATTING_RECALL
import com.gh0u1l5.wechatmagician.Global.SETTINGS_CHATTING_RECALL_PROMPT
import com.gh0u1l5.wechatmagician.R
import com.gh0u1l5.wechatmagician.backend.WechatHook
import com.gh0u1l5.wechatmagician.backend.storage.Strings
import com.gh0u1l5.wechatmagician.backend.storage.cache.MessageCache
import com.gh0u1l5.wechatmagician.spellbook.WechatGlobal.MsgStorageObject
import com.gh0u1l5.wechatmagician.spellbook.interfaces.IDatabaseHook
import com.gh0u1l5.wechatmagician.spellbook.interfaces.IFileSystemHook
import com.gh0u1l5.wechatmagician.spellbook.interfaces.IMessageStorageHook
import com.gh0u1l5.wechatmagician.spellbook.interfaces.IXmlParserHook
import com.gh0u1l5.wechatmagician.spellbook.mirror.mm.storage.Methods.MsgInfoStorage_insert
import com.gh0u1l5.wechatmagician.spellbook.util.BasicUtil.tryAsynchronously
import com.gh0u1l5.wechatmagician.spellbook.util.BasicUtil.tryVerbosely
import com.gh0u1l5.wechatmagician.spellbook.util.ReflectionUtil
import com.gh0u1l5.wechatmagician.util.MessageUtil
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.io.File

object AntiRevoke : IDatabaseHook, IFileSystemHook, IMessageStorageHook, IXmlParserHook {

    private const val ROOT_TAG        = "sysmsg"
    private const val TYPE_TAG        = ".sysmsg.\$type"
    private const val REPLACE_MSG_TAG = ".sysmsg.revokemsg.replacemsg"

    private val pref = WechatHook.settings

    private fun isPluginEnabled() = pref.getBoolean(SETTINGS_CHATTING_RECALL, true)

    override fun onMessageStorageInserted(msgId: Long, msgObject: Any) {
        tryAsynchronously {
            MessageCache[msgId] = msgObject
        }
    }

    override fun onXmlParsed(root: String, xml: MutableMap<String, String>) {
        if (!isPluginEnabled()) {
            return
        }
        if (root == ROOT_TAG && xml[TYPE_TAG] == "revokemsg") {
            val msg = xml[REPLACE_MSG_TAG] ?: return
            if (msg.startsWith("\"")) {
                val default = Strings.getString(R.string.prompt_message_recall)
                val prompt = pref.getString(SETTINGS_CHATTING_RECALL_PROMPT, default)
                xml[REPLACE_MSG_TAG] = MessageUtil.applyEasterEgg(msg, prompt)
            }
        }
    }

    override fun onDatabaseUpdating(param: XC_MethodHook.MethodHookParam) {
        if (!isPluginEnabled()) {
            return
        }
        val table = param.args[0] as String? ?: return
        val values = param.args[1] as ContentValues? ?: return
        if (table == "message" && values["type"] == 10000) {
            if (values.getAsString("content").startsWith("\"")) {
                handleMessageRecall(values)
                param.result = 1
            }
        }
    }

    override fun onDatabaseDeleting(param: XC_MethodHook.MethodHookParam) {
        if (!isPluginEnabled()) {
            return
        }
        val table = param.args[0] as String?
        when (table) {
            "ImgInfo2", "voiceinfo", "videoinfo2", "WxFileIndex2" -> param.result = 1
        }
    }

    override fun onFileDeleting(file: File): Boolean {
        val path = file.absolutePath
        return when {
            path.contains("/image2/") -> true
            path.contains("/voice2/") -> true
            path.contains("/video/") -> true
            else -> false
        }
    }

    // handleMessageRecall notifies user that someone has recalled the given message.
    private fun handleMessageRecall(values: ContentValues) {
        if (MsgStorageObject == null) {
            return
        }

        tryVerbosely {
            val msgId = values["msgId"] as Long
            val msg = MessageCache[msgId] ?: return@tryVerbosely

            val copy = msg::class.java.newInstance()
            ReflectionUtil.shadowCopy(msg, copy)

            val createTime = XposedHelpers.getLongField(msg, "field_createTime")
            XposedHelpers.setIntField(copy, "field_type", values["type"] as Int)
            XposedHelpers.setObjectField(copy, "field_content", values["content"])
            XposedHelpers.setLongField(copy, "field_createTime", createTime + 1L)

            when (MsgInfoStorage_insert.parameterTypes.size) {
                1 -> MsgInfoStorage_insert.invoke(MsgStorageObject, copy)
                2 -> MsgInfoStorage_insert.invoke(MsgStorageObject, copy, false)
            }
        }
    }
}