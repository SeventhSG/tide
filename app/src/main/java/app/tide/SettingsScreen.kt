package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.OceanIntensity
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme

/**
 * Notification settings, which is currently all there is to set.
 *
 * The stance is on the screen rather than buried in a policy file: the app
 * sends one quiet summary a day if you ask it to, nothing at all if you do
 * not, and it never sends anything to bring you back. Writing that here is what
 * stops it being eroded later, because changing the behaviour would mean
 * changing the sentence that is in front of the person.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit = {},
    onToggleDigest: () -> Unit = {},
    onDigestEarlier: () -> Unit = {},
    onDigestLater: () -> Unit = {},
    onToggleQuietHours: () -> Unit = {},
    onQuietStartEarlier: () -> Unit = {},
    onQuietStartLater: () -> Unit = {},
    onQuietEndEarlier: () -> Unit = {},
    onQuietEndLater: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onSendTest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier, intensity = OceanIntensity.Subtle) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("<", style = DataStyle, color = TideColors.TextMuted)
                }
            }

            Text(
                "Notifications",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )
            Spacer(Modifier.height(14.dp))
            // On a card rather than straight on the water. It is a paragraph,
            // and the contrast rule is measured against the scrim.
            Card {
                Text(
                    "One quiet summary a day, or nothing. Tide never sends anything to bring " +
                        "you back, and there is no notification for a missed day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
            }

            if (!state.permissionGranted) {
                Spacer(Modifier.height(16.dp))
                Card {
                    Text(
                        "Notifications are off at the system level.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Nothing can be posted until Android allows it. The settings below " +
                            "are kept either way.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    TideGhostButton(onClick = onRequestPermission) {
                        Text(
                            "Allow notifications",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.Text,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card {
                Toggle("DAILY SUMMARY", state.digestEnabled, onToggleDigest)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.digestEnabled) {
                        "Sent once a day, silently, and only when something is actually due."
                    } else {
                        "Off. Nothing is sent."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
                if (state.digestEnabled) {
                    Spacer(Modifier.height(14.dp))
                    TimeRow("AT", state.digestAt, onDigestEarlier, onDigestLater)
                }
            }

            Spacer(Modifier.height(10.dp))
            Card {
                Toggle("QUIET HOURS", state.quietHoursEnabled, onToggleQuietHours)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Nothing arrives inside this window. Only something with a real " +
                        "deadline may break it, and nothing in Tide claims one yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
                if (state.quietHoursEnabled) {
                    Spacer(Modifier.height(14.dp))
                    TimeRow("FROM", state.quietStart, onQuietStartEarlier, onQuietStartLater)
                    Spacer(Modifier.height(10.dp))
                    TimeRow("TO", state.quietEnd, onQuietEndEarlier, onQuietEndLater)
                }
            }

            Spacer(Modifier.height(10.dp))
            Card {
                Text(
                    "Send one to this phone",
                    style = MaterialTheme.typography.labelLarge,
                    color = TideColors.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Posts a test summary now, on the silent channel, so you can see where " +
                        "it lands. It is recorded in the ledger like any other.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                TideGhostButton(onClick = onSendTest) {
                    Text(
                        "Send a test",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                }
                state.lastTestResult?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = LabelStyle, color = TideColors.TextFaint)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onToggle: () -> Unit) {
    val shape = ContinuousCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LabelStyle, color = if (on) TideColors.Accent else TideColors.TextMuted)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .width(52.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(
                    if (on) TideColors.Accent.copy(alpha = 0.90f)
                    else Color.White.copy(alpha = 0.06f),
                )
                .border(1.dp, TideColors.Hairline, CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (on) TideColors.OnAccent else TideColors.TextMuted),
            )
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    value: String,
    onDown: (() -> Unit)?,
    onUp: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.weight(1f))
        onDown?.let {
            Step("-", it)
            Spacer(Modifier.width(8.dp))
        }
        Text(value, style = DataStyle, color = TideColors.Text)
        Spacer(Modifier.width(8.dp))
        Step("+", onUp)
    }
}

@Composable
private fun Step(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(ContinuousCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, TideColors.Hairline, ContinuousCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DataStyle, color = TideColors.TextMuted)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = ContinuousCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TideColors.SurfaceRaised)
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        content = content,
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun SettingsPreview() {
    TideTheme {
        SettingsScreen(
            SettingsUiState(
                digestEnabled = true,
                digestAt = "08:00",
                quietHoursEnabled = true,
                quietStart = "22:00",
                quietEnd = "07:00",
                permissionGranted = false,
            ),
        )
    }
}
