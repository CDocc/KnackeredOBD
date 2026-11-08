package com.knackered.obd.protocol

import com.knackered.obd.transport.ObdNotAllowedException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReadOnlyPolicyTest {
    @Test
    fun allowsStandardReadsAndAdapterSetup() {
        ReadOnlyPolicy.check("010C")
        ReadOnlyPolicy.check("01 0C")
        ReadOnlyPolicy.check("03")
        ReadOnlyPolicy.check("ATZ")
        ReadOnlyPolicy.check("ATE0")
        ReadOnlyPolicy.check("ATSP0")
        ReadOnlyPolicy.check("ATSP6")
        ReadOnlyPolicy.check("ATDP")
        ReadOnlyPolicy.check("04", allowClear = true)
    }

    @Test
    fun rejectsClearUnlessConfirmedAndRejectsNonStandardRequests() {
        assertFailsWith<ObdNotAllowedException> { ReadOnlyPolicy.check("04") }
        assertFailsWith<ObdNotAllowedException> { ReadOnlyPolicy.check("ATSH7E0") }
        assertFailsWith<ObdNotAllowedException> { ReadOnlyPolicy.check("22F190") }
        assertFailsWith<ObdNotAllowedException> { ReadOnlyPolicy.check("2E") }
        assertFailsWith<ObdNotAllowedException> { ReadOnlyPolicy.check("08") }
        assertFailsWith<ObdNotAllowedException> { ReadOnlyPolicy.check("ATMA") }
    }
}
