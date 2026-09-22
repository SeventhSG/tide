package app.tide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tide.core.design.LiquidGlassNav
import app.tide.core.design.NavItem

/**
 * The five sections, and the bar that moves between them.
 *
 * Every main section keeps the nav: leaving the app's furniture behind is for
 * the screens you are doing one thing on and then coming back from, like the
 * logger or the import. Those are the only places the bar disappears.
 */
enum class Section(val label: String) {
    Today("TODAY"),
    Train("TRAIN"),
    Body("BODY"),
    Money("MONEY"),
    Ask("ASK"),
}

private val navItems = listOf(
    NavItem("TODAY", TideIcons.Waves),
    NavItem("TRAIN", TideIcons.Barbell),
    NavItem("BODY", TideIcons.Pulse),
    NavItem("MONEY", TideIcons.Card),
    NavItem("ASK", TideIcons.Chat),
)

@Composable
fun TideScaffold(
    section: Section,
    onSelectSection: (Section) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f), content = content)
        LiquidGlassNav(
            items = navItems,
            selected = section.ordinal,
            onSelect = { onSelectSection(Section.entries[it]) },
        )
    }
}
