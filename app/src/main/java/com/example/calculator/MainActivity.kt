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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

object C {
    val Bg = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141414)
    val Card = Color(0xFF1E1E1E)
    val Card2 = Color(0xFF252525)
    val Btn = Color(0xFF2C2C2C)
    val BtnGray = Color(0xFF3D3D3D)
    val Accent = Color(0xFFFF9F0A)
    val AccentSoft = Color(0x33FF9F0A)
    val Text = Color(0xFFF5F5F5)
    val Text2 = Color(0xFFB0B0B0)
    val Muted = Color(0xFF6E6E6E)
    val Danger = Color(0xFFFF453A)
}

enum class Screen { Calc, Vault, Settings, ChangePin }
enum class FType { IMAGE, VIDEO, AUDIO, DOC, OTHER }

data class VFile(val file: File, val type: FType)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorTheme { AppRoot() }
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.Calc) }
    val pin = prefs.getString("secret_pin", "1234") ?: "1234"

    when (screen) {
        Screen.Calc -> CalcScreen(pin) { screen = Screen.Vault }
        Screen.Vault -> VaultScreen(
            onLock = { screen = Screen.Calc },
            onSettings = { screen = Screen.Settings }
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Vault },
            onChangePin = { screen = Screen.ChangePin },
            onLock = { screen = Screen.Calc }
        )
        Screen.ChangePin -> ChangePinScreen(
            onSaved = {
                prefs.edit().putString("secret_pin", it).apply()
                screen = Screen.Settings
            },
            onBack = { screen = Screen.Settings }
        )
    }
}

