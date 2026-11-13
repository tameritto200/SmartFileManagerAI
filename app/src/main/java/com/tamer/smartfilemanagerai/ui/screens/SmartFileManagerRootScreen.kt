package com.tamer.smartfilemanagerai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tamer.smartfilemanagerai.core.model.NamingStyle
import com.tamer.smartfilemanagerai.data.local.entity.FileEntity
import com.tamer.smartfilemanagerai.organizer.OrganizationPreviewPlan
import com.tamer.smartfilemanagerai.ui.MainViewModel
import com.tamer.smartfilemanagerai.ui.navigation.AppDestination
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFileManagerRootScreen(
    viewModel: MainViewModel,
    onRequestSafFolderAccess: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }

    val statusBanner by viewModel.statusBannerMessageAr.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val previewPlan by viewModel.currentPreviewPlan.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "مدير الملفات الذكي بالذكاء الاصطناعي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "قل ما تريد... وسيقوم هاتفك بتنظيم نفسه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(AppDestination.entries) { dest ->
                        NavigationDrawerItem(
                            label = { Text(dest.titleAr) },
                            selected = currentDestination == dest,
                            onClick = {
                                currentDestination = dest
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentDestination.titleAr, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "القائمة")
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentDestination = AppDestination.SEMANTIC_SEARCH }) {
                            Icon(Icons.Default.Search, contentDescription = "البحث الذكي")
                        }
                        IconButton(onClick = { currentDestination = AppDestination.VOICE_ASSISTANT }) {
                            Icon(Icons.Default.Mic, contentDescription = "المساعد الصوتي")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (statusBanner != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusBanner ?: "",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (scanProgress.isScanning) {
                    LinearProgressIndicator(
                        progress = { scanProgress.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "جاري الفحص (${scanProgress.progressPercent}%): ${scanProgress.processedCount}/${scanProgress.totalDiscovered}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row {
                            if (scanProgress.isPaused) {
                                TextButton(onClick = { viewModel.scannerEngine.resumeScan() }) {
                                    Text("استكمال")
                                }
                            } else {
                                TextButton(onClick = { viewModel.scannerEngine.pauseScan() }) {
                                    Text("إيقاف مؤقت")
                                }
                            }
                            TextButton(onClick = { viewModel.scannerEngine.cancelScan() }) {
                                Text("إلغاء")
                            }
                        }
                    }
                }

                when (currentDestination) {
                    AppDestination.DASHBOARD -> DashboardScreenContent(
                        viewModel = viewModel,
                        onNavigate = { currentDestination = it }
                    )
                    AppDestination.ALL_FILES -> FileListScreenContent("جميع الملفات", viewModel.allFiles.collectAsState().value)
                    AppDestination.DOCUMENTS -> FileListScreenContent("المستندات", viewModel.documentFiles.collectAsState().value)
                    AppDestination.IMAGES -> FileListScreenContent("الصور", viewModel.imageFiles.collectAsState().value)
                    AppDestination.VIDEOS -> FileListScreenContent("الفيديوهات", viewModel.videoFiles.collectAsState().value)
                    AppDestination.AUDIO -> FileListScreenContent("الصوتيات", viewModel.audioFiles.collectAsState().value)
                    AppDestination.APKS -> FileListScreenContent(
                        "التطبيقات وملفات APK (فحص آمن دون تثبيت)",
                        viewModel.technicalFiles.collectAsState().value.filter { it.extension.equals("apk", true) }
                    )
                    AppDestination.COMPRESSED -> FileListScreenContent("الملفات المضغوطة", viewModel.compressedFiles.collectAsState().value)
                    AppDestination.FOLDERS -> FoldersScreenContent(viewModel)
                    AppDestination.DUPLICATES -> DuplicatesScreenContent(viewModel)
                    AppDestination.LARGE_FILES -> FileListScreenContent("الملفات الكبيرة (+50 م.ب)", viewModel.largeFiles.collectAsState().value)
                    AppDestination.UNCATEGORIZED -> FileListScreenContent("ملفات غير مصنفة / منخفضة الثقة (للمراجعة)", viewModel.uncategorizedFiles.collectAsState().value)
                    AppDestination.VOICE_ASSISTANT -> VoiceAssistantScreenContent(viewModel)
                    AppDestination.SEMANTIC_SEARCH -> SemanticSearchScreenContent(viewModel)
                    AppDestination.ORGANIZATION_PLAN -> OrganizationPreviewScreenContent(viewModel, previewPlan)
                    AppDestination.OPERATION_HISTORY -> OperationHistoryScreenContent(viewModel)
                    AppDestination.SETTINGS -> SettingsScreenContent(viewModel)
                    AppDestination.AI_MANAGEMENT -> AiManagementScreenContent(viewModel)
                    AppDestination.PERMISSIONS_MANAGER -> PermissionsManagerScreenContent(viewModel, onRequestSafFolderAccess)
                }
            }
        }
    }
}

