package com.example.calculator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.calculator.ui.theme.CalculatorTheme
import java.io.File
import java.io.FileOutputStream

// Professional Color Palette
object AppColors {
    val Background = Color(0xFF0D0D0D)
    val Surface = Color(0xFF1A1A1A)
    val SurfaceElevated = Color(0xFF242424)
    val ButtonDark = Color(0xFF2C2C2C)
    val ButtonGray = Color(0xFF3A3A3A)
    val Accent = Color(0xFFFF9F0A)
    val AccentDark = Color(0xFFE08A00)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFABABAB)
    val TextMuted = Color(0xFF6B6B6B)
    val Danger = Color(0xFFFF453A)
    val Success = Color(0xFF30D158)
}

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
    var isVaultOpen by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }

    val secretPin = prefs.getString("secret_pin", "1234") ?: "1234"

    if (isVaultOpen) {
        if (showChangePin) {
            ChangePinScreen(
                onPinChanged = { newPin ->
                    prefs.edit().putString("secret_pin", newPin).apply()
                    showChangePin = false
                },
                onBack = { showChangePin = false }
            )
        } else {
            VaultScreen(
                onLock = { isVaultOpen = false },
                onChangePin = { showChangePin = true }
            )
        }
    } else {
        CalculatorScreen(
            secretPin = secretPin,
            onVaultUnlock = { isVaultOpen = true }
        )
    }
}

@Composable
fun CalculatorScreen(
    secretPin: String,
    onVaultUnlock: () -> Unit
) {
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
            if (result % 1 == 0.0) result.toInt().toString()
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
        if (display.length > 1) {
            display = display.dropLast(1)
        } else {
            display = "0"
            newNumber = true
        }
    }

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
            // Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (operation.isNotEmpty()) {
                        Text(
                            text = "${previousValue.toLongOrNull() ?: previousValue} $operation",
                            color = AppColors.TextMuted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = display,
                        color = AppColors.TextPrimary,
                        fontSize = if (display.length > 8) 42.sp else 56.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Buttons
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
                            label == "=" -> AppColors.Accent
                            isOperator -> AppColors.Accent
                            isSpecial -> AppColors.ButtonGray
                            else -> AppColors.ButtonDark
                        }

                        val textColor = when {
                            isSpecial -> AppColors.Background
                            else -> AppColors.TextPrimary
                        }

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(74.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .clickable {
                                    when (label) {
                                        "C" -> onClear()
                                        "⌫" -> onDelete()
                                        "%" -> {
                                            val value = display.toDoubleOrNull() ?: 0.0
                                            display = (value / 100).toString()
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
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultScreen(
    onLock: () -> Unit,
    onChangePin: () -> Unit
) {
    val context = LocalContext.current
    val vaultDir = remember {
        File(context.filesDir, "secret_vault").apply { if (!exists()) mkdirs() }
    }

    var files by remember {
        mutableStateOf(vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList())
    }
    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val name = getFileName(context, it) ?: "file_${System.currentTimeMillis()}"
                val dest = File(vaultDir, name)
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                files = vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        // Professional Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(AppColors.Surface, AppColors.Background)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Vault",
                        color = AppColors.TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${files.size} items secured",
                        color = AppColors.TextMuted,
                        fontSize = 13.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = onChangePin,
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.SurfaceElevated
                    ) {
                        Text(
                            text = "PIN",
                            color = AppColors.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    Surface(
                        onClick = onLock,
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.SurfaceElevated
                    ) {
                        Text(
                            text = "Lock",
                            color = AppColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // Add Button
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
            ) {
                Text(
                    text = "+  Add Files",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }

        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📭",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No files yet",
                        color = AppColors.TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add photos, videos or any file",
                        color = AppColors.TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(files) { file ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, getMimeType(file.name))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = AppColors.SurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = getFileEmoji(file.name), fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    color = AppColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = formatFileSize(file.length()),
                                    color = AppColors.TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(onClick = { showDeleteConfirm = file }) {
                                Text("Delete", color = AppColors.Danger, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = AppColors.Surface,
            title = {
                Text("Delete file?", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete\n${file.name}?",
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    file.delete()
                    files = vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = AppColors.Danger, fontWeight = FontWeight.SemiBold)
                }
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
fun ChangePinScreen(
    onPinChanged: (String) -> Unit,
    onBack: () -> Unit
) {
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
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Change PIN",
            color = AppColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter a new 4–8 digit PIN",
            color = AppColors.TextMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
            label = { Text("New PIN") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.ButtonGray,
                focusedLabelColor = AppColors.Accent,
                unfocusedLabelColor = AppColors.TextMuted,
                cursorColor = AppColors.Accent,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
            label = { Text("Confirm PIN") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.ButtonGray,
                focusedLabelColor = AppColors.Accent,
                unfocusedLabelColor = AppColors.TextMuted,
                cursorColor = AppColors.Accent,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(error, color = AppColors.Danger, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

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
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            Text("Save PIN", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            Text("Cancel", color = AppColors.TextSecondary)
        }
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

fun getFileEmoji(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov") -> "🎬"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") -> "🖼️"
        lower.endsWith(".pdf") -> "📄"
        lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") -> "🎵"
        else -> "📁"
    }
}

fun getMimeType(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".mkv") -> "video/x-matroska"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".mp3") -> "audio/mpeg"
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
