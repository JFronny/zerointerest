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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.formatBalance
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
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

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RoomScreen(roomId: RoomId, onBack: () -> Unit) {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val trust = koinInject<SummaryTrustService>()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    fun NavBackStackEntry.has(destination: Destination.Room.RoomDestination) =
        this.destination.hierarchy.any { it.hasRoute(route = destination::class) }

    fun navigate(route: Destination.Room.RoomDestination) {
        navController.navigate(route) {
            // Pop up to the start destination of the graph to
            // avoid building up a large stack of destinations
            // on the back stack as users select items
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            // Avoid multiple copies of the same destination when
            // reselecting the same item
            launchSingleTop = true
            // Restore state when reselecting a previously selected item
            restoreState = true
        }
    }


    NavigationSuiteScaffold(navigationItems = {
        NavigationSuiteItem(
            selected = navBackStackEntry?.has(Destination.Room.RoomDestination.Balance) == true,
            onClick = { navigate(Destination.Room.RoomDestination.Balance) },
            icon = { Icon(Icons.Default.Paid, "Balances") },
            label = { Text("Balances") }
        )
        NavigationSuiteItem(
            selected = navBackStackEntry?.has(Destination.Room.RoomDestination.Transactions) == true,
            onClick = { navigate(Destination.Room.RoomDestination.Transactions) },
            icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, "Transactions") },
            label = { Text("Transactions") }
        )
    }, primaryActionContent = {
        FloatingActionButton(onClick = { onBack() }) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Back")
        }
    }) {
        NavHost(
            navController = navController,
            startDestination = Destination.Room.RoomDestination.Balance,
            typeMap = mapOf()
        ) {
            composable<Destination.Room.RoomDestination.Balance> {
                val eventNB by trust.getSummary(roomId).collectAsState(null)
                val event = eventNB // needed for smart cast
                if (event == null) {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(Modifier.align(Alignment.TopCenter))
                    }
                } else {
                    BalancesTab(
                        summary = event,
                        getName = nameResolver(client = client, roomId = roomId)
                    )
                }
            }
            composable<Destination.Room.RoomDestination.Transactions> {
                TransactionsTab(client, roomId)
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
        summary = SummaryTrustService.Summary.Trusted(ZeroInterestSummaryEvent(
            balances = mapOf(
                UserId("@alice:example.org") to 4200,
                UserId("@bob:example.org") to -1550,
                UserId("@carol:example.org") to 0,
            ),
            parents = emptyMap()
        )),
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
    summary: SummaryTrustService.Summary,
    getName: (UserId) -> Flow<String?>,
) {
    when (summary) {
        SummaryTrustService.Summary.Empty -> {
            Box(Modifier.fillMaxSize()) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                    Text("No balances yet", Modifier.align(Alignment.Center))
                }
            }
        }
        SummaryTrustService.Summary.Untrusted -> {
            Box(Modifier.fillMaxSize()) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                    Text("Latest Summary not trusted", Modifier.align(Alignment.Center))
                }
            }
        }
        is SummaryTrustService.Summary.Trusted -> {
            val balances = summary.event.balances
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
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
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
        log.info { "Loading transactions page content from $startFrom" }
        isLoading = true
        try {
            var emittedCount = 0
            var lastSeenId: EventId? = null

            val newItems = roomService.getTimelineEvents(
                roomId = roomId,
                startFrom = startFrom,
                direction = GetEvents.Direction.BACKWARDS,
                config = {
                    minSize = pageSize.toLong()
                    maxSize = pageSize.toLong()
                }
            ).lastOrNull()?.let { timelineEventFlow ->
                log.info { "Got one timeline event flow" }
                emittedCount++
                timelineEventFlow.map { event ->
                    lastSeenId = event.eventId

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
                            return@map eventId to content
                        }
                    }
                    return@map null
                }.filterNotNull().toList()
            } ?: emptyList()

            if (newItems.isNotEmpty()) {
                // Append to the end to keep newest-to-oldest ordering
                transactionEvents = (transactionEvents + newItems)
            }
            // Advance the anchor if we processed any events at all
            if (lastSeenId != null) {
                oldestLoadedId = lastSeenId
            }

            log.info { "Successfully loaded $emittedCount of $pageSize elements" }
            // Decide if there may be more events to load
            hasMore = emittedCount > pageSize
        } catch (e: Exception) {
            // Error while loading a page should stop further paging in this session
            log.error(e) { "Error loading transactions page" }
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
            log.error(e) { "Error loading transactions" }
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