@Composable
private fun DashboardScreenContent(
    viewModel: MainViewModel,
    onNavigate: (AppDestination) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsState()
    val docs by viewModel.documentFiles.collectAsState()
    val videos by viewModel.videoFiles.collectAsState()
    val images by viewModel.imageFiles.collectAsState()
    val audio by viewModel.audioFiles.collectAsState()
    val large by viewModel.largeFiles.collectAsState()
    val duplicates by viewModel.duplicateClusters.collectAsState()
    val uncategorized by viewModel.uncategorizedFiles.collectAsState()
    val operations by viewModel.operationHistory.collectAsState()
    val storage by viewModel.storageSpace.collectAsState()

    var quickSearchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header & Slogan (Section 10)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "مدير الملفات الذكي بالذكاء الاصطناعي",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ""قل ما تريد... وسيقوم هاتفك بتنظيم نفسه."",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = quickSearchQuery,
                        onValueChange = { quickSearchQuery = it },
                        placeholder = { Text("ابحث بوصف المحتوى (مثال: فيديو شرح Excel أو إيصال الكهرباء)...") },
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.runSemanticSearch(quickSearchQuery)
                                onNavigate(AppDestination.SEMANTIC_SEARCH)
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "بحث")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // Primary Action Buttons: Full Scan, Smart Organize, Voice Assistant
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.startComprehensiveScan() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("الفحص الشامل")
                }
                Button(
                    onClick = {
                        viewModel.generateSmartOrganizationPlan()
                        onNavigate(AppDestination.ORGANIZATION_PLAN)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("التنظيم الذكي")
                }
                FilledTonalButton(
                    onClick = { onNavigate(AppDestination.VOICE_ASSISTANT) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("أمر صوتي")
                }
            }
        }

        // Storage Ring Chart & Category Distribution Visual Chart (Section 10)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ratio = storage.usedRatio.coerceIn(0.05f, 1f)
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                        Canvas(modifier = Modifier.size(84.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = 360f * ratio,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تحليل مساحة التخزين والفهرس", fontWeight = FontWeight.Bold)
                        Text("إجمالي الملفات المفهرسة: ${allFiles.size} ملف")
                        Text("المستندات: ${docs.size} | الصور: ${images.size}")
                        Text("الفيديوهات: ${videos.size} | الصوتيات: ${audio.size}")
                    }
                }
            }
        }

        // Quick Diagnostic Cards: Large Files, Duplicates, Uncategorized, Undo History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardStatCard(
                    title = "الملفات الكبيرة",
                    count = large.size,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppDestination.LARGE_FILES) }
                )
                DashboardStatCard(
                    title = "المكررات",
                    count = duplicates.size,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.refreshDuplicateAnalysis()
                        onNavigate(AppDestination.DUPLICATES)
                    }
                )
                DashboardStatCard(
                    title = "غير مصنفة",
                    count = uncategorized.size,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppDestination.UNCATEGORIZED) }
                )
            }
        }

        // Recent Operations & Quick Undo
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("آخر العمليات المنفذة", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { viewModel.undoLastAction() }) {
                            Icon(Icons.Default.Undo, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("تراجع عن آخر عملية")
                        }
                    }
                    if (operations.isEmpty()) {
                        Text("لا توجد عمليات تعديل أو نقل مسجلة حتى الآن.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        operations.take(5).forEach { op ->
                            Text(
                                text = "• [${op.operationType}] ${op.previousName} ← ${op.newName} (${op.executionStatus})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FileListScreenContent(title: String, files: List<FileEntity>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "$title (${files.size} ملف)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد ملفات في هذا القسم حالياً. اضغط على 'الفحص الشامل' لفهرسة الملفات.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.fileId }) { file ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(file.currentName, fontWeight = FontWeight.Bold)
                            Text(
                                "المجلد: ${file.parentFolderPath} | الحجم: ${file.sizeBytes / 1024} ك.ب | الثقة: ${file.confidenceLevel}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersScreenContent(viewModel: MainViewModel) {
    val folders by viewModel.folders.collectAsState()
    val defaultTree = remember { viewModel.organizerEngine.getDefaultFolderStructurePreview() }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("المجلدات المفهرسة (${folders.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(folders) { folder ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(folder.folderName, fontWeight = FontWeight.Bold)
                    Text(folder.uriOrPath, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("الهيكل الذكي القياسي للمجلدات (قابل للتخصيص):", fontWeight = FontWeight.Bold)
        }
        items(defaultTree) { path ->
            Text("• $path", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DuplicatesScreenContent(viewModel: MainViewModel) {
    val clusters by viewModel.duplicateClusters.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("مجموعات الملفات المكررة والمتشابهة (${clusters.size})", fontWeight = FontWeight.Bold)
            Button(onClick = { viewModel.refreshDuplicateAnalysis() }) {
                Text("إعادة فحص التكرار")
            }
        }
        Text(
            "ملاحظة أمان: لا يقوم التطبيق بحذف أي نسخة تلقائياً، بل يوضح درجة التشابه والنسخة المقترح الاحتفاظ بها.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(clusters) { cluster ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${cluster.matchTypeAr} — المساحة المهدرة: ${cluster.wastedBytes / 1024} ك.ب", fontWeight = FontWeight.Bold)
                        cluster.members.forEach { member ->
                            Text(
                                text = (if (member.isRecommendedKeep) "[مقترح الاحتفاظ بها] " else "[نسخة مكررة ${member.similarityPercent}%] ") +
                                    "${member.file.currentName} (${member.file.uriOrPath})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceAssistantScreenContent(viewModel: MainViewModel) {
    var voiceInputText by remember {
        mutableStateOf("هات كل الفيديوهات اللي ليها علاقة بالتعليم، واعمل لها مجلد اسمه فيديوهات تعليمية، ورتبها حسب السنة، وغير أسماء الملفات اللي ملهاش أسماء واضحة")
    }
    val isListening by viewModel.voiceAssistant.isListening.collectAsState()
    val parsedPlan by viewModel.voiceAssistant.lastParsedPlan.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("المساعد الصوتي الذكي (يدعم اللهجة المصرية والفصحى والإنجليزية)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("يمكنك التحدث مباشرة عبر الميكروفون أو تجربة كتابة أي أمر صوتي مركب:", style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedTextField(
                value = voiceInputText,
                onValueChange = { voiceInputText = it },
                label = { Text("الأمر الصوتي (مفرد أو مركب)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.voiceAssistant.startListening("ar-EG") }) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isListening) "جاري الاستماع..." else "تحدث الآن (مصري)")
                }
                Button(onClick = { viewModel.submitSpokenOrTypedVoiceCommand(voiceInputText) }) {
                    Text("تحليل الأمر الصوتي وعرض الخطة")
                }
            }
        }
        if (parsedPlan != null) {
            item {
                val plan = parsedPlan!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("خطة تنفيذ الأمر الصوتي (${plan.detectedDialect}):", fontWeight = FontWeight.Bold)
                        Text(plan.summaryForTtsAr, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        plan.steps.forEach { step ->
                            Text("خطوة ${step.stepOrder}: ${step.descriptionAr}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (plan.requiresUserConfirmation) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.executeApprovedVoicePlan(plan) }) {
                                    Text("تأكيد وتنفيذ الخطة")
                                }
                                OutlinedButton(onClick = { viewModel.cancelCurrentPlan() }) {
                                    Text("إلغاء الأمر")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SemanticSearchScreenContent(viewModel: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("ابحث بوصف المحتوى أو المدة أو الكلمات داخل الملفات...") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { viewModel.runSemanticSearch(query) }) {
                    Icon(Icons.Default.Search, contentDescription = "بحث")
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { match ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${match.file.currentName} (درجة التطابق: ${match.relevanceScore})", fontWeight = FontWeight.Bold)
                        Text("سبب التطابق: ${match.matchedFieldsSummaryAr}", style = MaterialTheme.typography.bodySmall)
                        if (!match.extractedSnippet.isNullOrBlank()) {
                            Text("مقتطف المحتوى: ${match.extractedSnippet}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationPreviewScreenContent(
    viewModel: MainViewModel,
    plan: OrganizationPreviewPlan?
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (plan == null) {
            Text("لا توجد خطة تنظيم قيد المعاينة حالياً.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { viewModel.generateSmartOrganizationPlan() }) {
                Text("إنشاء خطة تنظيم ذكي ومعاينتها الآن")
            }
        } else {
            Text(plan.titleAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "الملفات المتأثرة: ${plan.totalAffectedCount} | غير المصنفة للمراجعة: ${plan.reviewQueueItems.size} | تعارض الأسماء: ${plan.conflictItems.size} | المساحة الإجمالية: ${plan.totalAffectedBytes / 1024} ك.ب",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.approveAndExecuteCurrentPlan() }) {
                    Text("موافقة وتنفيذ")
                }
                OutlinedButton(onClick = { viewModel.generateSmartOrganizationPlan(NamingStyle.PROFESSIONAL_STRUCTURED) }) {
                    Text("تعديل الخطة (نمط احترافي بالتاريخ)")
                }
                TextButton(onClick = { viewModel.cancelCurrentPlan() }) {
                    Text("إلغاء العملية")
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plan.items, key = { it.fileId }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = !item.isIgnoredByUser,
                                onCheckedChange = { viewModel.toggleIgnoreFileInPlan(item.fileId) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.originalName} ← ${item.proposedNewName}", fontWeight = FontWeight.Bold)
                                Text(
                                    "من: ${item.sourceFolderPath}
إلى: ${item.targetFolderPath} (${item.confidenceLevel.arabicLabel})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationHistoryScreenContent(viewModel: MainViewModel) {
    val operations by viewModel.operationHistory.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("سجل العمليات والتراجع (${operations.size})", fontWeight = FontWeight.Bold)
            Button(onClick = { viewModel.undoLastAction() }) {
                Text("التراجع عن آخر عملية")
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(operations, key = { it.operationId }) { op ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${op.operationType}: ${op.previousName} ← ${op.newName}", fontWeight = FontWeight.Bold)
                        Text("المسار الأصلي: ${op.originalPath}
المسار الجديد: ${op.newPath}", style = MaterialTheme.typography.bodySmall)
                        Text("الحالة: ${op.executionStatus} | قابل للتراجع: ${if (op.canUndo) "نعم" else "لا"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreenContent(viewModel: MainViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إعدادات التطبيق والتسمية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = prefs.darkThemeEnabled, onCheckedChange = { viewModel.updateDarkTheme(it) })
            Spacer(Modifier.width(8.dp))
            Text("تفعيل الوضع الداكن (Dark Mode)")
        }
        Text("نمط التسمية الافتراضي:", fontWeight = FontWeight.Bold)
        NamingStyle.entries.forEach { style ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = prefs.defaultNamingStyle == style,
                    onClick = { viewModel.updateNamingStyle(style) }
                )
                Text(style.arabicLabel)
            }
        }
    }
}

@Composable
private fun AiManagementScreenContent(viewModel: MainViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    var consentChecked by remember(prefs.cloudAiConsentGranted) { mutableStateOf(prefs.cloudAiConsentGranted) }
    var apiKeyInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إدارة الذكاء الاصطناعي الهجين (Hybrid AI)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("1. الذكاء الاصطناعي المحلي (On-Device AI): نشط دائماً بدون إنترنت", fontWeight = FontWeight.Bold)
                Text("يحلل الصور (ML Kit OCR)، ملفات PDF، البيانات الوصفية، ويصنف الملفات محلياً بخصوصية تامة 100%.")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("2. الذكاء الاصطناعي السحابي (Cloud AI): اختياري بموافقة صريحة", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentChecked, onCheckedChange = { consentChecked = it })
                    Text("أوافق صراحةً على استخدام الذكاء السحابي لتلخيص وتحليل النصوص المعقدة عند توفر الإنترنت")
                }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("مفتاح API السحابي (يُحفظ مشفراً في Android Keystore)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.updateCloudAiConfig(consentChecked, apiKeyInput) }) {
                    Text("حفظ إعدادات الذكاء الاصطناعي")
                }
            }
        }
    }
}

@Composable
private fun PermissionsManagerScreenContent(
    viewModel: MainViewModel,
    onRequestSafFolderAccess: () -> Unit
) {
    val persistedUris = remember { viewModel.storageManager.getPersistedTreeUris() }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إدارة الصلاحيات وقيود نظام Android", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Button(onClick = onRequestSafFolderAccess) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("منح صلاحية الوصول لمجلد عبر إطار التخزين الرسمي (SAF)")
        }
        Text("المجلدات المصرح بها حالياً (${persistedUris.size}):", fontWeight = FontWeight.Bold)
        persistedUris.forEach { uri ->
            Text("• $uri", style = MaterialTheme.typography.bodySmall)
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("توضيح القيود الرسمية لنظام Android (القسم الرابع عشر):", fontWeight = FontWeight.Bold)
                Text("• مجلدات النظام الجذرية (/system, /proc) ومجلدات Android/data و Android/obb محمية بواسطة نظام التشغيل ولا يقوم التطبيق بتعديلها أو حذفها.")
                Text("• يستخدم التطبيق Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE) و MediaStore لضمان التوافق الرسمي والأمان.")
            }
        }
    }
}
