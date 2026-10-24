package com.visioo.app

import com.visioo.app.data.CatalogRepository
import com.visioo.app.data.ContentDetail
import com.visioo.app.data.ContentSummary
import com.visioo.app.data.ContentType
import com.visioo.app.data.Genre
import com.visioo.app.data.HomeContent
import com.visioo.app.data.Season
import com.visioo.app.feature.explore.ExploreViewModel
import com.visioo.app.feature.explore.PAGE_SIZE
import com.visioo.app.feature.search.SEARCH_DEBOUNCE_MS
import com.visioo.app.feature.search.SearchUiState
import com.visioo.app.feature.search.SearchViewModel
import com.visioo.core.network.ApiError
import com.visioo.core.network.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun summary(id: String, type: ContentType = ContentType.Movie) =
    ContentSummary(id, "Title $id", type, null, null, null, 2020, false)

private class FakeCatalog : CatalogRepository {
    val browseCalls = mutableListOf<Triple<ContentType, String?, Int>>()
    val searchCalls = mutableListOf<String>()
    var total = 45
    var browseGate: CompletableDeferred<Unit>? = null
    var failSearch = false

    override suspend fun getHome(): Result<HomeContent> = Result.success(HomeContent(emptyList(), emptyList()))
    override suspend fun genres() = Result.success(listOf(Genre("action", "Action")))
    override suspend fun browse(type: ContentType, genre: String?, offset: Int, limit: Int): Result<List<ContentSummary>> {
        browseCalls += Triple(type, genre, offset)
        browseGate?.await()
        return Result.success((offset until minOf(offset + limit, total)).map { summary("${type.path}-${genre ?: "all"}-$it", type) })
    }
    override suspend fun detail(type: ContentType, id: String): Result<ContentDetail> = Result.failure(ApiException(ApiError.NotFound))
    override suspend fun seasons(seriesId: String): Result<List<Season>> = Result.success(emptyList())
    override suspend fun search(query: String): Result<List<ContentSummary>> {
        searchCalls += query
        if (failSearch) return Result.failure(ApiException(ApiError.Network))
        return Result.success(if (query == "none") emptyList() else listOf(summary(query)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeCatalog()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun exploreLoadsPagesUntilTheEnd() {
        val vm = ExploreViewModel(repo, ContentType.Movie)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(PAGE_SIZE, vm.state.value.items.size)
        assertFalse(vm.state.value.endReached)

        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(45, vm.state.value.items.size)
        assertTrue(vm.state.value.endReached)

        val calls = repo.browseCalls.size
        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("no request once the end is reached", calls, repo.browseCalls.size)
        assertEquals(listOf(0, 20, 40), repo.browseCalls.map { it.third })
    }

    @Test fun exploreIgnoresConcurrentLoadMore() {
        val vm = ExploreViewModel(repo, ContentType.Movie)
        dispatcher.scheduler.advanceUntilIdle()
        repo.browseGate = CompletableDeferred()
        vm.loadMore(); vm.loadMore(); vm.loadMore()
        dispatcher.scheduler.runCurrent()
        assertEquals(2, repo.browseCalls.size)
        repo.browseGate!!.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test fun changingGenreResetsTheListAndDropsStaleResponses() {
        val vm = ExploreViewModel(repo, ContentType.Movie)
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectGenre("action")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("action", vm.state.value.genre)
        assertTrue(vm.state.value.items.all { it.id.contains("-action-") })
        assertEquals(0, repo.browseCalls.last().third)
    }

    @Test fun switchingTypeClearsTheGenre() {
        val vm = ExploreViewModel(repo, ContentType.Movie)
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectGenre("action"); dispatcher.scheduler.advanceUntilIdle()
        vm.selectType(ContentType.Series); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, vm.state.value.genre)
        assertTrue(vm.state.value.items.all { it.type == ContentType.Series })
    }

    @Test fun searchWaitsForTheDebounceAndOnlySearchesTheLastQuery() {
        val vm = SearchViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onQueryChange("du"); dispatcher.scheduler.advanceTimeBy(100)
        vm.onQueryChange("dun"); dispatcher.scheduler.advanceTimeBy(100)
        vm.onQueryChange("dune"); dispatcher.scheduler.advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
        assertTrue(repo.searchCalls.isEmpty())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("dune"), repo.searchCalls)
        assertTrue(vm.state.value is SearchUiState.Results)
    }

    @Test fun searchShowsIdleForShortQueriesAndEmptyForNoMatches() {
        val vm = SearchViewModel(repo)
        vm.onQueryChange("a"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(SearchUiState.Idle, vm.state.value)
        assertTrue(repo.searchCalls.isEmpty())
        vm.onQueryChange("none"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(SearchUiState.Empty, vm.state.value)
    }

    @Test fun searchReportsErrorsAndRecoversOnRetry() {
        val vm = SearchViewModel(repo)
        repo.failSearch = true
        vm.onQueryChange("dune"); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is SearchUiState.Error)
        repo.failSearch = false
        vm.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertNotNull((vm.state.value as? SearchUiState.Results)?.items)
    }
}
