package com.gurbakir.mobile.di

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GurbakirDatabaseCompositionTest {
    @Test
    fun appProviderOpensExactLegacyFilenameInsideAnIsolatedTestDirectory() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(targetContext.cacheDir, "database-composition-${UUID.randomUUID()}").canonicalFile
        assertTrue(directory.mkdir())
        val isolatedContext = IsolatedDatabaseContext(targetContext, directory)
        val database = SearchModule.provideLocalCommerceDatabase(isolatedContext)
        try {
            val actualPath = requireNotNull(database.openHelper.writableDatabase.path)
            val actualFile = File(actualPath).canonicalFile
            assertEquals(DATABASE_NAME, actualFile.name)
            assertEquals(directory, actualFile.parentFile)
            assertEquals(isolatedContext.getDatabasePath(DATABASE_NAME), actualFile)
            assertTrue(actualFile.isFile)
        } finally {
            database.close()
            isolatedContext.deleteDatabase(DATABASE_NAME)
            assertFalse(isolatedContext.getDatabasePath(DATABASE_NAME).exists())
            assertTrue("Only the empty owned test directory is removed", directory.delete())
        }
    }

    /** Keeps Room's applicationContext and both SQLiteOpenHelper opening paths isolated. */
    private class IsolatedDatabaseContext(base: Context, private val directory: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File {
            require(name == DATABASE_NAME)
            return File(directory, name).canonicalFile.also { require(it.parentFile == directory) }
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase = openOrCreateDatabase(name, mode, factory, null)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase = SQLiteDatabase.openDatabase(
            getDatabasePath(name).path,
            factory,
            SQLiteDatabase.CREATE_IF_NECESSARY,
            errorHandler
        )

        override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    }

    private companion object {
        const val DATABASE_NAME = "gurbakir-local.db"
    }
}
