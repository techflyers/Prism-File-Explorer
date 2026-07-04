package com.raival.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.service.VaultFileRecord
import com.raival.compose.file.explorer.screen.main.tab.files.service.VaultService
import com.raival.compose.file.explorer.screen.main.tab.nfile_tools.VaultTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VaultTabContentView(tab: VaultTab) {
    val context = LocalContext.current
    var hasPinSet by remember { mutableStateOf(VaultService.isPasswordSet(context)) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasPinSet) {
            VaultSetupScreen(
                onPinCreated = { pin ->
                    VaultService.setPassword(context, pin)
                    hasPinSet = true
                }
            )
        } else if (!tab.isPinVerified) {
            VaultUnlockScreen(
                onSuccess = { pin ->
                    tab.activePassword = pin
                    tab.isPinVerified = true
                }
            )
        } else {
            VaultExplorerScreen(tab)
        }
    }
}

@Composable
fun VaultSetupScreen(onPinCreated: (String) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Setup your secure 4-digit PIN") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Create Secure Wallet",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Enter code indicator
        val activePinLength = if (step == 1) pin1.length else pin2.length
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (i in 1..4) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= activePinLength) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Keypad grid
        PinKeypad(
            onKeyPress = { key ->
                if (step == 1) {
                    if (pin1.length < 4) {
                        pin1 += key
                        if (pin1.length == 4) {
                            step = 2
                            message = "Confirm your 4-digit PIN"
                        }
                    }
                } else {
                    if (pin2.length < 4) {
                        pin2 += key
                        if (pin2.length == 4) {
                            if (pin1 == pin2) {
                                onPinCreated(pin1)
                            } else {
                                step = 1
                                pin1 = ""
                                pin2 = ""
                                message = "PINs do not match. Try again."
                            }
                        }
                    }
                }
            },
            onBackspace = {
                if (step == 1) {
                    if (pin1.isNotEmpty()) pin1 = pin1.dropLast(1)
                } else {
                    if (pin2.isNotEmpty()) pin2 = pin2.dropLast(1)
                }
            }
        )
    }
}

@Composable
fun VaultUnlockScreen(onSuccess: (String) -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Enter your 4-digit PIN to access vault") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Vault Locked",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Indicator dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (i in 1..4) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        PinKeypad(
            onKeyPress = { key ->
                if (pin.length < 4) {
                    pin += key
                    isError = false
                    message = "Enter your 4-digit PIN to access vault"
                    if (pin.length == 4) {
                        if (VaultService.verifyPassword(context, pin)) {
                            onSuccess(pin)
                        } else {
                            pin = ""
                            isError = true
                            message = "Incorrect PIN code. Try again."
                        }
                    }
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            }
        )
    }
}

@Composable
fun PinKeypad(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "C", "0", "⌫"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.width(280.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(keys) { key ->
            val isAction = key == "C" || key == "⌫"
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (isAction) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                    .clickable {
                        when (key) {
                            "C" -> {
                                // backspace callback is handles differently
                            }
                            "⌫" -> onBackspace()
                            else -> onKeyPress(key)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultExplorerScreen(tab: VaultTab) {
    val context = LocalContext.current
    var records by remember { mutableStateOf(VaultService.loadRecords(context)) }
    var showFilePicker by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pickedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showFilePicker = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Protect File")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (records.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EnhancedEncryption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Protected Wallet is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap '+' below to import files or folders securely.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records) { record ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        // Decrypt temporary and view!
                                        isBusy = true
                                        scope.launch {
                                            try {
                                                val tempFile = withContext(Dispatchers.IO) {
                                                    VaultService.decryptTemporary(context, record, tab.activePassword)
                                                }
                                                withContext(Dispatchers.Main) {
                                                    isBusy = false
                                                    val holder = LocalFileHolder(tempFile)
                                                    holder.open(context, false, false, null)
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    isBusy = false
                                                    Toast.makeText(context, "Error opening: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (record.isFolder) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (record.isFolder) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.originalName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Locked: ${record.lockedAt.substringBefore("T")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Restore action button
                                IconButton(onClick = {
                                    isBusy = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                VaultService.unlockFile(context, record, tab.activePassword)
                                            }
                                            withContext(Dispatchers.Main) {
                                                isBusy = false
                                                records = VaultService.loadRecords(context)
                                                Toast.makeText(context, "Restored file successfully", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isBusy = false
                                                Toast.makeText(context, "Unlock failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Rounded.Restore, contentDescription = "Restore File", tint = MaterialTheme.colorScheme.primary)
                                }
                                // Delete permanently button
                                IconButton(onClick = {
                                    File(record.scrambledPath).delete()
                                    val currentRecords = VaultService.loadRecords(context).toMutableList()
                                    currentRecords.removeAll { it.id == record.id }
                                    VaultService.saveRecords(context, currentRecords)
                                    records = currentRecords
                                    Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (isBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    FileSelectionDialog(
        show = showFilePicker,
        onDismissRequest = { showFilePicker = false },
        onItemsSelected = { selected ->
            pickedFiles = selected
            if (selected.isNotEmpty()) {
                showImportDialog = true
            }
        }
    )

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Security Option") },
            text = { Text("Choose import style. Sandbox copies items into application private sandbox directory. In-place scrambles in same folder.") },
            confirmButton = {
                Button(onClick = {
                    showImportDialog = false
                    isBusy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                for (file in pickedFiles) {
                                    if (file.isDirectory) {
                                        VaultService.lockDirectory(context, file, tab.activePassword, false)
                                    } else {
                                        VaultService.lockFile(context, file, tab.activePassword, false)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                isBusy = false
                                records = VaultService.loadRecords(context)
                                Toast.makeText(context, "Protected ${pickedFiles.size} items", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isBusy = false
                                Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) {
                    Text("Secure Import (Sandbox)")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showImportDialog = false
                    isBusy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                for (file in pickedFiles) {
                                    if (file.isDirectory) {
                                        VaultService.lockDirectory(context, file, tab.activePassword, true)
                                    } else {
                                        VaultService.lockFile(context, file, tab.activePassword, true)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                isBusy = false
                                records = VaultService.loadRecords(context)
                                Toast.makeText(context, "Obfuscated ${pickedFiles.size} items", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isBusy = false
                                Toast.makeText(context, "Obfuscated error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) {
                    Text("In-Place Scramble (Fast)")
                }
            }
        )
    }
}
