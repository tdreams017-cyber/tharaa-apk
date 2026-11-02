package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.AdBanner
import com.example.ads.AdManager
import com.example.api.GeminiClient
import com.example.data.BookData
import com.example.data.Chapter
import com.example.data.FirestoreManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob SDK
        AdManager.initialize(this)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    
    // Load local stored preferences on launch
    LaunchedEffect(Unit) {
        FirestoreManager.loadLocalData(context)
    }
    
    val userBookData by FirestoreManager.userBookData.collectAsStateWithLifecycle()
    val currentUser by FirestoreManager.currentUser.collectAsStateWithLifecycle()
    val simulatedUser by FirestoreManager.simulatedUser.collectAsStateWithLifecycle()
    
    // Synchronize ThemeConfig with userBookData
    LaunchedEffect(userBookData.isDarkMode, userBookData.fontColorIndex) {
        ThemeConfig.isDarkMode = userBookData.isDarkMode
        ThemeConfig.fontColorIndex = userBookData.fontColorIndex
    }

    // Core App State
    var currentTab by remember { mutableStateOf("home") } // home, book, tools, coach
    
    val currentChapterIndex = userBookData.lastReadIndex
    val fontScale = userBookData.fontScale
    val bookmarkedChapters = userBookData.bookmarks
    
    // Save Preference Helpers
    val saveLastRead: (Int) -> Unit = { index ->
        FirestoreManager.saveUserData(context) {
            this.lastReadIndex = index
        }
    }

    val toggleBookmark: (Int) -> Unit = { id ->
        FirestoreManager.saveUserData(context) {
            this.bookmarks = if (this.bookmarks.contains(id)) {
                this.bookmarks - id
            } else {
                this.bookmarks + id
            }
        }
    }

    val updateFontScale: (Float) -> Unit = { scale ->
        FirestoreManager.saveUserData(context) {
            this.fontScale = scale
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(RichObsidian)
        ) {
            // Main Content Area with dynamic switching
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    "home" -> HomeTabScreen(
                        lastReadChapter = BookData.chapters[currentChapterIndex],
                        onNavigateToChapter = { index ->
                            saveLastRead(index)
                            currentTab = "book"
                        },
                        bookmarkedCount = bookmarkedChapters.size,
                        currentUser = currentUser,
                        simulatedUser = simulatedUser,
                        onLoginDemo = { name, email ->
                            FirestoreManager.loginSimulatedUser(context, name, email)
                        },
                        onLogout = {
                            FirestoreManager.logoutUser(context)
                        }
                    )
                    "book" -> BookTabScreen(
                        currentChapterIndex = currentChapterIndex,
                        onChapterChanged = { index ->
                            saveLastRead(index)
                        },
                        fontScale = fontScale,
                        onFontScaleChanged = updateFontScale,
                        isBookmarked = bookmarkedChapters.contains(BookData.chapters[currentChapterIndex].id),
                        onToggleBookmark = { toggleBookmark(BookData.chapters[currentChapterIndex].id) }
                    )
                    "tools" -> WealthToolsTabScreen()
                    "coach" -> AiCoachTabScreen()
                }
            }

            // Always present Ad banner at the very bottom of every tab to maximize revenue!
            AdBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

// --- NAVIGATION COMPONENT ---

@Composable
fun BottomNavigationBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = DeepForestCard,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, DeepForestBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                NavigationItem("home", "الرئيسية", Icons.Default.Home, "🏡"),
                NavigationItem("book", "الكتاب", Icons.Default.Menu, "📖"),
                NavigationItem("tools", "أدوات الثراء", Icons.Default.Build, "📊"),
                NavigationItem("coach", "مستشار AI", Icons.Default.Send, "🤖")
            )

            tabs.forEach { tab ->
                val isSelected = currentTab == tab.id
                val tintColor = if (isSelected) GoldAccent else TextGray

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab.id) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tab.emoji,
                        fontSize = if (isSelected) 22.sp else 18.sp,
                        modifier = Modifier.animateContentSize()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = tintColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

data class NavigationItem(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val emoji: String
)

// --- TAB 1: HOME TAB SCREEN ---

