package com.randomchat.shnapp.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.ErrorRed
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary

private val REPORT_REASONS = listOf(
    "Harassment / Bullying",
    "Explicit / Inappropriate content",
    "Shared personal info / doxxing",
    "Spam or advertising",
    "Hate speech",
    "Impersonation",
    "Other"
)

@Composable
fun ReportDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit
) {
    if (!visible) return

    var selectedReason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(24.dp))
                .border(1.dp, SubtleBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text(
                "Report this Malayali",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                "Select a reason for reporting this user",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            REPORT_REASONS.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selectedReason == reason) AccentCyan.copy(0.1f) else ElevatedCard,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (selectedReason == reason) AccentCyan.copy(0.5f) else SubtleBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedReason = reason }
                        .padding(14.dp)
                ) {
                    Text(reason, color = if (selectedReason == reason) AccentCyan else TextPrimary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Submit Report",
                    color = if (selectedReason.isEmpty()) TextMuted else ErrorRed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(
                            if (selectedReason.isEmpty()) ElevatedCard else ErrorRed.copy(0.15f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = selectedReason.isNotEmpty()) { onReport(selectedReason) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
