package com.parent.accessibility_service

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.BaseColumns

object ContentProviderContract {
    const val AUTHORITY = "com.parent.accessibility_service.provider"
    val CONTENT_URI_APP: Uri = Uri.parse("content://$AUTHORITY/${Data.TABLE_NAME_APP}")
    val CONTENT_URI_WORK: Uri = Uri.parse("content://$AUTHORITY/${Data.TABLE_NAME_WORK}")

    object Data : BaseColumns {
        const val TABLE_NAME_APP = "data_app"
        const val TABLE_NAME_WORK = "data_work"

        const val COLUMN_PACKAGES = "packages"
        const val COLUMN_FROM = "work_from"
        const val COLUMN_TO = "work_to"

        const val CONTENT_TYPE_APP =
            "${ContentResolver.CURSOR_DIR_BASE_TYPE}/$AUTHORITY/$TABLE_NAME_APP"
        const val CONTENT_TYPE_WORK =
            "${ContentResolver.CURSOR_DIR_BASE_TYPE}/$AUTHORITY/$TABLE_NAME_WORK"
    }
}

class DbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        val createTableApps = """
            CREATE TABLE ${ContentProviderContract.Data.TABLE_NAME_APP} (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${ContentProviderContract.Data.COLUMN_PACKAGES} TEXT UNIQUE
            )
        """
        val createTableWork = """
            CREATE TABLE ${ContentProviderContract.Data.TABLE_NAME_WORK} (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${ContentProviderContract.Data.COLUMN_FROM} INTEGER UNIQUE,
                ${ContentProviderContract.Data.COLUMN_TO} INTEGER UNIQUE
            )
        """
        db.execSQL(createTableApps)
        db.execSQL(createTableWork)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${ContentProviderContract.Data.TABLE_NAME_APP}")
        db.execSQL("DROP TABLE IF EXISTS ${ContentProviderContract.Data.TABLE_NAME_WORK}")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "accessibility"
        private const val DATABASE_VERSION = 1
    }
}