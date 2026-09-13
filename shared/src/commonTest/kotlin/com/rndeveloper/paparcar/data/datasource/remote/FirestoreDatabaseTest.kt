package com.rndeveloper.paparcar.data.datasource.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirestoreDatabaseTest {

    @Test
    fun should_useDebugDatabase_when_debugBuild() {
        assertEquals("debug", FirestoreDatabase.idFor(isDebugBuild = true))
    }

    @Test
    fun should_useDefaultDatabase_when_releaseBuild() {
        assertNull(FirestoreDatabase.idFor(isDebugBuild = false))
    }
}
