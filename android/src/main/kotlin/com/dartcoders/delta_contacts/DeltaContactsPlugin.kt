package com.dartcoders.delta_contacts

import android.Manifest
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** DeltaContactsPlugin */
class DeltaContactsPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var activity: FlutterActivity
    private lateinit var contentResolver: ContentResolver
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val contactList = mutableListOf<Map<String, Any>>()

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "delta_contacts")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    contentResolver = context.contentResolver
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
      if (checkPermission()) {
        if (call.method == "getContacts") {
          val lastUpdatedAt = call.argument<Long>("lastUpdatedAt") ?: 0
            mainScope.launch {
                try {
                    val data: List<Map<String, Any>>
                    withContext(Dispatchers.Default) {
                        data = fetchContacts(lastUpdatedAt)
                    }
                    result.success(data)
                } catch (e: Exception) {
                    Log.e("Contact fetcher plugin", "Some error occoured")
                }
            }
        } else {
            result.notImplemented()
        }
      } else {
        Log.e("Contact fetcher Plugin", "Contact permission is not enabled")
        result.error("Contact Permission not enabled", "", ArrayList<JSONObject>().toString())
      }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
      activity = binding.activity as FlutterActivity
  }

  override fun onDetachedFromActivityForConfigChanges() {
      channel.setMethodCallHandler(null)
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
      activity = binding.activity as FlutterActivity
  }

  override fun onDetachedFromActivity() {
      channel.setMethodCallHandler(null)
  }

  @TargetApi(Build.VERSION_CODES.M)
  private fun fetchContacts(lastUpdatedAt: Long): List<Map<String, Any>> {
      val selection = if (lastUpdatedAt > 0) 
            "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} > ?" 
      else 
          null
      val selectionArgs = if (lastUpdatedAt > 0) 
          arrayOf(lastUpdatedAt.toString()) 
      else 
          null
      val cursor = contentResolver.query(
          ContactsContract.Contacts.CONTENT_URI, arrayOf(
              ContactsContract.Contacts._ID,
              ContactsContract.Contacts.DISPLAY_NAME,
              ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
          ), selection, selectionArgs, null, null
      )
      contactList.clear()
      if (cursor != null && cursor.moveToFirst()) {
          bindDataFromCursor(cursor)
          cursor.close()
      }
      return contactList
  }

  private fun bindDataFromCursor(cursor: Cursor) {
      var count = 0
      do {
          val contactObject = mutableMapOf<String, Any>()
          val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
          contactObject["id"] = id
          contactObject["name"] = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
          contactObject["last_updated_at"] = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP))
          val phoneCursor = contentResolver.query(
              ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
              null,
              ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " =?",
              arrayOf(id),
              null
          )
          val phoneNumberList = mutableListOf<String>()
          while (phoneCursor!!.moveToNext()) {
                phoneNumberList.add(
                    phoneCursor.getString(
                        phoneCursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        )
                    )
                )
            }
            phoneCursor.close()
            contactObject["phone_numbers"] = phoneNumberList

            val emailCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " =?",
                arrayOf(id),
                null
            )
            val emailList = mutableListOf<String>()
            while (emailCursor != null && emailCursor.moveToNext()) {
                emailList.add(
                    emailCursor.getString(
                        emailCursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Email.ADDRESS
                        )
                    )
                )
            }
            emailCursor?.close()
            contactObject["emails"] = emailList

            val contactName = contactObject["name"] as? String
            val phoneNumbers = contactObject["phone_numbers"] as? List<*> ?: emptyList<Any>()
            
            if ((contactName != null && contactName.isNotEmpty()) || phoneNumbers.isNotEmpty()) {
                contactList.add(contactObject)
            }
            ++count
      } while (cursor.moveToNext())
  }

  private fun checkPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
  }
}
