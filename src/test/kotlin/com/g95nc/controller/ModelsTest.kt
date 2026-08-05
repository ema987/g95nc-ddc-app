package com.g95nc.controller

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {

    @Test
    fun testLayoutFromCode() {
        val layout = Layout.fromCode(0x08)
        assertEquals(Layout.PBP_3_25_50_25, layout)
    }

    @Test
    fun testSourceCodeFromE3() {
        val src = SourceCode.fromE3Code(0x10)
        assertEquals(SourceCode.DP, src)
    }
}
