package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.TideColors
import app.tide.core.design.TideTheme
import app.tide.core.design.oceanScrimColor

/**
 * Today.
 *
 * A vertical priority stack, not a grid, and its shape changes across the week.
 * The screen is opened twenty-plus times a day, so nothing animates on entry:
 * content is present at the first frame.
 *
 * Every value here is placeholder data. Nothing reads a database yet.
 */
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    OceanBackground(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("TIDE", style = LabelStyle, color = TideColors.TextMuted)

            Spacer(Modifier.height(30.dp))
            Text("TUE 22 SEPTEMBER", style = LabelStyle, color = TideColors.TextFaint)

            Spacer(Modifier.height(10.dp))
            Text(
                "No session\ntoday.",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )

            Spacer(Modifier.height(11.dp))
            Text(
                "Last was Pull A, two days ago. Legs is scheduled for tomorrow.",
                style = MaterialTheme.typography.bodyLarge,
                color = TideColors.TextMuted,
            )

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {},
                    modifier = Modifier.defaultMinSize(minHeight = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TideColors.Accent,
                        contentColor = TideColors.OnAccent,
                    ),
                ) { Text("Start session", style = MaterialTheme.typography.labelLarge) }

                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.defaultMinSize(minHeight = 52.dp),
                ) { Text("Skip today") }
            }

            Spacer(Modifier.height(26.dp))
            Scrim {
                Text("DRIFT", style = LabelStyle, color = TideColors.TextFaint)
                Spacer(Modifier.height(12.dp))
                DriftRow("Volume, 7 days", "12 480 kg")
                DriftRow("Sleep, average", "6h 41m")
                DriftRow("Resting heart rate", "54 bpm")
                DriftRow("Renews in 30 days", "4 items", TideColors.Warning)
                DriftRow("Last backup", "19 days", TideColors.Critical, divider = false)
            }

            Spacer(Modifier.height(12.dp))
            Scrim {
                Text("NEEDS YOU", style = LabelStyle, color = TideColors.TextFaint)
                Spacer(Modifier.height(10.dp))
                // The honest empty state, written before the populated one.
                Text(
                    "Nothing. The week is clear.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TideColors.Text,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * The surface every readable thing sits on. The ocean is visible around it and
 * never behind the glyphs, which is what keeps the contrast ratios honest.
 */
@Composable
private fun Scrim(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(26.dp))
            .background(oceanScrimColor())
            .border(1.dp, TideColors.Hairline, ContinuousCornerShape(26.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun DriftRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TideColors.Text,
    divider: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TideColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = DataStyle, color = valueColor, textAlign = TextAlign.End)
    }
    if (divider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TideColors.Hairline))
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayPreview() {
    TideTheme { TodayScreen() }
}
