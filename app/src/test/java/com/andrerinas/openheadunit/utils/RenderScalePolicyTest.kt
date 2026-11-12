package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderScalePolicyTest {

    @Test
    fun fullScaleKeepsThePanelMargins() {
        assertEquals(RenderScalePolicy.Margins(82, 0), RenderScalePolicy.margins(1920, 1080, 82, 0, 100))
    }

    @Test
    fun eightyFivePercentOf1080pSitsBetween720pAnd1080p() {
        val m = RenderScalePolicy.margins(1920, 1080, 0, 0, 85)
        assertEquals(1632, 1920 - m.width)
        assertEquals(918, 1080 - m.height)
    }

    @Test
    fun scalesTheCanvasThePanelLeftSoItsShapeHolds() {
        // A 1838x1080 canvas (82 px of width margin) at 85 %.
        val m = RenderScalePolicy.margins(1920, 1080, 82, 0, 85)
        assertEquals(1562, 1920 - m.width)
        assertEquals(918, 1080 - m.height)
        assertEquals(1838f / 1080f, 1562f / 918f, 0.002f)
    }

    @Test
    fun canvasSidesStayEvenSoTheMarginsSplitEqually() {
        for (percent in RenderScalePolicy.MIN..RenderScalePolicy.FULL) {
            val m = RenderScalePolicy.margins(1920, 1080, 82, 0, percent)
            assertEquals("width at $percent", 0, (1920 - m.width) % 2)
            assertEquals("height at $percent", 0, (1080 - m.height) % 2)
        }
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        assertEquals(RenderScalePolicy.margins(1920, 1080, 0, 0, 50), RenderScalePolicy.margins(1920, 1080, 0, 0, 10))
        assertEquals(RenderScalePolicy.Margins(0, 0), RenderScalePolicy.margins(1920, 1080, 0, 0, 140))
    }

    @Test
    fun anUnknownVideoSizeLeavesTheMarginsAlone() {
        assertEquals(RenderScalePolicy.Margins(5, 7), RenderScalePolicy.margins(0, 0, 5, 7, 80))
    }
}
