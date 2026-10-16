package com.tumiart.mobile.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandEditorialTypography
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.tumiart.mobile.legal.TumiArtFontLicenses

internal const val TURKISH_GLYPH_SAMPLE = "İ ı Ş ş Ğ ğ Ç ç Ö ö Ü ü"
internal const val LONG_TURKISH_SAMPLE =
    "Işığın, gölgenin ve özgün çizgilerin buluştuğu uzun Türkçe eser ve koleksiyon adı"

/** No catalog data, credentials or remote media. Exercises the same app-owned fonts/tokens. */
@Composable
internal fun TumiArtThemeSpecimen() {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val editorial = LocalBrandEditorialTypography.current
    val spacing = LocalBrandSpacing.current
    var selected by remember { mutableStateOf(false) }
    Surface(color = colors.background, contentColor = colors.onBackground) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().testTag("specimen-root")
                .verticalScroll(rememberScrollState()).padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text("TumiArt · Tema denemesi", style = type.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                "Sanatla yaşayın.",
                style = editorial.hero,
                modifier = Modifier.testTag("editorial-hero").semantics {
                    heading()
                }
            )
            Text(TURKISH_GLYPH_SAMPLE, style = editorial.title, modifier = Modifier.testTag("editorial-glyphs"))
            Text("İşlevsel metin", style = type.titleMedium)
            Text(TURKISH_GLYPH_SAMPLE, style = type.bodyLarge, modifier = Modifier.testTag("functional-glyphs"))
            Text(LONG_TURKISH_SAMPLE, style = type.titleSmall, modifier = Modifier.testTag("long-name"))
            Text("Fiyat ve uygunluk canlı mağaza verisi gerektirir.", style = type.bodyMedium)
            Text("₺ · 0123456789 · , .", style = type.bodyLarge, modifier = Modifier.testTag("price-glyphs"))
            Surface(
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "Taş yüzey · Türkçe açıklama ve küçük destek metni",
                    Modifier.padding(12.dp),
                    style = type.bodySmall
                )
            }
            Button(
                onClick = { selected = !selected },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("primary-action")
            ) {
                Text(if (selected) "Seçildi" else "Devam et")
            }
            OutlinedButton(
                onClick = { selected = false },
                enabled = selected,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("secondary-action")
            ) {
                Text("Seçimi sıfırla")
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = selected, role = Role.Checkbox, onValueChange = { selected = it })
                    .testTag("selection-action"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selected, onCheckedChange = null)
                Text("Seçimi değiştir", Modifier.padding(start = 8.dp))
            }
            OutlinedTextField(
                value = "Işıl Şengül",
                onValueChange = {},
                readOnly = true,
                shape = MaterialTheme.shapes.small,
                label = { Text("Adınız") },
                isError = true,
                supportingText = { Text("Lütfen alanı kontrol edin.") },
                modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }.testTag("error-field")
            )
            Text(
                "Baskı hikâyesi",
                style = editorial.title,
                modifier = Modifier.testTag("editorial-title").semantics {
                    heading()
                }
            )
            Text("İkincil rehber bağlantısı", color = colors.tertiary, style = type.labelLarge)
            Text(
                "Hata durumunda metin ve simgeler renk dışında da durumu açıklar.",
                color = colors.onErrorContainer,
                style = type.bodyMedium,
                modifier = Modifier.fillMaxWidth().background(
                    colors.errorContainer,
                    MaterialTheme.shapes.small
                ).padding(12.dp)
            )
            TumiArtFontLicenses()
        }
    }
}

@Preview(name = "Gallery light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun TumiArtLightPreview() {
    TumiArtThemeProbe(darkTheme = false)
}

@Preview(name = "Gallery dark large text", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Composable
private fun TumiArtDarkLargePreview() {
    TumiArtThemeProbe(darkTheme = true)
}