@Composable
fun CalcScreen(secretPin: String, onUnlock: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var prev by remember { mutableStateOf(0.0) }
    var op by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(true) }

    fun num(n: String) {
        if (fresh) { display = n; fresh = false }
        else if (display == "0") display = n
        else if (display.length < 12) display += n
    }
    fun oper(o: String) { prev = display.toDoubleOrNull() ?: 0.0; op = o; fresh = true }
    fun eq() {
        if (display == secretPin) { onUnlock(); display = "0"; fresh = true; return }
        val cur = display.toDoubleOrNull() ?: 0.0
        val r = when (op) {
            "+" -> prev + cur; "-" -> prev - cur; "×" -> prev * cur
            "÷" -> if (cur != 0.0) prev / cur else Double.NaN; else -> cur
        }
        display = if (r.isNaN()) "Error" else if (r % 1 == 0.0) r.toLong().toString()
        else String.format("%.8f", r).trimEnd('0').trimEnd('.')
        op = ""; fresh = true
    }
    fun clr() { display = "0"; prev = 0.0; op = ""; fresh = true }
    fun del() { if (display.length > 1) display = display.dropLast(1) else { display = "0"; fresh = true } }
    fun fmtPrev() = if (prev % 1 == 0.0) prev.toLong().toString() else prev.toString()

    Box(Modifier = Modifier.fillMaxSize().background(C.Bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Column(Modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalAlignment = Alignment.End) {
                if (op.isNotEmpty()) {
                    Text("${fmtPrev()} $op", color = C.Muted, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    display, color = C.Text,
                    fontSize = if (display.length > 8) 40.sp else 56.sp,
                    fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            val rows = listOf(
                listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"), listOf("1", "2", "3", "+"), listOf("0", ".", "=")
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { label ->
                        val isOp = label in listOf("÷", "×", "-", "+", "=")
                        val isSp = label in listOf("C", "⌫", "%")
                        val w = if (label == "0") 2.1f else 1f
                        val bg = when { isOp -> C.Accent; isSp -> C.BtnGray; else -> C.Btn }
                        val tc = if (isSp) C.Bg else C.Text
                        Box(
                            Modifier.weight(w).height(72.dp).clip(CircleShape).background(bg)
                                .clickable {
                                    when (label) {
                                        "C" -> clr(); "⌫" -> del()
                                        "%" -> display = ((display.toDoubleOrNull() ?: 0.0) / 100).toString()
                                        "÷", "×", "-", "+" -> oper(label); "=" -> eq()
                                        "." -> if (!display.contains(".")) {
                                            if (fresh) { display = "0."; fresh = false } else display += "."
                                        }
                                        else -> num(label)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = tc, fontSize = 26.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultScreen(onLock: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val dir = remember { File(ctx.filesDir, "secret_vault").apply { if (!exists()) mkdirs() } }
    var files by remember { mutableStateOf(loadFiles(dir)) }
    var del by remember { mutableStateOf<VFile?>(null) }
    var grid by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            try {
                val name = fileName(ctx, uri) ?: "file_${System.currentTimeMillis()}"
                ctx.contentResolver.openInputStream(uri)?.use { inp ->
                    FileOutputStream(File(dir, name)).use { out -> inp.copyTo(out) }
                }
            } catch (_: Exception) {}
        }
        files = loadFiles(dir)
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(C.Surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Secret Vault", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${files.size} secured items", color = C.Muted, fontSize = 13.sp)
            }
            Text(
                if (grid) "List" else "Grid",
                color = C.Text2, fontSize = 13.sp,
                modifier = Modifier.clickable { grid = !grid }.padding(8.dp)
            )
            Text("Settings", color = C.Text2, fontSize = 13.sp,
                modifier = Modifier.clickable { onSettings() }.padding(8.dp))
            Text("Lock", color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onLock() }.padding(8.dp))
        }

        Button(
            onClick = { picker.launch("*/*") },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Accent)
        ) {
            Text("+  Add Photos, Videos & Files", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No files yet", color = C.Text2, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("Tap + to hide media", color = C.Muted, fontSize = 14.sp)
                }
            }
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files) { item ->
                    ThumbCard(item, onClick = { openFile(ctx, item.file) }, onLong = { del = item })
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files) { item ->
                    ListRow(item, onClick = { openFile(ctx, item.file) }, onDel = { del = item })
                }
            }
        }
    }

    del?.let { item ->
        AlertDialog(
            onDismissRequest = { del = null },
            containerColor = C.Card,
            title = { Text("Delete?", color = C.Text, fontWeight = FontWeight.SemiBold) },
            text = { Text(item.file.name, color = C.Text2) },
            confirmButton = {
                TextButton(onClick = {
                    item.file.delete(); files = loadFiles(dir); del = null
                }) { Text("Delete", color = C.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { del = null }) { Text("Cancel", color = C.Text2) }
            }
        )
    }
}

@Composable
fun ThumbCard(item: VFile, onClick: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(C.Card2).clickable(onClick = onClick)
    ) {
        when (item.type) {
            FType.IMAGE, FType.VIDEO -> {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(item.file).crossfade(true).build(),
                    contentDescription = item.file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.type == FType.VIDEO) {
                    Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                        Text("▶", color = Color.White, fontSize = 28.sp)
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(typeLabel(item.type), color = C.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(item.file.extension.uppercase().ifEmpty { "FILE" }, color = C.Muted, fontSize = 11.sp)
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(6.dp)
        ) {
            Text(item.file.name, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ListRow(item: VFile, onClick: () -> Unit, onDel: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(C.Card2), contentAlignment = Alignment.Center) {
            if (item.type == FType.IMAGE || item.type == FType.VIDEO) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(item.file).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(typeLabel(item.type), color = C.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.file.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${sizeStr(item.file.length())} · ${item.type.name.lowercase()}", color = C.Muted, fontSize = 12.sp)
        }
        Text("Delete", color = C.Danger, fontSize = 13.sp, modifier = Modifier.clickable { onDel() }.padding(8.dp))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onChangePin: () -> Unit, onLock: () -> Unit) {
    val ctx = LocalContext.current
    val dir = File(ctx.filesDir, "secret_vault")
    val files = remember { loadFiles(dir) }
    val total = files.sumOf { it.file.length() }
    val imgs = files.count { it.type == FType.IMAGE }
    val vids = files.count { it.type == FType.VIDEO }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(Modifier.fillMaxWidth().background(C.Surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("← Back", color = C.Text2, modifier = Modifier.clickable { onBack() }.padding(end = 16.dp))
            Text("Settings", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(20.dp)) {
                    Text("Vault Storage", color = C.Text2, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(sizeStr(total), color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column { Text("${files.size}", color = C.Accent, fontWeight = FontWeight.Bold); Text("Total", color = C.Muted, fontSize = 11.sp) }
                        Column { Text("$imgs", color = C.Accent, fontWeight = FontWeight.Bold); Text("Photos", color = C.Muted, fontSize = 11.sp) }
                        Column { Text("$vids", color = C.Accent, fontWeight = FontWeight.Bold); Text("Videos", color = C.Muted, fontSize = 11.sp) }
                    }
                }
            }
            item { Text("SECURITY", color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            item { SettingRow("Change PIN", "Update your secret code", onChangePin) }
            item { SettingRow("Lock Vault", "Return to calculator", onLock) }
            item { Text("ABOUT", color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            item { SettingRow("Calculator Vault", "Version 2.0", {}) }
            item { SettingRow("How to unlock", "Type PIN on calculator, press =", {}) }
            item {
                Text(
                    "Files stay in private app storage. Other apps cannot access them.",
                    color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun SettingRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.AccentSoft), contentAlignment = Alignment.Center) {
            Text("•", color = C.Accent, fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = C.Muted, fontSize = 12.sp)
        }
        Text(">", color = C.Muted)
    }
}

@Composable
fun ChangePinScreen(onSaved: (String) -> Unit, onBack: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(C.Bg).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("Change PIN", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("4–8 digit numbers only", color = C.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            a, { if (it.length <= 8 && it.all { c -> c.isDigit() }) a = it },
            label = { Text("New PIN") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = fieldColors(), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            b, { if (it.length <= 8 && it.all { c -> c.isDigit() }) b = it },
            label = { Text("Confirm PIN") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = fieldColors(), modifier = Modifier.fillMaxWidth()
        )
        if (err.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(err, color = C.Danger, fontSize = 13.sp) }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                when {
                    a.length < 4 -> err = "At least 4 digits"
                    a != b -> err = "PINs do not match"
                    else -> onSaved(a)
                }
            },
            Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Accent)
        ) { Text("Save PIN", color = Color.Black, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("Cancel", color = C.Text2) }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Accent, unfocusedBorderColor = C.BtnGray,
    focusedLabelColor = C.Accent, unfocusedLabelColor = C.Muted,
    cursorColor = C.Accent, focusedTextColor = C.Text, unfocusedTextColor = C.Text
)

fun loadFiles(dir: File) = dir.listFiles()?.sortedByDescending { it.lastModified() }
    ?.map { VFile(it, detect(it.name)) } ?: emptyList()

fun detect(name: String): FType {
    val e = name.lowercase()
    return when {
        e.endsWith(".jpg") || e.endsWith(".jpeg") || e.endsWith(".png") || e.endsWith(".webp") || e.endsWith(".gif") -> FType.IMAGE
        e.endsWith(".mp4") || e.endsWith(".mkv") || e.endsWith(".avi") || e.endsWith(".mov") || e.endsWith(".webm") -> FType.VIDEO
        e.endsWith(".mp3") || e.endsWith(".wav") || e.endsWith(".m4a") -> FType.AUDIO
        e.endsWith(".pdf") || e.endsWith(".doc") || e.endsWith(".txt") -> FType.DOC
        else -> FType.OTHER
    }
}

fun typeLabel(t: FType) = when (t) {
    FType.IMAGE -> "IMG"; FType.VIDEO -> "VID"; FType.AUDIO -> "AUD"; FType.DOC -> "DOC"; else -> "FILE"
}

fun openFile(ctx: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {}
}

fun fileName(ctx: Context, uri: Uri): String? {
    var n: String? = null
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0) n = c.getString(i)
        }
    }
    return n
}

fun mime(name: String): String {
    val e = name.lowercase()
    return when {
        e.endsWith(".mp4") -> "video/mp4"
        e.endsWith(".jpg") || e.endsWith(".jpeg") -> "image/jpeg"
        e.endsWith(".png") -> "image/png"
        e.endsWith(".webp") -> "image/webp"
        e.endsWith(".gif") -> "image/gif"
        e.endsWith(".pdf") -> "application/pdf"
        e.endsWith(".mp3") -> "audio/mpeg"
        else -> "*/*"
    }
}

fun sizeStr(b: Long) = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "${"%.1f".format(b / (1024.0 * 1024.0))} MB"
}
