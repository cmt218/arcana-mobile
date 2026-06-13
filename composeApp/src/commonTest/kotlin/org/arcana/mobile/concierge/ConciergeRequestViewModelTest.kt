@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.concierge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.networking.ConciergeApi
import org.arcana.mobile.networking.ConciergeError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConciergeRequestViewModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test fun `cannot submit when message is blank`() {
        val vm = ConciergeRequestViewModel(FakeApi())
        assertFalse(vm.canSubmit)
        vm.updateMessage("   ")
        assertFalse(vm.canSubmit)
    }

    @Test fun `can submit with a non-blank message`() {
        val vm = ConciergeRequestViewModel(FakeApi())
        vm.updateMessage("Help please")
        assertTrue(vm.canSubmit)
    }

    @Test fun `message is capped at the max length`() {
        val vm = ConciergeRequestViewModel(FakeApi())
        vm.updateMessage("x".repeat(ConciergeRequestViewModel.MESSAGE_MAX_LENGTH + 50))
        assertEquals(ConciergeRequestViewModel.MESSAGE_MAX_LENGTH, vm.message.value.length)
    }

    @Test fun `successful submit transitions to Sent and trims message`() = runTest {
        val api = FakeApi()
        val vm = ConciergeRequestViewModel(api)
        vm.updateMessage("  Reach me  ")
        vm.submit()
        assertTrue(vm.submitState.value is ConciergeSubmit.Sent)
        assertEquals("Reach me", api.lastMessage)
    }

    @Test fun `server error transitions to Failed`() = runTest {
        val vm = ConciergeRequestViewModel(FakeApi(error = ConciergeError("concierge_failed")))
        vm.updateMessage("Reach me")
        vm.submit()
        assertTrue(vm.submitState.value is ConciergeSubmit.Failed)
    }

    @Test fun `editing after a failure clears the error`() = runTest {
        val vm = ConciergeRequestViewModel(FakeApi(error = ConciergeError("x")))
        vm.updateMessage("Reach me")
        vm.submit()
        assertTrue(vm.submitState.value is ConciergeSubmit.Failed)
        vm.updateMessage("Reach me now")
        assertTrue(vm.submitState.value is ConciergeSubmit.Idle)
    }

    @Test fun `submit is a no-op when blank`() = runTest {
        val api = FakeApi()
        val vm = ConciergeRequestViewModel(api)
        vm.submit()
        assertTrue(vm.submitState.value is ConciergeSubmit.Idle)
        assertEquals(0, api.callCount)
    }

    private class FakeApi(private val error: Throwable? = null) : ConciergeApi {
        var lastMessage: String? = null
        var callCount = 0
        override suspend fun createConciergeRequest(message: String): Int {
            callCount++
            lastMessage = message
            error?.let { throw it }
            return 1
        }
    }
}
