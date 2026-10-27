package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupRulesTest {

    @Test
    fun backupRulesExcludeAllAppFilesFromCloudBackup() {
        val backupRulesFile = File("src/main/res/xml/backup_rules.xml")
        assertTrue("backup_rules.xml should exist", backupRulesFile.exists())
        val content = backupRulesFile.readText()
        assertTrue("Backup must exclude the app root", content.contains("<exclude domain=\"root\" path=\".\""))
        assertTrue("Sensitive PIN file must not be explicitly included", !content.contains("<include") && !content.contains("dhanam_secure_pin.xml"))
    }

    @Test
    fun dataExtractionRulesExcludeCloudBackupAndDeviceTransfer() {
        val rulesFile = File("src/main/res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml should exist", rulesFile.exists())
        val content = rulesFile.readText()
        assertTrue("Cloud backup must exclude the app root", content.contains("<cloud-backup>") && content.contains("<exclude domain=\"root\" path=\".\""))
        assertTrue("Device transfer must exclude the app root", content.contains("<device-transfer>") && content.contains("<exclude domain=\"root\" path=\".\""))
        assertTrue("Sensitive PIN file must not be explicitly included", !content.contains("<include") && !content.contains("dhanam_secure_pin.xml"))
    }
}
