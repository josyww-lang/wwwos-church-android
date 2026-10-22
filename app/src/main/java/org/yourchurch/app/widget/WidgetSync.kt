package org.yourchurch.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.yourchurch.app.MainActivity
import org.yourchurch.app.R
import org.yourchurch.app.data.Event
import org.yourchurch.app.data.ReadingPlan
import org.yourchurch.app.data.toLocalDate

/**
 * The app saves what the widget shows (the reading plan, the next events and the verse, with the labels in the
 * person's language) every time Home loads. The widget works out "today" itself, so it stays right through the day.
 */
object WidgetSync {
    private const val FILE = "widget"

    fun save(
        context: Context,
        name: String,
        labels: Triple<String, String, String>,
        plan: ReadingPlan?,
        nextDay: Int,
        events: List<Event>,
        verse: String
    ) {
        val o = JSONObject()
        o.put("name", name)
        o.put("lr", labels.first)
        o.put("le", labels.second)
        o.put("lv", labels.third)
        o.put("verse", verse)
        if (plan != null) {
            o.put(
                "plan",
                // "next" is this person's next reading (the first day they have not read), worked out by the app.
                JSONObject().put("start", plan.startDate.toLocalDate().toEpochDay()).put("days", JSONArray(plan.days)).put("next", nextDay)
            )
        }
        val list = JSONArray()
        events.take(5).forEach {
            list.put(JSONObject().put("title", it.title).put("start", it.start.time).put("where", it.location))
        }
        o.put("events", list)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("json", o.toString()).apply()
        refresh(context)
    }

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ChurchWidgetProvider::class.java))
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    fun build(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_church)
        val o = runCatching {
            JSONObject(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("json", "{}") ?: "{}")
        }.getOrDefault(JSONObject())

        views.setTextViewText(R.id.widget_name, o.optString("name", context.getString(R.string.app_name)).uppercase())

        var reading = ""
        o.optJSONObject("plan")?.let { plan ->
            val index = if (plan.has("next")) plan.optInt("next") else (LocalDate.now().toEpochDay() - plan.optLong("start")).toInt()
            val days = plan.optJSONArray("days")
            if (days != null && index >= 0 && index < days.length()) reading = days.optString(index)
        }

        var event = ""
        val cutoff = System.currentTimeMillis() - 2 * 3_600_000L
        o.optJSONArray("events")?.let { list ->
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                if (item.optLong("start") >= cutoff) {
                    val whenText = SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault()).format(Date(item.optLong("start")))
                    val where = item.optString("where")
                    event = item.optString("title") + "\n" + whenText + if (where.isNotBlank()) "  \u00B7  $where" else ""
                    break
                }
            }
        }

        val verse = o.optString("verse").ifBlank { if (o.length() == 0) context.getString(R.string.widget_empty) else "" }

        fun show(labelId: Int, textId: Int, label: String, text: String) {
            val visible = if (text.isBlank()) View.GONE else View.VISIBLE
            views.setViewVisibility(labelId, visible)
            views.setViewVisibility(textId, visible)
            views.setTextViewText(labelId, label.uppercase())
            views.setTextViewText(textId, text)
        }
        show(R.id.widget_reading_label, R.id.widget_reading, o.optString("lr"), reading)
        show(R.id.widget_event_label, R.id.widget_event, o.optString("le"), event)
        views.setTextViewText(R.id.widget_verse, verse)
        views.setViewVisibility(R.id.widget_verse, if (verse.isBlank()) View.GONE else View.VISIBLE)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }
}
