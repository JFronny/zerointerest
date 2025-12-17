package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.formatBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.user
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

private enum class RoomTab { Balances, Transactions }

@Composable
fun RoomScreen(roomId: RoomId) {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val trust = koinInject<SummaryTrustService>()

    var selectedTab by remember { mutableStateOf(RoomTab.Balances) }

    NavigationSuiteScaffold(navigationItems = {
        NavigationSuiteItem(
            selected = selectedTab == RoomTab.Balances,
            onClick = { selectedTab = RoomTab.Balances },
            icon = { Text("B") },
            label = { Text("Balances") }
        )
        NavigationSuiteItem(
            selected = selectedTab == RoomTab.Transactions,
            onClick = { selectedTab = RoomTab.Transactions },
            icon = { Text("T") },
            label = { Text("Transactions") }
        )
    }) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                when (selectedTab) {
                    RoomTab.Balances -> {
                        val eventNB by trust.getSummary(roomId).collectAsState(null)
                        val event = eventNB // needed for smart cast
                        if (event == null) {
                            CircularProgressIndicator()
                        } else {
                            BalancesTab(
                                event = event,
                                getName = nameResolver(client = client, roomId = roomId)
                            )
                        }
                    }
                    RoomTab.Transactions -> {
                        TransactionsTab(client, roomId)
                    }
                }
            }
        }
    }
}

@Composable
private fun nameResolver(client: MatrixClient, roomId: RoomId): (UserId) -> Flow<String?> {
    val users by client.user.getAll(roomId).collectAsState(emptyMap())
    return { users[it]?.map { it?.name } ?: flowOf(null) }
}

@Preview
@Composable
private fun BalancesTabPreview() = AppTheme {
    BalancesTab(
        event = ZeroInterestSummaryEvent(
            balances = mapOf(
                UserId("@alice:example.org") to 4200,
                UserId("@bob:example.org") to -1550,
                UserId("@carol:example.org") to 0,
            ),
            parents = emptyMap()
        ),
        getName = { userId ->
            flowOf(when (userId.full) {
                "@alice:example.org" -> "Alice"
                "@bob:example.org" -> "Bob"
                "@carol:example.org" -> "Carol"
                else -> null
            })
        }
    )
}

@Composable
private fun BalancesTab(
    event: ZeroInterestSummaryEvent,
    getName: (UserId) -> Flow<String?>,
) {
    val balances = event.balances
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(balances.entries.toList()) { entry ->
            val usernameNB by getName(entry.key).collectAsState(null)
            val username = usernameNB ?: entry.key.full
            val balance = entry.value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = username)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = formatBalance(balance))
            }
        }
    }
}

@Composable
private fun TransactionsTab(client: MatrixClient, roomId: RoomId) {
    val roomService: RoomService = client.room

    // State to hold the list of transaction events
    var transactionEvents by remember(roomId) { mutableStateOf<List<Pair<EventId, ZeroInterestTransactionEvent>>>(emptyList()) }
    var isLoading by remember(roomId) { mutableStateOf(true) }
    var hasMore by remember(roomId) { mutableStateOf(true) }

    val listState = rememberLazyListState()

    // Keep a set of already loaded ids for deduplication when paging
    val loadedIds = remember(roomId) { mutableSetOf<EventId>() }

    // Reuse name resolver so it isn't created per-row
    val resolveName = nameResolver(client = client, roomId = roomId)

    // Track the oldest loaded event id to continue paging from there
    var oldestLoadedId by remember(roomId) { mutableStateOf<EventId?>(null) }

    val pageSize = 50

    // Small helper to load one page from a start event id going backwards
    suspend fun loadPage(startFrom: EventId) {
        isLoading = true
        try {
            val newItems = mutableListOf<Pair<EventId, ZeroInterestTransactionEvent>>()
            var emittedCount = 0
            var lastSeenId: EventId? = null

            roomService.getTimelineEvents(
                roomId = roomId,
                startFrom = startFrom,
                direction = GetEvents.Direction.BACKWARDS,
                config = {
                    minSize = pageSize.toLong()
                    maxSize = pageSize.toLong()
                }
            ).collect { timelineEventFlow ->
                emittedCount++
                val firstEvent = timelineEventFlow.first()
                lastSeenId = firstEvent.eventId

                // Get the first emission with a usable content for this timeline event
                val pair: Pair<EventId, ZeroInterestTransactionEvent>? = timelineEventFlow
                    .map { te ->
                        val content = te.content?.getOrNull()
                        if (content is ZeroInterestTransactionEvent) (te.eventId to content) else null
                    }
                    .filterNotNull()
                    .firstOrNull()
                if (pair != null) {
                    val (eventId, content) = pair
                    if (loadedIds.add(eventId)) {
                        newItems.add(eventId to content)
                    }
                }
            }

            if (newItems.isNotEmpty()) {
                // Append to the end to keep newest-to-oldest ordering
                transactionEvents = (transactionEvents + newItems)
            }
            // Advance the anchor if we processed any events at all
            if (lastSeenId != null) {
                oldestLoadedId = lastSeenId
            }

            // Decide if there may be more events to load
            hasMore = emittedCount >= pageSize
        } catch (e: Exception) {
            // Error while loading a page should stop further paging in this session
            println("Error loading transactions page: ${e.message}")
            hasMore = false
        } finally {
            isLoading = false
        }
    }

    // Initial load
    LaunchedEffect(roomId) {
        isLoading = true
        transactionEvents = emptyList()
        hasMore = true
        loadedIds.clear()
        oldestLoadedId = null

        try {
            val room = roomService.getById(roomId).first()
            val lastEventId = room?.lastEventId

            if (lastEventId != null) {
                loadPage(startFrom = lastEventId)
            } else {
                // No events in this room
                isLoading = false
                hasMore = false
            }
        } catch (e: Exception) {
            println("Error loading transactions: ${e.message}")
            isLoading = false
            hasMore = false
        }
    }

    // Detect when user scrolls near the end to load more
    val shouldLoadMore by remember {
        derivedStateOf {
            if (!hasMore || isLoading) return@derivedStateOf false

            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            totalItemsNumber > 0 && lastVisibleItemIndex >= totalItemsNumber - 5
        }
    }

    // Load more when scrolling near end
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoading) {
            val anchor = oldestLoadedId
            if (anchor != null) {
                loadPage(startFrom = anchor)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && transactionEvents.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = listState
            ) {
                items(
                    items = transactionEvents,
                    key = { it.first.full }
                ) { (_, transaction) ->
                    TransactionTabItem(
                        transaction = transaction,
                        getName = resolveName
                    )
                }

                // Show loading indicator at the bottom when loading more
                if (isLoading && transactionEvents.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (!isLoading && transactionEvents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No transactions yet")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionTabItem(
    transaction: ZeroInterestTransactionEvent,
    getName: (UserId) -> Flow<String?>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        //TODO add an icon
        Column {
            if (transaction.description == ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION) {
                Text(text = "Payment")
            } else {
                Text(text = transaction.description)
            }
            val usernameNB by getName(transaction.sender).collectAsState(null)
            val username = usernameNB ?: transaction.sender.full
            Text(text = username)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = formatBalance(transaction.total))
    }
}