package com.example.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomSchemaValidationTest {

    @Test
    fun testRoomSchemaVersion1FileExistsAndIsValid() {
        val schemaFile = File("schemas/com.example.data.local.AppDatabase/1.json")
        assertTrue("Room schema version 1.json must exist under app/schemas/", schemaFile.exists())
        val json = schemaFile.readText()
        assertTrue("Schema version must be 1", json.contains("\"version\": 1"))
        assertTrue("Schema must contain expenses table", json.contains("\"tableName\": \"expenses\""))
        assertTrue("Schema must contain loans table", json.contains("\"tableName\": \"loans\""))
        assertTrue("Schema must contain category_budgets table", json.contains("\"tableName\": \"category_budgets\""))
    }
}
