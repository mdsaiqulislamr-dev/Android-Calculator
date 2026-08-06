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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.calculator.ui.theme.CalculatorTheme
import java.io.File
import java.io.FileOutputStream

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
            else display += number
        }
    }

    fun onOperationClick(op: String) {
        previousValue = display.toDoubleOrNull() ?: 0.0
        operation = op
        newNumber = true
    }

    fun onEqualsClick() {
        // Secret PIN check
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C))
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = display,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.End,
            maxLines = 1
        )

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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { label ->
                    val isOperator = label in listOf("÷", "×", "-", "+", "=")
                    val isSpecial = label in listOf("C", "⌫", "%")

                    val backgroundColor = when {
                        label == "=" -> Color(0xFFFF9500)
                        isOperator -> Color(0xFFFF9500)
                        isSpecial -> Color(0xFFA5A5A5)
                        else -> Color(0xFF333333)
                    }

                    val textColor = when {
                        isSpecial -> Color.Black
                        else -> Color.White
                    }

                    val weight = if (label == "0") 2f else 1f

                    Button(
                        onClick = {
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
                        modifier = Modifier
                            .weight(weight)
                            .height(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = backgroundColor,
                            contentColor = textColor
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium
                        )
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

    var files by remember { mutableStateOf(vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()) }
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
            .background(Color(0xFF121212))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1C))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Secret Vault",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChangePin) {
                    Text("PIN", color = Color(0xFFFF9500))
                }
                TextButton(onClick = onLock) {
                    Text("Lock", color = Color.White)
                }
            }
        }

        // Add File Button
        Button(
            onClick = { filePicker.launch("*/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
        ) {
            Text("+ Add Photo / Video / File")
        }

        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No files yet.\nTap + to add photos, videos or any file.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(files) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = getFileEmoji(file.name),
                                fontSize = 28.sp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatFileSize(file.length()),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(onClick = { showDeleteConfirm = file }) {
                                Text("Delete", color = Color.Red, fontSize = 12.sp)
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
            title = { Text("Delete file?") },
            text = { Text("Are you sure you want to delete ${file.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    file.delete()
                    files = vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
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
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Change Secret PIN",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
            label = { Text("New PIN (numbers only)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
            label = { Text("Confirm PIN") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Text(error, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    newPin.length < 4 -> error = "PIN must be at least 4 digits"
                    newPin != confirmPin -> error = "PINs do not match"
                    else -> onPinChanged(newPin)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save New PIN")
        }

        TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Cancel", color = Color.Gray)
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
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
