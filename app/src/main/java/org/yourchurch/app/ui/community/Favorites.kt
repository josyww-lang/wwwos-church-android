package org.yourchurch.app.ui.community

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R
import org.yourchurch.app.ui.common.InfoHint

/** The shortcuts a person has pinned on Home (up to four). Kept on the phone. */
object Favorites {
    val ALL = listOf("give", "pray", "serve", "sermons", "events", "news", "chat", "visit")
    const val MAX = 4
    private val DEFAULT = listOf("give", "pray", "serve", "sermons")
    private const val FILE = "settings"
    private const val KEY = "home_favourites"

    var keys by mutableStateOf(DEFAULT)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split(",")?.filter { it in ALL }?.take(MAX)
        if (!saved.isNullOrEmpty()) keys = saved
        loaded = true
    }

    fun set(context: Context, list: List<String>) {
        keys = list.filter { it in ALL }.take(MAX).ifEmpty { DEFAULT }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, keys.joinToString(",")).apply()
    }
}

/** "Choose up to four shortcuts": opened by pressing and holding a shortcut on Home. */
@Composable
fun FavoritesDialog(options: List<Pair<String, Int>>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Favorites.keys) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fav_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoHint(stringResource(R.string.fav_hint, Favorites.MAX), modifier = Modifier.padding(bottom = 8.dp))
                options.forEach { (key, @StringRes label) ->
                    val checked = key in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = when {
                                    checked -> selected - key
                                    selected.size < Favorites.MAX -> selected + key
                                    else -> selected
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { Favorites.set(context, selected); onDismiss() },
                enabled = selected.isNotEmpty()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
