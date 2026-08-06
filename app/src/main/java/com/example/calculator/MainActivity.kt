package com.example.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.ui.theme.CalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorTheme {
                CalculatorScreen()
            }
        }
    }
}

@Composable
fun CalculatorScreen() {
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
