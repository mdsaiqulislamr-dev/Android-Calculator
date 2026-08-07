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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.calculator.ui.theme.CalculatorTheme
import java.io.File
import java.io.FileOutputStream

object AppColors {
    val Background = Color(0xFF0D0D0D)
    val Surface = Color(0xFF1A1A1A)
    val SurfaceElevated = Color(0xFF242424)
    val ButtonDark = Color(0xFF2C2C2C)
    val ButtonGray = Color(0xFF3A3A3A)
    val Accent = Color(0xFFFF9F0A)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFABABAB)
    val TextMuted = Color(0xFF6B6B6B)
    val Danger = Color(0xFFFF453A)
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
    var showSettings by remember { mutableStateOf(false) }

    val secretPin = prefs.getString("secret_pin", "1234") ?: "1234"

    when {
        !isVaultOpen -> CalculatorScreen(secretPin) { isVaultOpen = true }
        showChangePin -> ChangePinScreen(
            onPinChanged = {
                prefs.edit().putString("secret_pin", it).apply()
                showChangePin = false
            },
            onBack = { showChangePin = false }
        )
        showSettings -> SettingsScreen(
            fileCount = File(context.filesDir, "secret_vault").listFiles()?.size ?: 0,
            onBack = { showSettings = false },
            onChangePin = { showChangePin = true },
            onLock = { isVaultOpen = false; showSettings = false }
        )
        else -> VaultScreen(
            onLock = { isVaultOpen = false },
            onChangePin = { showChangePin = true },
            onSettings = { showSettings = true }
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
        if (newNumber) { display = number; newNumber = false }
        else {
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

    fun onClear() { display = "0"; previousValue = 0.0; operation = ""; newNumber = true }
    fun onDelete() {
        if (display.length > 1) display = display.dropLast(1)
        else { display = "0"; newNumber = true }
    }
    fun formatPrev() = if (previousValue % 1 == 0.0) previousValue.toLong().toString() else previousValue.toString()

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), contentAlignment = Alignment.BottomEnd) {
                Column(horizontalAlignment = Alignment.End) {
                    if (operation.isNotEmpty()) {
                        Text("${formatPrev()} $operation", color = AppColors.TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Light)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        display, color = AppColors.TextPrimary,
                        fontSize = if (display.length > 8) 42.sp else 56.sp,
                        fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
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
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { label ->
                        val isOperator = label in listOf("÷", "×", "-", "+", "=")
                        val isSpecial = label in listOf("C", "⌫", "%")
                        val weight = if (label == "0") 2.1f else 1f
                        val bgColor = when {
                            isOperator -> AppColors.Accent
                            isSpecial -> AppColors.ButtonGray
                            else -> AppColors.ButtonDark
                        }
                        val textColor = if (isSpecial) AppColors.Background else AppColors.TextPrimary

                        Box(
                            modifier = Modifier.weight(weight).height(74.dp).clip(CircleShape).background(bgColor)
                                .clickable {
                                    when (label) {
                                        "C" -> onClear()
                                        "⌫" -> onDelete()
                                        "%" -> display = ((display.toDoubleOrNull() ?: 0.0) / 100).toString()
                                        "÷", "×", "-", "+" -> onOperationClick(label)
                                        "=" -> onEqualsClick()
                                        "." -> if (!display.contains(".")) {
                                            if (newNumber) { display = "0."; newNumber = false } else display += "."
                                        }
                                        else -> onNumberClick(label)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultScreen(onLock: () -> Unit, onChangePin: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val vaultDir = remember { File(context.filesDir, "secret_vault").apply { if (!exists()) mkdirs() } }
    var files by remember { mutableStateOf(vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val name = getFileName(context, it) ?: "file_${System.currentTimeMillis()}"
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(File(vaultDir, name)).use { output -> input.copyTo(output) }
                }
                files = vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(AppColors.Surface).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Secret Vault", color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${files.size} items secured", color = AppColors.TextMuted, fontSize = 13.sp)
            }
            Text("Settings", color = AppColors.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.clickable { onSettings() }.padding(8.dp))
            Text("PIN", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onChangePin() }.padding(8.dp))
            Text("Lock", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onLock() }.padding(8.dp))
        }

        Button(
            onClick = { filePicker.launch("*/*") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            Text("+  Add Photos, Videos & Files", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No files yet", color = AppColors.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Add photos, videos or any file", color = AppColors.TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(files) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.SurfaceElevated)
                            .clickable {
                                try {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, getMimeType(file.name))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                } catch (_: Exception) {}
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(getFileLabel(file.name), color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(formatFileSize(file.length()), color = AppColors.TextMuted, fontSize = 12.sp)
                        }
                        Text("Delete", color = AppColors.Danger, fontSize = 13.sp,
                            modifier = Modifier.clickable { showDeleteConfirm = file })
                    }
                }
            }
        }
    }

    showDeleteConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = AppColors.Surface,
            title = { Text("Delete file?", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("Delete ${file.name}?", color = AppColors.TextSecondary) },
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
fun SettingsScreen(fileCount: Int, onBack: () -> Unit, onChangePin: () -> Unit, onLock: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(AppColors.Surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Back", color = AppColors.TextSecondary, modifier = Modifier.clickable { onBack() }.padding(end = 16.dp))
            Text("Settings", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.SurfaceElevated).padding(20.dp)
            ) {
                Text("Vault Storage", color = AppColors.TextMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("$fileCount files secured", color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Text("SECURITY", color = AppColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            SettingsRow("Change PIN", "Update your secret code", onChangePin)
            SettingsRow("Lock Vault", "Return to calculator", onLock)

            Text("ABOUT", color = AppColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            SettingsRow("Calculator Vault", "Version 2.0", {})
            SettingsRow("How to unlock", "Type PIN then press =", {})

            Text(
                "Files are stored in private app storage. Other apps cannot access them.",
                color = AppColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.SurfaceElevated)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = AppColors.TextMuted, fontSize = 12.sp)
        }
        Text(">", color = AppColors.TextMuted)
    }
}

@Composable
fun ChangePinScreen(onPinChanged: (String) -> Unit, onBack: () -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("Change PIN", color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Enter a new 4–8 digit PIN", color = AppColors.TextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
            label = { Text("New PIN") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent, unfocusedBorderColor = AppColors.ButtonGray,
                focusedLabelColor = AppColors.Accent, unfocusedLabelColor = AppColors.TextMuted,
                cursorColor = AppColors.Accent, focusedTextColor = AppColors.TextPrimary, unfocusedTextColor = AppColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
            label = { Text("Confirm PIN") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent, unfocusedBorderColor = AppColors.ButtonGray,
                focusedLabelColor = AppColors.Accent, unfocusedLabelColor = AppColors.TextMuted,
                cursorColor = AppColors.Accent, focusedTextColor = AppColors.TextPrimary, unfocusedTextColor = AppColors.TextPrimary
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            Text("Save PIN", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("Cancel", color = AppColors.TextSecondary) }
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

fun getFileLabel(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov") -> "VID"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") -> "IMG"
        lower.endsWith(".pdf") -> "PDF"
        lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") -> "AUD"
        else -> "FILE"
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
