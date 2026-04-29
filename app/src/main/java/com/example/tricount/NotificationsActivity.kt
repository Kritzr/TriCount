package com.example.tricount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.AppNotification
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class NotificationsActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels {
        TricountViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sessionManager = SessionManager(this)

        // Start the real-time listener — this is the single source of truth for
        // notifications. Do NOT also call loadNotifications() here; the listener
        // fires immediately with the current snapshot and keeps updating on its own.
        viewModel.startNotificationListener()
        viewModel.loadPendingJoinRequests()

        setContent {
            TriCountTheme(darkTheme = sessionManager.getDarkMode()) {
                NotificationsScreen(
                    viewModel = viewModel,
                    onBack    = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // FIX: removed loadNotifications() — the real-time listener handles it
        // automatically and calling both caused duplicate IDs in the LazyColumn,
        // crashing with "Key was already used".
        // Only reload join requests (no live listener for those).
        viewModel.loadPendingJoinRequests()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel : TricountViewModel,
    onBack    : () -> Unit
) {
    val notifications   by viewModel.notifications.collectAsStateWithLifecycle()
    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    val unreadCount  = remember(notifications)   { notifications.count   { !it.read } }
    val pendingCount = remember(pendingRequests) { pendingRequests.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = { Icon(Icons.Filled.Notifications, null, Modifier.size(18.dp)) },
                    text     = {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Activity",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                            if (unreadCount > 0) NotifBadge(unreadCount)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = { Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp)) },
                    text     = {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Join Requests",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                            if (pendingCount > 0) NotifBadge(pendingCount)
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> NotificationsTab(
                    notifications = notifications,
                    onMarkRead    = { id -> viewModel.markNotificationRead(id) }
                )
                1 -> JoinRequestsTab(
                    requests  = pendingRequests,
                    onApprove = { tId, uid, email ->
                        viewModel.approveJoinRequest(tId, uid, email)
                    },
                    onReject  = { tId, uid ->
                        viewModel.rejectJoinRequest(tId, uid)
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 0 — Activity / notifications
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationsTab(
    notifications : List<AppNotification>,
    onMarkRead    : (String) -> Unit
) {
    if (notifications.isEmpty()) {
        EmptyState(
            icon     = Icons.Filled.NotificationsNone,
            title    = "No Notifications",
            subtitle = "You're all caught up! Activity from your Tricounts will appear here."
        )
        return
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(notifications, key = { it.id }) { notif ->
            NotificationItem(
                notification = notif,
                onMarkRead   = { onMarkRead(notif.id) }
            )
        }
    }
}

@Composable
private fun NotificationItem(
    notification : AppNotification,
    onMarkRead   : () -> Unit
) {
    val (icon, tint) = notifIconAndTint(notification.type)

    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape          = RoundedCornerShape(12.dp),
        color          = if (!notification.read)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (!notification.read) 2.dp else 0.dp
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape    = CircleShape,
                color    = tint.copy(alpha = 0.15f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (notification.tricountName.isNotBlank()) {
                    Text(
                        notification.tricountName,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    notification.message,
                    fontSize   = 14.sp,
                    fontWeight = if (!notification.read) FontWeight.SemiBold else FontWeight.Normal,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 3,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatRelativeTime(notification.createdAt),
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!notification.read) {
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.primary
                    ) {}
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick        = onMarkRead,
                        modifier       = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Mark read", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 — Join requests (for tricount creators)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun JoinRequestsTab(
    requests  : List<Map<String, Any>>,
    onApprove : (tricountId: String, requesterUid: String, requesterEmail: String) -> Unit,
    onReject  : (tricountId: String, requesterUid: String) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyState(
            icon     = Icons.Filled.HowToReg,
            title    = "No Pending Requests",
            subtitle = "When someone uses your join code, their request will appear here."
        )
        return
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = requests,
            key   = { it["docId"]?.toString() ?: (it["uid"]?.toString() + it["tricountId"]?.toString()) }
        ) { req ->
            JoinRequestItem(
                request   = req,
                onApprove = onApprove,
                onReject  = onReject
            )
        }
    }
}

@Composable
private fun JoinRequestItem(
    request   : Map<String, Any>,
    onApprove : (tricountId: String, requesterUid: String, requesterEmail: String) -> Unit,
    onReject  : (tricountId: String, requesterUid: String) -> Unit
) {
    val requesterUid   = request["uid"]?.toString()          ?: return
    val requesterName  = request["name"]?.toString()         ?: "Unknown"
    val requesterEmail = request["email"]?.toString()        ?: ""
    val tricountId     = request["tricountId"]?.toString()   ?: return
    val tricountName   = request["tricountName"]?.toString() ?: "Unknown Tricount"
    val requestedAt    = (request["requestedAt"] as? Long)   ?: 0L

    var decided  by remember(requesterUid + tricountId) { mutableStateOf(false) }
    var decision by remember(requesterUid + tricountId) { mutableStateOf("") }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            requesterName.firstOrNull()?.uppercase() ?: "?",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        requesterName,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (requesterEmail.isNotBlank()) {
                        Text(
                            requesterEmail,
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Group, null,
                        modifier = Modifier.size(16.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Wants to join: $tricountName",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.primary,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "Requested ${formatRelativeTime(requestedAt)}",
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(visible = !decided, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = {
                            decided  = true
                            decision = "rejected"
                            onReject(tricountId, requesterUid)
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Deny", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick  = {
                            decided  = true
                            decision = "approved"
                            onApprove(tricountId, requesterUid, requesterEmail)
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Accept", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            AnimatedVisibility(visible = decided, enter = fadeIn(), exit = fadeOut()) {
                val (fbIcon, fbText, fbColor) = when (decision) {
                    "approved" -> Triple(
                        Icons.Filled.CheckCircle,
                        "Accepted! $requesterName has been added.",
                        MaterialTheme.colorScheme.primary
                    )
                    else -> Triple(
                        Icons.Filled.Cancel,
                        "Request denied.",
                        MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(fbIcon, null, tint = fbColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(fbText, fontSize = 13.sp, color = fbColor, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotifBadge(count: Int) {
    Box(
        modifier         = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            fontSize   = 10.sp,
            color      = MaterialTheme.colorScheme.onError,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(72.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
            Spacer(Modifier.height(20.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

/** Maps notification type string → (icon, tint colour). */
@Composable
private fun notifIconAndTint(type: String): Pair<ImageVector, Color> = when (type) {
    "JOIN_REQUEST"  -> Pair(Icons.Filled.PersonAdd,     MaterialTheme.colorScheme.tertiary)
    "JOIN_APPROVED" -> Pair(Icons.Filled.CheckCircle,   MaterialTheme.colorScheme.primary)
    "JOIN_REJECTED" -> Pair(Icons.Filled.Cancel,        MaterialTheme.colorScheme.error)
    "MEMBER_ADDED"  -> Pair(Icons.Filled.Group,         MaterialTheme.colorScheme.secondary)
    else            -> Pair(Icons.Filled.Notifications, MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Human-readable relative timestamp. */
private fun formatRelativeTime(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val diff = System.currentTimeMillis() - epochMillis
    return when {
        diff < 60_000L          -> "just now"
        diff < 3_600_000L       -> "${diff / 60_000} min ago"
        diff < 86_400_000L      -> "${diff / 3_600_000} hr ago"
        diff < 7 * 86_400_000L  -> "${diff / 86_400_000} days ago"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMillis))
    }
}