package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.body.HealthSource
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme
import app.tide.core.design.oceanScrimColor
import app.tide.core.design.tidePress

/**
 * Body, Money and Ask: the three sections whose state is mostly about what is
 * and is not there yet.
 *
 * They are in one file because they share a shape, not because they are small:
 * a heading, a plain statement of what the app can and cannot currently do,
 * and whatever real data exists. None of them shows an example number.
 */

@Composable
fun BodyScreen(
    state: BodyUiState,
    onConnect: () -> Unit = {},
    onOpenHealthConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text("BODY", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.height(8.dp))
            Text(
                "Today.",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.availability == HealthSource.Availability.NotInstalled -> Scrim {
                    Text(
                        "Health Connect is not installed.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android keeps steps, sleep, heart rate and weight in Health Connect. " +
                            "Tide reads from it and never writes to it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    TideGhostButton(onClick = onOpenHealthConnect) {
                        Text(
                            "Get Health Connect",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.Text,
                        )
                    }
                }

                state.availability == HealthSource.Availability.NotSupported -> Scrim {
                    Text(
                        "This device cannot provide Health Connect.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Nothing can be read here, and Tide measures none of it itself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }

                !state.granted -> Scrim {
                    Text(
                        "Not connected yet.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Four readings, read-only: steps, sleep, heart rate and weight. " +
                            "They stay on the phone, and the permission can be withdrawn " +
                            "in Health Connect at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    TideButton(onClick = onConnect) {
                        Text(
                            "Connect Health Connect",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.OnAccent,
                        )
                    }
                    state.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = TideColors.Warning)
                    }
                }

                !state.hasAnyReading -> Scrim {
                    Text(
                        "Connected, and nothing recorded today.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Readings appear here as your watch, phone or scale writes them. " +
                            "Tide does not estimate them in the meantime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }

                else -> Scrim {
                    Text("FROM HEALTH CONNECT", style = LabelStyle, color = TideColors.TextFaint)
                    Spacer(Modifier.height(8.dp))
                    Reading("Steps", state.steps)
                    Reading("Sleep", state.sleep)
                    state.sleepInsight?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.TextMuted,
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                    Reading("Lowest heart rate", state.heartRate)
                    Reading("Weight", state.weight, state.weightAge, divider = false)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Reading(label: String, value: String?, age: String? = null, divider: Boolean = true) {
    // A reading with no value is left out entirely rather than shown as a dash
    // with nothing behind it.
    if (value == null) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TideColors.TextMuted)
            age?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = LabelStyle, color = TideColors.TextFaint)
            }
        }
        Text(value, style = DataStyle, color = TideColors.Text)
    }
    if (divider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TideColors.Hairline))
    }
}

/**
 * Money: what renews, and when.
 *
 * No forecasting, no budget, no spending category. A subscription renews on
 * its own whether this screen is ever opened, so there is nothing to mark
 * done and nothing to miss, only a date to know about. The monthly figure is
 * whatever actually falls inside the month being looked at, not every
 * subscription's cost squeezed onto a common cadence it does not have.
 */
