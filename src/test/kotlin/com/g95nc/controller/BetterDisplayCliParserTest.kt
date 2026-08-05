package com.g95nc.controller

import kotlin.test.Test
import kotlin.test.assertEquals

class BetterDisplayCliParserTest {

    @Test
    fun testParseIdentifiersOutput_valid() {
        val jsonOutput = """
            {
              "UUID" : "63F2A128-0F49-495C-8A4E-AD03A17EAE76",
              "name" : "Odyssey G95NC",
              "tagID" : "6"
            },{
              "UUID" : "F34BBC6A-0D59-4299-A9D8-5B7A668950BE",
              "name" : "Odyssey G95NC",
              "tagID" : "7"
            }
        """.trimIndent()
        
        val tag = BetterDisplayCliController.parseIdentifiersOutput(jsonOutput)
        assertEquals("6", tag)
    }

    @Test
    fun testParseIdentifiersOutput_invalid() {
        val jsonOutput = """
            {
              "name" : "Some Other Monitor",
              "tagID" : "1"
            }
        """.trimIndent()
        
        val tag = BetterDisplayCliController.parseIdentifiersOutput(jsonOutput)
        assertEquals(null, tag)
    }

    @Test
    fun testParseVcpOutput_integer() {
        val output = "14593"
        val vcp = BetterDisplayCliController.parseVcpOutput(output)
        assertEquals(14593, vcp)
    }

    @Test
    fun testParseVcpOutput_hex() {
        val output = "Value: 0x3901"
        val vcp = BetterDisplayCliController.parseVcpOutput(output)
        assertEquals(14593, vcp) // 0x3901 is 14593
    }
    
    @Test
    fun testParseVcpOutput_hexLowercase() {
        val output = "Value: 0x390b"
        val vcp = BetterDisplayCliController.parseVcpOutput(output)
        assertEquals(14603, vcp) // 0x390b is 14603
    }

    @Test
    fun testParseVcpOutput_withTrailingNewline() {
        val output = "14593\n"
        val vcp = BetterDisplayCliController.parseVcpOutput(output)
        assertEquals(14593, vcp)
    }

    @Test
    fun testParseVcpOutput_withGarbagePrefix() {
        val output = "Querying tagID 6...\n14593"
        val vcp = BetterDisplayCliController.parseVcpOutput(output)
        assertEquals(14593, vcp)
    }

    @Test
    fun testParseVcpOutput_withError() {
        val output = "Failed. Request timed out. BetterDisplay might not be running."
        val vcp = BetterDisplayCliController.parseVcpOutput(output)
        assertEquals(null, vcp)
    }
}
