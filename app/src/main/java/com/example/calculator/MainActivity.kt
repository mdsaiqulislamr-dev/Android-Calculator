package com.example.calculator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.calculator.ui.theme.CalculatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AppColors {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141414)
    val SurfaceElevated = Color(0xFF1E1E1E)
    val SurfaceCard = Color(0xFF252525)
    val ButtonDark = Color(0xFF2C2C2C)
    val ButtonGray = Color(0xFF3D3D3D)
    val Accent = Color(0xFFFF9F0A)
    val AccentSoft = Color(0x33FF9F0A)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB0B0B0)
    val TextMuted = Color(0xFF6E6E6E)
    val Danger = Color(0xFFFF453A)
    val Success = Color(0xFF30D158)
    val Divider = Color(0xFF2A2A2A)
}

enum class Screen { Calculator, Vault, Settings, ChangePin }

enum class FileType { IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }

data class VaultFile(
    val file: File,
    val type: FileType
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.Calculator) }
    val secretPin = prefs.getString("secret_pin", "1234") ?: "1234"

    when (screen) {
        Screen.Calculator -> CalculatorScreen(
            secretPin = secretPin,
            onVaultUnlock = { screen = Screen.Vault }
        )
        Screen.Vault -> VaultScreen(
            onLock = { screen = Screen.Calculator },
            onSettings = { screen = Screen.Settings }
        )
        Screen.Settings -> SettingsScreen(
            secretPin = secretPin,
            onBack = { screen = Screen.Vault },
            onChangePin = { screen = Screen.ChangePin },
            onLock = { screen = Screen.Calculator }
        )
        Screen.ChangePin -> ChangePinScreen(
            onPinChanged = { newPin ->
                prefs.edit().putString("secret_pin", newPin).apply()
                screen = Screen.Settings
            },
            onBack = { screen = Screen.Settings }
        )
    }
}

// ──────────────────── Calculator ────────────────────

