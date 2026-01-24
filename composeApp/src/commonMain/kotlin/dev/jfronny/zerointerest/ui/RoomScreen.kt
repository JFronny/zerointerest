package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.SourceCodeUrl
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.service.ZeroInterestDatabase
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.NavigationHelper
import dev.jfronny.zerointerest.util.formatBalance
import dev.jfronny.zerointerest.util.room
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(roomId: RoomId, onBack: () -> Unit, onAddTransaction: (TransactionTemplate?) -> Unit, navHelper: NavigationHelper) {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val trust = koinInject<SummaryTrustService>()
    val database = koinInject<ZeroInterestDatabase>()
    val roomNavHelper = navHelper.room()
    val roomIs = roomNavHelper.roomIs()
    val scope = rememberCoroutineScope()

    NavigationSuiteScaffold(navigationItems = {
        NavigationSuiteItem(
            selected = roomIs(Destination.Room.RoomDestination.Balance),
            onClick = { roomNavHelper.navigateTab(Destination.Room.RoomDestination.Balance) },
            icon = { Icon(Icons.Default.Paid, stringResource(Res.string.balances)) },
            label = { Text(stringResource(Res.string.balances)) }
        )
        NavigationSuiteItem(
            selected = roomIs(Destination.Room.RoomDestination.Transactions),
            onClick = { roomNavHelper.navigateTab(Destination.Room.RoomDestination.Transactions) },
            icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, stringResource(Res.string.transactions)) },
            label = { Text(stringResource(Res.string.transactions)) }
        )
    }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val room by client.room.getById(roomId).collectAsState(null)
                        Text(room?.name?.explicitName ?: stringResource(Res.string.room))
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                        }
                    },
                    actions = {
                        val uriHandler = LocalUriHandler.current
                        IconButton(onClick = {
                            uriHandler.openUri(SourceCodeUrl)
                        }) {
                            Icon(Icons.Default.ForkRight, stringResource(Res.string.source_code))
                        }
                    }
                )
            },
            floatingActionButton = {
                val templatesFlow = remember(roomId) { database.getTransactionTemplates(roomId) }
                val templates by templatesFlow.collectAsState(emptyList())

                if (templates.isEmpty()) {
                    FloatingActionButton(onClick = { onAddTransaction(null) }) {
                        Icon(Icons.Default.Add, stringResource(Res.string.add_transaction))
                    }
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        FloatingActionButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Add, stringResource(Res.string.add_transaction))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                             DropdownMenuItem(
                                text = { Text(stringResource(Res.string.new_transaction)) },
                                onClick = {
                                    expanded = false
                                    onAddTransaction(null)
                                }
                            )
                            templates.forEach { template ->
                                DropdownMenuItem(
                                    text = { Text(template.description) },
                                    onClick = {
                                        expanded = false
                                        onAddTransaction(template)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = roomNavHelper.room,
                startDestination = Destination.Room.RoomDestination.Balance,
                typeMap = mapOf(),
                modifier = Modifier.padding(padding)
            ) {
                composable<Destination.Room.RoomDestination.Balance> {
                    var forceReload by remember { mutableIntStateOf(0) }
                    val flow = remember(roomId, forceReload) { trust.getSummary(roomId) }
                    val event by flow.collectAsState(null)
                    event?.let {
                        BalancesTab(
                            summary = it,
                            userUI = UserUI(client, roomId),
                            forceTrust = {
                                scope.launch {
                                    trust.forceAccept(it)
                                    forceReload++
                                }
                            }
                        )
                    } ?: run {
                        Box(Modifier.fillMaxSize()) {
                            CircularProgressIndicator(Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
                composable<Destination.Room.RoomDestination.Transactions> {
                    TransactionsTab(client, roomId, navHelper)
                }
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
        userUI = PreviewUserUI,
        forceTrust = {},
    )
}

@Composable
private fun BalancesTab(
    summary: SummaryTrustService.Summary,
    userUI: UserUI,
    forceTrust: (SummaryTrustService.Summary.Untrusted) -> Unit
) {
    when (summary) {
        SummaryTrustService.Summary.Empty -> {
            Box(Modifier.fillMaxSize()) {
                Text(stringResource(Res.string.no_balances_yet), Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is SummaryTrustService.Summary.Untrusted -> {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.latest_summary_not_trusted), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { forceTrust(summary) }) {
                        Text(stringResource(Res.string.override))
                    }
                }
            }
        }
        is SummaryTrustService.Summary.Trusted -> {
            val balances = summary.event.balances
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(balances.entries.toList()) { entry ->
                    val balance = entry.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userUI(entry.key)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = formatBalance(-balance))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun TransactionsTab(client: MatrixClient, roomId: RoomId, navHelper: NavigationHelper) {
    val roomService: RoomService = client.room
    val trust = koinInject<SummaryTrustService>()

    // State to hold the list of transaction events
    var transactionEvents by remember(roomId) { mutableStateOf<List<Pair<EventId, ZeroInterestTransactionEvent>>>(emptyList()) }
    var isLoading by remember(roomId) { mutableStateOf(true) }
    var hasMore by remember(roomId) { mutableStateOf(true) }

    val listState = rememberLazyListState()

    // Keep a set of already loaded ids for deduplication when paging
    val loadedIds = remember(roomId) { mutableSetOf<EventId>() }
    // Reload included transactions when coming back to the screen
    val includedTransactionsFlow = remember(roomId, transactionEvents) {
        flow {
            emit(trust.getSummariesReferencingTransactions(roomId, transactionEvents.map { it.first }.toSet()))
        }
    }
    val includedTransactions by includedTransactionsFlow.collectAsState(emptyMap())

    // Reuse name resolver so it isn't created per-row
    val userUI = UserUI(client, roomId)

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
                    fetchTimeout = 12.seconds
                    allowReplaceContent = false
                }
            ).mapNotNull { timelineEventFlow ->
                emittedCount++
                val firstEvent = timelineEventFlow.first()
                lastSeenId = firstEvent.eventId

                // Get the first emission with a usable content for this timeline event
                val content = firstEvent.content?.getOrNull()
                if (content is ZeroInterestTransactionEvent && loadedIds.add(firstEvent.eventId)) (firstEvent.eventId to content)
                else null
            }.toList()

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
                ) { (eventId, transaction) ->
                    TransactionTabItem(
                        transaction = transaction,
                        included = includedTransactions[eventId]?.isNotEmpty() ?: false,
                        userUI = userUI,
                        onClick = { navHelper.navigate(Destination.TransactionDetails(roomId, eventId)) }
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
                            Text(stringResource(Res.string.no_transactions_yet))
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
    included: Boolean,
    userUI: UserUI,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val component = userUI.component(transaction.sender)
        component.Icon()
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (transaction.description == ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION) {
                    Text(text = stringResource(Res.string.payment), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(text = transaction.description, style = MaterialTheme.typography.titleMedium)
                }
                if (!included) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(Res.string.not_included_in_summary),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(text = component.name, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = formatBalance(transaction.total))
    }
}
