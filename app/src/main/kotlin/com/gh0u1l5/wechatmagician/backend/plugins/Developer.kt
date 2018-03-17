package com.gh0u1l5.wechatmagician.backend.plugins

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.widget.ListAdapter
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_DATABASE_DELETE
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_DATABASE_EXECUTE
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_DATABASE_INSERT
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_DATABASE_PASSWORD
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_DATABASE_QUERY
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_DATABASE_UPDATE
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_TRACE_FILES
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_TRACE_LOGCAT
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_UI_DUMP_POPUP_MENU
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_UI_TOUCH_EVENT
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_UI_TRACE_ACTIVITIES
import com.gh0u1l5.wechatmagician.Global.DEVELOPER_XML_PARSER
import com.gh0u1l5.wechatmagician.backend.WechatHook
import com.gh0u1l5.wechatmagician.spellbook.C
import com.gh0u1l5.wechatmagician.spellbook.annotations.WechatHookMethod
import com.gh0u1l5.wechatmagician.spellbook.mirror.mm.sdk.platformtools.Classes.Logcat
import com.gh0u1l5.wechatmagician.spellbook.mirror.mm.sdk.platformtools.Classes.XmlParser
import com.gh0u1l5.wechatmagician.spellbook.mirror.mm.sdk.platformtools.Methods.XmlParser_parse
import com.gh0u1l5.wechatmagician.spellbook.mirror.mm.ui.base.Classes.MMListPopupWindow
import com.gh0u1l5.wechatmagician.spellbook.mirror.wcdb.Classes.SQLiteErrorHandler
import com.gh0u1l5.wechatmagician.spellbook.mirror.wcdb.database.Classes.SQLiteCipherSpec
import com.gh0u1l5.wechatmagician.spellbook.mirror.wcdb.database.Classes.SQLiteCursorFactory
import com.gh0u1l5.wechatmagician.spellbook.mirror.wcdb.database.Classes.SQLiteDatabase
import com.gh0u1l5.wechatmagician.spellbook.mirror.wcdb.support.Classes.SQLiteCancellationSignal
import com.gh0u1l5.wechatmagician.spellbook.util.ReflectionUtil.findAndHookMethod
import com.gh0u1l5.wechatmagician.util.MessageUtil.argsToString
import com.gh0u1l5.wechatmagician.util.MessageUtil.bundleToString
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import java.io.File

object Developer {

    private val pref = WechatHook.developer

