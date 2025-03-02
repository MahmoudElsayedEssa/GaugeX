package com.binissa.api

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class GaugeXInitializer : ContentProvider() {
    private val TAG = "GaugeXInitializer"

    override fun onCreate(): Boolean {
        val context = context ?: return false

        Log.i(TAG, "Initializing GaugeX SDK")

        // Initialize in a background thread to avoid blocking app startup
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                GaugeXProvider.initialize(context)
                Log.i(TAG, "GaugeX SDK initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize GaugeX SDK", e)
            }
        }

        return true
    }

    // ContentProvider implementation (not used, just for automatic initialization)
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
