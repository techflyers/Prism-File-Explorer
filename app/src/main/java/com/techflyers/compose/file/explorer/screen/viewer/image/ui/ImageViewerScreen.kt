package com.techflyers.compose.file.explorer.screen.viewer.image.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.isValidAsFileName
import com.techflyers.compose.file.explorer.common.joinFileName
import com.techflyers.compose.file.explorer.common.splitFileName
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.common.ui.autoShowKeyboard
import com.techflyers.compose.file.explorer.screen.main.MainActivity
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.WidthFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.image.ImageEditorActivity
import com.techflyers.compose.file.explorer.screen.viewer.image.ImageViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.image.ImageViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.image.misc.ImageInfo
import com.techflyers.compose.file.explorer.screen.viewer.image.misc.ImageInfo.Companion.extractImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import com.techflyers.compose.file.explorer.screen.viewer.archive.ArchiveMediaQueueManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(instance: ImageViewerInstance) {
    val defaultColor = MaterialTheme.colorScheme.surface
    var dominantColor by remember { mutableStateOf(defaultColor) }
    var secondaryColor by remember { mutableStateOf(defaultColor) }
    val imageBackgroundColors = listOf(Color.Transparent, Color.White, Color.Gray, Color.Black)
    var currentImageBackgroundColorIndex by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var imageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var imageDimensions by remember { mutableStateOf("" to "") }
    var contentScale by remember { mutableStateOf(ContentScale.Fit) }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    var imageUris by remember { mutableStateOf(instance.imageList.ifEmpty { listOf(instance.uri) }) }
    var imagePaths by remember { mutableStateOf(instance.imagePaths) }
    val imageList = imageUris
    val pagerState = rememberPagerState(
        initialPage = instance.initialIndex.coerceIn(0, (imageList.size - 1).coerceAtLeast(0)),
        pageCount = { imageList.size }
    )

    val safeIndex = pagerState.currentPage.coerceIn(0, (imageList.size - 1).coerceAtLeast(0))
    val currentUri = imageList.getOrNull(safeIndex)
    val currentResolvedPath = imagePaths.getOrNull(safeIndex) ?: imageInfo?.path ?: (context as? ImageViewerActivity)?.let { ImageViewerActivity.resolveFilePath(it, currentUri ?: instance.uri) }

    val archiveSession = remember { (context as? ImageViewerActivity)?.intent?.let { ArchiveMediaQueueManager.getOrCreateSession(it) } }

    LaunchedEffect(pagerState.currentPage, archiveSession) {
        archiveSession?.let { session ->
            ArchiveMediaQueueManager.prefetchWindow(session, pagerState.currentPage, windowSize = 3)
        }
    }

    if (imageList.isEmpty() || currentUri == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    LaunchedEffect(currentUri) {
        rotationAngle = 0f
        val resolvedPath = imagePaths.getOrNull(safeIndex)
        withContext(Dispatchers.IO) {
            val info = extractImageInfo(currentUri, explicitPath = resolvedPath)
            withContext(Dispatchers.Main) {
                imageInfo = info
                if (info.dimensions.contains("×")) {
                    val parts = info.dimensions.split("×").map { it.trim() }
                    if (parts.size == 2) {
                        imageDimensions = parts[0] to parts[1]
                    }
                }
            }
        }
    }

    LaunchedEffect(imageDimensions) {
        if (imageDimensions.first.isNotEmpty() && imageDimensions.second.isNotEmpty()) {
            val newDims = "${imageDimensions.first} × ${imageDimensions.second}"
            if (imageInfo != null && imageInfo?.dimensions != newDims) {
                imageInfo = imageInfo?.copy(dimensions = newDims)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.8f),
                        secondaryColor.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        // Always leave userScrollEnabled = true. Telephoto participates in nested
        // scroll: when zoomed out, horizontal drags go to the pager; when zoomed in,
        // they pan the image. Gating on zoomFraction is unsafe — for some aspect
        // ratios (e.g. tall screenshots vs wide photos) zoomFraction reports 1.0
        // even when fully zoomed out, which permanently locked swipe (telephoto #152).
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            beyondViewportPageCount = 1,
            key = { page -> imageList.getOrNull(page)?.toString() ?: page.toString() }
        ) { page ->
            val pageUri = imageList.getOrNull(page) ?: return@HorizontalPager
            val zoomableState = rememberZoomableState()
            val imageState = rememberZoomableImageState(zoomableState)

            // Reset zoom only when navigating *to* this page. Do not key on
            // isScrollInProgress or zoom level — that would reset while the user
            // is intentionally zoomed in. Fresh reset on arrival avoids leftover
            // transforms from a previous aspect-ratio image locking nested scroll.
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage == page) {
                    try {
                        zoomableState.resetZoom()
                    } catch (_: Throwable) {
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val targetPath = imagePaths.getOrNull(page)
                var isExtracted by remember(pageUri, targetPath) {
                    mutableStateOf(targetPath == null || ArchiveMediaQueueManager.isExtracted(targetPath))
                }

                LaunchedEffect(pageUri, isExtracted) {
                    if (!isExtracted && targetPath != null && archiveSession != null) {
                        ArchiveMediaQueueManager.ensureExtracted(archiveSession, page)
                        isExtracted = ArchiveMediaQueueManager.isExtracted(targetPath)
                    }
                }

                if (!isExtracted) {
                    LoadingState(text = stringResource(R.string.extracting_media))
                } else {
                    var image by remember(pageUri) { mutableStateOf<Image?>(null) }
                    var isError by remember(pageUri) { mutableStateOf(false) }
                    var isLoading by remember(pageUri) { mutableStateOf(true) }

                    if (isError) {
                        ErrorState(onClose = { (context as? ViewerActivity)?.finish() })
                    } else {
                        ZoomableAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(pageUri)
                            .listener(
                                onSuccess = { _, state ->
                                    image = state.image
                                    isLoading = false
                                    if (page == pagerState.currentPage) {
                                        state.image?.let {
                                            imageDimensions = "${it.width}" to "${it.height}"
                                        }
                                    }
                                },
                                onError = { _, error ->
                                    logger.logError(error.throwable)
                                    isError = true
                                    isLoading = false
                                }
                            ).build(),
                        contentDescription = null,
                        contentScale = contentScale,
                        state = imageState,
                        onClick = { showControls = !showControls },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (page == pagerState.currentPage) {
                                    rotationZ = rotationAngle
                                }
                            }
                            .background(imageBackgroundColors[currentImageBackgroundColorIndex])
                    )

                    if (isLoading) LoadingState()

                    LaunchedEffect(image, pagerState.currentPage) {
                        if (image != null && page == pagerState.currentPage) {
                            image?.let {
                                imageDimensions = "${it.width}" to "${it.height}"
                            }
                        }
                    }

                    LaunchedEffect(image) {
                        if (image != null && page == pagerState.currentPage) {
                            try {
                                withContext(Dispatchers.Default) {
                                    val bitmap = image!!.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
                                    val palette = Palette.from(bitmap).generate()
                                    val dominant = Color(palette.getDominantColor(defaultColor.toArgb()))
                                    val secondary = Color(palette.getMutedColor(defaultColor.toArgb()))
                                    withContext(Dispatchers.Main) {
                                        dominantColor = dominant
                                        secondaryColor = secondary
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        TopAppBarDefaults.MediumAppBarCollapsedHeight
                                + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                                + 8.dp
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                defaultColor.copy(alpha = 0.8f),
                                defaultColor.copy(alpha = 0.4f)
                            )
                        )
                    )
            )
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showControls,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        TopAppBarDefaults.MediumAppBarCollapsedHeight
                                + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                defaultColor.copy(alpha = 0.4f),
                                defaultColor.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = currentResolvedPath != null) {
                                showRenameDialog = true
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = imageInfo?.name ?: (currentUri.lastPathSegment
                                    ?: stringResource(R.string.unknown)),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentResolvedPath != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.rename),
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        val subtitle = buildString {
                            imageInfo?.let { info ->
                                append("${info.size} • ${info.dimensions}")
                            }
                            if (imageList.size > 1) {
                                if (isNotEmpty()) append(" • ")
                                append("${safeIndex + 1} / ${imageList.size}")
                            }
                        }
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? ViewerActivity)?.finish() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (currentResolvedPath != null) {
                        IconButton(onClick = {
                            val file = File(currentResolvedPath)
                            if (file.exists()) {
                                val localHolder = LocalFileHolder(file)
                                val activeTab = globalClass.mainActivityManager.getActiveTab()
                                if (activeTab is FilesTab) {
                                    globalClass.mainActivityManager.replaceCurrentTabWith(FilesTab(source = localHolder))
                                } else {
                                    globalClass.mainActivityManager.addTabAndSelect(FilesTab(source = localHolder))
                                }
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                context.startActivity(intent)
                                (context as? ViewerActivity)?.finish()
                            } else {
                                globalClass.showMsg(R.string.file_not_found)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = stringResource(R.string.locate),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = {
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = currentUri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                openIntent,
                                context.getString(R.string.open_with)
                            )
                        )
                    }) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = "Open with",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showControls,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            BottomControls(
                onInvertBackgroundColors = {
                    currentImageBackgroundColorIndex =
                        (currentImageBackgroundColorIndex + 1) % imageBackgroundColors.size
                },
                onRotate = { rotationAngle = (rotationAngle + 90f) % 360f },
                onEdit = {
                    val editIntent = Intent(context, ImageEditorActivity::class.java).apply {
                        data = currentUri
                        putExtra("extra_file_path", imageInfo?.path)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    context.startActivity(editIntent)
                },
                onContentScale = { contentScale = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (imageList.size > 1 && showControls) {
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "${safeIndex + 1} / ${imageList.size}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (showInfo) {
            val info = imageInfo ?: extractImageInfo(
                currentUri,
                imageDimensions.first,
                imageDimensions.second,
                imagePaths.getOrNull(safeIndex)
            ).also { imageInfo = it }
            ImageInfoBottomSheet(imageInfo = info, onDismiss = { showInfo = false })
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    if (!isDeleting) showDeleteConfirmation = false
                },
                title = { Text(stringResource(R.string.delete_confirmation)) },
                text = { Text(stringResource(R.string.delete_confirmation_message)) },
                confirmButton = {
                    TextButton(
                        enabled = !isDeleting,
                        onClick = {
                            val deletedIndex = pagerState.currentPage.coerceIn(0, imageList.lastIndex)
                            val path = imagePaths.getOrNull(deletedIndex)
                                ?: imageInfo?.path
                                ?: ImageViewerActivity.resolveFilePath(context, currentUri)
                            val uriToDelete = imageList[deletedIndex]
                            val viewerActivity = context as? ViewerActivity

                            val deleted = if (!path.isNullOrEmpty() && java.io.File(path).exists()) {
                                java.io.File(path).delete()
                            } else {
                                runCatching { context.contentResolver.delete(uriToDelete, null, null) }.getOrDefault(0) > 0
                            }

                            if (deleted) {
                                viewerActivity?.onFileDeleted(path)
                                val remainingUris = imageList.filterIndexed { index, _ -> index != deletedIndex }
                                val remainingPaths = if (imagePaths.size == imageList.size) {
                                    imagePaths.filterIndexed { index, _ -> index != deletedIndex }
                                } else {
                                    imagePaths
                                }

                                showDeleteConfirmation = false

                                if (remainingUris.isEmpty()) {
                                    imageUris = emptyList()
                                    imagePaths = emptyList()
                                    viewerActivity?.finish()
                                } else if (deletedIndex == imageList.lastIndex) {
                                    // At last image: must go back to previous image
                                    val targetPage = deletedIndex - 1
                                    isDeleting = true
                                    scope.launch {
                                        try {
                                            pagerState.animateScrollToPage(targetPage)
                                        } catch (_: Exception) {
                                            pagerState.scrollToPage(targetPage)
                                        } finally {
                                            imageUris = remainingUris
                                            imagePaths = remainingPaths
                                            isDeleting = false
                                        }
                                    }
                                } else {
                                    // Not at last image: advance to next image (which moves into deletedIndex)
                                    imageUris = remainingUris
                                    imagePaths = remainingPaths
                                }
                            } else {
                                showDeleteConfirmation = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isDeleting,
                        onClick = { showDeleteConfirmation = false }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showRenameDialog && currentResolvedPath != null) {
            val currentFile = File(currentResolvedPath)
            val split = remember(currentFile.absolutePath, currentFile.name) {
                splitFileName(currentFile.name, isFolder = false)
            }
            var nameInput by remember(currentFile.absolutePath, currentFile.name) {
                mutableStateOf(TextFieldValue(split.first, TextRange(split.first.length)))
            }
            var extensionInput by remember(currentFile.absolutePath, currentFile.name) {
                mutableStateOf(TextFieldValue(split.second, TextRange(split.second.length)))
            }
            val newNameInput = joinFileName(nameInput.text, extensionInput.text)
            var error by remember(currentFile.absolutePath) { mutableStateOf("") }
            val extensionFocusRequester = remember { FocusRequester() }
            val moveToExtension = {
                extensionInput = extensionInput.copy(selection = TextRange(extensionInput.text.length))
                extensionFocusRequester.requestFocus()
            }

            LaunchedEffect(newNameInput) {
                val parent = currentFile.parentFile
                val targetFile = if (parent != null) File(parent, newNameInput) else null
                val isSameFileCaseChange = targetFile != null && targetFile.exists() && targetFile.name.equals(currentFile.name, ignoreCase = true)
                error = if (newNameInput.isBlank() || newNameInput == currentFile.name) {
                    emptyString
                } else if (!newNameInput.isValidAsFileName()) {
                    globalClass.getString(R.string.invalid_file_name)
                } else if (targetFile != null && targetFile.exists() && !isSameFileCaseChange) {
                    globalClass.getString(R.string.similar_file_exists)
                } else {
                    emptyString
                }
            }

            val canRename = error.isEmpty() && nameInput.text.isNotBlank() && newNameInput != currentFile.name

            val executeRename: () -> Unit = {
                if (canRename) {
                    scope.launch {
                        val parent = currentFile.parentFile
                        if (parent != null) {
                            val newFile = File(parent, newNameInput)
                            val isSameFileCaseChange = newFile.exists() && newFile.name.equals(currentFile.name, ignoreCase = true)
                            if (newFile.exists() && !isSameFileCaseChange) {
                                globalClass.showMsg(R.string.similar_file_exists)
                                return@launch
                            }
                            var renamed = currentFile.renameTo(newFile)
                            if (!renamed && currentFile.name.equals(newNameInput, ignoreCase = true)) {
                                val tempFile = File(parent, "${currentFile.name}_tmp_${System.currentTimeMillis()}")
                                if (currentFile.renameTo(tempFile)) {
                                    renamed = tempFile.renameTo(newFile)
                                    if (!renamed) {
                                        tempFile.renameTo(currentFile)
                                    }
                                }
                            }
                            if (renamed) {
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(currentFile.absolutePath, newFile.absolutePath),
                                    null,
                                    null
                                )
                                val newUri = try {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        newFile
                                    )
                                } catch (_: Exception) {
                                    android.net.Uri.fromFile(newFile)
                                }
                                withContext(Dispatchers.Main) {
                                    val newPaths = imagePaths.toMutableList()
                                    val newUris = imageUris.toMutableList()
                                    if (safeIndex in newPaths.indices) {
                                        newPaths[safeIndex] = newFile.absolutePath
                                        imagePaths = newPaths
                                    }
                                    if (safeIndex in newUris.indices) {
                                        newUris[safeIndex] = newUri
                                        imageUris = newUris
                                    }
                                    imageInfo = extractImageInfo(newUri, explicitPath = newFile.absolutePath)
                                    showRenameDialog = false
                                    globalClass.mainActivityManager.refreshAllTabs()
                                }
                            } else {
                                globalClass.showMsg(R.string.unable_to_continue_task)
                            }
                        }
                    }
                } else if (newNameInput == currentFile.name) {
                    showRenameDialog = false
                } else if (error.isNotEmpty()) {
                    globalClass.showMsg(error)
                }
            }

            Dialog(onDismissRequest = { showRenameDialog = false }) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.rename),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Space(8.dp)
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            TextField(
                                modifier = Modifier
                                    .weight(1.4f)
                                    .autoShowKeyboard()
                                    .onPreviewKeyEvent { keyEvent ->
                                        if ((keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) && keyEvent.type == KeyEventType.KeyDown) {
                                            moveToExtension()
                                            true
                                        } else false
                                    },
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text(text = stringResource(R.string.name)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { moveToExtension() },
                                    onDone = { moveToExtension() },
                                    onGo = { moveToExtension() }
                                ),
                                shape = RoundedCornerShape(6.dp),
                                colors = TextFieldDefaults.colors(
                                    errorIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                isError = error.isNotEmpty()
                            )
                            TextField(
                                modifier = Modifier
                                    .weight(0.8f)
                                    .focusRequester(extensionFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if ((keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) && keyEvent.type == KeyEventType.KeyDown) {
                                            executeRename()
                                            true
                                        } else false
                                    },
                                value = extensionInput,
                                onValueChange = {
                                    val clean = it.text.replace(".", "")
                                    val newSelection = if (clean != it.text) TextRange(clean.length) else it.selection
                                    extensionInput = it.copy(text = clean, selection = newSelection)
                                },
                                label = { Text(text = stringResource(R.string.extension)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { executeRename() },
                                    onNext = { executeRename() },
                                    onGo = { executeRename() }
                                ),
                                shape = RoundedCornerShape(6.dp),
                                colors = TextFieldDefaults.colors(
                                    errorIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        if (error.isNotEmpty()) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { showRenameDialog = false },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = executeRename,
                                enabled = canRename,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.rename),
                                    style = MaterialTheme.typography.labelLarge
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
private fun LoadingState(text: String = stringResource(R.string.loading_image)) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ErrorState(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.failed_to_load_image),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.check_file_exists_and_try_again),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun BottomControls(
    onInvertBackgroundColors: () -> Unit,
    onRotate: () -> Unit,
    onEdit: () -> Unit,
    onContentScale: (ContentScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentScales = listOf(
        ContentScale.Fit, ContentScale.Crop, ContentScale.FillWidth,
        ContentScale.FillHeight, ContentScale.FillBounds, ContentScale.Inside
    )
    var selectedContentScale by remember { mutableStateOf(contentScales[0]) }

    Row(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = Icons.Default.InvertColors,
            onClick = onInvertBackgroundColors,
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )
        ActionButton(
            icon = when (selectedContentScale) {
                ContentScale.Fit -> Icons.Default.FitScreen
                ContentScale.Crop -> Icons.Default.Crop
                ContentScale.FillWidth -> Icons.Default.WidthFull
                ContentScale.FillHeight -> Icons.Default.Height
                ContentScale.FillBounds -> Icons.Default.BorderOuter
                else -> Icons.Default.FilterCenterFocus
            },
            onClick = {
                val idx = contentScales.indexOf(selectedContentScale)
                selectedContentScale = contentScales[if (idx == contentScales.lastIndex) 0 else idx + 1]
                onContentScale(selectedContentScale)
            },
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )
        ActionButton(
            icon = Icons.AutoMirrored.Filled.RotateRight,
            onClick = onRotate,
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )
        ActionButton(
            icon = Icons.Default.Edit,
            onClick = onEdit,
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(backgroundColor)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageInfoBottomSheet(imageInfo: ImageInfo, onDismiss: () -> Unit) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.size(width = 32.dp, height = 4.dp))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = "Image Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            InfoRow(stringResource(R.string.name), imageInfo.name)
            InfoRow(stringResource(R.string.size), imageInfo.size)
            InfoRow(stringResource(R.string.dimensions), imageInfo.dimensions)
            InfoRow(stringResource(R.string.format), imageInfo.format)
            InfoRow(stringResource(R.string.last_modified), imageInfo.lastModified)
            InfoRow(stringResource(R.string.path), imageInfo.path)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f)
        )
    }
}
