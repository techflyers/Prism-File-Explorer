package com.techflyers.compose.file.explorer.screen.main.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

private data class OnboardingSlide(
    val icon: ImageVector,
    val accentStart: Color,
    val accentEnd: Color,
    val title: String,
    val body: String,
    /** Optional bullet list items shown below the body */
    val bullets: List<String> = emptyList()
)

// ---------------------------------------------------------------------------
// Public entry-point
// ---------------------------------------------------------------------------

/**
 * Full-screen onboarding carousel shown on the very first install.
 * Call [onFinish] when the user taps "Get Started" or "Skip".
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val slides = rememberSlides()
    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Gradient backdrop that animates with the page ──────────────────
        val currentSlide = slides[pagerState.currentPage]
        val gradientBrush = Brush.radialGradient(
            colors = listOf(
                currentSlide.accentStart.copy(alpha = 0.22f),
                currentSlide.accentEnd.copy(alpha = 0.06f),
                Color.Transparent
            ),
            radius = 1200f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Skip button (top-right) ─────────────────────────────────────
            val isLastPage = pagerState.currentPage == slides.size - 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                val skipAlpha by animateFloatAsState(
                    targetValue = if (isLastPage) 0f else 1f,
                    animationSpec = tween(220),
                    label = "skip_alpha"
                )
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.alpha(skipAlpha),
                    enabled = !isLastPage
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Pager ───────────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 0.dp
            ) { page ->
                SlideContent(slide = slides[page], isActive = page == pagerState.currentPage)
            }

            // ── Dots + navigation ───────────────────────────────────────────
            BottomNavBar(
                pagerState = pagerState,
                totalPages = slides.size,
                isLastPage = isLastPage,
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onFinish = onFinish
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Slide content
// ---------------------------------------------------------------------------

@Composable
private fun SlideContent(slide: OnboardingSlide, isActive: Boolean) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (isActive) {
            delay(80)
            visible = true
        } else {
            visible = false
        }
    }

    val iconScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "content_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon container with gradient background
        Box(
            modifier = Modifier
                .size(128.dp)
                .scale(iconScale)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(slide.accentStart.copy(alpha = 0.25f), slide.accentEnd.copy(alpha = 0.15f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = slide.icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = slide.accentStart
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = slide.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
            modifier = Modifier.alpha(contentAlpha)
        )

        if (slide.bullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            BulletList(
                bullets = slide.bullets,
                accentColor = slide.accentStart,
                alpha = contentAlpha
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// Bullet list
// ---------------------------------------------------------------------------

@Composable
private fun BulletList(bullets: List<String>, accentColor: Color, alpha: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .background(color = accentColor, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom navigation bar (dots + Next / Get Started)
// ---------------------------------------------------------------------------

@Composable
private fun BottomNavBar(
    pagerState: PagerState,
    totalPages: Int,
    isLastPage: Boolean,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Dot indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { index ->
                val isSelected = index == pagerState.currentPage
                val dotWidth: Dp by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 8.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "dot_width_$index"
                )
                val dotAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.35f,
                    label = "dot_alpha_$index"
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .alpha(dotAlpha)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // Next / Get Started button
        AnimatedContent(
            targetState = isLastPage,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally { it / 2 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally { -it / 2 })
            },
            label = "next_button"
        ) { last ->
            Button(
                onClick = if (last) onFinish else onNext,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = if (last) "Get Started" else "Next",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Slide definitions — updated per user feedback
// ---------------------------------------------------------------------------

@Composable
private fun rememberSlides(): List<OnboardingSlide> {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error

    // Fixed colour pairs used as gradient accents per slide
    val purple = Color(0xFF9C27B0)
    val blue = Color(0xFF1565C0)
    val teal = Color(0xFF00897B)
    val orange = Color(0xFFE65100)
    val green = Color(0xFF2E7D32)
    val pink = Color(0xFFAD1457)
    val indigo = Color(0xFF283593)
    val amber = Color(0xFFF57F17)

    return remember {
        listOf(
            // 0 – Welcome
            OnboardingSlide(
                icon = Icons.Outlined.FolderOpen,
                accentStart = primary,
                accentEnd = secondary,
                title = "Welcome to Prism File Explorer +",
                body = "A modern, feature-rich file manager built entirely with Kotlin and Jetpack Compose. " +
                        "Explore, manage, and share your files beautifully."
            ),

            // 1 – File Management & Tasks
            OnboardingSlide(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                accentStart = blue,
                accentEnd = teal,
                title = "Powerful File Management",
                body = "Everything you need in one place, with an intelligent task queue unlike any other file manager.",
                bullets = listOf(
                    "Multi-source clipboard — copy files from different folders into one operation",
                    "Background task queue with pause, resume, retry and conflict resolution",
                    "Smart Move: detects same-filesystem moves and uses atomic rename",
                    "Bulk rename with pattern support",
                    "Recycle Bin with full path restore via metadata sidecar",
                    "Batch select across an entire directory tree",
                    "Privileged access via Shizuku or root for system folders"
                )
            ),

            // 2 – Save to Prism
            OnboardingSlide(
                icon = Icons.Outlined.Share,
                accentStart = pink,
                accentEnd = purple,
                title = "Save to Prism",
                body = "Can't save a file from another app to local storage? Just share it to Prism.",
                bullets = listOf(
                    "Accept any file shared from any app via the Android Share sheet",
                    "Browse your storage and drop the file exactly where you want it",
                    "Works with single files, multiple files, and shared text snippets",
                    "No workarounds or root needed — uses standard Android intents"
                )
            ),

            // 3 – Built-in Viewers & Editors
            OnboardingSlide(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                accentStart = teal,
                accentEnd = green,
                title = "Premium Built-in Viewers",
                body = "Open almost any file without leaving the app. No third-party apps required.",
                bullets = listOf(
                    "Jetpack PDF viewer — native rendering, text selection, search, highlights",
                    "Excel (.xls/.xlsx) spreadsheet viewer with coordinate headers",
                    "Office documents (Word / PowerPoint) — layout via Alldocumentsreader (limited fidelity)",
                    "E-Book reader: EPUB, MOBI, AZW, FB2, RTF",
                    "Comic viewer: CBZ, CBR, CB7, CBT",
                    "Image viewer + merger (stitch images vertically/horizontally)",
                    "Audio player with directory scanning and track queue",
                    "Video player with PiP, speed control, gesture brightness/volume",
                    "Markdown, HTML live preview, LaTeX offline compiler (Tectonic)"
                )
            ),

            // 4 – Archive Manager
            OnboardingSlide(
                icon = Icons.Outlined.Archive,
                accentStart = orange,
                accentEnd = amber,
                title = "Comprehensive Archive Manager",
                body = "Extract and create archives in a huge range of formats, powered by the lib7za native binary.",
                bullets = listOf(
                    "Extract: ZIP, 7z, RAR, TAR, GZ, BZ2, XZ, ZST, LZ4, ISO, DMG, MSI, CAB, ARJ and more",
                    "Compound archives: .tar.gz, .tar.xz, .tar.bz2, .tar.zst, .tgz, .tbz2, …",
                    "Create: ZIP, 7z, TAR and tar-compressed variants",
                    "AES-256 encryption when creating ZIP / 7z archives",
                    "In-place edit: add, delete, rename files inside ZIP / 7z / TAR",
                    "Browse encrypted archives with password entry"
                )
            ),

            // 5 – Servers & Remote
            OnboardingSlide(
                icon = Icons.Outlined.Hub,
                accentStart = indigo,
                accentEnd = blue,
                title = "Servers & Remote Connections",
                body = "Turn your phone into a file server or connect to remote storages — all from within the app.",
                bullets = listOf(
                    "Local FTP Server with persistent foreground service",
                    "Web Sharing Portal: directory listing, media streaming, remote uploads",
                    "Public tunnels via SSH port-forwarding to localhost.run",
                    "Remote Explorer: FTP, SFTP, WebDAV, SMB/LAN — browse, download, upload"
                )
            ),

            // 6 – Terminal & AI
            OnboardingSlide(
                icon = Icons.Outlined.Terminal,
                accentStart = green,
                accentEnd = teal,
                title = "Terminal & Smart Search",
                body = "Built-in terminal emulator and AI-powered file search, all on-device.",
                bullets = listOf(
                    "Full terminal emulator with session persistence",
                    "Ubuntu/PRoot container provisioning for a Linux environment",
                    "Run scripts directly from the built-in text editor",
                    "Split APK bundle installation (.apks, .xapk, .apkm)",
                    "AI Semantic Search with OCR support and similarity scores"
                )
            ),

            // 7 – Permissions & Privacy
            OnboardingSlide(
                icon = Icons.Outlined.Security,
                accentStart = green,
                accentEnd = teal,
                title = "Privacy & Permissions",
                body = "Prism is open-source (GPLv3). Here's exactly why each permission and component is needed:",
                bullets = listOf(
                    "MANAGE_EXTERNAL_STORAGE — full file access for a file manager",
                    "FOREGROUND_SERVICE / DATA_SYNC — background copy/move tasks",
                    "FOREGROUND_SERVICE_SPECIAL_USE — required by the terminal service on Android 14+",
                    "ACCESS_WIFI_STATE — read local IP address for FTP/Web-Sharing server",
                    "CHANGE_WIFI_MULTICAST_STATE — mDNS discovery for SMB/LAN connections",
                    "WAKE_LOCK — keeps terminal sessions alive while screen is off",
                    "moe.shizuku.manager.permission.API_V23 — bind to Shizuku for privileged file access",
                    "MlKitComponentDiscoveryService / MlKitInitProvider — on-device AI Semantic Search; no data leaves your device"
                )
            )
        )
    }
}
