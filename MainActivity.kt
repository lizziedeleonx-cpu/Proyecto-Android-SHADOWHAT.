package com.shadowhat.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.shadowhat.app.data.*
import com.shadowhat.app.dispatcher.WhatsAppDispatcher
import com.shadowhat.app.service.CampaignWorker
import com.shadowhat.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class BackupPayload(
    val app: String = "SHADOWHAT",
    val timestamp: Long = System.currentTimeMillis(),
    val groups: List<GroupEntity> = emptyList(),
    val campaigns: List<CampaignEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val dao = database.dao()

        setContent {
            ShadowHatTheme {
                ShadowHatApp(dao)
            }
        }
    }
}

enum class Screen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Inicio", Icons.Default.Dashboard),
    GROUPS("Grupos", Icons.Default.Group),
    NEW_CAMPAIGN("Campaña", Icons.Default.Send),
    QUEUE("Cola", Icons.Default.PlayArrow),
    HISTORY("Historial", Icons.Default.History),
    SETTINGS("Ajustes", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowHatApp(dao: ShadowHatDao) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var preselectedGroupIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val groups by dao.getAllGroups().collectAsState(initial = emptyList())
    val campaigns by dao.getAllCampaigns().collectAsState(initial = emptyList())
    val queue by dao.getAllQueueItems().collectAsState(initial = emptyList())
    val history by dao.getAllHistory().collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope()
    val pendingCount = queue.count { it.status == "pending" || it.status == "dispatching" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SHADOWHAT",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BlackOnyx,
                    titleContentColor = WhiteText
                ),
                actions = {
                    Text(
                        text = if (pendingCount > 0) "COLA: $pendingCount" else "WA BUSINESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonPrimary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = BlackOnyx,
                tonalElevation = 8.dp
            ) {
                Screen.values().forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (screen == Screen.QUEUE && pendingCount > 0) {
                                        Badge(containerColor = CrimsonPrimary) {
                                            Text(pendingCount.toString(), color = Color.White)
                                        }
                                    }
                                }
                            ) {
                                Icon(screen.icon, contentDescription = screen.title)
                            }
                        },
                        label = { Text(screen.title, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CrimsonPrimary,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = DarkGraphite,
                            unselectedIconColor = GraySubtext,
                            unselectedTextColor = GraySubtext
                        )
                    )
                }
            }
        },
        containerColor = BlackOnyx
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BlackOnyx)
        ) {
            when (currentScreen) {
                Screen.DASHBOARD -> DashboardContent(
                    groups = groups,
                    campaigns = campaigns,
                    queue = queue,
                    history = history,
                    onNavigate = { currentScreen = it }
                )
                Screen.GROUPS -> GroupsContent(
                    groups = groups,
                    onSaveGroup = { grp -> coroutineScope.launch { dao.insertGroup(grp) } },
                    onDeleteGroup = { grp -> coroutineScope.launch { dao.deleteGroup(grp) } },
                    onDeleteMultiple = { ids -> coroutineScope.launch { dao.deleteGroupsByIds(ids) } },
                    onCampaignWithSelected = { ids ->
                        preselectedGroupIds = ids
                        currentScreen = Screen.NEW_CAMPAIGN
                    }
                )
                Screen.NEW_CAMPAIGN -> NewCampaignContent(
                    groups = groups,
                    initialSelectedIds = preselectedGroupIds,
                    onStartCampaign = { campaign, selectedGroups ->
                        coroutineScope.launch {
                            dao.insertCampaign(campaign)
                            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            val tasks = selectedGroups.map { grp ->
                                val msg = campaign.message
                                    .replace("{grupo}", grp.name)
                                    .replace("{fecha}", dateStr)
                                    .replace("{hora}", timeStr)
                                TaskQueueEntity(
                                    id = UUID.randomUUID().toString(),
                                    campaignId = campaign.id,
                                    groupId = grp.id,
                                    groupName = grp.name,
                                    message = msg,
                                    status = "pending",
                                    attempts = 0,
                                    maxAttempts = 3
                                )
                            }
                            dao.insertQueueItems(tasks)
                            // Enqueue WorkManager background recovery task
                            WorkManager.getInstance(context).enqueue(
                                OneTimeWorkRequestBuilder<CampaignWorker>().build()
                            )
                            Toast.makeText(context, "Cola cargada con ${tasks.size} grupos", Toast.LENGTH_SHORT).show()
                            currentScreen = Screen.QUEUE
                        }
                    }
                )
                Screen.QUEUE -> QueueContent(
                    queue = queue,
                    onUpdateTask = { task -> coroutineScope.launch { dao.updateQueueItem(task) } },
                    onInsertHistory = { record -> coroutineScope.launch { dao.insertHistory(record) } },
                    onClearCompleted = { coroutineScope.launch { dao.clearCompletedQueueItems() } },
                    onCancelAll = {
                        coroutineScope.launch {
                            val now = System.currentTimeMillis()
                            queue.filter { it.status == "pending" || it.status == "dispatching" }.forEach { item ->
                                dao.updateQueueItem(item.copy(status = "skipped", errorMessage = "Cancelado por el usuario"))
                                dao.insertHistory(
                                    HistoryEntity(
                                        id = UUID.randomUUID().toString(),
                                        campaignId = item.campaignId,
                                        campaignTitle = "Campaña Cancelada",
                                        groupId = item.groupId,
                                        groupName = item.groupName,
                                        messageSnippet = item.message.take(60),
                                        status = "cancelled",
                                        timestamp = now,
                                        attempts = item.attempts
                                    )
                                )
                            }
                        }
                    }
                )
                Screen.HISTORY -> HistoryContent(
                    history = history,
                    onClear = { coroutineScope.launch { dao.clearHistory() } }
                )
                Screen.SETTINGS -> SettingsContent(dao = dao)
            }
        }
    }
}

