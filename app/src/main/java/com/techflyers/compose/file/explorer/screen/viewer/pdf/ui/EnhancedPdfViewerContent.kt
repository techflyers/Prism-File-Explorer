package com.techflyers.compose.file.explorer.screen.viewer.pdf.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.fragment.compose.AndroidFragment
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.view.PdfView
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfTextServiceImpl
import com.techflyers.compose.file.explorer.screen.viewer.document.ui.DocumentAiSheet
import com.techflyers.compose.file.explorer.screen.viewer.document.ui.DocumentSelectionToolbar
import com.techflyers.compose.file.explorer.screen.viewer.document.ui.DocumentTopBar
import com.techflyers.compose.file.explorer.screen.viewer.pdf.CustomPdfViewerFragment
import com.techflyers.compose.file.explorer.screen.viewer.pdf.misc.DocumentShareHelper
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.AnnotationType
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfAnnotationItem
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfOperations
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfStrokeItem
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfStrokePoint
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog.CompressPdfDialog
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog.ConvertPdfToImagesDialog
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog.OrganizePagesDialog
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog.SavePdfDialog
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog.SignaturePadDialog
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog.SignatureStroke
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog.WatermarkPdfDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val DarkInvertMatrix = ColorMatrix(
    floatArrayOf(
        -0.85f, 0f, 0f, 0f, 240f,
        0f, -0.85f, 0f, 0f, 240f,
        0f, 0f, -0.85f, 0f, 240f,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * Ultra-fast, hardware-accelerated PDF and converted document viewer.
 *
 * Backed by Google's native AndroidX [CustomPdfViewerFragment] for:
 *  - 120Hz smooth fling scrolling
 *  - Deep multi-level tile zoom with zero pixelation or memory bloat
 *  - Native text selection handles with magnifier and copy action mode
 *  - Native fast scrollbar that appears on scroll and auto-hides
 *
 * Enhanced with ClearPDF / Prism Explorer features:
 *  - Real text annotations: Highlight (multi-color), Underline, Strikethrough
 *  - Document Save dialog: Overwrite, Save as Copy, Discard, and Dismiss
 *  - Advanced PDF tools: Compress PDF, Convert to Images, Organize Pages, Watermark PDF
 *  - Modern Material 3 TopBar with page chip, search, Ask AI, and overflow menu
 *  - Disabled auto-hide during menu or dialog interaction
 */
@OptIn(ExperimentalPdfApi::class)
@Composable
fun EnhancedPdfViewerContent(
    documentUri: Uri,
    fileName: String,
    infoProperties: List<Pair<String, String>> = emptyList(),
    isOfficeConverted: Boolean = false,
    originalSourceUri: Uri? = null,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showToolbars by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }

    // Page tracking
    var currentPage by remember { mutableIntStateOf(0) } // 0-based
    var totalPages by remember { mutableIntStateOf(0) }

    // User interaction state
    var gestureState by remember { mutableIntStateOf(PdfView.GESTURE_STATE_IDLE) }
    var interactionCount by remember { mutableIntStateOf(0) }
    var showIndicator by remember { mutableStateOf(false) }
    var isMenuOpen by remember { mutableStateOf(false) }

    var pdfFragment by remember { mutableStateOf<CustomPdfViewerFragment?>(null) }
    var documentLoaded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf<String?>(null) }
    var currentSelectionBounds by remember { mutableStateOf<List<androidx.pdf.PdfRect>>(emptyList()) }

    // Working file & editing state
    var currentWorkingUri by remember { mutableStateOf(documentUri) }
    var workingPdfFile by remember { mutableStateOf<File?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    // Dialog states
    var showSaveDialog by remember { mutableStateOf(false) }
    var isExitAfterSave by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showConvertToImagesDialog by remember { mutableStateOf(false) }
    var showOrganizeDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }

    // Text selection anchor point for in-place floating toolbar
    var selectionAnchorPoint by remember { mutableStateOf<Offset?>(null) }

    // Freehand drawing / annotation state
    var isAnnotating by remember { mutableStateOf(false) }
    var isEraserMode by remember { mutableStateOf(false) }
    var penColor by remember { mutableStateOf(Color(0xFF141414)) }
    var penStrokeWidth by remember { mutableFloatStateOf(4f) }
    val activeDrawnStrokes = remember { mutableStateListOf<SignatureStroke>() }
    var currentDrawingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Interactive Stamp Placement / Free-Transform state (for both images and signatures)
    var pendingStamp by remember { mutableStateOf<PendingStamp?>(null) }
    var isTransformScrollLocked by remember { mutableStateOf(true) }

    LaunchedEffect(pendingStamp, isTransformScrollLocked, isAnnotating) {
        pdfFragment?.isScrollLocked = if (pendingStamp != null) isTransformScrollLocked else isAnnotating
    }

    suspend fun getOrCreateWorkingFile(): File = withContext(Dispatchers.IO) {
        workingPdfFile?.takeIf { it.exists() && it.length() > 0 } ?: run {
            val file = File(context.cacheDir, "pdf_working_${System.currentTimeMillis()}_${fileName.substringBeforeLast('.')}.pdf")
            context.contentResolver.openInputStream(currentWorkingUri)?.use { input ->
                FileOutputStream(file).use { out ->
                    input.copyTo(out)
                }
            }
            workingPdfFile = file
            file
        }
    }

    // Image stamp picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                val densityVal = context.resources.displayMetrics.density
                                val initialW = 200f * densityVal
                                val initialH = (initialW * (bmp.height.toFloat() / bmp.width)).coerceIn(50f * densityVal, 350f * densityVal)
                                val screenW = context.resources.displayMetrics.widthPixels.toFloat()
                                val screenH = context.resources.displayMetrics.heightPixels.toFloat()
                                val initX = ((screenW - initialW) / 2f).coerceAtLeast(16f * densityVal)
                                val initY = ((screenH - initialH) / 2f).coerceAtLeast(80f * densityVal)
                                isTransformScrollLocked = true
                                pendingStamp = PendingStamp(
                                    bitmap = bmp,
                                    isSignature = false,
                                    screenX = initX,
                                    screenY = initY,
                                    width = initialW,
                                    height = initialH,
                                    rotation = 0f
                                )
                            }
                        }
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load image: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val isAnyDialogOpen = showInfoDialog || showGoToPageDialog || showAiSheet ||
            showSaveDialog || showCompressDialog || showConvertToImagesDialog ||
            showOrganizeDialog || showWatermarkDialog || showSignatureDialog || isAnnotating || (pendingStamp != null)

    // Pre-create working file copy in background for instant edits
    LaunchedEffect(documentUri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val temp = File(context.cacheDir, "pdf_working_${System.currentTimeMillis()}_${fileName.substringBeforeLast('.')}.pdf")
                context.contentResolver.openInputStream(documentUri)?.use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                }
                if (temp.exists() && temp.length() > 0) {
                    workingPdfFile = temp
                }
            }
        }
    }

    // Auto-hide control bars after 3.2 seconds of inactivity (disabled when menu or dialog is open)
    LaunchedEffect(showToolbars, gestureState, interactionCount, isMenuOpen, isAnyDialogOpen) {
        if (showToolbars && !isMenuOpen && !isAnyDialogOpen && gestureState == PdfView.GESTURE_STATE_IDLE) {
            delay(3200)
            showToolbars = false
        }
    }

    // Auto-hide floating page indicator pill after 2.5 seconds
    LaunchedEffect(showIndicator, gestureState) {
        if (showIndicator && gestureState == PdfView.GESTURE_STATE_IDLE) {
            delay(2500)
            showIndicator = false
        }
    }

    // Unified back handling supporting all dialogs, annotation modes, stamp transform, and unsaved edits
    val handleBack: () -> Unit = {
        when {
            pendingStamp != null -> {
                pendingStamp = null
            }
            isAnnotating -> {
                activeDrawnStrokes.clear()
                currentDrawingPoints = emptyList()
                isAnnotating = false
                isEraserMode = false
            }
            selectedText != null || currentSelectionBounds.isNotEmpty() -> {
                pdfFragment?.clearSelection()
                selectedText = null
                currentSelectionBounds = emptyList()
                selectionAnchorPoint = null
            }
            isSearchActive -> {
                isSearchActive = false
            }
            showSaveDialog -> {
                if (isExitAfterSave) {
                    hasUnsavedChanges = false
                    runCatching { workingPdfFile?.delete() }
                    workingPdfFile = null
                    showSaveDialog = false
                    isExitAfterSave = false
                    onBackPress()
                } else {
                    showSaveDialog = false
                    isExitAfterSave = false
                }
            }
            showInfoDialog -> showInfoDialog = false
            showGoToPageDialog -> showGoToPageDialog = false
            showAiSheet -> showAiSheet = false
            showCompressDialog -> showCompressDialog = false
            showConvertToImagesDialog -> showConvertToImagesDialog = false
            showOrganizeDialog -> showOrganizeDialog = false
            showWatermarkDialog -> showWatermarkDialog = false
            showSignatureDialog -> showSignatureDialog = false
            hasUnsavedChanges -> {
                isExitAfterSave = true
                showSaveDialog = true
            }
            else -> onBackPress()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    // AI text extraction cache
    val textService = remember { PdfTextServiceImpl() }
    var fullDocumentText by remember { mutableStateOf<String?>(null) }
    var pageTextsList by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var isExtractingAiText by remember { mutableStateOf(false) }

    // Sync search state with native fragment Find bar
    LaunchedEffect(isSearchActive, pdfFragment) {
        pdfFragment?.isTextSearchActive = isSearchActive
    }

    // Lazy AI text extraction when Ask AI is opened
    LaunchedEffect(showAiSheet) {
        if (showAiSheet && fullDocumentText.isNullOrBlank() && !isExtractingAiText) {
            isExtractingAiText = true
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val pCount = totalPages.coerceAtLeast(1)
                    val pList = mutableListOf<Pair<Int, String>>()
                    val sb = StringBuilder()
                    for (p in 0 until minOf(pCount, 60)) {
                        val blocks = textService.extractPage(context, currentWorkingUri, p)
                        val text = blocks.joinToString(" ") { it.text }
                        if (text.isNotBlank()) {
                            pList.add(p to text)
                            sb.append(text).append("\n\n")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        pageTextsList = pList
                        fullDocumentText = sb.toString().ifBlank { "No readable text found in document." }
                        isExtractingAiText = false
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        isExtractingAiText = false
                        fullDocumentText = "Could not extract document text: ${it.message}"
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showInfoDialog) {
        val props = if (infoProperties.isNotEmpty()) infoProperties else listOf(
            "File Name" to fileName,
            "Total Pages" to totalPages.toString(),
            "Renderer" to if (isOfficeConverted) "LibreOffice + AndroidX Native PDF" else "Google AndroidX Native PDF",
            "Path" to (documentUri.path ?: documentUri.toString())
        )
        InfoDialog(
            title = globalClass.getString(R.string.pdf_info),
            properties = props,
            onDismiss = { showInfoDialog = false }
        )
    }

    if (showGoToPageDialog && totalPages > 0) {
        GoToPageDialog(
            currentPage = currentPage + 1,
            totalPages = totalPages,
            onDismiss = { showGoToPageDialog = false },
            onConfirm = { page ->
                showGoToPageDialog = false
                pdfFragment?.scrollToPage(page - 1)
                currentPage = page - 1
                showToolbars = true
                interactionCount++
            }
        )
    }

    if (showAiSheet) {
        DocumentAiSheet(
            visible = showAiSheet,
            documentTitle = fileName,
            fullText = fullDocumentText ?: if (isExtractingAiText) "Extracting document text…" else "Ready to analyze document.",
            pagesText = pageTextsList,
            selectedText = selectedText,
            onDismiss = { showAiSheet = false }
        )
    }

    val authenticBaseName = remember(fileName) { fileName.substringBeforeLast('.') }
    val authenticPdfName = remember(authenticBaseName) { "$authenticBaseName.pdf" }
    val effectiveSourceUri = remember(originalSourceUri, documentUri) { originalSourceUri ?: documentUri }

    if (showSaveDialog) {
        val canOverwrite = !isOfficeConverted && run {
            val localFile = DocumentShareHelper.resolveLocalFile(context, effectiveSourceUri)
            (localFile != null && localFile.canWrite()) || effectiveSourceUri.scheme == "file"
        }
        SavePdfDialog(
            visible = showSaveDialog,
            originalFileName = fileName,
            canOverwrite = canOverwrite,
            isOfficeConverted = isOfficeConverted,
            isUnsavedExit = isExitAfterSave,
            onOverwrite = {
                showSaveDialog = false
                scope.launch(Dispatchers.IO) {
                    val srcFile = workingPdfFile
                    if (srcFile != null && srcFile.exists()) {
                        try {
                            val localFile = DocumentShareHelper.resolveLocalFile(context, effectiveSourceUri)
                            val success = if (localFile != null && localFile.exists() && localFile.canWrite()) {
                                srcFile.copyTo(localFile, overwrite = true)
                                MediaScannerConnection.scanFile(context, arrayOf(localFile.absolutePath), arrayOf("application/pdf"), null)
                                true
                            } else {
                                val out = context.contentResolver.openOutputStream(effectiveSourceUri, "rwt")
                                    ?: context.contentResolver.openOutputStream(effectiveSourceUri, "wt")
                                out?.use { stream ->
                                    srcFile.inputStream().use { input -> input.copyTo(stream) }
                                    true
                                } ?: false
                            }

                            withContext(Dispatchers.Main) {
                                if (success) {
                                    hasUnsavedChanges = false
                                    Toast.makeText(context, "Saved changes successfully", Toast.LENGTH_SHORT).show()
                                    if (isExitAfterSave) onBackPress()
                                } else {
                                    Toast.makeText(context, "Cannot overwrite original; please save as copy", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onSaveCopy = { newFileName ->
                showSaveDialog = false
                scope.launch(Dispatchers.IO) {
                    val srcFile = workingPdfFile
                    if (srcFile != null && srcFile.exists()) {
                        try {
                            val targetDir = DocumentShareHelper.resolveTargetDirectory(context, effectiveSourceUri)
                            val cleanName = if (newFileName.endsWith(".pdf", ignoreCase = true)) newFileName.trim() else "${newFileName.trim()}.pdf"
                            val destFile = File(targetDir, cleanName)
                            srcFile.copyTo(destFile, overwrite = true)
                            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("application/pdf"), null)
                            withContext(Dispatchers.Main) {
                                hasUnsavedChanges = false
                                Toast.makeText(context, "Saved copy to ${destFile.parentFile?.name ?: "folder"}: $cleanName", Toast.LENGTH_LONG).show()
                                if (isExitAfterSave) onBackPress()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Save copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onShare = {
                showSaveDialog = false
                val f = workingPdfFile
                val targetUri = if (f != null && f.exists()) Uri.fromFile(f) else currentWorkingUri
                DocumentShareHelper.shareDocument(
                    context = context,
                    uri = targetUri,
                    title = globalClass.getString(R.string.share),
                    desiredFileName = authenticPdfName
                )
            },
            onDiscard = {
                showSaveDialog = false
                hasUnsavedChanges = false
                runCatching { workingPdfFile?.delete() }
                workingPdfFile = null
                if (isExitAfterSave) {
                    onBackPress()
                } else {
                    currentWorkingUri = documentUri
                    pdfFragment?.documentUri = documentUri
                    pdfFragment?.clearAllMarkups()
                    Toast.makeText(context, "Changes discarded", Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = {
                showSaveDialog = false
                isExitAfterSave = false
            },
            onDismiss = {
                if (isExitAfterSave) {
                    hasUnsavedChanges = false
                    runCatching { workingPdfFile?.delete() }
                    workingPdfFile = null
                    showSaveDialog = false
                    isExitAfterSave = false
                    onBackPress()
                } else {
                    showSaveDialog = false
                    isExitAfterSave = false
                }
            }
        )
    }

    if (showSignatureDialog) {
        SignaturePadDialog(
            visible = showSignatureDialog,
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { signatureBmp ->
                showSignatureDialog = false
                val densityVal = context.resources.displayMetrics.density
                val initialW = 180f * densityVal
                val initialH = (initialW * (signatureBmp.height.toFloat() / signatureBmp.width)).coerceIn(40f * densityVal, 160f * densityVal)
                val screenW = context.resources.displayMetrics.widthPixels.toFloat()
                val screenH = context.resources.displayMetrics.heightPixels.toFloat()
                val initX = ((screenW - initialW) / 2f).coerceAtLeast(16f * densityVal)
                val initY = ((screenH - initialH) / 2f).coerceAtLeast(80f * densityVal)
                isTransformScrollLocked = true
                pendingStamp = PendingStamp(
                    bitmap = signatureBmp,
                    isSignature = true,
                    screenX = initX,
                    screenY = initY,
                    width = initialW,
                    height = initialH,
                    rotation = 0f
                )
            }
        )
    }

    if (showCompressDialog && workingPdfFile != null) {
        CompressPdfDialog(
            visible = showCompressDialog,
            currentFile = workingPdfFile!!,
            onCompressedSuccess = { compressedFile, isOverwrite ->
                showCompressDialog = false
                if (isOverwrite) {
                    workingPdfFile = compressedFile
                    currentWorkingUri = Uri.fromFile(compressedFile)
                    pdfFragment?.documentUri = currentWorkingUri
                    hasUnsavedChanges = true
                    Toast.makeText(context, "PDF compressed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Compressed copy saved", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showCompressDialog = false }
        )
    }

    if (showConvertToImagesDialog) {
        ConvertPdfToImagesDialog(
            visible = showConvertToImagesDialog,
            sourceUri = currentWorkingUri,
            totalPages = totalPages,
            onDismiss = { showConvertToImagesDialog = false }
        )
    }

    if (showOrganizeDialog && workingPdfFile != null) {
        OrganizePagesDialog(
            visible = showOrganizeDialog,
            currentFile = workingPdfFile!!,
            onApplySuccess = { newFile ->
                showOrganizeDialog = false
                workingPdfFile = newFile
                currentWorkingUri = Uri.fromFile(newFile)
                pdfFragment?.documentUri = currentWorkingUri
                hasUnsavedChanges = true
                Toast.makeText(context, "Pages reorganized", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showOrganizeDialog = false }
        )
    }

    if (showWatermarkDialog && workingPdfFile != null) {
        WatermarkPdfDialog(
            visible = showWatermarkDialog,
            currentFile = workingPdfFile!!,
            onApplySuccess = { newFile ->
                showWatermarkDialog = false
                workingPdfFile = newFile
                currentWorkingUri = Uri.fromFile(newFile)
                pdfFragment?.documentUri = currentWorkingUri
                hasUnsavedChanges = true
                Toast.makeText(context, "Watermark applied", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showWatermarkDialog = false }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Native C++ Hardware-Accelerated PDF Viewer Fragment
        AndroidFragment<CustomPdfViewerFragment>(
            modifier = Modifier.fillMaxSize(),
            onUpdate = { fragment ->
                pdfFragment = fragment
                fragment.isScrollLocked = if (pendingStamp != null) isTransformScrollLocked else isAnnotating

                if (!documentLoaded) {
                    fragment.documentUri = currentWorkingUri
                    documentLoaded = true
                }

                fragment.onPageChanged = { page, total ->
                    currentPage = page
                    totalPages = total
                    showIndicator = true
                    interactionCount++
                }

                fragment.onScrollDirection = { _ ->
                    showIndicator = true
                    interactionCount++
                }

                fragment.onGestureStateChanged = { state ->
                    gestureState = state
                    if (state == PdfView.GESTURE_STATE_INTERACTING || state == PdfView.GESTURE_STATE_SETTLING) {
                        showIndicator = true
                        interactionCount++
                    }
                }

                fragment.onSingleTap = {
                    showToolbars = !showToolbars
                    interactionCount++
                }

                fragment.onTextSelected = { text ->
                    selectedText = text
                    interactionCount++
                }

                fragment.onTextSelectedWithBounds = { text, bounds, anchorPoint ->
                    selectedText = text
                    currentSelectionBounds = bounds
                    selectionAnchorPoint = anchorPoint
                    interactionCount++
                }

                fragment.onSelectionCleared = {
                    selectedText = null
                    currentSelectionBounds = emptyList()
                    selectionAnchorPoint = null
                }
            }
        )

        // ClearPDF / Prism M3 Enhanced TopBar
        DocumentTopBar(
            title = fileName,
            pageCount = totalPages,
            currentPage = currentPage,
            renderedByLibreOffice = isOfficeConverted,
            isBookmarked = isBookmarked,
            visible = showToolbars && !isAnnotating,
            hasUnsavedChanges = hasUnsavedChanges,
            onBack = {
                handleBack()
            },
            onSearchClick = {
                isSearchActive = !isSearchActive
                interactionCount++
            },
            onBookmarkToggle = {
                isBookmarked = !isBookmarked
            },
            onAskAiClick = {
                showAiSheet = true
                interactionCount++
            },
            onSaveClick = {
                isExitAfterSave = false
                showSaveDialog = true
            },
            onAnnotateClick = {
                isAnnotating = true
                interactionCount++
            },
            onSignClick = {
                showSignatureDialog = true
                interactionCount++
            },
            onAddImageClick = {
                imagePickerLauncher.launch("image/*")
                interactionCount++
            },
            onCompressClick = {
                scope.launch {
                    getOrCreateWorkingFile()
                    showCompressDialog = true
                }
            },
            onConvertToImagesClick = {
                showConvertToImagesDialog = true
            },
            onOrganizePagesClick = {
                scope.launch {
                    getOrCreateWorkingFile()
                    showOrganizeDialog = true
                }
            },
            onWatermarkClick = {
                scope.launch {
                    getOrCreateWorkingFile()
                    showWatermarkDialog = true
                }
            },
            onInfoClick = {
                showInfoDialog = true
                interactionCount++
            },
            onOpenWithClick = {
                DocumentShareHelper.openWith(
                    context = context,
                    uri = if (workingPdfFile?.exists() == true) Uri.fromFile(workingPdfFile) else currentWorkingUri,
                    title = globalClass.getString(R.string.open_with)
                )
            },
            onShareClick = {
                DocumentShareHelper.shareDocument(
                    context = context,
                    uri = if (workingPdfFile?.exists() == true) Uri.fromFile(workingPdfFile) else currentWorkingUri,
                    title = globalClass.getString(R.string.share),
                    desiredFileName = authenticPdfName
                )
            },
            onPrintClick = {
                runCatching {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                    val pfd = context.contentResolver.openFileDescriptor(currentWorkingUri, "r")
                    if (printManager != null && pfd != null) {
                        printManager.print(
                            fileName,
                            object : android.print.PrintDocumentAdapter() {
                                override fun onLayout(
                                    oldAttributes: android.print.PrintAttributes?,
                                    newAttributes: android.print.PrintAttributes?,
                                    cancellationSignal: android.os.CancellationSignal?,
                                    callback: LayoutResultCallback?,
                                    extras: android.os.Bundle?
                                ) {
                                    val info = android.print.PrintDocumentInfo.Builder(fileName)
                                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                        .setPageCount(totalPages)
                                        .build()
                                    callback?.onLayoutFinished(info, true)
                                }

                                override fun onWrite(
                                    pages: Array<out android.print.PageRange>?,
                                    destination: android.os.ParcelFileDescriptor?,
                                    cancellationSignal: android.os.CancellationSignal?,
                                    callback: WriteResultCallback?
                                ) {
                                    if (destination != null) {
                                        java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                                            java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                                    }
                                }
                            },
                            null
                        )
                    }
                }
            },
            onMenuVisibilityChange = { isMenuOpen = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
        )

        // ── In-place Floating Text Selection Ribbon (Updates only that page, no reload!) ───
        DocumentSelectionToolbar(
            visible = !selectedText.isNullOrBlank() && !isAnnotating,
            anchorPosition = selectionAnchorPoint,
            onCopy = {
                selectedText?.let { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Copied Text", text)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            },
            onHighlight = { colorArgb ->
                val bounds = currentSelectionBounds.ifEmpty { pdfFragment?.currentSelectionBounds ?: emptyList() }
                if (bounds.isNotEmpty()) {
                    for (b in bounds) {
                        pdfFragment?.addHighlight(b, colorArgb.toInt())
                    }
                    hasUnsavedChanges = true
                    pdfFragment?.clearSelection()
                    selectedText = null
                    currentSelectionBounds = emptyList()
                    selectionAnchorPoint = null
                    Toast.makeText(context, "Highlighted text", Toast.LENGTH_SHORT).show()

                    scope.launch(Dispatchers.IO) {
                        val file = getOrCreateWorkingFile()
                        val item = PdfAnnotationItem(
                            type = AnnotationType.HIGHLIGHT,
                            bounds = bounds,
                            colorArgb = colorArgb.toInt()
                        )
                        PdfOperations.applyAnnotations(context, file, listOf(item))
                    }
                } else {
                    Toast.makeText(context, "Please select text to highlight", Toast.LENGTH_SHORT).show()
                }
            },
            onUnderline = {
                val bounds = currentSelectionBounds.ifEmpty { pdfFragment?.currentSelectionBounds ?: emptyList() }
                if (bounds.isNotEmpty()) {
                    for (b in bounds) {
                        pdfFragment?.addUnderline(b, 0xFF1976D2.toInt())
                    }
                    hasUnsavedChanges = true
                    pdfFragment?.clearSelection()
                    selectedText = null
                    currentSelectionBounds = emptyList()
                    selectionAnchorPoint = null
                    Toast.makeText(context, "Underlined text", Toast.LENGTH_SHORT).show()

                    scope.launch(Dispatchers.IO) {
                        val file = getOrCreateWorkingFile()
                        val item = PdfAnnotationItem(
                            type = AnnotationType.UNDERLINE,
                            bounds = bounds,
                            colorArgb = 0xFF1976D2.toInt()
                        )
                        PdfOperations.applyAnnotations(context, file, listOf(item))
                    }
                } else {
                    Toast.makeText(context, "Please select text to underline", Toast.LENGTH_SHORT).show()
                }
            },
            onStrike = {
                val bounds = currentSelectionBounds.ifEmpty { pdfFragment?.currentSelectionBounds ?: emptyList() }
                if (bounds.isNotEmpty()) {
                    for (b in bounds) {
                        pdfFragment?.addStrikethrough(b, 0xFFD32F2F.toInt())
                    }
                    hasUnsavedChanges = true
                    pdfFragment?.clearSelection()
                    selectedText = null
                    currentSelectionBounds = emptyList()
                    selectionAnchorPoint = null
                    Toast.makeText(context, "Strikethrough applied", Toast.LENGTH_SHORT).show()

                    scope.launch(Dispatchers.IO) {
                        val file = getOrCreateWorkingFile()
                        val item = PdfAnnotationItem(
                            type = AnnotationType.STRIKETHROUGH,
                            bounds = bounds,
                            colorArgb = 0xFFD32F2F.toInt()
                        )
                        PdfOperations.applyAnnotations(context, file, listOf(item))
                    }
                } else {
                    Toast.makeText(context, "Please select text for strikethrough", Toast.LENGTH_SHORT).show()
                }
            },
            onAskAi = {
                showAiSheet = true
                interactionCount++
            },
            onShare = {
                selectedText?.let { text ->
                    DocumentShareHelper.shareText(context, text)
                }
            },
            onSearch = {
                selectedText?.let { text ->
                    DocumentShareHelper.searchWeb(context, text)
                }
            },
            onDismiss = {
                pdfFragment?.clearSelection()
                selectedText = null
                selectionAnchorPoint = null
            },
            modifier = Modifier.zIndex(3f)
        )

        // ── Floating Page Number Pill Bar ─────────────────────
        // zIndex(2f) ensures it draws on top of native AndroidFragment
        AnimatedVisibility(
            visible = (showIndicator || showToolbars) && totalPages > 0 && !isAnnotating,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .zIndex(2f)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { showGoToPageDialog = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "${currentPage + 1} / $totalPages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // ── Freehand Annotation / Drawing Canvas Overlay ──────────────────────────────
        if (isAnnotating) {
            val haptics = LocalHapticFeedback.current
            fun eraseAt(pos: Offset) {
                val thresholdSq = 32f * 32f * context.resources.displayMetrics.density
                val toRemove = activeDrawnStrokes.filter { stroke ->
                    stroke.points.any { pt ->
                        val dx = pt.x - pos.x
                        val dy = pt.y - pos.y
                        dx * dx + dy * dy <= thresholdSq
                    }
                }
                if (toRemove.isNotEmpty()) {
                    activeDrawnStrokes.removeAll(toRemove)
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f)
            ) {
                // Touch capture and drawing layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(penColor, penStrokeWidth, isEraserMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (isEraserMode) {
                                        eraseAt(offset)
                                    } else {
                                        currentDrawingPoints = listOf(offset)
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (isEraserMode) {
                                        eraseAt(change.position)
                                    } else {
                                        currentDrawingPoints = currentDrawingPoints + change.position
                                    }
                                },
                                onDragEnd = {
                                    if (!isEraserMode && currentDrawingPoints.size > 1) {
                                        activeDrawnStrokes.add(
                                            SignatureStroke(
                                                points = currentDrawingPoints,
                                                color = penColor,
                                                width = penStrokeWidth
                                            )
                                        )
                                    }
                                    currentDrawingPoints = emptyList()
                                },
                                onDragCancel = {
                                    currentDrawingPoints = emptyList()
                                }
                            )
                        }
                ) {
                    ComposeCanvas(modifier = Modifier.matchParentSize()) {
                        for (stroke in activeDrawnStrokes) {
                            if (stroke.points.size < 2) continue
                            val path = Path().apply {
                                moveTo(stroke.points[0].x, stroke.points[0].y)
                                for (i in 1 until stroke.points.size) {
                                    lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = stroke.color,
                                style = Stroke(
                                    width = stroke.width,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        if (!isEraserMode && currentDrawingPoints.size > 1) {
                            val path = Path().apply {
                                moveTo(currentDrawingPoints[0].x, currentDrawingPoints[0].y)
                                for (i in 1 until currentDrawingPoints.size) {
                                    lineTo(currentDrawingPoints[i].x, currentDrawingPoints[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = penColor,
                                style = Stroke(
                                    width = penStrokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                // Drawing toolbar bar at bottom
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp, start = 12.dp, end = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val colors = listOf(
                            Color(0xFF141414),
                            Color(0xFFD32F2F),
                            Color(0xFF1976D2),
                            Color(0xFF388E3C),
                            Color(0xFFFBC02D)
                        )
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (!isEraserMode && penColor == c) 2.5.dp else 1.dp,
                                        color = if (!isEraserMode && penColor == c) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        penColor = c
                                        isEraserMode = false
                                    }
                            )
                        }

                        // Eraser Tool
                        IconButton(
                            onClick = {
                                isEraserMode = !isEraserMode
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isEraserMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        ) {
                            Icon(
                                Icons.Rounded.AutoFixNormal,
                                contentDescription = "Eraser",
                                modifier = Modifier.size(18.dp),
                                tint = if (isEraserMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Clear All Button
                        IconButton(
                            onClick = {
                                activeDrawnStrokes.clear()
                                currentDrawingPoints = emptyList()
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            },
                            enabled = activeDrawnStrokes.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear all", modifier = Modifier.size(18.dp))
                        }

                        // Undo Button
                        IconButton(
                            onClick = { if (activeDrawnStrokes.isNotEmpty()) activeDrawnStrokes.removeAt(activeDrawnStrokes.lastIndex) },
                            enabled = activeDrawnStrokes.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                        }

                        // Cancel Button
                        IconButton(
                            onClick = {
                                activeDrawnStrokes.clear()
                                isAnnotating = false
                                isEraserMode = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                        }

                        // Done Button (Commits strokes to their actual respective page)
                        Button(
                            onClick = {
                                if (activeDrawnStrokes.isNotEmpty()) {
                                    val strokesByPage = mutableMapOf<Int, MutableList<PdfStrokeItem>>()
                                    for (s in activeDrawnStrokes) {
                                        val ptsWithPage = s.points.mapNotNull { pt -> pdfFragment?.viewToPdfPoint(pt.x, pt.y) }
                                        if (ptsWithPage.size >= 2) {
                                            val pNum = ptsWithPage.first().pageNum
                                            val strokeItem = PdfStrokeItem(
                                                points = ptsWithPage.map { PdfStrokePoint(it.x, it.y) },
                                                colorArgb = s.color.toArgb(),
                                                strokeWidth = s.width
                                            )
                                            strokesByPage.getOrPut(pNum) { mutableListOf() }.add(strokeItem)
                                        }
                                    }

                                    for ((pNum, strokes) in strokesByPage) {
                                        for (st in strokes) {
                                            pdfFragment?.addStrokeItem(pNum, st)
                                        }
                                        scope.launch(Dispatchers.IO) {
                                            val file = getOrCreateWorkingFile()
                                            PdfOperations.addStrokes(context, file, pNum, strokes)
                                        }
                                    }
                                    hasUnsavedChanges = true
                                    Toast.makeText(context, "Drawings saved", Toast.LENGTH_SHORT).show()
                                }
                                activeDrawnStrokes.clear()
                                isAnnotating = false
                                isEraserMode = false
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }

        // ── Interactive Transform Overlay for Placed Image or Signature ───────────────
        val stamp = pendingStamp
        if (stamp != null) {
            TransformStampOverlay(
                stamp = stamp,
                isScrollLocked = isTransformScrollLocked,
                onToggleScrollLock = {
                    isTransformScrollLocked = !isTransformScrollLocked
                },
                onTransformChanged = { updatedStamp ->
                    pendingStamp = updatedStamp
                },
                onCancel = {
                    pendingStamp = null
                },
                onConfirm = {
                    val currentStamp = pendingStamp ?: stamp
                    val cx = currentStamp.screenX + currentStamp.width / 2f
                    val cy = currentStamp.screenY + currentStamp.height / 2f
                    val pCenter = pdfFragment?.viewToPdfPoint(cx, cy)
                    val pRef = pdfFragment?.viewToPdfPoint(cx + 60f, cy + 60f)
                    val scale = if (pCenter != null && pRef != null && pCenter.pageNum == pRef.pageNum && kotlin.math.abs(pRef.x - pCenter.x) > 0.001f) {
                        kotlin.math.abs(pRef.x - pCenter.x) / 60f
                    } else 1f

                    val targetW = currentStamp.width * scale
                    val targetH = currentStamp.height * scale
                    val pdfX = if (pCenter != null) pCenter.x - targetW / 2f else 100f
                    val pdfY = if (pCenter != null) pCenter.y - targetH / 2f else 160f
                    val targetPage = pCenter?.pageNum ?: currentPage

                    val finalBmp = if (currentStamp.rotation != 0f) {
                        val matrix = android.graphics.Matrix().apply { postRotate(currentStamp.rotation) }
                        Bitmap.createBitmap(currentStamp.bitmap, 0, 0, currentStamp.bitmap.width, currentStamp.bitmap.height, matrix, true)
                    } else currentStamp.bitmap

                    pdfFragment?.addStampItem(targetPage, finalBmp, pdfX, pdfY, targetW, targetH)
                    scope.launch(Dispatchers.IO) {
                        val file = getOrCreateWorkingFile()
                        PdfOperations.stampBitmap(context, file, targetPage, finalBmp, pdfX, pdfY, targetW, targetH)
                    }
                    hasUnsavedChanges = true
                    pendingStamp = null
                    Toast.makeText(context, "${if (currentStamp.isSignature) "Signature" else "Image"} placed on page ${targetPage + 1}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            textService.close()
        }
    }
}

data class PendingStamp(
    val bitmap: Bitmap,
    val isSignature: Boolean,
    val screenX: Float,
    val screenY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f
)

@Composable
private fun TransformStampOverlay(
    stamp: PendingStamp,
    isScrollLocked: Boolean,
    onToggleScrollLock: () -> Unit,
    onTransformChanged: (PendingStamp) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val density = LocalDensity.current
    val minSizePx = with(density) { 48.dp.toPx() }
    val haptics = LocalHapticFeedback.current

    var posX by remember(stamp.bitmap) { mutableFloatStateOf(stamp.screenX) }
    var posY by remember(stamp.bitmap) { mutableFloatStateOf(stamp.screenY) }
    var currentWidth by remember(stamp.bitmap) { mutableFloatStateOf(stamp.width) }
    var currentHeight by remember(stamp.bitmap) { mutableFloatStateOf(stamp.height) }
    var rotationAngle by remember(stamp.bitmap) { mutableFloatStateOf(stamp.rotation) }

    val currentOnTransformChanged by rememberUpdatedState(onTransformChanged)

    fun notifyChanged() {
        currentOnTransformChanged(
            stamp.copy(
                screenX = posX,
                screenY = posY,
                width = currentWidth,
                height = currentHeight,
                rotation = rotationAngle
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(6f)
            .then(
                if (isScrollLocked) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { /* consume background taps outside */ }
                    }
                } else Modifier
            )
    ) {
        // Stamp container
        Box(
            modifier = Modifier
                .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                .size(
                    width = with(density) { currentWidth.toDp() },
                    height = with(density) { currentHeight.toDp() }
                )
                // Drag anywhere inside to move
                .pointerInput(stamp.bitmap) {
                    detectDragGestures(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX += dragAmount.x
                            posY += dragAmount.y
                            notifyChanged()
                        }
                    )
                }
        ) {
            // Render bitmap with rotation
            Image(
                bitmap = stamp.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotationAngle
                    }
            )

            // Primary bounding border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
            )

            // Corner decorative handle: Top-Left (Move indicator)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopStart)
                    .offset((-12).dp, (-12).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.OpenWith,
                    contentDescription = "Move Handle",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Corner action handle: Top-Right (Rotate 90°)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopEnd)
                    .offset(12.dp, (-12).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        rotationAngle = (rotationAngle + 90f) % 360f
                        notifyChanged()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.RotateRight,
                    contentDescription = "Rotate 90°",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Corner decorative handle: Bottom-Left
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomStart)
                    .offset((-6).dp, 6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )

            // Bottom-Right Corner RESIZE HANDLE
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.BottomEnd)
                    .offset(16.dp, 16.dp)
                    .pointerInput(stamp.bitmap) {
                        val aspectRatio = (stamp.bitmap.width.toFloat() / stamp.bitmap.height).coerceAtLeast(0.1f)
                        detectDragGestures(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newW = (currentWidth + dragAmount.x).coerceAtLeast(minSizePx)
                                val newH = (newW / aspectRatio).coerceAtLeast(minSizePx)
                                currentWidth = newW
                                currentHeight = newH
                                notifyChanged()
                            }
                        )
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.OpenWith,
                    contentDescription = "Resize",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
            }
        }

        // Floating Action Controls Bar at bottom
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scroll Lock Toggle Button
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onToggleScrollLock()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isScrollLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = if (isScrollLocked) "Page Scroll Locked" else "Page Scroll Unlocked",
                        tint = if (isScrollLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Rotate 90°
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        rotationAngle = (rotationAngle + 90f) % 360f
                        notifyChanged()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.RotateRight,
                        contentDescription = "Rotate 90°",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Cancel
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onCancel()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Place
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirm()
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Place")
                }
            }
        }
    }
}
