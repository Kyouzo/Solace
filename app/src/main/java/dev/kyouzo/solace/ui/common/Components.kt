package dev.kyouzo.solace.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R

/** Scrolling single-column screen. Width is capped and centred so wide windows don't get stretched phone UI. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, spacing: Dp = 12.dp, content: LazyListScope.() -> Unit) {
    val padding = dimensionResource(R.dimen.screen_padding)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = dimensionResource(R.dimen.content_max_width)).fillMaxSize(),
            contentPadding = PaddingValues(start = padding, end = padding, top = padding, bottom = padding + 8.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

@Composable
fun BrandHeader(subtitle: String) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(stringResource(R.string.brand), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Text(subtitle, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

/** Small uppercase label that introduces a group of settings. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium
    )
}

/** The one card style used across the app: large radius, tonal fill, no outline. */
@Composable
fun SolaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = container)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = content)
    else Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = content)
}

/** Round tonal badge holding an icon. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.primaryContainer, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer, size: Dp = 40.dp) {
    Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** One line in a settings group: leading icon, title, optional subtitle, optional trailing widget. */
@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Card wrapping several [SettingRow]s; use [GroupDivider] between them. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    SolaceCard { Column(content = content) }
}

@Composable
fun GroupDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

/** Compact status chip with a coloured dot; the dot pulses while [pulse] is true. */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = if (pulse) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha"
    )
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).alpha(alpha).background(dot, CircleShape))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun PrivacyCard(title: String, text: String, action: String = "", onClick: (() -> Unit)? = null) {
    SolaceCard(onClick = onClick) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action.isNotBlank()) Text(action, modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun StatusCard() {
    SolaceCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.status_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.status_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