@Composable
fun DashboardContent(
    groups: List<GroupEntity>,
    campaigns: List<CampaignEntity>,
    queue: List<TaskQueueEntity>,
    history: List<HistoryEntity>,
    onNavigate: (Screen) -> Unit
) {
    val pendingTasks = queue.count { it.status == "pending" || it.status == "dispatching" }
    val verifiedSent = history.count { it.status == "sent_verified" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGraphite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CrimsonPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(2.dp, CrimsonPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "SHADOW THE HEDGEHOG — CHAOS CONTROL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"Controla tus grupos de WhatsApp Business sin retrasos ni falsas promesas. Cada envío es verificado.\"",
                            fontSize = 13.sp,
                            color = WhiteText
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard("Grupos", groups.size.toString(), Modifier.weight(1f))
                StatCard("Campañas", campaigns.size.toString(), Modifier.weight(1f))
                StatCard("Pendientes", pendingTasks.toString(), Modifier.weight(1f))
                StatCard("Verificados", verifiedSent.toString(), Modifier.weight(1f))
            }
        }

        item {
            Button(
                onClick = { onNavigate(Screen.NEW_CAMPAIGN) },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("NUEVA CAMPAÑA", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (pendingTasks > 0) {
            item {
                OutlinedButton(
                    onClick = { onNavigate(Screen.QUEUE) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("CONTINUAR COLA PENDIENTE ($pendingTasks TAREAS)", color = CrimsonPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkGraphite),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 10.sp, color = GraySubtext)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = WhiteText)
        }
    }
}

@Composable
fun GroupsContent(
    groups: List<GroupEntity>,
    onSaveGroup: (GroupEntity) -> Unit,
    onDeleteGroup: (GroupEntity) -> Unit,
    onDeleteMultiple: (List<String>) -> Unit,
    onCampaignWithSelected: (List<String>) -> Unit
) {
    val categories = listOf("Todos", "Ventas", "Clientes VIP", "Distribuidores", "Personal", "General")
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    val selectedIds = remember { mutableStateListOf<String>() }

    var editingGroup by remember { mutableStateOf<GroupEntity?>(null) }
    var showModal by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("Ventas") }
    var notesInput by remember { mutableStateOf("") }

    val filtered = groups.filter { grp ->
        val matchesSearch = grp.name.contains(searchQuery, ignoreCase = true) ||
            (grp.notes?.contains(searchQuery, ignoreCase = true) == true)
        val matchesCat = selectedCategory == "Todos" || grp.category == selectedCategory
        matchesSearch && matchesCat
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mis Grupos (${groups.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = WhiteText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedIds.isNotEmpty()) {
                    Button(
                        onClick = { onCampaignWithSelected(selectedIds.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Campaña (${selectedIds.size})", fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = {
                        editingGroup = null
                        nameInput = ""
                        categoryInput = "Ventas"
                        notesInput = ""
                        showModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+ Nuevo", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar grupo o nota...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 11.sp) }
                )
            }
        }

        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${selectedIds.size} seleccionados", fontSize = 12.sp, color = CrimsonPrimary)
                TextButton(
                    onClick = {
                        onDeleteMultiple(selectedIds.toList())
                        selectedIds.clear()
                    }
                ) {
                    Text("Eliminar seleccionados", color = CrimsonPrimary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { grp ->
                val isSelected = selectedIds.contains(grp.id)
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkGraphite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) selectedIds.add(grp.id) else selectedIds.remove(grp.id)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(grp.name, fontWeight = FontWeight.Bold, color = WhiteText)
                            Text(grp.category, fontSize = 11.sp, color = CrimsonPrimary)
                            if (!grp.notes.isNullOrBlank()) {
                                Text(grp.notes, fontSize = 11.sp, color = GraySubtext)
                            }
                        }
                        IconButton(
                            onClick = {
                                editingGroup = grp
                                nameInput = grp.name
                                categoryInput = grp.category
                                notesInput = grp.notes ?: ""
                                showModal = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GraySubtext)
                        }
                        IconButton(onClick = { onDeleteGroup(grp) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = CrimsonPrimary)
                        }
                    }
                }
            }
        }

        if (showModal) {
            AlertDialog(
                onDismissRequest = { showModal = false },
                title = { Text(if (editingGroup != null) "Editar Grupo" else "Registrar Grupo", color = WhiteText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Nombre exacto del grupo") }
                        )
                        OutlinedTextField(
                            value = categoryInput,
                            onValueChange = { categoryInput = it },
                            label = { Text("Categoría (Ventas, Clientes VIP...)") }
                        )
                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text("Notas") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                val now = System.currentTimeMillis()
                                val entity = GroupEntity(
                                    id = editingGroup?.id ?: UUID.randomUUID().toString(),
                                    name = nameInput.trim(),
                                    category = categoryInput.trim().ifBlank { "General" },
                                    notes = notesInput.trim().ifBlank { null },
                                    createdAt = editingGroup?.createdAt ?: now,
                                    updatedAt = now
                                )
                                onSaveGroup(entity)
                                showModal = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                    ) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showModal = false }) {
                        Text("Cancelar", color = GraySubtext)
                    }
                },
                containerColor = DarkGraphite
            )
        }
    }
}

