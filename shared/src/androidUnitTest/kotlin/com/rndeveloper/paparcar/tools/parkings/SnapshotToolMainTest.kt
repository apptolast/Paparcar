package com.rndeveloper.paparcar.tools.parkings

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotToolMainTest {
    private fun inScratch(block: (File) -> Unit) {
        val scratch = Files.createTempDirectory("parking-snapshot-test").toFile()
        try { block(scratch) } finally { scratch.deleteRecursively() }
    }

    private fun raw(root: File, name: String, id: Int): File = File(root, name).apply {
        writeText("""{"elements":[{"type":"node","id":$id,"lat":48.85,"lon":2.35,
            "tags":{"amenity":"parking","parking":"surface","fee":"yes"}}]}""")
    }

    @Test
    fun should_keepBothTerritoriesAndDeduplicate_when_extractsOverlap() = inScratch { root ->
        val es = raw(root, "es.json", 1)
        val fr = raw(root, "fr.json", 2)
        val out = File(root, "public")
        SnapshotToolMain.main(arrayOf("--snapshot", out.path, "week-run-1", es.path, fr.path, es.path))
        val pointer = File(out, "parkings/v1/latest.json").readText()
        assertTrue(pointer.contains("\"parkings\":2"))
        assertTrue(File(out, "parkings/v1/week-run-1/parkings.ndjson.gz").isFile)
    }

    @Test
    fun should_preservePublishedBytes_when_theVersionIsReused() = inScratch { root ->
        val input = raw(root, "es.json", 1)
        val out = File(root, "public")
        val args = arrayOf("--snapshot", out.path, "week-run-1", input.path)
        SnapshotToolMain.main(args)
        val pointer = File(out, "parkings/v1/latest.json")
        val before = pointer.readText()
        assertFailsWith<IllegalArgumentException> { SnapshotToolMain.main(args) }
        assertEquals(before, pointer.readText())
    }

    @Test
    fun should_notMovePointer_when_anyExtractIsEmpty() = inScratch { root ->
        val es = raw(root, "es.json", 1)
        val empty = File(root, "fr.json").apply { writeText("""{"elements":[]}""") }
        val out = File(root, "public")
        assertFailsWith<IllegalArgumentException> {
            SnapshotToolMain.main(arrayOf("--snapshot", out.path, "new", es.path, empty.path))
        }
        assertFalse(File(out, "parkings/v1/latest.json").exists())
    }

    @Test
    fun should_rejectPathTraversal_when_versionIsNotAName() = inScratch { root ->
        val input = raw(root, "es.json", 1)
        assertFailsWith<IllegalArgumentException> {
            SnapshotToolMain.main(arrayOf("--snapshot", root.path, "../other", input.path))
        }
        assertFalse(File(root, "parkings").exists())
    }
}
