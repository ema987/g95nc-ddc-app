package com.g95nc.controller

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DdcutilParserTest {

    @Test
    fun testParseVcpOutput_cncTerseFormat() {
        // VCP E2 CNC x00 x7f x39 x00 -> sh=0x39, sl=0x00 -> 0x3900 (14592)
        val output = "VCP E2 CNC x00 x7f x39 x00"
        val vcp = DdcutilController.parseVcpOutput(output)
        assertEquals(0x3900, vcp)
    }

    @Test
    fun testParseVcpOutput_cncTerseWith0xPrefix() {
        // VCP E2 CNC 0x00 0x7f 0x39 0x0b -> sh=0x39, sl=0x0b -> 0x390b
        val output = "VCP E2 CNC 0x00 0x7f 0x39 0x0b"
        val vcp = DdcutilController.parseVcpOutput(output)
        assertEquals(0x390b, vcp)
    }

    @Test
    fun testParseVcpOutput_cncTerseE3() {
        // VCP E3 CNC x51 x51 x02 x01 -> sh=0x02, sl=0x01 -> 0x0201 (HDMI3 / HDMI2)
        val output = "VCP E3 CNC x51 x51 x02 x01"
        val vcp = DdcutilController.parseVcpOutput(output)
        assertEquals(0x0201, vcp)
    }

    @Test
    fun testParseVcpOutput_sncTerse() {
        // VCP 60 SNC x32 -> 0x32
        val output = "VCP 60 SNC x32"
        val vcp = DdcutilController.parseVcpOutput(output)
        assertEquals(0x32, vcp)
    }

    @Test
    fun testParseVcpOutput_sncTerseWith0x() {
        // VCP 60 SNC 0x11 -> 0x11
        val output = "VCP 60 SNC 0x11"
        val vcp = DdcutilController.parseVcpOutput(output)
        assertEquals(0x11, vcp)
    }

    @Test
    fun testParseVcpOutput_nonTerse() {
        val output = "VCP code 0xe2 (Manufacturer Specific ): mh=0x00, ml=0x7f, sh=0x39, sl=0x00"
        val vcp = DdcutilController.parseVcpOutput(output)
        assertEquals(0x3900, vcp)
    }

    @Test
    fun testParseVcpOutput_nonTerseSnc() {
        val output = "VCP code 0x60 (Input Source ): Invalid value (sl=0x32)"
        val vcp = DdcutilController.parseVcpOutput(output)
        assertEquals(0x32, vcp)
    }

    @Test
    fun testParseVcpOutput_emptyOrError() {
        assertNull(DdcutilController.parseVcpOutput(""))
        assertNull(DdcutilController.parseVcpOutput("Display not found"))
    }

    @Test
    fun testParseHexByte() {
        assertEquals(0x39, DdcutilController.parseHexByte("x39"))
        assertEquals(0x39, DdcutilController.parseHexByte("0x39"))
        assertEquals(0x00, DdcutilController.parseHexByte("00"))
        assertNull(DdcutilController.parseHexByte("invalid"))
    }
}
