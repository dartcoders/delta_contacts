package com.dartcoders.delta_contacts

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** DeltaContactsPlugin */
class DeltaContactsPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    private lateinit var contentResolver: ContentResolver
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var pendingResult: MethodChannel.Result? = null

    companion object {
        private const val REQ_PICK_CONTACT = 1001
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "delta_contacts")
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
        contentResolver = context.contentResolver
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getContacts" -> {
                if (!checkPermission()) {
                    Log.e("DeltaContactsPlugin", "Contact permission not granted")
                    result.error("permission_denied", "READ_CONTACTS permission not granted", null)
                    return
                }

                val lastUpdatedAt = call.argument<Long>("lastUpdatedAt") ?: 0L

                mainScope.launch {
                    try {
                        val data = fetchContactsAsMaps(lastUpdatedAt)
                        result.success(data)
                    } catch (e: SecurityException) {
                        Log.e("DeltaContactsPlugin", "SecurityException while reading contacts", e)
                        result.error("security_error", e.message ?: "security exception", null)
                    } catch (e: Exception) {
                        Log.e("DeltaContactsPlugin", "Error fetching contacts", e)
                        result.error("fetch_error", e.message ?: "unknown error", null)
                    }
                }
            }
            "pickContact" -> {
                if (pendingResult != null) {
                    result.error("already_active", "Another picker is active", null)
                    return
                }
                try {
                    val act = activity
                    if (act == null) {
                        result.error("no_activity", "Activity not available", null)
                        return
                    }
                    val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                    pendingResult = result
                    act.startActivityForResult(intent, REQ_PICK_CONTACT)
                } catch (e: Exception) {
                    pendingResult = null
                    Log.e("DeltaContactsPlugin", "Failed to launch contact picker", e)
                    result.error("picker_error", e.message ?: "failed to launch picker", null)
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        pendingResult?.success(null)
        pendingResult = null
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        pendingResult?.success(null)
        pendingResult = null
        activity = null
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @TargetApi(Build.VERSION_CODES.M)
    private suspend fun fetchContactsAsMaps(lastUpdatedAt: Long = 0L): List<Map<String, Any>> =
        withContext(Dispatchers.IO) {
            val mimePhone = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            val mimeEmail = ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE

            val projection = arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
            )

            val selBuilder = StringBuilder("${ContactsContract.Data.MIMETYPE} IN (?, ?)")
            val selArgsList = mutableListOf(mimePhone, mimeEmail)
            if (lastUpdatedAt > 0L) {
                selBuilder.append(" AND ${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} > ?")
                selArgsList.add(lastUpdatedAt.toString())
            }
            val selection = selBuilder.toString()
            val selectionArgs = selArgsList.toTypedArray()

            val contactMap = LinkedHashMap<String, MutableMap<String, Any?>>()

            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idxId = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                val idxName = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                val idxMime = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val idxPhone = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER) // may be -1
                val idxEmail = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS) // may be -1

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idxId) ?: continue
                    val name = cursor.getString(idxName) ?: ""
                    val mime = cursor.getString(idxMime) ?: ""

                    val contact = contactMap.getOrPut(id) {
                        mutableMapOf<String, Any?>(
                            "id" to id,
                            "name" to name,
                            "phone_numbers" to linkedSetOf<String>(),
                            "emails" to linkedSetOf<String>()
                        )
                    }

                    if ((contact["name"] as? String).isNullOrEmpty() && name.isNotEmpty()) {
                        contact["name"] = name
                    }

                    if (mime == mimePhone && idxPhone >= 0) {
                        val raw = cursor.getString(idxPhone)
                        if (!raw.isNullOrBlank()) {
                            val normalized = PhoneNumberUtils.normalizeNumber(raw)
                            if (normalized.isNotBlank()) {
                                @Suppress("UNCHECKED_CAST")
                                val phones = contact["phone_numbers"] as MutableSet<String>
                                phones.add(normalized)
                            }
                        }
                    } else if (mime == mimeEmail && idxEmail >= 0) {
                        val e = cursor.getString(idxEmail)
                        if (!e.isNullOrBlank()) {
                            @Suppress("UNCHECKED_CAST")
                            val emails = contact["emails"] as MutableSet<String>
                            emails.add(e)
                        }
                    }
                }
            }

            val out = ArrayList<Map<String, Any>>(contactMap.size)
            for ((_, v) in contactMap) {
                val id = v["id"] as String
                val name = (v["name"] as? String).orEmpty()
                @Suppress("UNCHECKED_CAST")
                val phones = (v["phone_numbers"] as? Set<String>)?.toList().orEmpty()
                @Suppress("UNCHECKED_CAST")
                val emails = (v["emails"] as? Set<String>)?.toList().orEmpty()

                if (name.isNotEmpty() || phones.isNotEmpty()) {
                    val map = mapOf<String, Any>(
                        "id" to id,
                        "name" to name,
                        "phone_numbers" to phones,
                        "emails" to emails
                    )
                    out.add(map)
                }
            }

            out
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_PICK_CONTACT) return false
        val result = pendingResult
        pendingResult = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            result?.success(null)
            return true
        }
        mainScope.launch {
            try {
                val contact = withContext(Dispatchers.IO) {
                    fetchContactFromPhoneUri(data.data!!)
                }
                result?.success(contact)
            } catch (e: Exception) {
                Log.e("DeltaContactsPlugin", "Failed to read picked contact", e)
                result?.error("read_error", e.message, null)
            }
        }
        return true
    }

    private fun fetchContactFromPhoneUri(uri: Uri): Map<String, Any>? {
        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        )
        var number: String? = null
        var name: String = ""
        var contactId: String? = null
        contentResolver.query(uri, phoneProjection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idxNum = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idxName = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val idxCid = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                number = c.getString(idxNum)
                name = c.getString(idxName) ?: ""
                contactId = c.getLong(idxCid).toString()
            }
        }
        val phones = mutableListOf<String>()
        number?.let {
            val normalized = PhoneNumberUtils.normalizeNumber(it)
            if (normalized.isNotBlank() && !phones.contains(normalized)) {
                phones.add(normalized)
            }
        }
        val emails = mutableListOf<String>()

        return if (contactId == null && phones.isEmpty() && name.isEmpty()) {
            null
        } else {
            mapOf(
                "id" to (contactId ?: ""),
                "name" to name,
                "phone_numbers" to phones,
                "emails" to emails
            )
        }
    }
}
