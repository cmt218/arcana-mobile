@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.review

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.analytics.FakeAnalytics
import org.arcana.mobile.analytics.NoopCrashReporter
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.FeedbackAgainDto
import org.arcana.mobile.data.FeedbackFeedDto
import org.arcana.mobile.data.FeedbackScopeDto
import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.ErrorType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedbackFeedViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val scope = FeedbackScopeDto(type = "brand", value = "soto-method", label = "Soto Method", count = 3, again = FeedbackAgainDto(2, 1, 0))

    private fun api(pages: (String?) -> FeedbackFeedDto) = FakeReviewApi().apply { feedPages = pages }

    @Test fun `loads the first page for its scope and reports opening once`() = runTest {
        val api = api { FeedbackFeedDto(scope, listOf(review(3), review(2)), "c1") }
        val fake = FakeAnalytics()
        val vm = FeedbackFeedViewModel("brand", "soto-method", "studio_page", api, Telemetry(fake, NoopCrashReporter))
        vm.onOpened(); vm.onOpened()
        val s = vm.uiState.value as FeedbackFeedUiState.Success
        assertEquals(listOf(3, 2), s.items.map { it.id })
        assertEquals("c1", s.nextCursor)
        assertEquals(Triple("brand", "soto-method", null), api.feedCalls.single())
        assertEquals(listOf("feedback_feed_opened"), fake.names())
        assertEquals(mapOf<String, Any?>("scope_type" to "brand", "source" to "studio_page"), fake.events[0].properties)
        vm.onItemTapped()
        assertEquals(mapOf<String, Any?>("scope_type" to "brand", "target" to "studio_page"), fake.events[1].properties)
    }

    @Test fun `load more appends dedupes and stops at the last cursor`() = runTest {
        val api = api { cursor ->
            when (cursor) {
                null -> FeedbackFeedDto(scope, listOf(review(3), review(2)), "c1")
                "c1" -> FeedbackFeedDto(scope, listOf(review(2), review(1)), null)
                else -> error("unexpected cursor $cursor")
            }
        }
        val vm = FeedbackFeedViewModel("brand", "soto-method", "studio_page", api)
        vm.loadMore()
        val s = vm.uiState.value as FeedbackFeedUiState.Success
        assertEquals(listOf(3, 2, 1), s.items.map { it.id })
        assertNull(s.nextCursor)
        assertFalse(s.loadingMore)
        vm.loadMore()
        assertEquals(2, api.feedCalls.size)
    }

    @Test fun `a failed page keeps the rows until retried`() = runTest {
        var fail = true
        val api = api { cursor ->
            if (cursor == null) FeedbackFeedDto(scope, listOf(review(3)), "c1")
            else if (fail) throw ApiHttpError(502)
            else FeedbackFeedDto(scope, listOf(review(2)), null)
        }
        val vm = FeedbackFeedViewModel("all", "", "discover", api)
        vm.loadMore()
        val failed = vm.uiState.value as FeedbackFeedUiState.Success
        assertEquals(ErrorType.SERVER, failed.pageError)
        vm.loadMore()
        assertEquals(2, api.feedCalls.size)
        fail = false
        vm.retryLoadMore()
        val ok = vm.uiState.value as FeedbackFeedUiState.Success
        assertNull(ok.pageError)
        assertEquals(listOf(3, 2), ok.items.map { it.id })
    }

    @Test fun `a cold failure is the full error and retry recovers`() = runTest {
        var fail = true
        val api = api { if (fail) throw ApiHttpError(500) else FeedbackFeedDto(scope, emptyList(), null) }
        val vm = FeedbackFeedViewModel("all", "", "discover", api)
        assertEquals(FeedbackFeedUiState.Error(ErrorType.SERVER), vm.uiState.value)
        fail = false
        vm.retry()
        assertTrue(vm.uiState.value is FeedbackFeedUiState.Success)
    }

    @Test fun `a failed refresh keeps content and raises the flag`() = runTest {
        var fail = false
        val api = api { if (fail) throw ApiHttpError(500) else FeedbackFeedDto(scope, listOf(review(3)), null) }
        val vm = FeedbackFeedViewModel("all", "", "discover", api)
        fail = true
        vm.refresh()
        assertTrue(vm.uiState.value is FeedbackFeedUiState.Success)
        assertTrue(vm.refreshFailed.value)
        vm.dismissRefreshFailed()
        assertFalse(vm.refreshFailed.value)
    }
}
