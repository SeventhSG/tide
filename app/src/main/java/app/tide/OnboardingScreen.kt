package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme

/**
 * Asked once, before Today, rather than scattered across the first few
 * sessions.
 *
 * Two permissions, both real and both skippable: notifications, so the daily
 * summary can post once it is turned on later; Health Connect, so Body can
 * read a watch's steps and sleep. Neither is turned on here. Granting the OS
 * permission only makes it possible to ask later; the daily summary stays off
 * until you turn it on in Settings, same as always.
 *
 * Both rows work whether or not the person taps them: pressing Continue with
 * nothing granted is a normal, equal path, not a lesser one, and both
 * permissions can still be granted later from Settings and Body themselves.
 */
@Composable
fun OnboardingScreen(
    notificationsGranted: Boolean,
    healthConnectAvailable: Boolean,
    healthConnectGranted: Boolean,
    healthConnectError: String? = null,
    onRequestNotifications: () -> Unit = {},
    onRequestHealthConnect: () -> Unit = {},
    onContinue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text("BEFORE WE START", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.height(8.dp))
            Text(
                "Two things\nTide can ask for.",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Both are optional, and skipping either changes nothing else here. There is " +
                    "no account and nothing is sent anywhere either way.",
                style = MaterialTheme.typography.bodyMedium,
                color = TideColors.TextMuted,
            )

            Spacer(Modifier.height(20.dp))
            Card {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.labelLarge,
                    color = TideColors.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "For the daily summary, which stays off until you turn it on yourself in " +
                        "Settings. This only makes turning it on possible later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                if (notificationsGranted) {
                    Text("ALLOWED", style = LabelStyle, color = TideColors.Accent)
                } else {
                    TideGhostButton(onClick = onRequestNotifications) {
                        Text(
                            "Allow notifications",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.Text,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Card {
                Text(
                    "Health Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = TideColors.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (healthConnectAvailable) {
                        "For Body: steps, sleep, heart rate and weight, read-only, from " +
                            "whatever your watch already writes. Tide never writes to it."
                    } else {
                        "Not installed on this device. Body offers this again once it is."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
                if (healthConnectAvailable) {
                    Spacer(Modifier.height(12.dp))
                    if (healthConnectGranted) {
                        Text("CONNECTED", style = LabelStyle, color = TideColors.Accent)
                    } else {
                        TideGhostButton(onClick = onRequestHealthConnect) {
                            Text(
                                "Connect Health Connect",
                                style = MaterialTheme.typography.labelLarge,
                                color = TideColors.Text,
                            )
                        }
                        healthConnectError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = TideColors.Warning)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            TideButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.labelLarge,
                    color = TideColors.OnAccent,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
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
private fun OnboardingPreview() {
    TideTheme {
        OnboardingScreen(
            notificationsGranted = false,
            healthConnectAvailable = true,
            healthConnectGranted = false,
        )
    }
}
