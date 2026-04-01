package com.example.tricount

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AuthViewModel
import com.example.tricount.viewModel.TricountViewModel


class HomeActivity : ComponentActivity() {

    private val tricountViewModel: TricountViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionManager = SessionManager(this)

        // Safety check: if somehow session is empty, go back to login
        if (!sessionManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContent {
            val isDarkMode = remember { mutableStateOf(sessionManager.getDarkMode()) }

            TriCountTheme(darkTheme = isDarkMode.value) {
                HomeScreen(
                    viewModel        = tricountViewModel,
                    sessionManager   = sessionManager,
                    isDarkMode       = isDarkMode.value,
                    onDarkModeToggle = { enabled ->
                        isDarkMode.value = enabled
                        sessionManager.setDarkMode(enabled)
                    },
                    onTricountClick = { tricountId, tricountName ->
                        startActivity(
                            Intent(this, TricountDetailActivity::class.java).apply {
                                putExtra("TRICOUNT_ID",   tricountId)
                                putExtra("TRICOUNT_NAME", tricountName)
                            }
                        )
                    },
                    onLogoutClick = {
                        // authViewModel.logout() signs out of Firebase AND clears session
                        authViewModel.logout()
                        val intent = Intent(this, LoginActivity::class.java).apply {
                            // Clear the back stack so Back doesn't return to HomeActivity
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    },
                    onDeleteAccountClick = {
                        authViewModel.logout()
                        sessionManager.clearSession()
                        val intent = Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tricountViewModel.loadTricounts()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen shell  (unchanged from original — just keeping it here for
// completeness so you have a single file to drop in)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel            : TricountViewModel,
    sessionManager       : SessionManager,
    isDarkMode           : Boolean,
    onDarkModeToggle     : (Boolean) -> Unit,
    onTricountClick      : (Int, String) -> Unit,
    onLogoutClick        : () -> Unit,
    onDeleteAccountClick : () -> Unit
) {
    var selectedBottomTab by remember { mutableStateOf(0) }
    val context           = LocalContext.current
    var showBottomSheet   by remember { mutableStateOf(false) }
    val sheetState        = rememberModalBottomSheetState()

    val addTricountLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.loadTricounts() }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    icon     = {
                        Icon(Icons.Filled.Home, null,
                            modifier = Modifier.size(if (selectedBottomTab == 0) 28.dp else 24.dp))
                    },
                    label    = { Text("TriCounts") },
                    selected = selectedBottomTab == 0,
                    onClick  = { selectedBottomTab = 0 },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = MaterialTheme.colorScheme.primary,
                        selectedTextColor   = MaterialTheme.colorScheme.primary,
                        indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    icon     = {
                        Icon(Icons.Filled.Person, null,
                            modifier = Modifier.size(if (selectedBottomTab == 1) 28.dp else 24.dp))
                    },
                    label    = { Text("Profile") },
                    selected = selectedBottomTab == 1,
                    onClick  = { selectedBottomTab = 1 },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = MaterialTheme.colorScheme.primary,
                        selectedTextColor   = MaterialTheme.colorScheme.primary,
                        indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedBottomTab == 0) {
                val scale by animateFloatAsState(
                    targetValue   = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessLow
                    ),
                    label = "fab_scale"
                )
                FloatingActionButton(
                    onClick        = { showBottomSheet = true },
                    modifier       = Modifier.scale(scale),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Tricount")
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState  = selectedBottomTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "screen_transition"
        ) { target ->
            when (target) {
                0 -> TriCountListScreen(
                    modifier        = Modifier.padding(padding),
                    viewModel       = viewModel,
                    sessionManager  = sessionManager,
                    onTricountClick = onTricountClick,
                    onArchivedClick = {
                        context.startActivity(
                            Intent(context, ArchivedTricountsActivity::class.java)
                        )
                    }
                )
                1 -> ProfileScreen(
                    modifier             = Modifier.padding(padding),
                    sessionManager       = sessionManager,
                    isDarkMode           = isDarkMode,
                    onDarkModeToggle     = onDarkModeToggle,
                    onLogoutClick        = onLogoutClick,
                    onDeleteAccountClick = onDeleteAccountClick
                )
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState       = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Choose an option",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(bottom = 16.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBottomSheet = false
                                addTricountLauncher.launch(
                                    Intent(context, AddTricountActivity::class.java)
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, null,
                                modifier = Modifier.size(32.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Start a New Tricount",
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Create a new expense group from scratch",
                                    fontSize = 14.sp,
                                    color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBottomSheet = false
                                addTricountLauncher.launch(
                                    Intent(context, JoinTricountActivity::class.java)
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PersonAdd, null,
                                modifier = Modifier.size(32.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Join an Existing Tricount",
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Enter a code to join a friend's Tricount",
                                    fontSize = 14.sp,
                                    color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TriCountListScreen — unchanged logic, kept here for completeness
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriCountListScreen(
    modifier        : Modifier = Modifier,
    viewModel       : TricountViewModel,
    sessionManager  : SessionManager,
    onTricountClick : (Int, String) -> Unit,
    onArchivedClick : () -> Unit = {}
) {
    val tricounts         by viewModel.tricounts.collectAsStateWithLifecycle()
    val favoriteTricounts by viewModel.favoriteTricounts.collectAsStateWithLifecycle()
    val currentUserId     = sessionManager.getUserId()
    val userId            : Int = currentUserId ?: -1
    var tricountToDelete  by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var tricountToEdit    by remember { mutableStateOf<com.example.tricount.data.entity.TricountEntity?>(null) }
    var selectedTab       by remember { mutableStateOf(0) }
    val tabs              = listOf("Created", "Joined", "Favorites")

    val filteredTricounts : List<com.example.tricount.data.entity.TricountEntity> =
        remember(tricounts, favoriteTricounts, selectedTab, userId) {
            when (selectedTab) {
                0    -> tricounts.filter { it.creatorId == userId && !it.isArchived }
                1    -> tricounts.filter { it.creatorId != userId && !it.isArchived }
                2    -> favoriteTricounts.filter { !it.isArchived }
                else -> tricounts
            }
        }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            2    -> if (currentUserId != null) viewModel.loadFavoriteTricounts(currentUserId)
            else -> viewModel.loadTricounts()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        "My TriCounts",
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${tricounts.size} total • ${filteredTricounts.size} ${tabs[selectedTab].lowercase()}",
                        fontSize = 14.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = MaterialTheme.colorScheme.surface,
                    contentColor     = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            text     = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold
                                    else FontWeight.Normal,
                                    fontSize   = 13.sp
                                )
                            },
                            icon = {
                                when (index) {
                                    0 -> Icon(Icons.Filled.Star,     null, modifier = Modifier.size(18.dp))
                                    1 -> Icon(Icons.Filled.Group,    null, modifier = Modifier.size(18.dp))
                                    2 -> Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                }
            }
        }

        when {
            selectedTab == 2 && filteredTricounts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.FavoriteBorder, null,
                            modifier = Modifier.size(64.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Favorites Yet",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap the heart icon on a Tricount to add it to favorites",
                            fontSize  = 14.sp,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }

            filteredTricounts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.AddCircleOutline
                            else Icons.Filled.PersonAdd,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            when (selectedTab) {
                                0    -> "No Created Tricounts"
                                1    -> "No Joined Tricounts"
                                else -> "No Tricounts"
                            },
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (selectedTab) {
                                0    -> "Tap the + button to create your first Tricount"
                                1    -> "Ask a friend to share their join code"
                                else -> ""
                            },
                            fontSize = 14.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                val favoriteIds = remember(favoriteTricounts) {
                    favoriteTricounts.map { it.id }.toSet()
                }
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTricounts, key = { it.id }) { tricount ->
                        AnimatedTricountCard(
                            tricount         = tricount,
                            isCreator        = tricount.creatorId == userId,
                            isFavorite       = favoriteIds.contains(tricount.id),
                            onClick          = { onTricountClick(tricount.id, tricount.name) },
                            onDeleteClick    = { tricountToDelete = Pair(tricount.id, tricount.name) },
                            onArchiveClick   = { viewModel.archiveTricount(tricount.id) },
                            onEditClick      = { tricountToEdit = tricount },
                            onDuplicateClick = { viewModel.duplicateTricount(tricount.id) },
                            onFavoriteClick  = {
                                if (currentUserId != null)
                                    viewModel.toggleFavorite(currentUserId, tricount.id)
                            }
                        )
                    }
                    item(key = "archived_banner") {
                        Card(
                            modifier  = Modifier.fillMaxWidth(),
                            colors    = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onArchivedClick() }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape    = CircleShape,
                                    color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Archive, null,
                                            modifier = Modifier.size(22.dp),
                                            tint     = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Archived Tricounts",
                                        fontSize   = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color      = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "View trips you have archived",
                                        fontSize = 12.sp,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Filled.ChevronRight, null,
                                    modifier = Modifier.size(20.dp),
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Delete confirmation ────────────────────────────────────────────────────
    tricountToDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { tricountToDelete = null },
            title = { Text("Delete Tricount?") },
            text  = { Text("Are you sure you want to delete \"$name\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTricount(id); tricountToDelete = null },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { tricountToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── Edit dialog ────────────────────────────────────────────────────────────
    tricountToEdit?.let { tricount ->
        var editName by remember(tricount.id) { mutableStateOf(tricount.name) }
        var editDesc by remember(tricount.id) { mutableStateOf(tricount.description) }
        AlertDialog(
            onDismissRequest = { tricountToEdit = null },
            title = { Text("Edit Tricount") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = editName,
                        onValueChange = { editName = it },
                        label         = { Text("Name") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = editDesc,
                        onValueChange = { editDesc = it },
                        label         = { Text("Description") },
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick  = {
                        viewModel.editTricount(tricount.id, editName.trim(), editDesc.trim())
                        tricountToEdit = null
                    },
                    enabled  = editName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { tricountToEdit = null }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AnimatedTricountCard — unchanged
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnimatedTricountCard(
    tricount         : com.example.tricount.data.entity.TricountEntity,
    isCreator        : Boolean,
    isFavorite       : Boolean,
    onClick          : () -> Unit,
    onDeleteClick    : () -> Unit,
    onArchiveClick   : () -> Unit,
    onEditClick      : () -> Unit,
    onDuplicateClick : () -> Unit,
    onFavoriteClick  : () -> Unit
) {
    var isPressed       by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "card_scale"
    )

    Card(
        modifier  = Modifier.fillMaxWidth().scale(scale),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = { isPressed = true; onClick() },
                    onLongClick = { showContextMenu = true }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        tricount.emoji.ifBlank { "⛺" },
                        fontSize = 22.sp
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tricount.name,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                if (tricount.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tricount.description,
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Code: ${tricount.joinCode}",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onFavoriteClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint     = if (isFavorite) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight, null,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }

    if (showContextMenu) {
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text(tricount.name, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ContextMenuOption(Icons.Filled.Edit, "Edit") {
                        showContextMenu = false; onEditClick()
                    }
                    ContextMenuOption(Icons.Filled.ContentCopy, "Duplicate") {
                        showContextMenu = false; onDuplicateClick()
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ContextMenuOption(
                        Icons.Filled.Archive, "Archive",
                        tint = MaterialTheme.colorScheme.secondary
                    ) {
                        showContextMenu = false; onArchiveClick()
                    }
                    ContextMenuOption(
                        Icons.Filled.Delete, "Delete",
                        tint = MaterialTheme.colorScheme.error
                    ) {
                        showContextMenu = false; onDeleteClick()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextMenu = false }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) { kotlinx.coroutines.delay(100); isPressed = false }
    }
}

@Composable
private fun ContextMenuOption(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    tint    : androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, color = tint)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProfileScreen — kept here so HomeActivity compiles standalone
// ─────────────────────────────────────────────────────────────────────────────

private val LANGUAGES = listOf(
    "English", "Spanish", "French", "German",
    "Hindi", "Japanese", "Chinese", "Arabic",
    "Portuguese", "Korean"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier             : Modifier = Modifier,
    sessionManager       : SessionManager,
    isDarkMode           : Boolean,
    onDarkModeToggle     : (Boolean) -> Unit,
    onLogoutClick        : () -> Unit,
    onDeleteAccountClick : () -> Unit
) {
    val context = LocalContext.current

    var displayName    by remember { mutableStateOf(sessionManager.getUserName() ?: "") }
    var nickname       by remember { mutableStateOf(sessionManager.getNickname()) }
    val email                    = sessionManager.getUserEmail() ?: ""
    var photoUriString by remember { mutableStateOf(sessionManager.getProfilePhotoUri()) }
    var language       by remember { mutableStateOf(sessionManager.getLanguage()) }

    var showEditName     by remember { mutableStateOf(false) }
    var showEditNickname by remember { mutableStateOf(false) }
    var showLangDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            photoUriString = it.toString()
            sessionManager.setProfilePhotoUri(it.toString())
            Toast.makeText(context, "Photo updated!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header banner
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier        = Modifier.size(90.dp),
                        shape           = CircleShape,
                        color           = MaterialTheme.colorScheme.primary,
                        shadowElevation = 6.dp
                    ) {
                        if (!photoUriString.isNullOrEmpty()) {
                            AsyncImage(
                                model              = Uri.parse(photoUriString),
                                contentDescription = "Profile photo",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    displayName.firstOrNull()?.uppercase() ?: "?",
                                    fontSize   = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Surface(
                        modifier  = Modifier
                            .size(30.dp)
                            .clickable { photoPicker.launch("image/*") },
                        shape     = CircleShape,
                        color     = MaterialTheme.colorScheme.secondary,
                        shadowElevation = 3.dp
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, "Change photo",
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.onSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (nickname.isNotBlank()) {
                    Text("@$nickname", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
                }
                Text(email, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Dark mode toggle
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(modifier = Modifier.size(38.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            null, modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dark Mode", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(if (isDarkMode) "On" else "Off", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked         = isDarkMode,
                    onCheckedChange = onDarkModeToggle,
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Log out
        Button(
            onClick  = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor   = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(Icons.Filled.Logout, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        // Delete account
        OutlinedButton(
            onClick  = { showDeleteDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Filled.DeleteForever, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete Profile", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(32.dp))
    }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = {
                Icon(Icons.Filled.DeleteForever, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp))
            },
            title = { Text("Delete Profile?", color = MaterialTheme.colorScheme.error) },
            text  = {
                Text("This will permanently delete your account and all data. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; onDeleteAccountClick() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete Forever") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}