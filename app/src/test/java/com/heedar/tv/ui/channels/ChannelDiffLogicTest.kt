package com.heedar.tv.ui.channels

import com.heedar.tv.domain.Channel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelDiffLogicTest {

    private val bein = Channel(1, "Bein 1", null, "5")

    @Test
    fun `same id is the same item even when the name changes`() {
        val renamed = bein.copy(name = "Bein 1 HD")

        assertTrue(ChannelDiffLogic.sameItem(bein, renamed))
        assertFalse(ChannelDiffLogic.sameContent(bein, renamed))
    }

    @Test
    fun `different id is a different item`() {
        assertFalse(ChannelDiffLogic.sameItem(bein, bein.copy(id = 2)))
    }

    @Test
    fun `identical channels report same content`() {
        assertTrue(ChannelDiffLogic.sameContent(bein, bein.copy()))
    }
}
