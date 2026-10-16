package org.yourchurch.app.ui.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.yourchurch.app.R
import org.yourchurch.app.ui.common.SkeletonCards

/** The numbers on the leader dashboard. A value of -1 means "couldn't be counted". */
private class DashboardCounts(
    val accounts: Long,
    val members: Long,
    val groups: Long,
    val prayersWeek: Long,
    val prayersLeaders: Long,
    val events: Long,
    val serving: Long,
    val feedback: Long
)

private suspend fun count(query: Query): Long =
    runCatching { withTimeout(15_000) { query.count().get(AggregateSource.SERVER).await().count } }.getOrDefault(-1L)

private suspend fun loadCounts(): DashboardCounts = coroutineScope {
    val db = FirebaseFirestore.getInstance()
    val now = Timestamp.now()
    val weekAgo = Timestamp(Date(System.currentTimeMillis() - 7L * 24 * 3600 * 1000))
    val accounts = async { count(db.collection("users")) }
    val members = async { count(db.collection("approvedMembers")) }
    val rooms = async { count(db.collection("chatRooms")) }
    val directs = async { count(db.collection("chatRooms").whereEqualTo("direct", true)) }
    val prayersWeek = async { count(db.collection("prayers").whereGreaterThanOrEqualTo("createdAt", weekAgo)) }
    val prayersLeaders = async { count(db.collection("leaderPrayers")) }
    val events = async { count(db.collection("events").whereGreaterThanOrEqualTo("start", now)) }
    val serving = async { count(db.collection("servingSlots").whereGreaterThanOrEqualTo("start", now)) }
    val feedback = async { count(db.collection("feedback")) }
    val totalRooms = rooms.await()
    val directRooms = directs.await()
    DashboardCounts(
        accounts = accounts.await(),
        members = members.await(),
        groups = if (totalRooms < 0 || directRooms < 0) -1L else totalRooms - directRooms,
        prayersWeek = prayersWeek.await(),
        prayersLeaders = prayersLeaders.await(),
        events = events.await(),
        serving = serving.await(),
        feedback = feedback.await()
    )
}

/** A short look at the church in the app, for leaders only. */
@Composable
fun LeaderDashboard() {
    var counts by remember { mutableStateOf<DashboardCounts?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        counts = null
        counts = loadCounts()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.dashboard_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val c = counts
        if (c == null) {
            SkeletonCards(count = 4)
        } else {
            StatRow(R.string.dash_accounts, c.accounts, R.string.dash_members, c.members)
            StatRow(R.string.dash_groups, c.groups, R.string.dash_events, c.events)
            StatRow(R.string.dash_prayers_week, c.prayersWeek, R.string.dash_prayers_leaders, c.prayersLeaders)
            StatRow(R.string.dash_serving, c.serving, R.string.dash_feedback, c.feedback)
        }
        TextButton(onClick = { tick++ }) { Text(stringResource(R.string.dash_refresh)) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatRow(labelA: Int, valueA: Long, labelB: Int, valueB: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(labelA, valueA, Modifier.weight(1f))
        StatCard(labelB, valueB, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: Int, value: Long, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (value < 0) "\u2013" else value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
