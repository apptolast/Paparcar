package com.rndeveloper.paparcar.bluetooth

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30, 31, 34])
class AndroidBluetoothScannerPermissionTest {
    private class PermissionContext(private val granted: Boolean) :
        ContextWrapper(RuntimeEnvironment.getApplication()) {
        var checkedPermission: String? = null
        var serviceCalls = 0

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
            checkedPermission = permission
            return if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }

        override fun getSystemService(name: String): Any? {
            if (name == Context.BLUETOOTH_SERVICE) {
                serviceCalls++
                throw SecurityException("Access revoked after permission check")
            }
            return super.getSystemService(name)
        }
    }

    @Test
    fun should_skip_adapter_when_permission_is_missing() {
        val context = PermissionContext(false)
        val scanner = AndroidBluetoothScanner(context)
        assertFalse(scanner.isBluetoothEnabled())
        assertEquals(emptyList(), scanner.getBondedDevices())
        assertEquals(0, context.serviceCalls)
        assertEquals(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH
            },
            context.checkedPermission,
        )
    }

    @Test
    fun should_return_unavailable_when_platform_revokes_access_after_check() {
        val context = PermissionContext(true)
        val scanner = AndroidBluetoothScanner(context)
        assertFalse(scanner.isBluetoothEnabled())
        assertEquals(emptyList(), scanner.getBondedDevices())
        assertEquals(2, context.serviceCalls)
    }
}
