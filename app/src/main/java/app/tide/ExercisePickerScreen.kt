package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.OceanIntensity
import app.tide.core.design.TideColors
import app.tide.core.design.TideTheme

/**
 * Choosing what to train.
 *
 * The door into the logger, and until a weekly plan exists it is the only one.
 * Two ways in, because both are real: you know the name and type three letters
 * of it, or you are standing in front of a rack and want to see what trains a
 * back.
 *
 * **Recent comes first**, and it is the last time each lift was really trained,
 * not a guess at what you want. A lift with no history shows no date rather
 * than "never", because never is not news.
 *
 * The rows are a [LazyColumn]. Thirty-eight would scroll fine in a column; the
 * library this is built for is about 1,300.
 *
 * **The ocean is off here**, as it is on the logger. This screen is opened in
 * the gym, between sets, and scrolled: shafts of light moving under a moving
 * list is an obstacle, and the eyebrow labels and the search field would be
 * reading text straight off the water, which the contrast rule forbids.
 */
@Composable
fun ExercisePickerScreen(
    state: ExercisePickerUiState,
    onBack: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onPick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier, intensity = OceanIntensity.Off) {
        Column(
            Modifier
                .fillMaxSize()
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
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.libraryCount == 1) "1 EXERCISE" else "${state.libraryCount} EXERCISES",
                    style = LabelStyle,
                    color = TideColors.TextFaint,
                )
            }

            Text(
                "Exercises",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )
            if (state.inSession) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "A session is open. Picking a lift adds to it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextMuted,
                )
            }

            Spacer(Modifier.height(14.dp))
            SearchField(state.query, onQueryChange)
            Spacer(Modifier.height(14.dp))

            when {
                state.sections.isEmpty() && state.searching -> Empty(
                    "Nothing matches \"${state.query}\".",
                    // No "add a custom exercise" offer: nothing in the app can
                    // do that yet. The importer is the only thing that creates
                    // one, so that is what this says.
                    "The starter library is small. Importing a history brings your own " +
                        "lifts in under their own names.",
                )

                state.sections.isEmpty() -> Empty(
                    "The library is empty.",
                    "That should not happen, since the app seeds a starter set on first run.",
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.sections.forEach { section ->
                        item(key = "h-${section.title}") {
                            Spacer(Modifier.height(10.dp))
                            Text(section.title, style = LabelStyle, color = TideColors.TextFaint)
                            Spacer(Modifier.height(6.dp))
                        }
                        items(section.rows, key = { "${section.title}-${it.id}" }) { row ->
                            ExerciseRow(row, onPick)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(row: ExercisePickerUiState.Row, onPick: (String) -> Unit) {
    val shape = ContinuousCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(TideColors.SurfaceRaised)
            .border(1.dp, TideColors.Hairline, shape)
            .clickable(role = Role.Button) { onPick(row.id) }
            // 56dp, like the logger's controls, because this is pressed with a
            // thumb in the same place and under the same conditions.
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, color = TideColors.Text)
            Spacer(Modifier.height(2.dp))
            Text(row.detail, style = LabelStyle, color = TideColors.TextFaint)
        }
        row.lastTrained?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, style = LabelStyle, color = TideColors.TextMuted)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val shape = ContinuousCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TideColors.Text),
            cursorBrush = SolidColor(TideColors.Accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                if (query.isEmpty()) {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TideColors.TextFaint,
                    )
                }
                field()
            },
        )
    }
}

@Composable
private fun Empty(title: String, body: String) {
    val shape = ContinuousCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TideColors.SurfaceRaised)
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = TideColors.Text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TideColors.TextMuted)
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun ExercisePickerPreview() {
    TideTheme {
        ExercisePickerScreen(
            ExercisePickerUiState(
                libraryCount = 38,
                sections = listOf(
                    ExercisePickerUiState.Section(
                        "RECENT",
                        listOf(
                            ExercisePickerUiState.Row("squat", "Back squat", "BARBELL, QUADS", "YESTERDAY"),
                            ExercisePickerUiState.Row("row", "Barbell row", "BARBELL, BACK", "3 DAYS AGO"),
                        ),
                    ),
                    ExercisePickerUiState.Section(
                        "CHEST",
                        listOf(
                            ExercisePickerUiState.Row("bench", "Bench press", "BARBELL", "4 DAYS AGO"),
                            ExercisePickerUiState.Row("dip", "Dip", "BODYWEIGHT", null),
                        ),
                    ),
                ),
            ),
        )
    }
}
