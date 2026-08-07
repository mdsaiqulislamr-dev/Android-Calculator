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
import com.example.calculator.ui.theme.CalculatorTheme
import java.io.File
import java.io.FileOutputStream

object AppColors {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF161616)
    val SurfaceElevated = Color(0xFF1E1E1E)
    val SurfaceCard = Color(0xFF252525)
    val ButtonDark = Color(0xFF2A2A2A)
    val ButtonGray = Color(0xFF3D3D3D)
    val Accent = Color(0xFFFF9F0A)
    val AccentSoft = Color(0x33FF9F0A)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB0B0B0)
    val TextMuted = Color(0xFF6E6E6E)
    val Danger = Color(0xFFFF453A)
    val Divider = Color(0xFF2A2A2A)
}

enum class Screen { Calculator, Vault, Settings, ChangePin }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
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
            onBack = { screen = Screen.Vault },
            onChangePin = { screen = Screen.ChangePin },
            prefs = prefs
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
        display = "0"
        previousValue = 0.0
        operation = ""
        newNumber = true
    }

    fun onDelete() {
        if (display.length > 1) display = display.dropLast(1)
        else {
            display = "0"
            newNumber = true
        }
    }

    fun formatPrev(): String =
        if (previousValue % 1 == 0.0) previousValue.toLong().toString() else previousValue.toString()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (operation.isNotEmpty()) {
                        Text(
                            text = "${formatPrev()} $operation",
                            color = AppColors.TextMuted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = display,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    row.forEach { label ->
                        val isOperator = label in listOf("÷", "×", "-", "+", "=")
                        val isSpecial = label in listOf("C", "⌫", "%")
                        val weight = if (label == "0") 2.1f else 1f
                        val bgColor = when {
                            isOperator || label == "=" -> AppColors.Accent
                            isSpecial -> AppColors.ButtonGray
                            else -> AppColors.ButtonDark
                        }
                        val textColor = if (isSpecial) AppColors.Background else AppColors.TextPrimary

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(72.dp)
                                .clip(CircleShape)
                                .background(bgColor)
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
                                                if (newNumber) {
                                                    display = "0."
                                                    newNumber = false
                                                } else display += "."
                                            }
                                        }
                                        else -> onNumberClick(label)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultScreen(onLock: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val vaultDir = remember {
        File(context.filesDir, "secret_vault").apply { if (!exists()) mkdirs() }
    }
    var files by remember {
        mutableStateOf(vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList())
    }
    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }
    var viewMode by remember { mutableStateOf("grid") } // grid or list

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                val name = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                val dest = File(vaultDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        files = vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        // Top App Bar
        Surface(
            color = AppColors.Surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Vault",
                        color = AppColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${files.size} items · secured",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp
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

        // Add button
        Box(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(Modifier = Modifier.width(8.dp))
                Text("Add Photos, Videos & Files", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = AppColors.TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No files yet", color = AppColors.TextSecondary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Add photos, videos or any file", color = AppColors.TextMuted, fontSize = 13.sp)
                }
            }
        } else if (viewMode == "grid") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    MediaThumbnailCard(
                        file = file,
                        onClick = { openFile(context, file) },
                        onLongClick = { showDeleteConfirm = file }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(files) { file ->
                    MediaListItem(
                        file = file,
                        onClick = { openFile(context, file) },
                        onDelete = { showDeleteConfirm = file }
                    )
                }
            }
        }
    }

    showDeleteConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = AppColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete file?", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("Permanently delete\n${file.name}?", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    file.delete()
                    files = vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                    showDeleteConfirm = null
                }) { Text("Delete", color = AppColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
fun MediaThumbnailCard(file: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val isImage = isImageFile(file.name)
    val isVideo = isVideoFile(file.name)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceCard)
            .clickable(onClick = onClick)
    ) {
        when {
            isImage -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isVideo -> {
                val bitmap = remember(file.path) { loadVideoThumbnail(file) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Videocam, null, tint = AppColors.TextMuted, modifier = Modifier.size(32.dp))
                    }
                }
                // Play overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            fileTypeIcon(file.name),
                            null,
                            tint = AppColors.Accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            file.extension.uppercase().ifEmpty { "FILE" },
                            color = AppColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Bottom gradient + name
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                file.name,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MediaListItem(file: File, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val isImage = isImageFile(file.name)
    val isVideo = isVideoFile(file.name)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.SurfaceElevated
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isImage -> AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    isVideo -> {
                        val bitmap = remember(file.path) { loadVideoThumbnail(file) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Videocam, null, tint = AppColors.TextMuted)
                        }
                    }
                    else -> Icon(fileTypeIcon(file.name), null, tint = AppColors.Accent)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(formatFileSize(file.length()), color = AppColors.TextMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, null, tint = AppColors.Danger, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    prefs: android.content.SharedPreferences
) {
    val context = LocalContext.current
    val vaultDir = File(context.filesDir, "secret_vault")
    val fileCount = vaultDir.listFiles()?.size ?: 0
    val totalSize = vaultDir.listFiles()?.sumOf { it.length() } ?: 0L
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        // Header
        Surface(color = AppColors.Surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextPrimary)
                }
                Text(
                    "Settings",
                    color = AppColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingsSectionTitle("Security")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Pin,
                    title = "Change PIN",
                    subtitle = "Update your secret unlock code",
                    onClick = onChangePin
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSectionTitle("Storage")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Folder,
                    title = "Vault files",
                    subtitle = "$fileCount items · ${formatFileSize(totalSize)}",
                    onClick = {}
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear all files",
                    subtitle = "Permanently delete everything in vault",
                    titleColor = AppColors.Danger,
                    onClick = { showClearConfirm = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSectionTitle("About")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Calculator Vault",
                    subtitle = "Version 2.0 · Hidden private storage",
                    onClick = {}
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "How to unlock",
                    subtitle = "Type your PIN on calculator and press =",
                    onClick = {}
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = AppColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Clear vault?", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("This will permanently delete all $fileCount files. This cannot be undone.", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vaultDir.listFiles()?.forEach { it.delete() }
                    showClearConfirm = false
                    onBack()
                }) { Text("Clear all", color = AppColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        title.uppercase(),
        color = AppColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = AppColors.TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.SurfaceElevated
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = AppColors.TextMuted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ChangePinScreen(onPinChanged: (String) -> Unit, onBack: () -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Surface(color = AppColors.Surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextPrimary)
                }
                Text("Change PIN", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Pin, null, tint = AppColors.Accent, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("New secret PIN", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("4–8 digits", color = AppColors.TextMuted, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(32.dp))

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
            ) {
                Text("Save PIN", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
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

fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadVideoThumbnail(file: File): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun isImageFile(name: String): Boolean {
    val l = name.lowercase()
    return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") ||
            l.endsWith(".webp") || l.endsWith(".gif") || l.endsWith(".bmp")
}

fun isVideoFile(name: String): Boolean {
    val l = name.lowercase()
    return l.endsWith(".mp4") || l.endsWith(".mkv") || l.endsWith(".avi") ||
            l.endsWith(".mov") || l.endsWith(".webm") || l.endsWith(".3gp")
}

fun fileTypeIcon(name: String): ImageVector {
    val l = name.lowercase()
    return when {
        l.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        l.endsWith(".mp3") || l.endsWith(".wav") || l.endsWith(".m4a") -> Icons.Default.AudioFile
        l.endsWith(".doc") || l.endsWith(".docx") || l.endsWith(".txt") -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index)
        }
    }
    return name
}

fun getMimeType(name: String): String {
    val l = name.lowercase()
    return when {
        l.endsWith(".mp4") -> "video/mp4"
        l.endsWith(".mkv") -> "video/x-matroska"
        l.endsWith(".jpg") || l.endsWith(".jpeg") -> "image/jpeg"
        l.endsWith(".png") -> "image/png"
        l.endsWith(".webp") -> "image/webp"
        l.endsWith(".gif") -> "image/gif"
        l.endsWith(".pdf") -> "application/pdf"
        l.endsWith(".mp3") -> "audio/mpeg"
        else -> "*/*"
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
