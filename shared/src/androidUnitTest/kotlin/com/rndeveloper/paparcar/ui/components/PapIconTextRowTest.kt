package com.rndeveloper.paparcar.ui.components

import android.os.Looper
import android.view.View.MeasureSpec
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PapIconTextRowTest {
    @Test
    fun should_centerSingleLineAndKeepWrappedTextAtTop_when_availableWidthChanges() {
        val labelBounds = mutableMapOf<Boolean, Rect>()
        val iconBounds = mutableMapOf<Boolean, Rect>()
        val lineCounts = mutableMapOf<Boolean, Int>()

        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val view = ComposeView(activity)
            activity.setContentView(view)
            view.setContent {
                Column {
                    for (narrow in listOf(false, true)) {
                        PapIconTextRow(
                            gap = 8.dp,
                            modifier = Modifier.width(if (narrow) 96.dp else 300.dp),
                            icon = {
                                Box(
                                    Modifier.size(36.dp).onGloballyPositioned {
                                        iconBounds[narrow] = it.boundsInRoot()
                                    },
                                )
                            },
                        ) {
                            Column {
                                Text(
                                    text = "Parking history",
                                    fontSize = 16.sp,
                                    modifier = Modifier.onGloballyPositioned {
                                        labelBounds[narrow] = it.boundsInRoot()
                                    },
                                    onTextLayout = { lineCounts[narrow] = it.lineCount },
                                )
                            }
                        }
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(
                MeasureSpec.makeMeasureSpec(1200, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(1200, MeasureSpec.AT_MOST),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            assertEquals(1, lineCounts.getValue(false))
            assertEquals(iconBounds.getValue(false).center.y, labelBounds.getValue(false).center.y, 0.5f)

            assertTrue(lineCounts.getValue(true) > 1)
            assertEquals(iconBounds.getValue(true).top, labelBounds.getValue(true).top)
        }
    }
}
