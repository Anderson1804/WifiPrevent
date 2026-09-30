package com.anderson.wifiprevent.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExpandableSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) "Expandido" else "Contraído"
            },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
        ) {
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Start)
            Text(if (expanded) "Ocultar" else "Ver")
        }
        if (expanded) content()
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RiskBadge(level: String?, evaluated: Boolean = true) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (background, foreground) = when {
        !evaluated -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        level == "high" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        level == "medium" -> if (dark) Color(0xFF443514) to Color(0xFFFFDB8B)
            else Color(0xFFFFEFC8) to Color(0xFF735000)
        level == "low" -> if (dark) Color(0xFF173D32) to Color(0xFF9EE0C2)
            else Color(0xFFDEF4E9) to Color(0xFF176247)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(8.dp)) {
        Text(formatRiskLevel(level, evaluated), Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MetricValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusNotice(message: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

fun uiBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.2f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

fun uiDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

fun uiDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.forLanguageTag("es")))
}.getOrDefault(value)
