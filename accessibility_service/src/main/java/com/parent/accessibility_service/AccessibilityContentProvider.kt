package com.parent.accessibility_service

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri

class AccessibilityContentProvider : ContentProvider() {

    companion object {
        private const val DATA_APP = 1
        private const val DATA_WORK = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(
                ContentProviderContract.AUTHORITY,
                ContentProviderContract.Data.TABLE_NAME_APP,
                DATA_APP
            )
            addURI(
                ContentProviderContract.AUTHORITY,
                ContentProviderContract.Data.TABLE_NAME_WORK,
                DATA_WORK
            )
        }
    }

    private lateinit var databaseHelper: DbHelper

    override fun onCreate(): Boolean {
        databaseHelper = DbHelper(context!!)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db: SQLiteDatabase = databaseHelper.readableDatabase
        val table = table(uri)

        return db.query(
            table,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val db: SQLiteDatabase = databaseHelper.writableDatabase
        val table = table(uri)


        val id = db.insert(table, null, values)
        if (id == -1L) {
            return null
        }
        context?.contentResolver?.notifyChange(uri, null)
        return ContentUris.withAppendedId(uri, id)
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val db: SQLiteDatabase = databaseHelper.writableDatabase
        val table = table(uri)

        val rowsUpdated = db.update(table, values, selection, selectionArgs)
        if (rowsUpdated != 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsUpdated
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db: SQLiteDatabase = databaseHelper.writableDatabase
        val table = table(uri)

        val rowsDeleted = db.delete(table, selection, selectionArgs)
        if (rowsDeleted != 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsDeleted
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            DATA_APP -> ContentProviderContract.Data.CONTENT_TYPE_APP
            DATA_WORK -> ContentProviderContract.Data.CONTENT_TYPE_WORK
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    private fun table(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            DATA_APP -> ContentProviderContract.Data.TABLE_NAME_APP
            DATA_WORK -> ContentProviderContract.Data.TABLE_NAME_WORK
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }
}
