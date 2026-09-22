package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.LiquidGlassNav
import app.tide.core.design.NavItem
import app.tide.core.design.OceanBackground
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme
import app.tide.core.design.oceanScrimColor

/**
 * Today.
 *
 * A vertical priority stack, not a grid, and its shape changes across the week.
 * Opened twenty-plus times a day, so nothing animates on entry: content is
 * present at the first frame.
 *
 * Every value here is placeholder data. Nothing reads a database yet.
 */
private val navItems = listOf(
    NavItem("TODAY", TideIcons.Waves),
    NavItem("TRAIN", TideIcons.Barbell),
    NavItem("BODY", TideIcons.Pulse),
    NavItem("MONEY", TideIcons.Card),
    NavItem("ASK", TideIcons.Chat),
)

@Composable
fun TodayScreen(modifier: Modifier = Modifier, onStartSession: () -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    OceanBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                TopBar()

                Spacer(Modifier.height(26.dp))
                Text("TUE 22 SEPTEMBER", style = LabelStyle, color = TideColors.TextFaint)

                Spacer(Modifier.height(8.dp))
                Text(
                    "No session\ntoday.",
                    style = MaterialTheme.typography.displayLarge,
                    color = TideColors.Text,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "Last was Pull A, two days ago. Legs is scheduled for tomorrow.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TideColors.TextMuted,
                )

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TideButton(onClick = onStartSession) {
                        Text(
                            "Start session",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.OnAccent,
                        )
                    }
                    TideGhostButton(onClick = {}) {
                        Text(
                            "Skip today",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.TextMuted,
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Scrim {
                    Text("DRIFT", style = LabelStyle, color = TideColors.TextFaint)
                    Spacer(Modifier.height(6.dp))
                    DriftRow("Volume, 7 days", "12 480 kg")
                    DriftRow("Sleep, average", "6h 41m")
                    DriftRow("Resting heart rate", "54 bpm")
                    DriftRow("Renews in 30 days", "4 items", TideColors.Warning)
                    DriftRow("Last backup", "19 days", TideColors.Critical, divider = false)
                }

                Spacer(Modifier.height(10.dp))
                Scrim {
                    Text("NEEDS YOU", style = LabelStyle, color = TideColors.TextFaint)
                    Spacer(Modifier.height(8.dp))
                    // The honest empty state, written before the populated one.
                    Text(
                        "Nothing. The week is clear.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TideColors.Text,
                    )
                }

                Spacer(Modifier.height(10.dp))
                WeekStrip()

                Spacer(Modifier.height(16.dp))
            }

            LiquidGlassNav(
                items = navItems,
                selected = tab,
                onSelect = { tab = it },
            )
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TIDE", style = LabelStyle, color = TideColors.TextMuted)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Box {
                Icon(
                    TideIcons.Inbox,
                    contentDescription = "Inbox, 2 unread",
                    tint = TideColors.TextMuted,
                    modifier = Modifier.size(21.dp),
                )
                // A real semantic state, which is the only case where a dot earns
                // its place. It is not decoration.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(TideColors.Accent),
                )
            }
        }
    }
}

/**
 * The week, at a glance. Done days, today, and what is planned ahead.
 *
 * This is the "one asymmetric tile for whatever is live this week" slot. It
 * earns its place by being the only thing on the screen that shows the shape of
 * the week rather than a single number, and it is why Today is not the same
 * screen on a Monday as on a Friday.
 */
@Composable
private fun WeekStrip() {
    data class Day(val letter: String, val state: Int)   // 0 none, 1 done, 2 planned
    val days = listOf(
        Day("M", 1), Day("T", 1), Day("W", 0), Day("T", 0),
        Day("F", 2), Day("S", 2), Day("S", 0),
    )
    val today = 3

    Scrim {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THIS WEEK", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.weight(1f))
            Text("2 of 4 done", style = DataStyle, color = TideColors.TextMuted)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEachIndexed { i, d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.letter,
                        style = LabelStyle,
                        color = if (i == today) TideColors.Text else TideColors.TextFaint,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(ContinuousCornerShape(11.dp))
                            .background(
                                when (d.state) {
                                    1 -> TideColors.Accent.copy(alpha = 0.90f)
                                    2 -> TideColors.Accent.copy(alpha = 0.14f)
                                    else -> Color.White.copy(alpha = 0.05f)
                                },
                            )
                            .border(
                                1.dp,
                                if (i == today) TideColors.Text.copy(alpha = 0.55f)
                                else Color.Transparent,
                                ContinuousCornerShape(11.dp),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * The surface every readable thing sits on. The ocean shows around it and never
 * behind the glyphs, which is what keeps the contrast ratios honest.
 */
@Composable
private fun Scrim(content: @Composable ColumnScope.() -> Unit) {
    val shape = ContinuousCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(oceanScrimColor())
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
private fun DriftRow(
    label: String,
    value: String,
    valueColor: Color = TideColors.Text,
    divider: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
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

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun TodayPreview() {
    TideTheme { TodayScreen() }
}
