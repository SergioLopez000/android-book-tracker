package com.librosleidos.data.auth

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Spec: "Backup rules exclude account store". */
class BackupRulesTest {

    private val resDir = File("src/main/res/xml")
    private val excluded = "datastore/${DataStoreAccountMetadataStore.FILE_NAME}"

    private fun excludes(name: String): List<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            File(resDir, name)
        )
        val nodes = doc.getElementsByTagName("exclude")
        return (0 until nodes.length).map { i ->
            val e = nodes.item(i) as Element
            e.parentNode.nodeName to "${e.getAttribute("domain")}:${e.getAttribute("path")}"
        }
    }

    @Test
    fun `full backup rules exclude the account store`() {
        assertEquals(
            listOf("full-backup-content" to "file:$excluded"),
            excludes("backup_rules.xml")
        )
    }

    @Test
    fun `data extraction rules exclude it from cloud backup and device transfer`() {
        val found = excludes("data_extraction_rules.xml")

        assertTrue(found.contains("cloud-backup" to "file:$excluded"))
        assertTrue(found.contains("device-transfer" to "file:$excluded"))
    }

    @Test
    fun `manifest references both rule files`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }
}
