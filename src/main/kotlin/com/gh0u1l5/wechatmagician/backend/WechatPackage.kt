@file:Suppress("MemberVisibilityCanPrivate")

package com.gh0u1l5.wechatmagician.backend

import android.content.Context
import com.gh0u1l5.wechatmagician.C
import com.gh0u1l5.wechatmagician.Global.WECHAT_PACKAGE_NAME
import com.gh0u1l5.wechatmagician.Version
import com.gh0u1l5.wechatmagician.util.FileUtil
import com.gh0u1l5.wechatmagician.util.PackageUtil.findClassIfExists
import com.gh0u1l5.wechatmagician.util.PackageUtil.findClassesFromPackage
import com.gh0u1l5.wechatmagician.util.PackageUtil.findFieldsWithType
import com.gh0u1l5.wechatmagician.util.PackageUtil.findMethodsByExactParameters
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.bean.DexClass
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// WechatPackage analyzes and stores critical classes and objects in Wechat application.
// These classes and objects will be used for hooking and tampering with runtime data.
object WechatPackage {

    // status stores the working status of all the hooks.
    private val statusLock = ReentrantReadWriteLock()
    private val status: HashMap<String, Boolean> = hashMapOf()

    var LogCat: Class<*>? = null
    var XLogSetup: Class<*>? = null
    var WebWXLoginUI: Class<*>? = null
    var RemittanceAdapter: Class<*>? = null
    var ActionBarEditText: Class<*>? = null

    var WXCustomScheme: Class<*>? = null
    var WXCustomSchemeEntryMethod: Method? = null

    var EncEngine: Class<*>? = null
    var EncEngineEDMethod: Method? = null

    var SQLiteDatabasePkg = ""
    var SQLiteDatabase: Class<*>? = null
    var SQLiteCursorFactory: Class<*>? = null
    var SQLiteErrorHandler: Class<*>? = null
    var SQLiteCancellationSignal: Class<*>? = null

    var MMActivity: Class<*>? = null
    var MMFragmentActivity: Class<*>? = null
    var MMListPopupWindow: Class<*>? = null

    var MMBaseAdapter: Class<*>? = null
    var AddressAdapter: Class<*>? = null
    var ConversationWithCacheAdapter: Class<*>? = null

    var SnsActivity: Class<*>? = null
    var SnsUploadUI: Class<*>? = null
    var SnsUploadUIEditTextField: Field? = null
    var SnsUserUI: Class<*>? = null
    var SnsTimeLineUI: Class<*>? = null

    var AlbumPreviewUI: Class<*>? = null
    var SelectContactUI: Class<*>? = null
    var SelectConversationUI: Class<*>? = null
    var SelectConversationUIMaxLimitMethod: Method? = null

    var MsgInfoClass: Class<*>? = null
    var ContactInfoClass: Class<*>? = null

    var MsgStorageClass: Class<*>? = null
    var MsgStorageInsertMethod: Method? = null
    @Volatile var MsgStorageObject: Any? = null

    val CacheMapClass = "$WECHAT_PACKAGE_NAME.a.f"
    val CacheMapPutMethod = "k"

    var ImgStorageClass: Class<*>? = null
    var ImgStorageCacheField = ""
    val ImgStorageLoadMethod = "a"
    @Volatile var ImgStorageObject: Any? = null

    var XMLParserClass: Class<*>? = null
    var XMLParseMethod: Method? = null

    // Analyzes Wechat package statically for the name of classes.
    // WechatHook will do the runtime analysis and set the objects.
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val loader = lpparam.classLoader
        val version = getVersion(lpparam)

        var apkFile: ApkFile? = null
        val classes: Array<DexClass>
        try {
            apkFile = ApkFile(lpparam.appInfo.sourceDir)
            classes = apkFile.dexClasses
        } finally {
            apkFile?.close()
        }


