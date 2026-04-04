package com.wcg.app.specapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupCard(title: String, subtitle: String, modifier: Modifier = Modifier, actionText: String? = null, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(24.dp)) {
        if (title.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(subtitle, color = TextMuted, fontSize = 12.sp)
                }
                if (actionText != null) {
                    Text(actionText, color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        content()
    }
}

@Composable
fun DarkTextField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, singleLine = true,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderDark,
                unfocusedTextColor = TextWhite,
                unfocusedContainerColor = BgDark
            )
        )
    }
}

@Composable
fun SetupRowItem(title: String, subtitle: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        content()
    }
}

@Composable
fun FormatBox(title: String, desc: String, isSelected: Boolean, modifier: Modifier) {
    Box(modifier = modifier.border(1.dp, if (isSelected) AccentCyan else BorderDark, RoundedCornerShape(6.dp)).background(if (isSelected) Color(0xFF1E2D4A) else Color.Transparent).padding(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(desc, color = TextMuted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun Badge(text: String, bgColor: Color, textColor: Color = AccentCyan) {
    Box(modifier = Modifier.background(bgColor, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}