package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupRulesTest {

    @Test
    fun testBackupRulesTargetCorrectDatabaseName() {
        val backupRulesFile = File("src/main/res/xml/backup_rules.xml")
        assertTrue("backup_rules.xml should exist", backupRulesFile.exists())
        val backupContent = backupRulesFile.readText()
        assertTrue("backup_rules.xml must include dhanam_expense_database", backupContent.contains("dhanam_expense_database"))
        assertTrue("backup_rules.xml must exclude dhanam_secure_pin.xml", backupContent.contains("dhanam_secure_pin.xml"))
    }

    @Test
    fun testDataExtractionRulesTargetCorrectDatabaseName() {
        val rulesFile = File("src/main/res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml should exist", rulesFile.exists())
        val rulesContent = rulesFile.readText()
        assertTrue("data_extraction_rules.xml must include dhanam_expense_database", rulesContent.contains("dhanam_expense_database"))
        assertTrue("data_extraction_rules.xml must exclude dhanam_secure_pin.xml", rulesContent.contains("dhanam_secure_pin.xml"))
    }
}