@Composable
fun NewCampaignContent(
    groups: List<GroupEntity>,
    initialSelectedIds: List<String>,
    onStartCampaign: (CampaignEntity, List<GroupEntity>) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shadowhat_drafts", Context.MODE_PRIVATE) }

    var title by remember { mutableStateOf(prefs.getString("draft_title", "") ?: "") }
    var message by remember { mutableStateOf(prefs.getString("draft_message", "") ?: "") }
    val selectedGroupIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(groups, initialSelectedIds) {
        if (initialSelectedIds.isNotEmpty()) {
            selectedGroupIds.clear()
            selectedGroupIds.addAll(initialSelectedIds)
        } else if (selectedGroupIds.isEmpty()) {
            selectedGroupIds.addAll(groups.map { it.id })
        }
    }

    // Auto-save draft on every edit
    LaunchedEffect(title, message) {
        prefs.edit()
            .putString("draft_title", title)
            .putString("draft_message", message)
            .apply()
    }

    val emojis = listOf("🔥", "⚡", "💎", "🚀", "📦", "🖤", "🦔", "✅", "⚠️", "🎯")
    val sampleGroupName = groups.firstOrNull { selectedGroupIds.contains(it.id) }?.name ?: "Mi Grupo"
    val previewMessage = message
        .replace("{grupo}", sampleGroupName)
        .replace("{fecha}", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
        .replace("{hora}", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Nueva Campaña (Borrador Automático)", fontWeight = FontWeight.Black, fontSize = 18.sp, color = WhiteText)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nombre de la campaña") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(onClick = { message += "{grupo}" }, label = { Text("+ {grupo}") })
                AssistChip(onClick = { message += "{fecha}" }, label = { Text("+ {fecha}") })
                AssistChip(onClick = { message += "{hora}" }, label = { Text("+ {hora}") })
                emojis.forEach { em ->
                    AssistChip(onClick = { message += em }, label = { Text(em) })
                }
            }
        }

        item {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensaje para WhatsApp Business") },
                modifier = Modifier.fillMaxWidth().height(130.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF005C4B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Vista previa ($sampleGroupName):", fontSize = 10.sp, color = Color(0xFF8696A0))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = previewMessage.ifBlank { "Escribe un mensaje para ver la vista previa..." },
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Destinatarios (${selectedGroupIds.size}/${groups.size})", fontWeight = FontWeight.Bold, color = WhiteText)
                TextButton(
                    onClick = {
                        if (selectedGroupIds.size == groups.size) selectedGroupIds.clear()
                        else {
                            selectedGroupIds.clear()
                            selectedGroupIds.addAll(groups.map { it.id })
                        }
                    }
                ) {
                    Text(if (selectedGroupIds.size == groups.size) "Ninguno" else "Todos", color = CrimsonPrimary)
                }
            }
        }

        items(groups, key = { it.id }) { grp ->
            val isSelected = selectedGroupIds.contains(grp.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) DarkGraphite else Color.Transparent)
                    .clickable {
                        if (isSelected) selectedGroupIds.remove(grp.id) else selectedGroupIds.add(grp.id)
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        if (checked) selectedGroupIds.add(grp.id) else selectedGroupIds.remove(grp.id)
                    },
                    colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                )
                Column {
                    Text(grp.name, color = WhiteText, fontWeight = FontWeight.Medium)
                    Text(grp.category, fontSize = 11.sp, color = GraySubtext)
                }
            }
        }

        item {
            Button(
                onClick = {
                    val selectedGroups = groups.filter { selectedGroupIds.contains(it.id) }
                    val campaign = CampaignEntity(
                        id = UUID.randomUUID().toString(),
                        title = title.trim().ifBlank { "Campaña ShadowHat" },
                        message = message.trim(),
                        targetGroupIdsJson = selectedGroupIds.joinToString(","),
                        status = "running"
                    )
                    prefs.edit().clear().apply()
                    onStartCampaign(campaign, selectedGroups)
                },
                enabled = selectedGroupIds.isNotEmpty() && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("INICIAR CAMPAÑA (${selectedGroupIds.size} GRUPOS)", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun QueueContent(
    queue: List<TaskQueueEntity>,
    onUpdateTask: (TaskQueueEntity) -> Unit,
    onInsertHistory: (HistoryEntity) -> Unit,
    onClearCompleted: () -> Unit,
    onCancelAll: () -> Unit
) {
    val context = LocalContext.current
    var verifyingTask by remember { mutableStateOf<TaskQueueEntity?>(null) }

    val pendingList = queue.filter { it.status == "pending" || it.status == "dispatching" }
    val verifiedCount = queue.count { it.status == "sent_verified" }
    val failedList = queue.filter { it.status == "failed" }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Cola de Tareas Persistente", fontWeight = FontWeight.Black, fontSize = 18.sp, color = WhiteText)
                Text("Pendientes: ${pendingList.size} | Verificados: $verifiedCount | Fallidos: ${failedList.size}", fontSize = 12.sp, color = GraySubtext)
            }
            if (pendingList.isNotEmpty()) {
                Button(
                    onClick = onCancelAll,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGraphite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Detener", color = CrimsonPrimary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (pendingList.isNotEmpty()) {
            val nextTask = pendingList.first()
            Button(
                onClick = {
                    val launched = WhatsAppDispatcher.dispatchMessage(context, nextTask.message, nextTask.groupName, true)
                    if (launched) {
                        val updated = nextTask.copy(
                            status = "dispatching",
                            attempts = nextTask.attempts + 1,
                            lastAttemptAt = System.currentTimeMillis()
                        )
                        onUpdateTask(updated)
                        verifyingTask = updated
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("INICIAR / DESPACHAR SIGUIENTE: ${nextTask.groupName}", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(queue, key = { it.id }) { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkGraphite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.groupName, fontWeight = FontWeight.Bold, color = WhiteText)
                            Text(
                                text = "Estado: ${task.status.uppercase()} • Intentos: ${task.attempts}/${task.maxAttempts}",
                                fontSize = 11.sp,
                                color = if (task.status == "sent_verified") Color(0xFF10B981) else CrimsonPrimary
                            )
                            Text(task.message, fontSize = 11.sp, color = GraySubtext, maxLines = 1)
                        }
                        if (task.status != "sent_verified" && task.attempts < task.maxAttempts) {
                            Button(
                                onClick = {
                                    val launched = WhatsAppDispatcher.dispatchMessage(context, task.message, task.groupName, true)
                                    if (launched) {
                                        val updated = task.copy(
                                            status = "dispatching",
                                            attempts = task.attempts + 1,
                                            lastAttemptAt = System.currentTimeMillis()
                                        )
                                        onUpdateTask(updated)
                                        verifyingTask = updated
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (task.attempts > 0) "Reintentar" else "Abrir WA", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (verifyingTask != null) {
            val current = verifyingTask!!
            AlertDialog(
                onDismissRequest = { verifyingTask = null },
                title = { Text("Confirmación de Envío Real", color = WhiteText) },
                text = {
                    Text(
                        "Se abrió WhatsApp Business con el mensaje preparado para '${current.groupName}'.\n\n¿Confirmas que enviaste el mensaje dentro del grupo?",
                        color = GraySubtext
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            onUpdateTask(current.copy(status = "sent_verified", verifiedAt = now))
                            onInsertHistory(
                                HistoryEntity(
                                    id = UUID.randomUUID().toString(),
                                    campaignId = current.campaignId,
                                    campaignTitle = "Campaña Verificada",
                                    groupId = current.groupId,
                                    groupName = current.groupName,
                                    messageSnippet = current.message.take(80),
                                    status = "sent_verified",
                                    timestamp = now,
                                    attempts = current.attempts
                                )
                            )
                            verifyingTask = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Sí, Enviado y Verificado", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            val now = System.currentTimeMillis()
                            onUpdateTask(current.copy(status = "failed", errorMessage = "No confirmado"))
                            onInsertHistory(
                                HistoryEntity(
                                    id = UUID.randomUUID().toString(),
                                    campaignId = current.campaignId,
                                    campaignTitle = "Envío Fallido",
                                    groupId = current.groupId,
                                    groupName = current.groupName,
                                    messageSnippet = current.message.take(80),
                                    status = "failed",
                                    timestamp = now,
                                    attempts = current.attempts
                                )
                            )
                            verifyingTask = null
                        }
                    ) {
                        Text("No se envió / Falló", color = CrimsonPrimary)
                    }
                },
                containerColor = DarkGraphite
            )
        }
    }
}

@Composable
fun HistoryContent(history: List<HistoryEntity>, onClear: () -> Unit) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Historial Verificable (${history.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = WhiteText)
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Vaciar Historial", color = CrimsonPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin registros de envíos.", color = GraySubtext)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkGraphite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.groupName, fontWeight = FontWeight.Bold, color = WhiteText)
                                Text(
                                    text = item.status.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.status == "sent_verified") Color(0xFF10B981) else CrimsonPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.messageSnippet, fontSize = 12.sp, color = GraySubtext)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${formatter.format(Date(item.timestamp))} • Intentos: ${item.attempts}",
                                fontSize = 10.sp,
                                color = GraySubtext
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsContent(dao: ShadowHatDao) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val availability = remember { WhatsAppDispatcher.checkInstalledWhatsApp(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val payload = BackupPayload(
                    groups = dao.getAllGroupsList(),
                    campaigns = dao.getAllCampaignsList(),
                    history = dao.getAllHistoryList()
                )
                val json = gson.toJson(payload)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Respaldo JSON exportado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!json.isNullOrBlank()) {
                        val payload = gson.fromJson(json, BackupPayload::class.java)
                        if (payload.groups.isNotEmpty()) dao.insertGroups(payload.groups)
                        if (payload.campaigns.isNotEmpty()) dao.insertCampaigns(payload.campaigns)
                        if (payload.history.isNotEmpty()) dao.insertHistoryList(payload.history)
                        Toast.makeText(context, "Importados ${payload.groups.size} grupos", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al importar JSON: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Ajustes del Sistema & Permisos", fontWeight = FontWeight.Black, fontSize = 18.sp, color = WhiteText)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGraphite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Estado de WhatsApp Business", fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                    Text(
                        text = when (availability) {
                            WhatsAppDispatcher.AppAvailability.WHATSAPP_BUSINESS -> "Detectado: WhatsApp Business (com.whatsapp.w4b)"
                            WhatsAppDispatcher.AppAvailability.WHATSAPP_STANDARD -> "Detectado: WhatsApp Estándar (com.whatsapp)"
                            WhatsAppDispatcher.AppAvailability.NONE -> "No detectado en el dispositivo"
                        },
                        fontSize = 12.sp,
                        color = WhiteText
                    )
                    Button(
                        onClick = { WhatsAppDispatcher.openWhatsAppBusinessApp(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Abrir WhatsApp Business", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGraphite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Permisos y Ahorro de Batería", fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { WhatsAppDispatcher.openNotificationSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Permiso Notificaciones", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { WhatsAppDispatcher.openBatteryOptimizationSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ahorro de Batería", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGraphite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exportación e Importación Local (JSON)", fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("SHADOWHAT_Backup.json") },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Exportar JSON", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGraphite),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Importar JSON", fontSize = 11.sp, color = WhiteText)
                        }
                    }
                }
            }
        }
    }
}
