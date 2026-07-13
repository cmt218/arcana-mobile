@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.signup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SignupSurveyViewModelTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeApi(var behavior: () -> SignupSurveyResult) : SignupSurveyCallable {
        var lastToken: String? = null
        var lastAnswers: JsonObject? = null
        var calls = 0
        override suspend fun submit(token: String, answers: JsonObject): SignupSurveyResult {
            calls++
            lastToken = token
            lastAnswers = answers
            return behavior()
        }
    }

    private fun SignupSurveyViewModel.fillRequired() {
        toggleMulti("modalities", "Hot yoga")
        selectSingle("weeklyFrequency", "3–4")
        toggleMulti("trainingTimes", "Evening")
        toggleMulti("neighborhoods", "Williamsburg")
        toggleMulti("fitnessMeaning", "Community")
        selectSingle("communityState", "I mostly train solo")
        toggleMulti("eventInterest", "Group run")
        selectSingle("monthlySpend", "$350–$500")
        selectSingle("soloOrSocial", "Either — depends on the studio")
        selectSingle("referralIntent", "Not yet")
        selectSingle("howHeard", "Instagram")
    }

    @Test
    fun `canSubmit gates on required questions`() {
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Success })
        assertFalse(vm.canSubmit.value)
        vm.fillRequired()
        assertTrue(vm.canSubmit.value)
    }

    @Test
    fun `submit sends token and answers then completes`() = runTest {
        val api = FakeApi { SignupSurveyResult.Success }
        val vm = SignupSurveyViewModel("tok-123", api)
        vm.fillRequired()
        vm.submit()
        assertEquals("tok-123", api.lastToken)
        assertNotNull(api.lastAnswers)
        assertEquals(1, api.calls)
        assertTrue(vm.completed.value)
    }

    @Test
    fun `submit without required answers is a no-op`() = runTest {
        val api = FakeApi { SignupSurveyResult.Success }
        val vm = SignupSurveyViewModel("tok", api)
        vm.submit()
        assertEquals(0, api.calls)
        assertFalse(vm.completed.value)
    }

    @Test
    fun `toggleMulti removes an existing selection`() {
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Success })
        vm.toggleMulti("modalities", "Boxing")
        vm.toggleMulti("modalities", "Boxing")
        assertTrue(vm.state.value.answers.multis["modalities"].orEmpty().isEmpty())
    }

    @Test
    fun `failure keeps answers and reveals the escape hatch`() = runTest {
        val api = FakeApi { SignupSurveyResult.NetworkError(RuntimeException("down")) }
        val vm = SignupSurveyViewModel("tok", api)
        vm.fillRequired()
        vm.submit()
        val state = vm.state.value
        assertFalse(vm.completed.value)
        assertEquals(SignupSurveyViewModel.NETWORK_MESSAGE, state.submitError)
        assertEquals(1, state.failedAttempts)
        // Answers survive the failure.
        assertEquals("Instagram", state.answers.singles["howHeard"])
        // Retry works once the server recovers.
        api.behavior = { SignupSurveyResult.Success }
        vm.submit()
        assertTrue(vm.completed.value)
    }

    @Test
    fun `server 5xx surfaces the server message`() = runTest {
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Other(503) })
        vm.fillRequired()
        vm.submit()
        assertEquals(SignupSurveyViewModel.SERVER_MESSAGE, vm.state.value.submitError)
    }

    @Test
    fun `expired token advances instead of trapping the member`() = runTest {
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.TokenExpiredOrConsumed })
        vm.fillRequired()
        vm.submit()
        // The claim screen owns the token-expired UX — the survey just steps aside.
        assertTrue(vm.completed.value)
    }

    @Test
    fun `continueAnyway completes without a successful submit`() = runTest {
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Other(500) })
        vm.fillRequired()
        vm.submit()
        assertFalse(vm.completed.value)
        vm.continueAnyway()
        assertTrue(vm.completed.value)
    }
}
