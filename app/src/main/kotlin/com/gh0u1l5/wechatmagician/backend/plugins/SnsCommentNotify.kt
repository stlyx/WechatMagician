package com.gh0u1l5.wechatmagician.backend.plugins

import android.app.AndroidAppHelper
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import android.text.format.DateFormat
import com.gh0u1l5.wechatmagician.Global
import com.gh0u1l5.wechatmagician.R
import com.gh0u1l5.wechatmagician.backend.WechatHook
import com.gh0u1l5.wechatmagician.backend.WechatHook.Companion.resources
import com.gh0u1l5.wechatmagician.spellbook.interfaces.IDatabaseHook
import com.gh0u1l5.wechatmagician.spellbook.mirror.mm.plugin.sns.ui.Classes.SnsTimeLineUI
import com.gh0u1l5.wechatmagician.util.MessageUtil.parseSnsComment
import de.robv.android.xposed.XC_MethodHook

object SnsCommentNotify : IDatabaseHook {

    private val pref = WechatHook.settings

    override fun onDatabaseInserting(param: XC_MethodHook.MethodHookParam) {
        val table = param.args[0] as String? ?: return
        val values = param.args[2] as ContentValues? ?: return
        when (table) {
            "SnsComment" -> { // new moment comment related to me received
                if (!pref.getBoolean(Global.SETTINGS_SNS_NEW_COMMENT_NOTIFICATION, true)) {
                    return
                }
                handleCommentInsert(values)
            }
            else -> return
        }
    }

    private fun handleCommentInsert(values: ContentValues) {
        val context = AndroidAppHelper.currentApplication() as Context
        val notifyMgr = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val parsedMsg = parseSnsComment(values) ?: return
        if ((parsedMsg["content"] as String?).isNullOrBlank()) return
        val mBuilder = NotificationCompat.Builder(context)
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle("${parsedMsg["sender"]} ${resources?.getString(R.string.prompt_sns_new_comment) ?: "comments at"} " +
                        DateFormat.format("H:mm:ss", parsedMsg["createTime"] as Long))
                .setContentText("${parsedMsg["content"]}")
                .setAutoCancel(true)
        val resultIntent = Intent(context, SnsTimeLineUI)

        val resultPendingIntent = TaskStackBuilder.create(context).addNextIntentWithParentStack(resultIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        mBuilder.setContentIntent(resultPendingIntent)
        notifyMgr.notify(Math.random().hashCode(), mBuilder.build())
    }

}