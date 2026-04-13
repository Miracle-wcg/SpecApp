package com.wcg.app.specapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.ui.theme.*

@Composable
fun DarkTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit, // 新增：状态更新回调
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange, // 绑定回调
            readOnly = false,              // 允许输入
            singleLine = true,
            modifier = Modifier.fillMaxWidth(), // 移除高度限制解决文字遮挡
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderDark,
                focusedBorderColor = AccentCyan,
                unfocusedTextColor = TextWhite,
                focusedTextColor = TextWhite,
                unfocusedContainerColor = BgDark,
                focusedContainerColor = BgDark
            ),
            shape = RoundedCornerShape(4.dp)
        )
    }
}