@Composable
fun MoneyScreen(
    state: MoneyUiState,
    onAdd: (name: String, amount: Double, cadence: Cadence, day: Int) -> Unit = { _, _, _, _ -> },
    onRemove: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }

    OceanBackground(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text("MONEY", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.height(8.dp))
            Text(
                state.monthlyTotalLabel?.let { "$it\nthis month." } ?: "Nothing\nrenewing yet.",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )
            Spacer(Modifier.height(16.dp))

            if (state.subscriptions.isEmpty() && !adding) {
                Scrim {
                    Text(
                        "No subscriptions yet.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add one below and it renews on its own from then on. Nothing here " +
                            "is marked done or missed: a renewal is a date, not a commitment " +
                            "you keep or fail.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }
                Spacer(Modifier.height(10.dp))
            } else if (state.subscriptions.isNotEmpty()) {
                state.subscriptions.forEach { sub ->
                    SubscriptionRow(sub, onRemove = { onRemove(sub.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (adding) {
                Spacer(Modifier.height(4.dp))
                AddSubscriptionForm(
                    onCancel = { adding = false },
                    onSubmit = { name, amount, cadence, day ->
                        onAdd(name, amount, cadence, day)
                        adding = false
                    },
                )
            } else {
                TideButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Add a subscription",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.OnAccent,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SubscriptionRow(sub: MoneyUiState.Subscription, onRemove: () -> Unit) {
    val shape = ContinuousCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TideColors.SurfaceRaised)
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, style = MaterialTheme.typography.bodyLarge, color = TideColors.Text)
                Spacer(Modifier.height(2.dp))
                Text("RENEWS ${sub.nextRenewalLabel}", style = LabelStyle, color = TideColors.TextFaint)
            }
            Text(sub.amountLabel, style = DataStyle, color = TideColors.Text)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            SmallAction("REMOVE", critical = true, onClick = onRemove)
        }
    }
}

@Composable
private fun SmallAction(label: String, critical: Boolean = false, onClick: () -> Unit) {
    val shape = ContinuousCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(
                if (critical) TideColors.Critical.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
            )
            .border(1.dp, TideColors.Hairline, shape)
            .tidePress(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = LabelStyle, color = if (critical) TideColors.Critical else TideColors.TextMuted)
    }
}

@Composable
private fun AddSubscriptionForm(
    onCancel: () -> Unit,
    onSubmit: (name: String, amount: Double, cadence: Cadence, day: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf(Cadence.Monthly) }
    var day by remember { mutableStateOf(1) }

    Scrim {
        Text("NAME", style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(6.dp))
        PlainField(name, "Netflix, rent, gym", onValueChange = { name = it })

        Spacer(Modifier.height(14.dp))
        Text("AMOUNT", style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(6.dp))
        PlainField(amountText, "0", onValueChange = { amountText = it }, keyboardType = KeyboardType.Decimal)

        Spacer(Modifier.height(14.dp))
        Text("HOW OFTEN", style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cadence.entries.forEach { c ->
                CadenceChip(c.name.uppercase(), selected = cadence == c) {
                    cadence = c
                    // A day of month means nothing for a weekly cadence, and a
                    // weekday means nothing for the other two: reset rather
                    // than carry a number the new cadence would misread.
                    day = if (c == Cadence.Weekly) 1 else 1
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (cadence == Cadence.Weekly) "WHICH DAY" else "WHICH DAY OF THE MONTH",
            style = LabelStyle,
            color = TideColors.TextFaint,
        )
        Spacer(Modifier.height(8.dp))
        if (cadence == Cadence.Weekly) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEKDAY_LABELS.forEachIndexed { i, label ->
                    CadenceChip(label, selected = day == i + 1) { day = i + 1 }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallAction("-") { day = (day - 1).coerceIn(1, 31) }
                Text("$day", style = DataStyle, color = TideColors.Text)
                SmallAction("+") { day = (day + 1).coerceIn(1, 31) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TideGhostButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = TideColors.Text)
            }
            TideButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (name.isNotBlank() && amount != null && amount > 0.0) {
                        onSubmit(name, amount, cadence, day)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Add", style = MaterialTheme.typography.labelLarge, color = TideColors.OnAccent)
            }
        }
    }
}

private val WEEKDAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

@Composable
private fun CadenceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = ContinuousCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) TideColors.Accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) TideColors.Accent.copy(alpha = 0.55f) else TideColors.Hairline, shape)
            .tidePress(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = LabelStyle, color = if (selected) TideColors.Accent else TideColors.TextMuted)
    }
}

@Composable
private fun PlainField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val shape = ContinuousCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TideColors.Text),
            cursorBrush = SolidColor(TideColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TideColors.TextFaint)
                }
                inner()
            },
        )
    }
}

