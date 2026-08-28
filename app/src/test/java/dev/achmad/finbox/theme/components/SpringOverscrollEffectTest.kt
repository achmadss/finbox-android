package dev.achmad.finbox.theme.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringOverscrollEffectTest {

    private val effect = SpringOverscrollEffect(CoroutineScope(Dispatchers.Unconfined))

    /** Standing in for a pager that has run out of pages: it consumes nothing. */
    private fun dragAtTheEnd(x: Float) =
        effect.applyToScroll(Offset(x, 0f), NestedScrollSource.UserInput) { Offset.Zero }

    /** Returns how much of the drag made it through to the pager. */
    private fun dragBack(x: Float): Float {
        var reached = 0f
        effect.applyToScroll(Offset(x, 0f), NestedScrollSource.UserInput) {
            reached = it.x
            it
        }
        return reached
    }

    @Test
    fun `the end gives way, and the way back pays it off before scrolling`() {
        assertFalse(effect.isInProgress)

        assertEquals(Offset.Zero, dragAtTheEnd(100f))
        assertTrue(effect.isInProgress)

        assertEquals(0f, dragBack(-10f), 0.01f)
        assertTrue(effect.isInProgress)

        assertEquals(-15f, dragBack(-20f), 0.01f)
        assertFalse(effect.isInProgress)
    }
}