    // Hook View.onTouchEvent to trace touch events.
    @WechatHookMethod @JvmStatic fun traceTouchEvents() {
        if (pref.getBoolean(DEVELOPER_UI_TOUCH_EVENT, false)) {
            findAndHookMethod(C.View, "onTouchEvent", C.MotionEvent, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    log("View.onTouchEvent => obj.class = ${param.thisObject::class.java}")
                }
            })
        }
    }

    // Hook Activity.startActivity and Activity.onCreate to trace activities.
    @WechatHookMethod @JvmStatic fun traceActivities() {
        if (pref.getBoolean(DEVELOPER_UI_TRACE_ACTIVITIES, false)) {
            findAndHookMethod(C.Activity, "startActivity", C.Intent, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[0] as Intent?
                    log("Activity.startActivity => " +
                            "${param.thisObject::class.java}, " +
                            "intent => ${bundleToString(intent?.extras)}")
                }
            })

            findAndHookMethod(C.Activity, "onCreate", C.Bundle, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val bundle = param.args[0] as Bundle?
                    val intent = (param.thisObject as Activity).intent
                    log("Activity.onCreate => " +
                            "${param.thisObject::class.java}, " +
                            "intent => ${bundleToString(intent?.extras)}, " +
                            "bundle => ${bundleToString(bundle)}")
                }
            })
        }
    }

    // Hook MMListPopupWindow to trace every popup menu.
    @WechatHookMethod @JvmStatic fun dumpPopupMenu() {
        if (pref.getBoolean(DEVELOPER_UI_DUMP_POPUP_MENU, false)) {
            hookAllConstructors(MMListPopupWindow, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.thisObject
                    val context = param.args[0]
                    log("POPUP => menu.class = ${menu::class.java}")
                    log("POPUP => context.class = ${context::class.java}")
                }
            })

            findAndHookMethod(MMListPopupWindow, "setAdapter", C.ListAdapter, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val adapter = param.args[0] as ListAdapter? ?: return
                    log("POPUP => adapter.count = ${adapter.count}")
                    (0 until adapter.count).forEach { index ->
                        log("POPUP => adapter.item[$index] = ${adapter.getItem(index)}")
                        log("POPUP => adapter.item[$index].class = ${adapter.getItem(index)::class.java}")
                    }
                }
            })
        }
    }

    // Hook SQLiteDatabase to trace all the database operations.
    @WechatHookMethod @JvmStatic fun traceDatabase() {
        if (pref.getBoolean(DEVELOPER_DATABASE_QUERY, false)) {
            findAndHookMethod(
                    SQLiteDatabase, "rawQueryWithFactory",
                    SQLiteCursorFactory, C.String, C.StringArray, C.String, SQLiteCancellationSignal, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val sql = param.args[1] as String?
                    val selectionArgs = param.args[2] as Array<*>?
                    log("DB => query sql = $sql, selectionArgs = ${argsToString(selectionArgs)}, db = ${param.thisObject}")
                }
            })
        }

        if (pref.getBoolean(DEVELOPER_DATABASE_INSERT, false)) {
            findAndHookMethod(
                    SQLiteDatabase, "insertWithOnConflict",
                    C.String, C.String, C.ContentValues, C.Int, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as String?
                    val values = param.args[2] as ContentValues?
                    log("DB => insert table = $table, values = $values, db = ${param.thisObject}")
                }
            })
        }

        if (pref.getBoolean(DEVELOPER_DATABASE_UPDATE, false)) {
            findAndHookMethod(
                    SQLiteDatabase, "updateWithOnConflict",
                    C.String, C.ContentValues, C.String, C.StringArray, C.Int, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as String?
                    val values = param.args[1] as ContentValues?
                    val whereClause = param.args[2] as String?
                    val whereArgs = param.args[3] as Array<*>?
                    log("DB => update " +
                            "table = $table, " +
                            "values = $values, " +
                            "whereClause = $whereClause, " +
                            "whereArgs = ${argsToString(whereArgs)}, " +
                            "db = ${param.thisObject}")
                }
            })
        }

        if (pref.getBoolean(DEVELOPER_DATABASE_DELETE, false)) {
            findAndHookMethod(
                    SQLiteDatabase, "delete",
                    C.String, C.String, C.StringArray, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as String?
                    val whereClause = param.args[1] as String?
                    val whereArgs = param.args[2] as Array<*>?
                    log("DB => delete " +
                            "table = $table, " +
                            "whereClause = $whereClause, " +
                            "whereArgs = ${argsToString(whereArgs)}, " +
                            "db = ${param.thisObject}")
                }
            })
        }

        if (pref.getBoolean(DEVELOPER_DATABASE_EXECUTE, false)) {
            findAndHookMethod(
                    SQLiteDatabase, "executeSql",
                    C.String, C.ObjectArray, SQLiteCancellationSignal, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val sql = param.args[0] as String?
                    val bindArgs = param.args[1] as Array<*>?
                    log("DB => executeSql sql = $sql, bindArgs = ${argsToString(bindArgs)}, db = ${param.thisObject}")
                }
            })
        }

        if (pref.getBoolean(DEVELOPER_DATABASE_PASSWORD, false)) {
            findAndHookMethod(
                    SQLiteDatabase, "openDatabase",
                    C.String, C.ByteArray, SQLiteCipherSpec, SQLiteCursorFactory,
                    C.Int, SQLiteErrorHandler, C.Int, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val path = param.args[0] as String? ?: return
                    if (path.endsWith("EnMicroMsg.db")) {
                        val password = param.args[1] as ByteArray? ?: return
                        log("DB => EnMicroMsg.db password = ${String(password)}")
                    }
                }
            })
        }
    }

    // Hook Log to trace hidden logcat output.
    @WechatHookMethod @JvmStatic fun traceLogCat() {
        if (pref.getBoolean(DEVELOPER_TRACE_LOGCAT, false)) {
            val functions = listOf("d", "e", "f", "i", "v", "w")
            functions.forEach { func ->
                findAndHookMethod(Logcat, func, C.String, C.String, C.ObjectArray, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val tag = param.args[0] as String?
                        val msg = param.args[1] as String?
                        val args = param.args[2] as Array<*>?
                        if (args == null) {
                            log("LOG.${func.toUpperCase()} => [$tag] $msg")
                        } else {
                            log("LOG.${func.toUpperCase()} => [$tag] ${msg?.format(*args)}")
                        }
                    }
                })
            }
        }
    }

    // Hook FileInputStream / FileOutputStream to trace file operations.
    @WechatHookMethod @JvmStatic fun traceFiles() {
        if (pref.getBoolean(DEVELOPER_TRACE_FILES, false)) {
            findAndHookConstructor(C.FileInputStream, C.File, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val path = (param.args[0] as File?)?.absolutePath ?: return
                    log("FILE => Read $path")
                }
            })

            findAndHookConstructor(C.FileOutputStream, C.File, C.Boolean, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val path = (param.args[0] as File?)?.absolutePath ?: return
                    log("FILE => Write $path")
                }
            })

            findAndHookMethod(C.File, "delete", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val file = param.thisObject as File
                    log("FILE => Delete ${file.absolutePath}")
                }
            })
        }
    }

    // Hook XML Parser to trace the XML files used in Wechat.
    @WechatHookMethod @JvmStatic fun traceXMLParse() {
        if (pref.getBoolean(DEVELOPER_XML_PARSER, false)) {
            findAndHookMethod(XmlParser, XmlParser_parse, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val xml = param.args[0] as String?
                    val root = param.args[1] as String?
                    log("XML => root = $root, xml = $xml")
                }
            })
        }
    }
}