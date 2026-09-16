package lk.salli.parser.utility

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UtilitySendersTest {
    @Test
    fun `known utility senders are recognised case-insensitively`() {
        listOf("SLTBILL", "sltmobitel", "1919", "Dialog", "CEB e-Bill", "NWSDB Bill").forEach {
            assertThat(UtilitySenders.isUtilitySender(it)).isTrue()
        }
    }

    @Test
    fun `banks and random promos are not utility senders`() {
        listOf("BOC", "PeoplesBank", "COMBANK", "Pizza Hut", "5555").forEach {
            assertThat(UtilitySenders.isUtilitySender(it)).isFalse()
        }
    }
}