        LogCat = findClassesFromPackage(loader, classes, "$WECHAT_PACKAGE_NAME.sdk.platformtools")
                .filterByMethod(null, "printErrStackTrace", C.String, C.Throwable, C.String, C.ObjectArray)
                .firstOrNull("LogCat")
        XLogSetup = findClassIfExists(
                "$WECHAT_PACKAGE_NAME.xlog.app.XLogSetup", loader)
        WebWXLoginUI = findClassIfExists(
                "$WECHAT_PACKAGE_NAME.plugin.webwx.ui.ExtDeviceWXLoginUI", loader)
        RemittanceAdapter = findClassIfExists(
                "$WECHAT_PACKAGE_NAME.plugin.remittance.ui.RemittanceAdapterUI", loader)
        ActionBarEditText = findClassIfExists(
                "$WECHAT_PACKAGE_NAME.ui.tools.ActionBarSearchView.ActionBarEditText", loader)

        WXCustomScheme = findClassIfExists(
                "$WECHAT_PACKAGE_NAME.plugin.base.stub.WXCustomSchemeEntryActivity", loader)
        WXCustomSchemeEntryMethod = findMethodsByExactParameters(
                WXCustomScheme, C.Boolean, C.Intent
        ).firstOrNull()

        EncEngine = findClassesFromPackage(loader, classes, "$WECHAT_PACKAGE_NAME.modelsfs")
                .filterByMethod(null, "seek", C.Long)
                .filterByMethod(null, "free")
                .firstOrNull("EncEngine")
        EncEngineEDMethod = findMethodsByExactParameters(
                EncEngine, C.Int, C.ByteArray, C.Int
        ).firstOrNull()


        SQLiteDatabasePkg = when {
            version >= Version("6.5.8") -> "com.tencent.wcdb"
            else -> "com.tencent.mmdb"
        }
        SQLiteDatabase = findClassIfExists(
                "$SQLiteDatabasePkg.database.SQLiteDatabase", loader)
        SQLiteCursorFactory = findClassIfExists(
                "$SQLiteDatabasePkg.database.SQLiteDatabase.CursorFactory", loader)
        SQLiteErrorHandler = findClassIfExists(
                "$SQLiteDatabasePkg.DatabaseErrorHandler", loader)
        SQLiteCancellationSignal = findClassIfExists(
                "$SQLiteDatabasePkg.support.CancellationSignal", loader)


        val pkgUI = "$WECHAT_PACKAGE_NAME.ui"
        MMActivity = findClassIfExists("$pkgUI.MMActivity", loader)
        MMFragmentActivity = findClassIfExists("$pkgUI.MMFragmentActivity", loader)
        MMListPopupWindow = findClassIfExists("$pkgUI.base.MMListPopupWindow", loader)


        AddressAdapter = findClassesFromPackage(loader, classes, "$pkgUI.contact")
                .filterByMethod(null, "pause")
                .firstOrNull("AddressAdapter")
        ConversationWithCacheAdapter = findClassesFromPackage(loader, classes, "$pkgUI.conversation")
                .filterByMethod(null, "clearCache")
                .firstOrNull("ConversationWithCacheAdapter")
        if (AddressAdapter != null && ConversationWithCacheAdapter != null) {
            if (AddressAdapter?.superclass != ConversationWithCacheAdapter?.superclass) {
                log("Unexpected base adapter: ${AddressAdapter?.superclass} and ${ConversationWithCacheAdapter?.superclass}")
            }
            MMBaseAdapter = AddressAdapter?.superclass
        }


        val pkgSnsUI = "$WECHAT_PACKAGE_NAME.plugin.sns.ui"
        val snsUIClasses = findClassesFromPackage(loader, classes, pkgSnsUI)
        SnsActivity = snsUIClasses
                .filterByField("$pkgUI.base.MMPullDownView")
                .firstOrNull("SnsActivity")
        SnsUploadUI = snsUIClasses
                .filterByField("$pkgSnsUI.LocationWidget")
                .filterByField("$pkgSnsUI.SnsUploadSayFooter")
                .firstOrNull("SnsUploadUI")
        SnsUploadUIEditTextField = findFieldsWithType(
                SnsUploadUI, "$pkgSnsUI.SnsEditText"
        ).firstOrNull()
        SnsUserUI = findClassIfExists("$pkgSnsUI.SnsUserUI", loader)
        SnsTimeLineUI = snsUIClasses
                .filterByField("android.support.v7.app.ActionBar")
                .firstOrNull("SnsTimeLineUI")