@Composable
fun HomeTabScreen(
    lastReadChapter: Chapter,
    onNavigateToChapter: (Int) -> Unit,
    bookmarkedCount: Int,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    simulatedUser: com.example.data.FirestoreManager.SimulatedUser?,
    onLoginDemo: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showLoginDialog by remember { mutableStateOf(false) }
    var loginName by remember { mutableStateOf("") }
    var loginEmail by remember { mutableStateOf("") }
    
    val isLoggedIn = currentUser != null || simulatedUser != null
    val userName = currentUser?.displayName ?: simulatedUser?.displayName ?: "قارئ طموح"
    val userEmail = currentUser?.email ?: simulatedUser?.email ?: "guest@wealth.com"
    
    // Login Dialog for emulator sandbox / Google credentials simulation
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = {
                Text(
                    text = "تسجيل الدخول عبر Google 🔐",
                    color = GoldAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "قم بربط حسابك لحفظ ملاحظاتك، صفحاتك المفضلة، ونسب تقدمك في اختبارات الثراء ومزامنتها سحابياً عبر Firestore.",
                        color = TextWhite,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = loginName,
                        onValueChange = { loginName = it },
                        label = { Text("الاسم الكامل", color = TextGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = DeepForestBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = loginEmail,
                        onValueChange = { loginEmail = it },
                        label = { Text("البريد الإلكتروني", color = TextGray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = DeepForestBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (loginName.isNotBlank() && loginEmail.isNotBlank()) {
                            onLoginDemo(loginName, loginEmail)
                            showLoginDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text("تسجيل دخول آمن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text("إلغاء", color = TextGray)
                }
            },
            containerColor = DeepForestCard,
            tonalElevation = 6.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header Brand
        Text(
            text = "طريقك نحو الثراء",
            color = GoldAccent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "(من الصفر إلى الاحتراف)",
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // User Account / Authentication Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLoggedIn) EmeraldDark.copy(alpha = 0.15f) else DeepForestCard
            ),
            border = BorderStroke(
                1.dp, 
                if (isLoggedIn) EmeraldPrimary.copy(alpha = 0.4f) else DeepForestBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Profile Avatar Placeholder
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (isLoggedIn) EmeraldPrimary.copy(alpha = 0.2f) else GoldAccent.copy(alpha = 0.1f), 
                                CircleShape
                            )
                            .border(
                                1.dp, 
                                if (isLoggedIn) EmeraldPrimary else GoldAccent, 
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoggedIn) userName.take(1) else "👤",
                            color = if (isLoggedIn) EmeraldPrimary else GoldAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = if (isLoggedIn) userName else "حساب غير متصل",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isLoggedIn) Color(0xFF4CAF50) else Color(0xFFFF9800), 
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLoggedIn) "سحابي (متصل ومؤمن بـ Firestore)" else "محلي (سجل لمزامنة بياناتك)",
                                color = TextGray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                
                if (isLoggedIn) {
                    TextButton(
                        onClick = onLogout,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
                    ) {
                        Text("خروج", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { showLoginDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("ربط حساب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Luxury Framed Book Cover
        Box(
            modifier = Modifier
                .size(200.dp)
                .shadow(16.dp, RoundedCornerShape(12.dp))
                .background(DeepForestCard, RoundedCornerShape(12.dp))
                .border(2.dp, GoldAccent, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_book_cover),
                contentDescription = "غلاف الكتاب الأصلي بدون علامات مائية",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Book Subtitle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = DeepForestCard),
            border = BorderStroke(1.dp, DeepForestBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "تأليف: الكاتب صادق الخفاجي ✍️",
                    color = TextGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "دليل عملي للربح من أبسط الأشياء وإتقان فنون التجارة والترويج وبناء الأنظمة المالية الناجحة.",
                    color = TextWhite,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Statistics / Resume Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Resume Card
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .clickable { onNavigateToChapter(BookData.chapters.indexOf(lastReadChapter)) },
                colors = CardDefaults.cardColors(containerColor = DeepForestCard),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "تابع القراءة 📖",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = lastReadChapter.title,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "الصفحة ${lastReadChapter.id}",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            }

            // Bookmarks count Card
            Card(
                modifier = Modifier.weight(0.8f),
                colors = CardDefaults.cardColors(containerColor = DeepForestCard),
                border = BorderStroke(1.dp, DeepForestBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "المحفوظات 🔖",
                        color = TextGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$bookmarkedCount",
                        color = GoldAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "صفحات مميزة",
                        color = TextGray,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // The Ultimate Roadmap Card (Book summary highlights)
        Text(
            text = "🗺️ مخطط طريقك نحو الثراء الفوري",
            color = GoldAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )

        val steps = listOf(
            StepItem("1️⃣", "تغيير العقلية", "توقف عن شراء الخصوم وابدأ بالاستثمار في الأصول التي تدر عليك دخلاً مستمراً."),
            StepItem("2️⃣", "إيجاد فكرة وحل مشكلة", "لا تخترع صاروخاً! ابحث عن المشاكل البسيطة للناس ووفر الحلول كدروب سيرفيسينج أو التجارة."),
            StepItem("3️⃣", "الترويج والتسويق الذكي", "استخدم معادلة الجذب (خطاف-قصة-عرض)، أطلق إعلانات واستهدف بذكاء لتضاعف أرباحك.")
        )

        steps.forEach { step ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DeepForestCard.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, DeepForestBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = step.emoji, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = step.title,
                            color = GoldAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = step.desc,
                            color = TextWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
    }
}

data class StepItem(val emoji: String, val title: String, val desc: String)

// --- TAB 2: BOOK READ TAB SCREEN ---

data class SearchResult(
    val chapterIndex: Int,
    val sectionIndex: Int,
    val title: String,
    val snippet: String
)

@Composable
fun BookTabScreen(
    currentChapterIndex: Int,
    onChapterChanged: (Int) -> Unit,
    fontScale: Float,
    onFontScaleChanged: (Float) -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit
) {
    val context = LocalContext.current
    val userBookData by FirestoreManager.userBookData.collectAsStateWithLifecycle()
    
    val chapter = BookData.chapters[currentChapterIndex]
    val scrollState = rememberScrollState()
    
    // Search states
    var searchQuery by remember { mutableStateOf("") }
    
    // Highlight and Notes states
    var selectedSectionForOptions by remember { mutableStateOf<Int?>(null) }
    var currentNoteInput by remember { mutableStateOf("") }
    
    // Page counter to trigger interstitial ad every 3 actions
    var pageChangeCount by remember { mutableStateOf(0) }

    val handleChapterChange: (Int) -> Unit = { newIndex ->
        if (newIndex in BookData.chapters.indices) {
            onChapterChanged(newIndex)
            pageChangeCount++
            
            // Trigger AdMob Interstitial every 3 pages!
            if (pageChangeCount % 3 == 0) {
                AdManager.showInterstitial(context) {
                    Log.d("BookTabScreen", "Interstitial Ad closed/failed, continuing reading.")
                }
            }
        }
    }

    // Reset scroll position on chapter change
    LaunchedEffect(currentChapterIndex) {
        scrollState.scrollTo(0)
    }

    // Parse all chapters for matching search queries
    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList() else {
            val list = mutableListOf<SearchResult>()
            BookData.chapters.forEachIndexed { chIdx, ch ->
                if (ch.title.contains(searchQuery, ignoreCase = true) || ch.intro.contains(searchQuery, ignoreCase = true)) {
                    list.add(SearchResult(chIdx, -1, ch.title, ch.intro.take(120) + "..."))
                }
                ch.sections.forEachIndexed { secIdx, sec ->
                    if (sec.title.contains(searchQuery, ignoreCase = true) || sec.content.contains(searchQuery, ignoreCase = true)) {
                        list.add(SearchResult(chIdx, secIdx, "${ch.title} > ${sec.title}", sec.content.take(120) + "..."))
                    }
                }
            }
            list
        }
    }

    // Interactive Notes & Highlight Dialog
    if (selectedSectionForOptions != null) {
        val secIndex = selectedSectionForOptions!!
        val sec = chapter.sections[secIndex]
        val noteKey = "${chapter.id}_${secIndex}"
        val currentHighlight = userBookData.highlights[noteKey] ?: ""

        AlertDialog(
            onDismissRequest = { selectedSectionForOptions = null },
            title = {
                Text(
                    text = "تخصيص المقطع المميز 🖌️",
                    color = GoldAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "اختر لوناً لتمييز المقطع وكتابة ملاحظتك الشخصية:",
                        color = TextWhite,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Color highlighter options row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val colors = listOf(
                            Triple("#FFF59D", "أصفر", Color(0xFFFFF59D)),
                            Triple("#A5D6A7", "أخضر", Color(0xFFA5D6A7)),
                            Triple("#E1BEE7", "بنفسجي", Color(0xFFE1BEE7)),
                            Triple("#FFCCBC", "وردي", Color(0xFFFFCCBC))
                        )
                        colors.forEach { (hex, name, composeColor) ->
                            val isSelected = currentHighlight == hex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(composeColor, CircleShape)
                                    .border(
                                        2.dp, 
                                        if (isSelected) GoldAccent else Color.Transparent, 
                                        CircleShape
                                    )
                                    .clickable {
                                        FirestoreManager.saveUserData(context) {
                                            val updatedHighlights = this.highlights.toMutableMap()
                                            updatedHighlights[noteKey] = hex
                                            this.highlights = updatedHighlights
                                        }
                                    }
                            )
                        }
                        
                        // Clear highlight option
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, Color.Gray, CircleShape)
                                .clickable {
                                    FirestoreManager.saveUserData(context) {
                                        val updatedHighlights = this.highlights.toMutableMap()
                                        updatedHighlights.remove(noteKey)
                                        this.highlights = updatedHighlights
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("❌", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Notes text field
                    OutlinedTextField(
                        value = currentNoteInput,
                        onValueChange = { currentNoteInput = it },
                        placeholder = { Text("أضف ملاحظتك الخاصة هنا...", color = TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        FirestoreManager.saveUserData(context) {
                            val updatedNotes = this.notes.toMutableMap()
                            if (currentNoteInput.isNotBlank()) {
                                updatedNotes[noteKey] = currentNoteInput
                            } else {
                                updatedNotes.remove(noteKey)
                            }
                            this.notes = updatedNotes
                        }
                        selectedSectionForOptions = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text("حفظ التعديلات", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSectionForOptions = null }) {
                    Text("إلغاء", color = TextGray)
                }
            },
            containerColor = DeepForestCard
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Real-time Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("البحث عن كلمات أو عبارات بالترويج والثراء...", color = TextGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = GoldAccent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextGray)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        if (searchQuery.isNotEmpty()) {
            // Search Results Panel
            Text(
                text = "نتائج البحث (${searchResults.size}):",
                color = GoldAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد تطابقات لهذه العبارة. جرب كلمة أخرى!", color = TextGray, fontSize = 14.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    searchResults.forEach { res ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    handleChapterChange(res.chapterIndex)
                                    searchQuery = "" // Clear search to return to book
                                },
                            colors = CardDefaults.cardColors(containerColor = DeepForestCard),
                            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(res.title, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(res.snippet, color = TextWhite, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else {
            // Reading Customization Toolbar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = DeepForestCard),
                border = BorderStroke(1.dp, DeepForestBorder)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Font Size Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حجم الخط: ",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                            TextButton(
                                onClick = { if (fontScale > 0.8f) onFontScaleChanged(fontScale - 0.1f) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("A-", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { if (fontScale < 1.6f) onFontScaleChanged(fontScale + 0.1f) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("A+", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Day/Night and Bookmark Controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Day/Night Toggler via Emoji Text Button
                            IconButton(
                                onClick = {
                                    FirestoreManager.saveUserData(context) {
                                        this.isDarkMode = !this.isDarkMode
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = if (userBookData.isDarkMode) "☀️" else "🌙",
                                    fontSize = 18.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))

                            // Bookmark Toggle
                            IconButton(
                                onClick = onToggleBookmark,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "حفظ الصفحة",
                                    tint = if (isBookmarked) Color.Red else GoldAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    
                    Divider(color = DeepForestBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                    
                    // Font Color Customization Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("لون الخط للقرائة:", color = TextGray, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val fontColorPresets = listOf(
                                Pair(0, if (userBookData.isDarkMode) Color.White else Color.Black), // Default
                                Pair(1, Color(0xFF5D4037)), // Sepia
                                Pair(2, Color(0xFF1B5E20)), // Sage Green
                                Pair(3, Color(0xFF0D47A1))  // Royal Blue
                            )
                            fontColorPresets.forEach { (index, col) ->
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(col, CircleShape)
                                        .border(
                                            1.5.dp, 
                                            if (userBookData.fontColorIndex == index) GoldAccent else Color.Transparent, 
                                            CircleShape
                                        )
                                        .clickable {
                                            FirestoreManager.saveUserData(context) {
                                                this.fontColorIndex = index
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Book Page Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                // Chapter Title Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmeraldDark.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "الصفحة ${chapter.id} من ${BookData.chapters.size}",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = chapter.title,
                            color = getDynamicFontColor(),
                            fontSize = (22 * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = chapter.titleEnglish,
                            color = TextGray,
                            fontSize = (13 * fontScale).sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Intro Statement
                Text(
                    text = chapter.intro,
                    color = getDynamicFontColor(),
                    fontSize = (15 * fontScale).sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (22 * fontScale).sp,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sections list
                chapter.sections.forEachIndexed { secIdx, sec ->
                    val sectionKey = "${chapter.id}_${secIdx}"
                    val sectionHighlightHex = userBookData.highlights[sectionKey] ?: ""
                    val sectionNote = userBookData.notes[sectionKey] ?: ""
                    
                    // Highlight color parsing
                    val sectionCardBg = if (sectionHighlightHex.isNotEmpty()) {
                        Color(android.graphics.Color.parseColor(sectionHighlightHex)).copy(alpha = 0.15f)
                    } else {
                        DeepForestCard
                    }
                    val sectionCardBorder = if (sectionHighlightHex.isNotEmpty()) {
                        Color(android.graphics.Color.parseColor(sectionHighlightHex))
                    } else {
                        DeepForestBorder
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                currentNoteInput = sectionNote
                                selectedSectionForOptions = secIdx
                            },
                        colors = CardDefaults.cardColors(containerColor = sectionCardBg),
                        border = BorderStroke(1.dp, sectionCardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(GoldAccent.copy(alpha = 0.15f), CircleShape)
                                            .border(1.dp, GoldAccent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val sectionEmoji = when (sec.icon) {
                                            "account_balance_wallet" -> "💸"
                                            "schedule" -> "⏳"
                                            "hourglass_empty" -> "⏰"
                                            "search" -> "🔍"
                                            "swap_horiz" -> "🔄"
                                            "construction" -> "🛠️"
                                            "campaign" -> "📢"
                                            "card_giftcard" -> "🎁"
                                            "track_changes" -> "🎯"
                                            "compare" -> "🆚"
                                            "cached" -> "🔁"
                                            "auto_mode" -> "⚙️"
                                            "workspace_premium" -> "🏆"
                                            "pie_chart" -> "📊"
                                            "transfer_within_a_station" -> "🤝"
                                            "monetization_on" -> "💰"
                                            "timer" -> "⏱️"
                                            "verified_user" -> "🛡️"
                                            "bolt" -> "⚡"
                                            "error_outline" -> "⚠️"
                                            "magic_button" -> "🪄"
                                            "filter_alt" -> "🌪️"
                                            "star" -> "⭐"
                                            "security" -> "🔒"
                                            "calculate" -> "🧮"
                                            "military_tech" -> "🥇"
                                            "inventory_2" -> "📦"
                                            "verified" -> "✅"
                                            "local_shipping" -> "🚚"
                                            "inventory" -> "📥"
                                            "warehouse" -> "🏢"
                                            "support_agent" -> "👥"
                                            "computer" -> "💻"
                                            "list_alt" -> "📋"
                                            "school" -> "💡"
                                            "video_camera_front" -> "🎥"
                                            "groups" -> "🤝"
                                            "add_shopping_cart" -> "🛒"
                                            "insert_chart" -> "📈"
                                            "gavel" -> "⚖️"
                                            "copyright" -> "®️"
                                            "receipt_long" -> "🧾"
                                            "health_and_safety" -> "🚨"
                                            "grid_view" -> "🎛️"
                                            "transform" -> "🚀"
                                            "logout" -> "🚪"
                                            "sell" -> "🏷️"
                                            "looks_one" -> "1️⃣"
                                            "looks_two" -> "2️⃣"
                                            "looks_3" -> "3️⃣"
                                            "emoji_events" -> "🏁"
                                            else -> "💡"
                                        }
                                        Text(text = sectionEmoji, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = sec.title,
                                        color = GoldAccent,
                                        fontSize = (15 * fontScale).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Customize / Note icon button via Emoji
                                Text(
                                    text = if (sectionNote.isNotEmpty()) "📝" else "🎨",
                                    fontSize = 18.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = sec.content,
                                color = getDynamicFontColor(),
                                fontSize = (13 * fontScale).sp,
                                lineHeight = (20 * fontScale).sp,
                                textAlign = TextAlign.Justify
                            )
                            
                            // Displays the customized Post-it style Sticky note below the text
                            if (sectionNote.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                                    border = BorderStroke(1.dp, Color(0xFFFBC02D).copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("📝 ملاحظتك الخاصة:", color = Color(0xFFF57F17), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(sectionNote, color = Color.DarkGray, fontSize = 12.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Previous / Next Chapter Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasPrev = currentChapterIndex > 0
                val hasNext = currentChapterIndex < BookData.chapters.size - 1

                Button(
                    onClick = { handleChapterChange(currentChapterIndex - 1) },
                    enabled = hasPrev,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepForestCard,
                        disabledContainerColor = DeepForestCard.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, if (hasPrev) GoldAccent else DeepForestBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "السابق", tint = if (hasPrev) GoldAccent else TextGray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("السابق", color = if (hasPrev) TextWhite else TextGray, fontSize = 12.sp)
                    }
                }

                Text(
                    text = "${currentChapterIndex + 1} / ${BookData.chapters.size}",
                    color = TextGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { handleChapterChange(currentChapterIndex + 1) },
                    enabled = hasNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepForestCard,
                        disabledContainerColor = DeepForestCard.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, if (hasNext) GoldAccent else DeepForestBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("التالي", color = if (hasNext) TextWhite else TextGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "التالي", tint = if (hasNext) GoldAccent else TextGray)
                    }
                }
            }
        }
    }
}

// --- TAB 3: WEALTH TOOLS SCREEN (QUIZ & CALCULATOR) ---

@Composable
fun WealthToolsTabScreen() {
    var activeSubTab by remember { mutableStateOf("quiz") } // quiz, calculator

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top tools header tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepForestCard, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (activeSubTab == "quiz") GoldAccent else Color.Transparent)
                    .clickable { activeSubTab = "quiz" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "تقييم عقلية الثراء 🧠",
                    color = if (activeSubTab == "quiz") RichObsidian else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (activeSubTab == "calculator") GoldAccent else Color.Transparent)
                    .clickable { activeSubTab = "calculator" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "حاسبة الأرباح والأصول 🧮",
                    color = if (activeSubTab == "calculator") RichObsidian else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sub Tab Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeSubTab == "quiz") {
                MindsetQuizScreen()
            } else {
                WealthCalculatorsScreen()
            }
        }
    }
}

@Composable
fun MindsetQuizScreen() {
    val quizQuestions = remember {
        listOf(
            QuizQuestion(
                text = "جاءتك مكافأة مالية مفاجئة بقيمة 1,000 دولار، ماذا تفعل بها أولاً؟",
                options = listOf(
                    QuizOption("أشتري هاتفاً ذكياً جديداً بالكامل أو ملابس فخمة ماركة (خصوم).", 10),
                    QuizOption("أدخرها في حساب البنك الجاري للطوارئ بدون تشغيلها.", 15),
                    QuizOption("أستثمرها فوراً في بضاعة للتجارة، إعلانات ممولة، أو كورس مالي (أصول).", 30)
                )
            ),
            QuizQuestion(
                text = "تريد شراء سيارة فارهة بالتقسيط مع علمك أنها تستهلك نصف راتبك الشهري:",
                options = listOf(
                    QuizOption("أشتريها فوراً للاستمتاع بالمظهر والوجاهة الاجتماعية أمام الناس.", 5),
                    QuizOption("أنتظر 24 ساعة (قاعدة الكتاب) لأقرر ما إذا كانت رغبة عاطفية أم حاجة حقيقية.", 20),
                    QuizOption("أؤجل الشراء وأركز على شراء أصول ماليّة أولاً تغطي تكلفة السيارة بأرباحها.", 35)
                )
            ),
            QuizQuestion(
                text = "تعرض حسابك الإعلاني للإغلاق المفاجئ وتوقفت مبيعاتك بالكامل، ماذا تفعل؟",
                options = listOf(
                    QuizOption("أشعر باليأس والإحباط وأقرر إيقاف مشروعي فوراً والعودة للوظيفة.", 5),
                    QuizOption("أبحث في جوجل ويوتيوب عن حلول برمجية بسيطة لتفعيله من جديد.", 15),
                    QuizOption("أقوم فوراً بتنويع مصادري؛ أطلق على منصة بديلة وأستثمر في بناء علامة تجارية مستقرة.", 30)
                )
            ),
            QuizQuestion(
                text = "تريد البدء في التجارة الإلكترونية، ولكن ليس لديك أي رأس مال حالياً:",
                options = listOf(
                    QuizOption("أنتظر لسنوات حتى أجمع رأس مال كافٍ أو أحصل على قرض بنكي معقد.", 10),
                    QuizOption("أبدأ ببيع خدمات الوساطة (Drop Servicing) أو التسويق بالعمولة بالاعتماد على مجهودي والمحتوى المجاني.", 30),
                    QuizOption("أستسلم وأقول التجارة تحتاج لرأس مال ضخم ولا يمكنني البدء من الصفر.", 5)
                )
            ),
            QuizQuestion(
                text = "أنت تدير مشروعك بالكامل بمفردك (تغليف، شحن، رد على رسائل، إعلانات):",
                options = listOf(
                    QuizOption("أستمر هكذا لأوفر كل المال لنفسي، مع علمي أنني مستنزف بالكامل.", 10),
                    QuizOption("أخطط فوراً لكتابة نظام تشغيل (SOP) وأوظف خدمة عملاء لتفويض المهام الروتينية وشراء وقتهم.", 30),
                    QuizOption("ليس لدي ثقة في توظيف الآخرين وأفضل إبقاء العمل صغيراً بيدي فقط.", 15)
                )
            )
        )
    }

    var currentQuestionIdx by remember { mutableStateOf(0) }
    var selectedOptionIdx by remember { mutableStateOf(-1) }
    var totalScore by remember { mutableStateOf(0) }
    var quizCompleted by remember { mutableStateOf(false) }

    val handleAnswerSubmit = {
        if (selectedOptionIdx != -1) {
            totalScore += quizQuestions[currentQuestionIdx].options[selectedOptionIdx].score
            if (currentQuestionIdx < quizQuestions.size - 1) {
                currentQuestionIdx++
                selectedOptionIdx = -1
            } else {
                quizCompleted = true
            }
        }
    }

    if (!quizCompleted) {
        val q = quizQuestions[currentQuestionIdx]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = "السؤال ${currentQuestionIdx + 1} من ${quizQuestions.size}",
                color = GoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Progress Bar
            LinearProgressIndicator(
                progress = { (currentQuestionIdx + 1).toFloat() / quizQuestions.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = EmeraldPrimary,
                trackColor = DeepForestBorder
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Question Text
            Text(
                text = q.text,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Options List
            q.options.forEachIndexed { idx, option ->
                val isSelected = selectedOptionIdx == idx
                val borderCol = if (isSelected) GoldAccent else DeepForestBorder
                val bgCol = if (isSelected) GoldAccent.copy(alpha = 0.15f) else DeepForestCard

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOptionIdx = idx },
                    colors = CardDefaults.cardColors(containerColor = bgCol),
                    border = BorderStroke(1.dp, borderCol)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(2.dp, if (isSelected) GoldAccent else TextGray, CircleShape)
                                .background(if (isSelected) GoldAccent else Color.Transparent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option.text,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Submit Button
            Button(
                onClick = handleAnswerSubmit,
                enabled = selectedOptionIdx != -1,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    disabledContainerColor = TextGray.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = if (currentQuestionIdx == quizQuestions.size - 1) "عرض النتيجة النهائية" else "تأكيد الإجابة والذهاب للتالي",
                    color = RichObsidian,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        // Quiz Results Screen
        val maxPossibleScore = 155
        val finalPercentage = ((totalScore.toFloat() / maxPossibleScore) * 100).toInt().coerceAtMost(100)

        val resultTitle: String
        val resultEmoji: String
        val resultDesc: String
        val adviceQuote: String

        when {
            finalPercentage >= 85 -> {
                resultTitle = "عقلية ثراء احترافية ومستقلة 🏆"
                resultEmoji = "💰📈"
                resultDesc = "تهانينا! أنت تفكر كالأثرياء تماماً وتدرك الفرق بين الأصول والخصوم، ولديك عقلية استباقية ممتازة لحل المشاكل وتفويض المهام وبناء البراند."
                adviceQuote = "نصيحة الكتاب: أنت مستعد تماماً! ابدأ فوراً بصناعة منتج تجريبي مبسط (MVP) وأطلق أول إعلان لتجربة السوق الحقيقي."
            }
            finalPercentage in 55..84 -> {
                resultTitle = "عقلية تجارية ذكية قيد النمو ⚖️"
                resultEmoji = "💡📊"
                resultDesc = "أنت تمتلك وعياً مالياً جيداً وتتخذ قرارات جيدة، ولكن لا يزال ينقصك بعض الجرأة والخطوات العملية والتفويض لتكبير مشروعك والتخلص من شراء بعض الخصوم."
                adviceQuote = "نصيحة الكتاب: ركز على أتمتة الأنظمة وفصل ذمتك المالية، ولا تخف من البدء بالوساطة والخدمات بدون رأس مال."
            }
            else -> {
                resultTitle = "عقلية استهلاكية تحتاج إلى إعادة برمجة 🧠"
                resultEmoji = "💸⚠️"
                resultDesc = "أنت تقع كثيراً في فخ الخصوم والمظاهر الاجتماعية، وتبيع وقتك مقابل راتب ثابت دون التفكير في مشاريع تدر لك المال أوتوماتيكياً."
                adviceQuote = "نصيحة الكتاب: توقف فوراً! طبق قاعدة الـ 24 ساعة قبل أي شراء عاطفي، واستثمر جزءاً من مالك في عقول تفيدك وتجارة أصول."
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "مستوى عقلية الثراء لديك",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Circular progress display
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(DeepForestCard, CircleShape)
                    .border(3.dp, GoldAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$finalPercentage%",
                        color = GoldAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "طاقة مالية",
                        color = TextGray,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                text = resultEmoji,
                fontSize = 32.sp
            )

            Text(
                text = resultTitle,
                color = GoldAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = resultDesc,
                color = TextWhite,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EmeraldDark.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = adviceQuote,
                        color = TextGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = {
                    currentQuestionIdx = 0
                    selectedOptionIdx = -1
                    totalScore = 0
                    quizCompleted = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
            ) {
                Text(
                    text = "أعد التقييم وتحدّ نفسك 🔁",
                    color = RichObsidian,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class QuizQuestion(val text: String, val options: List<QuizOption>)
data class QuizOption(val text: String, val score: Int)

@Composable
fun WealthCalculatorsScreen() {
    var sellPrice by remember { mutableStateOf("") }
    var buyCost by remember { mutableStateOf("") }
    var adCost by remember { mutableStateOf("") }
    var shippingCost by remember { mutableStateOf("") }

    // Computations
    val numSell = sellPrice.toFloatOrNull() ?: 0f
    val numBuy = buyCost.toFloatOrNull() ?: 0f
    val numAd = adCost.toFloatOrNull() ?: 0f
    val numShip = shippingCost.toFloatOrNull() ?: 0f

    val totalCosts = numBuy + numAd + numShip
    val netProfit = numSell - totalCosts
    val profitMargin = if (numSell > 0) (netProfit / numSell) * 100 else 0f
    val roi = if (totalCosts > 0) (netProfit / totalCosts) * 100 else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "💸 حاسبة صافي الربح والعائد على الاستثمار",
            color = GoldAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "تطبيقاً لمعادلات الكتاب المالية الدقيقة (صفحة 10 و 15):",
            color = TextGray,
            fontSize = 11.sp
        )

        // Fields Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DeepForestCard),
            border = BorderStroke(1.dp, DeepForestBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalculatorTextField(value = sellPrice, onValueChange = { sellPrice = it }, label = "سعر البيع المقترح للمنتج ($)")
                CalculatorTextField(value = buyCost, onValueChange = { buyCost = it }, label = "سعر الشراء من المورد المادي ($)")
                CalculatorTextField(value = adCost, onValueChange = { adCost = it }, label = "تكلفة الإعلان والترويج لكل مبيعة ($)")
                CalculatorTextField(value = shippingCost, onValueChange = { shippingCost = it }, label = "تكلفة التوصيل والتغليف والجمارك ($)")
            }
        }

        // Live Results Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RichObsidian),
            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("إجمالي التكاليف البسيطة:", color = TextGray, fontSize = 13.sp)
                    Text("$${String.format("%.1f", totalCosts)}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("صافي الربح الفعلي المباشر:", color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("$${String.format("%.1f", netProfit)}", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("هامش الربح التجاري (%):", color = TextGray, fontSize = 13.sp)
                    Text("${String.format("%.1f", profitMargin)}%", color = if (profitMargin >= 30) EmeraldPrimary else Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("العائد على الاستثمار (ROI %):", color = TextGray, fontSize = 13.sp)
                    Text("${String.format("%.1f", roi)}%", color = if (roi >= 30) EmeraldPrimary else Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Actionable Book Advice
        if (numSell > 0) {
            val adviceText: String
            val adviceBorderColor: Color

            if (profitMargin >= 30f && roi >= 30f) {
                adviceText = "✅ مؤشرات ممتازة ومثالية جداً! هامش الربح والعائد أعلى من 30% كما توصي صفحة 15 من الكتاب. ابدأ فوراً بضخ الإعلانات وتوسيع التجارة بشكل رأسي وأفقي مضاعف!"
                adviceBorderColor = EmeraldPrimary
            } else if (netProfit > 0) {
                adviceText = "⚠️ المشروع مربح ولكن الهوامش ضيقة وخطرة. حاول خفض تكلفة الشراء بالاستيراد المباشر من علي بابا (صفحة 11) أو تقليل التخزين عن طريق التخزين المنزلي (صفحة 12)، أو جرب إعلانات أخرى لرفع معدل التحويل."
                adviceBorderColor = GoldAccent
            } else {
                adviceText = "❌ التجارة خاسرة تماماً ومكلفة للغاية! التكاليف تفوق سعر البيع. تذكر المبدأ المالي بصفحة 10: لا تستعجل شراء البضاعة العشوائية، أعد دراسة السوق والتوريد بالكامل!"
                adviceBorderColor = Color.Red
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepForestCard),
                border = BorderStroke(1.dp, adviceBorderColor)
            ) {
                Text(
                    text = adviceText,
                    color = TextWhite,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun CalculatorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' }) },
        label = { Text(label, color = TextGray, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldAccent,
            unfocusedBorderColor = DeepForestBorder,
            focusedContainerColor = RichObsidian,
            unfocusedContainerColor = RichObsidian
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// --- TAB 4: AI WEALTH COACH CHAT SCREEN ---

@Composable
fun AiCoachTabScreen() {
    val coroutineScope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    
    // Chat Message History List
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage("🤖", "أهلاً بك يا شريك المستقبل الثري! أنا مستشارك التجاري الذكي المالي والمبني على ذكاء اصطناعي وتطبيقاً لكتاب طريقك نحو الثراء.\n\nكيف يمكنني مساعدتك في التخطيط وبناء الأرباح والتجارة الإلكترونية اليوم؟", false)
        )
    }
    
    var isGenerating by remember { mutableStateOf(false) }

    val handleSendMessage: (String) -> Unit = { userText ->
        if (userText.trim().isNotEmpty() && !isGenerating) {
            chatMessages.add(ChatMessage("👤", userText, true))
            prompt = ""
            isGenerating = true
            
            coroutineScope.launch {
                val response = GeminiClient.askCoach(userText)
                
                if (response == "API_KEY_MISSING") {
                    chatMessages.add(
                        ChatMessage(
                            "🤖",
                            "⚠️ مفتاح الذكاء الاصطناعي (Gemini API Key) غير مهيأ حالياً.\n\nلتفعيل المستشار الذكي، يرجى كتابة وتنشيط مفتاحك داخل لوحة (Secrets panel) في واجهة Google AI Studio بوضع المفتاح بقيمة 'GEMINI_API_KEY'.",
                            false
                        )
                    )
                } else {
                    chatMessages.add(ChatMessage("🤖", response, false))
                }
                isGenerating = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // AI Header Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EmeraldDark.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(GoldAccent.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, GoldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🤖", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "مستشار الثراء الذكي",
                        color = GoldAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اسأل عن دروب سيرفيسينج، التسويق بالعمولة، والتخطيط المالي",
                        color = TextGray,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chats History Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DeepForestCard.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .border(1.dp, DeepForestBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            val listScrollState = rememberScrollState()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(listScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chatMessages.forEach { msg ->
                    ChatBubble(msg = msg)
                }
                
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(24.dp))
                    }
                }
                
                // Auto scroll down
                LaunchedEffect(chatMessages.size, isGenerating) {
                    listScrollState.animateScrollTo(listScrollState.maxValue)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic Suggestion Buttons
        val suggestions = listOf(
            "كيف أبدأ دروب سيرفيسينج؟ 🤝",
            "ما هي قاعدة الـ 80/20؟ 📊",
            "كيف أطبق معادلة الجذب؟ 📢"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { sug ->
                Box(
                    modifier = Modifier
                        .background(DeepForestCard, RoundedCornerShape(16.dp))
                        .border(1.dp, DeepForestBorder, RoundedCornerShape(16.dp))
                        .clickable { handleSendMessage(sug.substringBeforeLast(" ")) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(sug, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Input Fields Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text("اطرح سؤالك المالي هنا...", color = TextGray, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = DeepForestBorder,
                    focusedContainerColor = DeepForestCard,
                    unfocusedContainerColor = DeepForestCard
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { handleSendMessage(prompt) },
                enabled = prompt.trim().isNotEmpty() && !isGenerating,
                modifier = Modifier
                    .size(48.dp)
                    .background(if (prompt.trim().isNotEmpty() && !isGenerating) GoldAccent else DeepForestBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "إرسال",
                    tint = if (prompt.trim().isNotEmpty() && !isGenerating) RichObsidian else TextGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.isUser) Alignment.End else Alignment.Start
    val bgModifier = if (msg.isUser) {
        Modifier.background(GoldAccent, RoundedCornerShape(12.dp))
    } else {
        Modifier.background(DeepForestCard, RoundedCornerShape(12.dp))
    }
    val textColor = if (msg.isUser) Color.White else TextWhite
    val borderCol = if (msg.isUser) Color.Transparent else DeepForestBorder

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!msg.isUser) {
                Text(msg.sender, fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp, top = 2.dp))
            }
            Box(
                modifier = Modifier
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .then(bgModifier)
                    .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = msg.text,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Start
                )
            }
            if (msg.isUser) {
                Text(msg.sender, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }
        }
    }
}

data class ChatMessage(val sender: String, val text: String, val isUser: Boolean)
