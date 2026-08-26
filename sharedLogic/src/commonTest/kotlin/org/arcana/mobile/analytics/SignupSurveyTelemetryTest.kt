@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.arcana.mobile.signup.SignupSurveyCallable
import org.arcana.mobile.signup.SignupSurveyResult
import org.arcana.mobile.signup.SignupSurveyViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Locks the signup-survey taxonomy so future edits can't silently drop events. */
class SignupSurveyTelemetryTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeApi(var behavior: () -> SignupSurveyResult) : SignupSurveyCallable {
        override suspend fun submit(token: String, answers: JsonObject) = behavior()
    }

    private fun SignupSurveyViewModel.fillRequired() {
        toggleMulti("modalities", "Hot yoga")
        selectSingle("weeklyFrequency", "3–4")
        toggleMulti("trainingTimes", "Evening")
        toggleMulti("neighborhoods", "Williamsburg")
        toggleMulti("fitnessMeaning", "Community")
        selectSingle("communityState", "I mostly train solo")
        toggleMulti("eventInterest", "Group run")
        toggleMulti("membershipTypes", "Class packs")
        selectSingle("monthlySpend", "$350–$500")
        selectSingle("soloOrSocial", "Either — depends on the studio")
        selectSingle("referralIntent", "Not yet")
        selectSingle("howHeard", "Instagram")
    }

    @Test fun `started fires on init`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Success }, telemetry)
        assertEquals(listOf("signup_survey_started"), analytics.names())
    }

    @Test fun `happy path fires started then submitted with answered_count`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Success }, telemetry)
        vm.fillRequired(); vm.submit()
        assertEquals(listOf("signup_survey_started", "signup_survey_submitted"), analytics.names())
        assertEquals(12, analytics.first("signup_survey_submitted")?.properties?.get("answered_count"))
    }

    @Test fun `network failure fires failed with reason`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupSurveyViewModel(
            "tok", FakeApi { SignupSurveyResult.NetworkError(RuntimeException("x")) }, telemetry,
        )
        vm.fillRequired(); vm.submit()
        assertEquals("network", analytics.first("signup_survey_failed")?.properties?.get("reason"))
    }

    @Test fun `server 5xx fires failed with status code`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Other(503) }, telemetry)
        vm.fillRequired(); vm.submit()
        val event = analytics.first("signup_survey_failed")
        assertEquals("server_5xx", event?.properties?.get("reason"))
        assertEquals(503, event?.properties?.get("status_code"))
    }

    @Test fun `expired token fires failed token_expired and no submitted`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupSurveyViewModel(
            "tok", FakeApi { SignupSurveyResult.TokenExpiredOrConsumed }, telemetry,
        )
        vm.fillRequired(); vm.submit()
        assertEquals("token_expired", analytics.first("signup_survey_failed")?.properties?.get("reason"))
        assertTrue(analytics.all("signup_survey_submitted").isEmpty())
    }

    @Test fun `continue anyway fires skipped`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupSurveyViewModel("tok", FakeApi { SignupSurveyResult.Other(500) }, telemetry)
        vm.fillRequired(); vm.submit(); vm.continueAnyway()
        assertEquals("submit_failed", analytics.first("signup_survey_skipped")?.properties?.get("reason"))
    }
}