        val pkgGalleryUI = "$WECHAT_PACKAGE_NAME.plugin.gallery.ui"
        AlbumPreviewUI = findClassIfExists("$pkgGalleryUI.AlbumPreviewUI", loader)
        SelectContactUI = findClassIfExists("$pkgUI.contact.SelectContactUI", loader)
        SelectConversationUI = findClassIfExists("$pkgUI.transmit.SelectConversationUI", loader)
        SelectConversationUIMaxLimitMethod = findMethodsByExactParameters(
                SelectConversationUI, C.Boolean, C.Boolean
        ).firstOrNull()


        val storageClasses = findClassesFromPackage(loader, classes, "$WECHAT_PACKAGE_NAME.storage")
        MsgInfoClass = storageClasses
                .filterByMethod(C.Boolean, "isSystem")
                .firstOrNull("MsgInfoClass")
        ContactInfoClass = storageClasses
                .filterByMethod(C.String, "getCityCode")
                .filterByMethod(C.String, "getCountryCode")
                .firstOrNull("ContactInfoClass")

        if (MsgInfoClass != null) {
            MsgStorageClass = when {
                version >= Version("6.5.8") ->
                    storageClasses
                            .filterByMethod(C.Long, MsgInfoClass!!, C.Boolean)
                            .firstOrNull("MsgStorageClass")
                else ->
                    storageClasses
                            .filterByMethod(C.Long, MsgInfoClass!!)
                            .firstOrNull("MsgStorageClass")
            }
            MsgStorageInsertMethod = when {
                version >= Version("6.5.8") ->
                    findMethodsByExactParameters(
                        MsgStorageClass, C.Long, MsgInfoClass!!, C.Boolean
                    ).firstOrNull()
                else ->
                    findMethodsByExactParameters(
                        MsgStorageClass, C.Long, MsgInfoClass!!
                    ).firstOrNull()
            }
        }

//        ImgStorageClass = findClassesFromPackage(loader, classes, WECHAT_PACKAGE_NAME, 1)
//                .filterByMethod(C.String, ImgStorageLoadMethod, C.String, C.String, C.String, C.Boolean)
//                .firstOrNull("ImgStorageClass")
//        ImgStorageCacheField = findFieldsWithGenericType(
//                ImgStorageClass, "$CacheMapClass<java.lang.String, android.graphics.Bitmap>"
//        ).firstOrNull()?.name ?: ""


        val platformClasses = findClassesFromPackage(loader, classes,"$WECHAT_PACKAGE_NAME.sdk.platformtools")
        XMLParserClass = platformClasses
                .filterByMethod(C.Map, C.String, C.String)
                .firstOrNull("XMLParserClass")
        XMLParseMethod = findMethodsByExactParameters(
                XMLParserClass, C.Map, C.String, C.String
        ).firstOrNull()
    }

    // getVersion returns the version of current package / application
    private fun getVersion(lpparam: XC_LoadPackage.LoadPackageParam): Version {
        val activityThreadClass = findClass("android.app.ActivityThread", null)
        val activityThread = callStaticMethod(activityThreadClass, "currentActivityThread")
        val context = callMethod(activityThread, "getSystemContext") as Context?
        val versionName = context?.packageManager?.getPackageInfo(lpparam.packageName, 0)?.versionName
        return Version(versionName ?: throw Error("Cannot get Wechat version"))
    }

    // setStatus updates current status of the Wechat hooks.
    fun setStatus(key: String, value: Boolean) {
        statusLock.write {
            status[key] = value
        }
    }

    // writeStatus writes current status to the given path.
    fun writeStatus(path: String) {
        statusLock.read {
            try {
                FileUtil.writeOnce(path, {
                    FileUtil.writeObjectToDisk(it, status)
                })
                FileUtil.setWorldReadable(File(path))
            } catch (_: Throwable) {
                // Ignore this one
            }
        }
    }

    override fun toString(): String {
        return this.javaClass.declaredFields.filter {
            when(it.name) {
                "INSTANCE", "status", "statusLock" -> false
                else -> true
            }
        }.joinToString("\n") {
            it.isAccessible = true; "${it.name} = ${it.get(this)}"
        }
    }
}