@Composable
fun AskScreen(
    state: AskUiState,
    onInstall: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRemove: () -> Unit = {},
    onSend: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text("ASK", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.installed) "Model is\non the phone." else "Nothing\nto ask yet.",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )

            Spacer(Modifier.height(16.dp))
            Scrim {
                Text(
                    "Install the model on this phone",
                    style = MaterialTheme.typography.labelLarge,
                    color = TideColors.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.modelName}, ${state.modelLicence}, about ${state.approximateSizeLabel}. " +
                        "Downloaded once, then it stays here. Nothing about you is sent, and " +
                        "the app is offline again the moment it finishes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )

                if (state.installing) {
                    Spacer(Modifier.height(14.dp))
                    Progress(state.progress)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.downloadedLabel ?: "STARTING",
                        style = LabelStyle,
                        color = TideColors.TextFaint,
                    )
                    Spacer(Modifier.height(12.dp))
                    TideGhostButton(onClick = onCancel) {
                        Text(
                            "Pause",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.Text,
                        )
                    }
                } else if (state.installed) {
                    Spacer(Modifier.height(12.dp))
                    state.downloadedLabel?.let {
                        Text(it, style = LabelStyle, color = TideColors.TextFaint)
                        Spacer(Modifier.height(10.dp))
                    }
                    TideGhostButton(onClick = onRemove) {
                        Text(
                            "Remove it",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.TextMuted,
                        )
                    }
                } else {
                    Spacer(Modifier.height(14.dp))
                    TideButton(onClick = onInstall) {
                        Text(
                            "Install the model",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.OnAccent,
                        )
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TideColors.Warning)
                }
            }

            Spacer(Modifier.height(10.dp))
            Scrim {
                Text("ASK YOUR TRAINING DATA", style = LabelStyle, color = TideColors.TextFaint)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Direct lookups, not a conversation, and not the model above: these read " +
                        "the database whether or not anything is installed. Try sessions this " +
                        "week, volume, muscle balance, or \"last time I did <exercise>\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )

                if (state.messages.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    state.messages.forEach { message ->
                        ChatBubble(message)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(if (state.messages.isEmpty()) 14.dp else 6.dp))
                ChatInput(onSend = onSend)
            }

            Spacer(Modifier.height(10.dp))
            Scrim {
                Text("WHAT IT CANNOT DO YET", style = LabelStyle, color = TideColors.TextFaint)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hold an actual conversation, or answer anything the lookups above do not " +
                        "name directly. That needs a real engine running on the model once it " +
                        "is installed, and building one blind, with no phone to load it on, is " +
                        "how a native library ends up crashing on the first device that runs " +
                        "it. This screen says so rather than guessing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ChatBubble(message: AskUiState.ChatMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        val shape = ContinuousCornerShape(16.dp)
        Box(
            Modifier
                .clip(shape)
                .background(
                    if (message.fromUser) TideColors.Accent.copy(alpha = 0.16f)
                    else Color.White.copy(alpha = 0.06f),
                )
                .border(
                    1.dp,
                    if (message.fromUser) TideColors.Accent.copy(alpha = 0.4f) else TideColors.Hairline,
                    shape,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TideColors.Text,
            )
        }
    }
}

@Composable
private fun ChatInput(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val shape = ContinuousCornerShape(16.dp)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, TideColors.Hairline, shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val send = {
                if (draft.isNotBlank()) {
                    onSend(draft)
                    draft = ""
                }
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TideColors.Text),
                cursorBrush = SolidColor(TideColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { field ->
                    if (draft.isEmpty()) {
                        Text(
                            "Ask something",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TideColors.TextFaint,
                        )
                    }
                    field()
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(52.dp)
                .clip(shape)
                .background(if (draft.isBlank()) Color.White.copy(alpha = 0.05f) else TideColors.Accent)
                .tidePress(enabled = draft.isNotBlank()) {
                    onSend(draft)
                    draft = ""
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "SEND",
                style = LabelStyle,
                color = if (draft.isBlank()) TideColors.TextFaint else TideColors.OnAccent,
            )
        }
    }
}

@Composable
private fun Progress(fraction: Float) {
    val shape = ContinuousCornerShape(8.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(TideColors.Hairline),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(shape)
                .background(TideColors.Accent),
        )
    }
}

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

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun AskPreview() {
    TideTheme {
        AskScreen(
            AskUiState(
                modelName = "Qwen2.5 0.5B Instruct, Q4_K_M",
                modelLicence = "Apache-2.0",
                approximateSizeLabel = "400 MB",
                installing = true,
                progress = 0.42f,
                downloadedLabel = "168 MB of 400 MB",
            ),
        )
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun BodyPreview() {
    TideTheme {
        BodyScreen(
            BodyUiState(
                granted = true,
                steps = "8 420",
                sleep = "6h 41m",
                heartRate = "54 bpm",
                weight = "81.4 kg",
                weightAge = "YESTERDAY",
            ),
        )
    }
}
