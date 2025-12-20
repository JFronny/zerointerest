package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.NavigationHelper
import dev.jfronny.zerointerest.util.formatBalance
import dev.jfronny.zerointerest.util.room
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.media
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.avatarUrl
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.client.user
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(roomId: RoomId, onBack: () -> Unit, onAddTransaction: () -> Unit, navHelper: NavigationHelper) {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val trust = koinInject<SummaryTrustService>()
    val navHelper = navHelper.room()
    val roomIs = navHelper.roomIs()

    NavigationSuiteScaffold(navigationItems = {
        NavigationSuiteItem(
            selected = roomIs(Destination.Room.RoomDestination.Balance),
            onClick = { navHelper.navigateTab(Destination.Room.RoomDestination.Balance) },
            icon = { Icon(Icons.Default.Paid, "Balances") },
            label = { Text("Balances") }
        )
        NavigationSuiteItem(
            selected = roomIs(Destination.Room.RoomDestination.Transactions),
            onClick = { navHelper.navigateTab(Destination.Room.RoomDestination.Transactions) },
            icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, "Transactions") },
            label = { Text("Transactions") }
        )
    }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val room by client.room.getById(roomId).collectAsState(null)
                        Text(room?.name?.explicitName ?: "Room")
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddTransaction) {
                    Icon(Icons.Default.Add, "Add Transaction")
                }
            }
        ) { padding ->
            NavHost(
                navController = navHelper.room,
                startDestination = Destination.Room.RoomDestination.Balance,
                typeMap = mapOf(),
                modifier = Modifier.padding(padding)
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
                            getUIData = uiResolver(client = client, roomId = roomId),
                            userIcon = userIcon(client)
                        )
                    }
                }
                composable<Destination.Room.RoomDestination.Transactions> {
                    TransactionsTab(client, roomId, trust)
                }
            }
        }
    }
}

@Composable
private fun uiResolver(client: MatrixClient, roomId: RoomId): (UserId) -> Flow<UserUIData> {
    val users by client.user.getAll(roomId).collectAsState(emptyMap())
    return { id -> users[id]?.map { UserUIData(it?.name ?: id.full, it?.avatarUrl) }
        ?: flowOf(UserUIData(id.full, null)) }
}

data class UserUIData(val name: String, val avatarUrl: String?)

fun userIcon(client: MatrixClient): @Composable (data: UserUIData) -> Unit = { data ->
    Surface(
        modifier = Modifier.size(40.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            @Composable
            fun Fallback() {
                val initials = data.name
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                    .take(2)
                    .joinToString("")
                    .ifEmpty { data.name.take(2).uppercase() }
                Text(
                    text = initials,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            if (data.avatarUrl == null) {
                Fallback()
            } else {
                var media by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(data.avatarUrl) {
                    media = client.media
                        .getMedia(data.avatarUrl)
                        .getOrNull()
                        ?.toByteArray(coroutineScope = this, maxSize = 1024 * 1024 * 8)
                        ?.decodeToImageBitmap()
                }
                media?.let { Image(it, "Avatar") } ?: Fallback()
            }
        }
    }
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
        getUIData = { userId ->
            flowOf(UserUIData(when (userId.full) {
                "@alice:example.org" -> "Alice"
                "@bob:example.org" -> "Bob"
                "@carol:example.org" -> "Carol"
                else -> userId.full
            }, null))
        },
        userIcon = {}
    )
}

@Composable
private fun BalancesTab(
    summary: SummaryTrustService.Summary,
    getUIData: (UserId) -> Flow<UserUIData>,
    userIcon: @Composable (data: UserUIData) -> Unit,
) {
    when (summary) {
        SummaryTrustService.Summary.Empty -> {
            Box(Modifier.fillMaxSize()) {
                Text("No balances yet", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
            }
        }
        SummaryTrustService.Summary.Untrusted -> {
            Box(Modifier.fillMaxSize()) {
                Text("Latest Summary not trusted", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is SummaryTrustService.Summary.Trusted -> {
            val balances = summary.event.balances
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(balances.entries.toList()) { entry ->
                    val uiData by getUIData(entry.key).collectAsState(UserUIData(entry.key.full, null))
                    val balance = entry.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userIcon(uiData)
                        Spacer(Modifier.width(8.dp))
                        Text(text = uiData.name)
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
private fun TransactionsTab(client: MatrixClient, roomId: RoomId, trust: SummaryTrustService) {
    val roomService: RoomService = client.room

    // State to hold the list of transaction events
    var transactionEvents by remember(roomId) { mutableStateOf<List<Pair<EventId, ZeroInterestTransactionEvent>>>(emptyList()) }
    var isLoading by remember(roomId) { mutableStateOf(true) }
    var hasMore by remember(roomId) { mutableStateOf(true) }

    val listState = rememberLazyListState()

    // Keep a set of already loaded ids for deduplication when paging
    val loadedIds = remember(roomId) { mutableSetOf<EventId>() }

    // Reuse name resolver so it isn't created per-row
    val getUIData = uiResolver(client = client, roomId = roomId)
    val userIcon = userIcon(client = client)

    // Track the oldest loaded event id to continue paging from there
    var oldestLoadedId by remember(roomId) { mutableStateOf<EventId?>(null) }

    val pageSize = 50

    // Small helper to load one page from a start event id going backwards
    suspend fun loadPage(startFrom: EventId) {
        log.info { "Loading transactions page content from $startFrom" }
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
                log.info { "Got one timeline event flow" }
                emittedCount++
                val firstEvent = timelineEventFlow.first()
                lastSeenId = firstEvent.eventId

                // Get the first emission with a usable content for this timeline event
                val pair: Pair<EventId, ZeroInterestTransactionEvent>? = run {
                    val content = firstEvent.content?.getOrNull()
                    if (content is ZeroInterestTransactionEvent) (firstEvent.eventId to content) else null
                }
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
                        getUIData = getUIData,
                        userIcon = userIcon,
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
    getUIData: (UserId) -> Flow<UserUIData>,
    userIcon: @Composable (data: UserUIData) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val uiData by getUIData(transaction.sender).collectAsState(UserUIData(transaction.sender.full, null))
        userIcon(uiData)
        Spacer(Modifier.width(8.dp))
        Column {
            if (transaction.description == ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION) {
                Text(text = "Payment",
                    style = MaterialTheme.typography.titleMedium)
            } else {
                Text(text = transaction.description,
                    style = MaterialTheme.typography.titleMedium)
            }
            Text(text = uiData.name,
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = formatBalance(transaction.total))
    }
}