@Composable
fun CalculatorScreen(secretPin: String, onVaultUnlock: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var previousValue by remember { mutableStateOf(0.0) }
    var operation by remember { mutableStateOf("") }
    var newNumber by remember { mutableStateOf(true) }

    fun onNumberClick(number: String) {
        if (newNumber) {
            display = number
            newNumber = false
        } else {
            if (display == "0") display = number
            else if (display.length < 12) display += number
        }
    }

    fun onOperationClick(op: String) {
        previousValue = display.toDoubleOrNull() ?: 0.0
        operation = op
        newNumber = true
    }

    fun onEqualsClick() {
        if (display == secretPin) {
            onVaultUnlock()
            display = "0"
            newNumber = true
            return
        }
        val current = display.toDoubleOrNull() ?: 0.0
        val result = when (operation) {
            "+" -> previousValue + current
            "-" -> previousValue - current
            "×" -> previousValue * current
            "÷" -> if (current != 0.0) previousValue / current else Double.NaN
            else -> current
        }
        display = if (result.isNaN()) "Error" else {
            if (result % 1 == 0.0) result.toLong().toString()
            else String.format("%.8f", result).trimEnd('0').trimEnd('.')
        }
        operation = ""
        newNumber = true
    }

    fun onClear() {
        display = "0"; previousValue = 0.0; operation = ""; newNumber = true
    }

    fun onDelete() {
        if (display.length > 1) display = display.dropLast(1)
        else { display = "0"; newNumber = true }
    }

    fun formatPrev(): String =
        if (previousValue % 1 == 0.0) previousValue.toLong().toString() else previousValue.toString()

    Box(Modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (operation.isNotEmpty()) {
                        Text(
                            "${formatPrev()} $operation",
                            color = AppColors.TextMuted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(Modifier = Modifier.height(4.dp))
                    }
                    Text(
                        display,
                        color = AppColors.TextPrimary,
                        fontSize = if (display.length > 8) 40.sp else 56.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val buttons = listOf(
                listOf("C", "⌫", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    row.forEach { label ->
                        val isOp = label in listOf("÷", "×", "-", "+", "=")
                        val isSpecial = label in listOf("C", "⌫", "%")
                        val weight = if (label == "0") 2.1f else 1f
                        val bg = when {
                            isOp -> AppColors.Accent
                            isSpecial -> AppColors.ButtonGray
                            else -> AppColors.ButtonDark
                        }
                        val tc = if (isSpecial) AppColors.Background else AppColors.TextPrimary

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(72.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable {
                                    when (label) {
                                        "C" -> onClear()
                                        "⌫" -> onDelete()
                                        "%" -> {
                                            val v = display.toDoubleOrNull() ?: 0.0
                                            display = (v / 100).toString()
                                        }
                                        "÷", "×", "-", "+" -> onOperationClick(label)
                                        "=" -> onEqualsClick()
                                        "." -> {
                                            if (!display.contains(".")) {
                                                if (newNumber) { display = "0."; newNumber = false }
                                                else display += "."
                                            }
                                        }
                                        else -> onNumberClick(label)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = tc, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────── Vault ────────────────────

@Composable
fun VaultScreen(onLock: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val vaultDir = remember {
        File(context.filesDir, "secret_vault").apply { if (!exists()) mkdirs() }
    }

    var files by remember {
        mutableStateOf(loadVaultFiles(vaultDir))
    }
    var showDelete by remember { mutableStateOf<VaultFile?>(null) }
    var viewMode by remember { mutableStateOf("grid") } // grid | list

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            try {
                val name = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                val dest = File(vaultDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {}
        }
        files = loadVaultFiles(vaultDir)
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        // Top App Bar
        Surface(color = AppColors.Surface, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Secret Vault",
                        color = AppColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${files.size} secured items",
                        color = AppColors.TextMuted,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = {
                    viewMode = if (viewMode == "grid") "list" else "grid"
                }) {
                    Icon(
                        if (viewMode == "grid") Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle view",
                        tint = AppColors.TextSecondary
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AppColors.TextSecondary)
                }
                IconButton(onClick = onLock) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock", tint = AppColors.Accent)
                }
            }
        }

        // FAB-style Add button
        Button(
            onClick = { filePicker.launch("*/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
            Spacer(Modifier = Modifier.width(8.dp))
            Text("Add Photos, Videos & Files", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No files yet", color = AppColors.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap + to hide photos, videos or files", color = AppColors.TextMuted, fontSize = 14.sp)
                }
            }
        } else if (viewMode == "grid") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files) { item ->
                    MediaThumbnailCard(
                        item = item,
                        onClick = { openFile(context, item.file) },
                        onLongClick = { showDelete = item }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files) { item ->
                    MediaListItem(
                        item = item,
                        onClick = { openFile(context, item.file) },
                        onDelete = { showDelete = item }
                    )
                }
            }
        }
    }

    showDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { showDelete = null },
            containerColor = AppColors.SurfaceElevated,
            title = { Text("Delete file?", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("Permanently delete\n${item.file.name}?", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    item.file.delete()
                    files = loadVaultFiles(vaultDir)
                    showDelete = null
                }) { Text("Delete", color = AppColors.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
fun MediaThumbnailCard(item: VaultFile, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceCard)
            .clickable(onClick = onClick)
    ) {
        when (item.type) {
            FileType.IMAGE -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            FileType.VIDEO -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.file)
                        .videoFrameMillis(1000)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Play overlay
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x44000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        fileTypeIcon(item.type),
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        item.file.extension.uppercase().ifEmpty { "FILE" },
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom gradient + name
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                item.file.name,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MediaListItem(item: VaultFile, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.SurfaceElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                when (item.type) {
                    FileType.IMAGE, FileType.VIDEO -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.file)
                                .apply { if (item.type == FileType.VIDEO) videoFrameMillis(1000) }
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.type == FileType.VIDEO) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> Icon(
                        fileTypeIcon(item.type),
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.file.name,
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "${formatFileSize(item.file.length())}  ·  ${item.type.name.lowercase()}",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = AppColors.Danger)
            }
        }
    }
}

// ──────────────────── Settings ────────────────────

@Composable
fun SettingsScreen(
    secretPin: String,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val vaultDir = File(context.filesDir, "secret_vault")
    val files = remember { loadVaultFiles(vaultDir) }
    val totalSize = files.sumOf { it.file.length() }
    val imageCount = files.count { it.type == FileType.IMAGE }
    val videoCount = files.count { it.type == FileType.VIDEO }
    val otherCount = files.size - imageCount - videoCount

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        // Top bar
        Surface(color = AppColors.Surface, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.TextPrimary)
                }
                Text(
                    "Settings",
                    color = AppColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.SurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Vault Storage", color = AppColors.TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            formatFileSize(totalSize),
                            color = AppColors.TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            StatChip("${files.size}", "Total")
                            StatChip("$imageCount", "Photos")
                            StatChip("$videoCount", "Videos")
                            StatChip("$otherCount", "Other")
                        }
                    }
                }
            }

            // Security section
            item {
                Text(
                    "SECURITY",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Pin,
                    title = "Change PIN",
                    subtitle = "Current PIN is set",
                    onClick = onChangePin
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Lock Vault Now",
                    subtitle = "Return to calculator",
                    onClick = onLock
                )
            }

            // About section
            item {
                Text(
                    "ABOUT",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Calculator Vault",
                    subtitle = "Version 2.0 · Private storage",
                    onClick = {}
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = "How to unlock",
                    subtitle = "Type your PIN on calculator and press =",
                    onClick = {}
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Files are stored in app private storage.\nNo other app can access them.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AppColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AppColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.SurfaceElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = AppColors.TextMuted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextMuted)
        }
    }
}

