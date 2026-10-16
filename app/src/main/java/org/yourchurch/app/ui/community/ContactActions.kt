package org.yourchurch.app.ui.community

import android.content.Context
import android.content.Intent
import android.net.Uri

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

internal fun openDirections(context: Context, address: String) {
    open(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))
}

internal fun openDialer(context: Context, phone: String) {
    open(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.trim(), null)))
}

internal fun openMail(context: Context, email: String) {
    open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email.trim())))
}

internal fun openWebsite(context: Context, url: String) {
    val clean = url.trim()
    val withScheme = if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "https://$clean"
    open(context, Intent(Intent.ACTION_VIEW, Uri.parse(withScheme)))
}


/** Opens the phone's calendar with this event filled in, ready to save (two hours long unless changed there). */
internal fun addToCalendar(context: Context, event: org.yourchurch.app.data.Event) {
    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
        data = android.provider.CalendarContract.Events.CONTENT_URI
        putExtra(android.provider.CalendarContract.Events.TITLE, event.title)
        putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, event.location)
        putExtra(android.provider.CalendarContract.Events.DESCRIPTION, event.description)
        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.time)
        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, event.start.time + 2L * 60 * 60 * 1000)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
