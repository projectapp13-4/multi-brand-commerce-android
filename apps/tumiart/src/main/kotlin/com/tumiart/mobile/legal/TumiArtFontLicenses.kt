@file:Suppress("FunctionNaming") // Compose APIs use PascalCase.

package com.tumiart.mobile.legal

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tumiart.mobile.R

/** Keep the complete font copyright/license notices packaged and readable, including offline. */
@Composable
internal fun TumiArtFontLicenses() {
    var visible by remember { mutableStateOf(false) }
    TextButton(onClick = { visible = true }) { Text(stringResource(R.string.tumiart_font_licenses)) }
    if (visible) {
        val resources = LocalResources.current
        val licenses = remember(resources) {
            listOf(R.raw.newsreader_ofl, R.raw.karla_ofl).joinToString("\n\n") { resource ->
                resources.openRawResource(resource).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text(stringResource(R.string.tumiart_font_licenses)) },
            text = { Text(licenses, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { visible = false }) { Text(stringResource(R.string.tumiart_close)) }
            }
        )
    }
}
