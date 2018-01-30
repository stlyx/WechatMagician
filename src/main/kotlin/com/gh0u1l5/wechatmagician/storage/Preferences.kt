package com.gh0u1l5.wechatmagician.storage

import android.content.*
import android.os.Environment
import com.gh0u1l5.wechatmagician.Global.ACTION_UPDATE_PREF
import com.gh0u1l5.wechatmagician.Global.FOLDER_SHARED_PREFS
import com.gh0u1l5.wechatmagician.Global.MAGICIAN_BASE_DIR
import com.gh0u1l5.wechatmagician.Global.PREFERENCE_STRING_LIST_KEYS
import com.gh0u1l5.wechatmagician.Global.tryWithLog
import com.gh0u1l5.wechatmagician.Global.tryWithThread
import com.gh0u1l5.wechatmagician.WaitChannel
import com.gh0u1l5.wechatmagician.util.FileUtil
import de.robv.android.xposed.XSharedPreferences
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

class Preferences : SharedPreferences {

    // loadChannel resumes all the threads waiting for the preference loading.
    private val loadChannel = WaitChannel()

    // listCache caches the string lists in memory to speed up getStringList()
    private val listCache: MutableMap<String, List<String>> = ConcurrentHashMap()

    // content is the preferences generated by the frond end of Wechat Magician.
    private var content: XSharedPreferences? = null
    // legacy is the legacy preferences generated by Wechat Magician of version <= 2.5.2
    @Volatile private var legacy: HashMap<String, Any?>? = null

    fun init(preferencesName: String) {
        tryWithThread {
            try {
                // First, check the legacy preferences on external storage
                // If legacy preferences exists, load the legacy content.
                val storage = Environment.getExternalStorageDirectory()
                val legacyPrefDir = File(storage, "WechatMagician/.prefs")
                val legacyPrefFile = File(legacyPrefDir, preferencesName)
                if (legacyPrefFile.exists()) {
                    val path = legacyPrefFile.absolutePath
                    legacy = FileUtil.readObjectFromDisk(path) as HashMap<String, Any?>
                }
                // Also load the preferences in the data directories.
                val preferencePath = "$MAGICIAN_BASE_DIR/$FOLDER_SHARED_PREFS/$preferencesName.xml"
                content = XSharedPreferences(File(preferencePath))
            } catch (_: FileNotFoundException) {
                // Ignore this one
            } finally {
                loadChannel.done()
                cacheStringList()
            }
        }
    }

    // listen registers a receiver to listen the update events from the frontend.
    fun listen(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                loadChannel.wait()

                // Reload and cache the shared preferences
                content?.reload()
                cacheStringList()

                // The latest preferences has been moved to the data directory.
                // It is safe to remove the legacy preferences on the external storage.
                legacy = null
                val storage = Environment.getExternalStorageDirectory()
                val legacyPrefDir = File(storage, "WechatMagician/.prefs")
                legacyPrefDir.deleteRecursively()
            }
        }
        tryWithLog {
            context.registerReceiver(receiver, IntentFilter(ACTION_UPDATE_PREF))
        }
    }

    fun cacheStringList() {
        PREFERENCE_STRING_LIST_KEYS.forEach { key ->
            listCache[key] = getString(key, "").split(" ").filter { it.isNotEmpty() }
        }
    }

    override fun contains(key: String): Boolean {
        if (legacy != null) {
            return legacy?.contains(key) ?: false
        }
        return content?.contains(key) ?: false
    }

    override fun getAll(): MutableMap<String, *>? {
        if (legacy != null) {
            return legacy
        }
        return content?.all
    }

    private fun getValue(key: String): Any? {
        loadChannel.wait()
        return all?.get(key)
    }

    private inline fun <reified T>getValue(key: String, defValue: T): T {
        return getValue(key) as? T ?: defValue
    }

    override fun getInt(key: String, defValue: Int): Int = getValue(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = getValue(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = getValue(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = getValue(key, defValue)

    override fun getString(key: String, defValue: String): String = getValue(key, defValue)

    override fun getStringSet(key: String, defValue: MutableSet<String>): MutableSet<String> = getValue(key, defValue)

    fun getStringList(key: String, defValue: List<String>): List<String> {
        loadChannel.wait()
        return listCache[key] ?: defValue
    }

    override fun edit(): SharedPreferences.Editor {
        throw UnsupportedOperationException()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        throw UnsupportedOperationException()
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        throw UnsupportedOperationException()
    }
}