// ──────────────────── Change PIN ────────────────────

@Composable
fun ChangePinScreen(onPinChanged: (String) -> Unit, onBack: () -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(Icons.Default.Pin, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Change PIN", color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Enter a new 4–8 digit PIN", color = AppColors.TextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(36.dp))

        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
            label = { Text("New PIN") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = pinFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
            label = { Text("Confirm PIN") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = pinFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(error, color = AppColors.Danger, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                when {
                    newPin.length < 4 -> error = "PIN must be at least 4 digits"
                    newPin != confirmPin -> error = "PINs do not match"
                    else -> onPinChanged(newPin)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            Text("Save PIN", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Cancel", color = AppColors.TextSecondary)
        }
    }
}

@Composable
fun pinFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.Accent,
    unfocusedBorderColor = AppColors.ButtonGray,
    focusedLabelColor = AppColors.Accent,
    unfocusedLabelColor = AppColors.TextMuted,
    cursorColor = AppColors.Accent,
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary
)

// ──────────────────── Helpers ────────────────────

fun loadVaultFiles(dir: File): List<VaultFile> {
    return dir.listFiles()
        ?.sortedByDescending { it.lastModified() }
        ?.map { VaultFile(it, detectFileType(it.name)) }
        ?: emptyList()
}

fun detectFileType(name: String): FileType {
    val e = name.lowercase()
    return when {
        e.endsWith(".jpg") || e.endsWith(".jpeg") || e.endsWith(".png") ||
        e.endsWith(".webp") || e.endsWith(".gif") || e.endsWith(".bmp") -> FileType.IMAGE
        e.endsWith(".mp4") || e.endsWith(".mkv") || e.endsWith(".avi") ||
        e.endsWith(".mov") || e.endsWith(".webm") || e.endsWith(".3gp") -> FileType.VIDEO
        e.endsWith(".mp3") || e.endsWith(".wav") || e.endsWith(".m4a") ||
        e.endsWith(".aac") || e.endsWith(".ogg") -> FileType.AUDIO
        e.endsWith(".pdf") || e.endsWith(".doc") || e.endsWith(".docx") ||
        e.endsWith(".txt") || e.endsWith(".xls") || e.endsWith(".xlsx") -> FileType.DOCUMENT
        else -> FileType.OTHER
    }
}

fun fileTypeIcon(type: FileType): ImageVector = when (type) {
    FileType.IMAGE -> Icons.Default.Image
    FileType.VIDEO -> Icons.Default.Videocam
    FileType.AUDIO -> Icons.Default.AudioFile
    FileType.DOCUMENT -> Icons.Default.Description
    FileType.OTHER -> Icons.Default.InsertDriveFile
}

fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = cursor.getString(idx)
        }
    }
    return name
}

fun getMimeType(name: String): String {
    val e = name.lowercase()
    return when {
        e.endsWith(".mp4") -> "video/mp4"
        e.endsWith(".mkv") -> "video/x-matroska"
        e.endsWith(".mov") -> "video/quicktime"
        e.endsWith(".jpg") || e.endsWith(".jpeg") -> "image/jpeg"
        e.endsWith(".png") -> "image/png"
        e.endsWith(".webp") -> "image/webp"
        e.endsWith(".gif") -> "image/gif"
        e.endsWith(".pdf") -> "application/pdf"
        e.endsWith(".mp3") -> "audio/mpeg"
        else -> "*/*"
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
