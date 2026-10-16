/**
 * Church app notification server.
 *
 * It sends a phone notification when:
 *   - someone sends a message in a chat group or a private message (to everyone else in that conversation)
 *   - an admin posts a new announcement (to everyone)
 *   - an admin adds a new event (to everyone)
 *   - an admin publishes a new sermon (to everyone)
 *   - your YouTube channel starts a live stream (to everyone), and the "LIVE NOW" banner in the app switches on
 *   - a member sends feedback (to the admins)
 *   - every evening at 18:00, volunteers are reminded of what they signed up for tomorrow
 *
 * The app saves each phone's notification address, and what that person wants (chat, announcements, events,
 * live services, quiet hours), in the database (collection "fcmTokens"). Everything below respects those choices.
 */
const { onDocumentCreated, onDocumentUpdated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

// ===== SETTINGS YOU MAY NEED TO CHANGE =====

// Must match where your database is. In the Firebase console: Project settings > General > "Default GCP resource location".
//   "nam5 (United States)"  ->  "us-central1"
//   "eur3 (Europe)"         ->  "europe-west1"
//   a single region such as europe-west3 or europe-north1 -> use that same name
const REGION = "europe-west1";

// Used when writing the time of an event in a notification.
const TIME_ZONE = "Europe/Oslo";

// Switch these to false if you only want chat notifications.
const NOTIFY_ANNOUNCEMENTS = true;
const NOTIFY_EVENTS = true;
const NOTIFY_SERMONS = true;
const DETECT_YOUTUBE_LIVE = true;  // needs YOUTUBE_API_KEY and YOUTUBE_CHANNEL_ID in functions/.env (see LIVE-AND-PLAY-STEPS.md)
const NOTIFY_VISITORS = true;   // tell the admins when a visitor says hello in the app
const NOTIFY_FEEDBACK = true;   // tell the admins when a member sends feedback
const REMIND_GIVING = true;     // remind everyone about giving on the 1st and the 15th of every month
const GIVING_SCHEDULE = "0 9 1,15 * *";   // 09:00 on the 1st and the 15th (in the time zone above)
const GIVING_TITLE = "Giving";
const GIVING_BODY = "A reminder about giving to our church. Tap to see how to give.";
const REMIND_EVENTS = true;     // remind people a day and an hour before an event (not those who answered "No")
const REMIND_SERVING = true;    // remind volunteers once a week (see SERVING_REMINDER_SCHEDULE below)

// ===========================================

const DEAD_TOKEN_ERRORS = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

function shorten(text, max) {
  const t = String(text || "").replace(/\s+/g, " ").trim();
  return t.length > max ? t.slice(0, max - 1) + "…" : t;
}

/** Minutes since midnight where the person is, from the phone's time zone (saved with their notification choices). */
function localMinutes(tzOffsetMinutes) {
  const now = new Date();
  const utc = now.getUTCHours() * 60 + now.getUTCMinutes();
  return (((utc + (Number.isInteger(tzOffsetMinutes) ? tzOffsetMinutes : 0)) % 1440) + 1440) % 1440;
}

/**
 * Does this person (their fcmTokens document) want this kind of notification right now?
 * type: "chat", "announcements", "events" or "live" (or null for no type choice).
 * Urgent notifications ignore quiet hours.
 */
function wants(data, type, urgent) {
  const prefs = data.prefs || {};
  if (type && prefs[type] === false) return false;
  if (!urgent && prefs.quietOn === true) {
    const from = (Number.isInteger(prefs.quietFrom) ? prefs.quietFrom : 22) * 60;
    const to = (Number.isInteger(prefs.quietTo) ? prefs.quietTo : 7) * 60;
    const now = localMinutes(data.tzOffset);
    const inside = from <= to ? now >= from && now < to : now >= from || now < to;
    if (from !== to && inside) return false;
  }
  return true;
}

/**
 * Looks up the notification addresses of some people, leaving out anyone who muted the conversation
 * (opts.mutedRoomId) or does not want this type now (opts.type, opts.urgent). Returns a list of { uid, token }.
 */
async function tokensFor(uids, opts = {}) {
  if (uids.length === 0) return [];
  const docs = await db.getAll(...uids.map((uid) => db.doc(`fcmTokens/${uid}`)));
  const result = [];
  for (const doc of docs) {
    if (!doc.exists) continue;
    const data = doc.data();
    // Someone who muted this conversation gets no notification from it.
    if (opts.mutedRoomId && Array.isArray(data.muted) && data.muted.includes(opts.mutedRoomId)) continue;
    if (!wants(data, opts.type, opts.urgent)) continue;
    if (Array.isArray(data.tokens)) data.tokens.forEach((token) => result.push({ uid: doc.id, token }));
  }
  return result;
}

/** Sends a notification to everyone who has notifications on and wants this type right now. */
async function broadcast(type, message, urgent, exclude) {
  const snap = await db.collection("fcmTokens").get();
  const entries = [];
  for (const doc of snap.docs) {
    const data = doc.data();
    if (exclude && exclude.has(doc.id)) continue;
    if (!wants(data, type, urgent)) continue;
    if (Array.isArray(data.tokens)) data.tokens.forEach((token) => entries.push({ uid: doc.id, token }));
  }
  if (entries.length === 0) return;
  await sendToTokens(entries, message);
}

/** Sends one notification to many phones, and removes addresses that no longer work. */
async function sendToTokens(entries, message) {
  const dead = [];
  for (let i = 0; i < entries.length; i += 500) {
    const chunk = entries.slice(i, i + 500);
    const response = await getMessaging().sendEachForMulticast({
      tokens: chunk.map((e) => e.token),
      ...message,
    });
    response.responses.forEach((r, index) => {
      if (!r.success && r.error && DEAD_TOKEN_ERRORS.has(r.error.code)) dead.push(chunk[index]);
    });
  }
  await Promise.all(
    dead.map((e) =>
      db.doc(`fcmTokens/${e.uid}`).set({ tokens: FieldValue.arrayRemove(e.token) }, { merge: true })
    )
  );
}

// ---------- Chat messages ----------
exports.notifyChatMessage = onDocumentCreated(
  { document: "chatRooms/{roomId}/messages/{messageId}", region: REGION },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const msg = snap.data();
    if (!msg || msg.deleted || !(msg.text || msg.imagePath || msg.audioPath)) return;

    const roomId = event.params.roomId;
    const roomSnap = await db.doc(`chatRooms/${roomId}`).get();
    if (!roomSnap.exists) return;
    const room = roomSnap.data();

    const recipients = (room.memberIds || []).filter((uid) => uid !== msg.senderId);
    const entries = await tokensFor(recipients, { mutedRoomId: roomId, type: "chat" });
    if (entries.length === 0) return;

    const title = room.direct ? msg.senderName || "New message" : room.name || "New message";
    const preview = msg.text || (msg.audioPath ? "\uD83C\uDFA4" : "\uD83D\uDCF7");
    const body = shorten(room.direct ? preview : `${msg.senderName}: ${preview}`, 140);

    await sendToTokens(entries, {
      notification: { title, body },
      data: { roomId },
      android: { priority: "high", notification: { channelId: "chat_messages" } },
    });
    logger.info(`Chat notification for room ${roomId} sent to ${entries.length} phone(s).`);
  }
);

// ---------- Announcements ----------
exports.notifyAnnouncement = onDocumentCreated(
  { document: "announcements/{announcementId}", region: REGION },
  async (event) => {
    if (!NOTIFY_ANNOUNCEMENTS || !event.data) return;
    const a = event.data.data();
    if (!a || !a.title) return;
    // A scheduled announcement is sent by sendScheduledAnnouncements, when its time comes.
    if (a.publishAt && typeof a.publishAt.toDate === "function" && a.publishAt.toDate() > new Date()) return;
    await broadcast(
      "announcements",
      {
        notification: { title: shorten(a.title, 80), body: shorten(a.message || "", 140) },
        android: { notification: { channelId: "announcements" } },
      },
      a.category === "urgent"
    );
  }
);

// ---------- Scheduled announcements ----------
// An admin can set "Publish later". This runs every 5 minutes and announces the ones whose time has come.
exports.sendScheduledAnnouncements = onSchedule(
  { schedule: "every 5 minutes", timeZone: TIME_ZONE, region: REGION },
  async () => {
    if (!NOTIFY_ANNOUNCEMENTS) return;
    const now = new Date();
    const since = new Date(now.getTime() - 24 * 3600 * 1000);
    const snap = await db
      .collection("announcements")
      .where("publishAt", ">", since)
      .where("publishAt", "<=", now)
      .get();
    for (const doc of snap.docs) {
      const a = doc.data();
      if (a.notified === true || !a.title) continue;
      await doc.ref.update({ notified: true });
      await broadcast(
        "announcements",
        {
          notification: { title: shorten(a.title, 80), body: shorten(a.message || "", 140) },
          android: { notification: { channelId: "announcements" } },
        },
        a.category === "urgent"
      );
    }
  }
);

// ---------- Events ----------
exports.notifyEvent = onDocumentCreated(
  { document: "events/{eventId}", region: REGION },
  async (event) => {
    if (!NOTIFY_EVENTS || !event.data) return;
    const e = event.data.data();
    if (!e || !e.title) return;
    let when = "";
    if (e.start && typeof e.start.toDate === "function") {
      when = e.start.toDate().toLocaleString("en-GB", {
        timeZone: TIME_ZONE,
        weekday: "short",
        day: "numeric",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
      });
    }
    const where = e.location ? `, ${e.location}` : "";
    await broadcast("events", {
      notification: { title: shorten(e.title, 80), body: shorten(`${when}${where}`, 140) },
      android: { notification: { channelId: "announcements" } },
    });
  }
);

// ---------- Sermons ----------
async function announceSermon(sermon) {
  const body = sermon.speaker ? shorten(sermon.speaker, 80) : "";
  await broadcast("sermons", {
    notification: { title: "New sermon: " + shorten(sermon.title, 70), body },
    data: { target: "sermons" },
    android: { notification: { channelId: "announcements" } },
  });
}

// A new sermon that is published straight away.
exports.notifySermon = onDocumentCreated(
  { document: "sermons/{sermonId}", region: REGION },
  async (event) => {
    if (!NOTIFY_SERMONS || !event.data) return;
    const sermon = event.data.data();
    // Drafts are not announced.
    if (!sermon || !sermon.title || sermon.published === false) return;
    await announceSermon(sermon);
  }
);

// A draft that is published later.
exports.notifySermonPublished = onDocumentUpdated(
  { document: "sermons/{sermonId}", region: REGION },
  async (event) => {
    if (!NOTIFY_SERMONS || !event.data) return;
    const before = event.data.before.data();
    const after = event.data.after.data();
    if (!before || !after || !after.title) return;
    if (before.published === false && after.published !== false) await announceSermon(after);
  }
);

// ---------- Event reminders: a day before and an hour before ----------
exports.eventReminders = onSchedule(
  { schedule: "every 15 minutes", timeZone: TIME_ZONE, region: REGION },
  async () => {
    if (!REMIND_EVENTS) return;
    const now = Date.now();
    const snap = await db
      .collection("events")
      .where("start", ">=", new Date(now))
      .where("start", "<=", new Date(now + 25 * 3600 * 1000))
      .get();
    for (const doc of snap.docs) {
      const e = doc.data();
      if (!e.title || !e.start || typeof e.start.toDate !== "function") continue;
      const start = e.start.toDate();
      const until = start.getTime() - now;
      const created = e.createdAt && typeof e.createdAt.toDate === "function" ? e.createdAt.toDate().getTime() : 0;

      const stateRef = db.doc(`eventReminders/${doc.id}`);
      let state = (await stateRef.get()).data() || {};
      // The event was moved to another time: remind again.
      if (state.start && typeof state.start.toMillis === "function" && state.start.toMillis() !== start.getTime()) state = {};

      let day = !!state.day;
      let hour = !!state.hour;
      let kind = null;
      if (!hour && until <= 3600 * 1000) {
        hour = true;
        kind = "hour";
      } else if (!day && until <= 24 * 3600 * 1000) {
        day = true;
        // An event added less than a day ahead needs no "a day before" reminder.
        if (!created || start.getTime() - created >= 24 * 3600 * 1000) kind = "day";
      }
      if (!kind) {
        if (day !== !!state.day) await stateRef.set({ start: e.start, day, hour });
        continue;
      }

      const rsvps = await doc.ref.collection("rsvps").where("status", "==", "no").get();
      const exclude = new Set(rsvps.docs.map((d) => d.id));
      const when = start.toLocaleString("en-GB", {
        timeZone: TIME_ZONE,
        weekday: "short",
        hour: "2-digit",
        minute: "2-digit",
      });
      const where = e.location ? `, ${e.location}` : "";
      const prefix = kind === "hour" ? "In 1 hour: " : until >= 12 * 3600 * 1000 ? "Tomorrow: " : "Today: ";
      await broadcast(
        "eventReminders",
        {
          notification: { title: shorten(prefix + e.title, 80), body: shorten(`${when}${where}`, 140) },
          data: { target: "events" },
          android: { notification: { channelId: "announcements" } },
        },
        false,
        exclude
      );
      await stateRef.set({ start: e.start, day, hour });
    }
  }
);

// ---------- The verse of the day ----------
// A well-known verse for each day, in the public-domain World English Bible, from the free service bible-api.com.
// Leaders can still write their own "verse of the week" in the app; it replaces this one while it is set.
const VERSES = [
  "John 3:16", "Psalm 23:1-3", "Philippians 4:13", "Jeremiah 29:11", "Romans 8:28",
  "Proverbs 3:5-6", "Isaiah 41:10", "Matthew 11:28", "Joshua 1:9", "Psalm 46:1",
  "Romans 12:2", "Galatians 5:22-23", "Ephesians 2:8-9", "2 Timothy 1:7", "Psalm 121:1-2",
  "Isaiah 40:31", "Matthew 6:33", "Romans 5:8", "1 Corinthians 13:4-7", "Hebrews 11:1",
  "Psalm 27:1", "Psalm 119:105", "Matthew 5:16", "John 14:6", "John 14:27",
  "John 15:5", "Romans 15:13", "2 Corinthians 5:17", "Philippians 4:6-7", "Colossians 3:23",
  "1 Peter 5:7", "James 1:5", "1 John 4:19", "Psalm 37:4", "Psalm 34:8",
  "Proverbs 16:3", "Isaiah 26:3", "Lamentations 3:22-23", "Micah 6:8", "Matthew 28:19-20",
  "Acts 1:8", "Romans 10:9", "Ephesians 6:10", "Hebrews 4:16", "Psalm 91:1-2",
  "Psalm 100:4-5", "Psalm 139:14", "Isaiah 43:2", "Deuteronomy 31:6", "Numbers 6:24-26",
  "Matthew 7:7", "Luke 1:37", "John 1:1", "John 8:12", "John 10:10",
  "John 11:25", "John 13:34", "Romans 6:23", "Romans 8:38-39", "1 Corinthians 10:13",
  "2 Corinthians 12:9", "Galatians 2:20", "Ephesians 3:20", "Philippians 1:6", "Philippians 2:3-4",
  "1 Thessalonians 5:16-18", "2 Timothy 3:16", "Hebrews 12:1-2", "James 4:8", "1 Peter 2:9",
  "1 John 1:9", "Revelation 21:4", "Psalm 16:11", "Psalm 19:14", "Psalm 30:5",
  "Psalm 51:10", "Psalm 62:1", "Psalm 73:26", "Psalm 103:1-2", "Psalm 118:24",
  "Psalm 145:18", "Proverbs 4:23", "Proverbs 18:10", "Proverbs 27:17", "Ecclesiastes 3:1",
  "Isaiah 9:6", "Isaiah 53:5", "Isaiah 55:8-9", "Jeremiah 33:3", "Habakkuk 3:19",
  "Zephaniah 3:17", "Matthew 5:3-4", "Matthew 5:9", "Matthew 6:25", "Matthew 18:20",
  "Mark 10:27", "Luke 6:31", "Luke 11:9", "John 6:35", "John 16:33",
  "Acts 2:38", "Genesis 1:1", "Genesis 28:15", "Exodus 14:14", "Exodus 15:2",
  "Deuteronomy 6:5", "Deuteronomy 7:9", "1 Samuel 16:7", "2 Chronicles 7:14", "Nehemiah 8:10",
  "Job 19:25", "Psalm 1:1-2", "Psalm 8:3-4", "Psalm 18:2", "Psalm 25:4-5",
  "Psalm 28:7", "Psalm 33:4", "Psalm 40:1-2", "Psalm 42:1", "Psalm 55:22",
  "Psalm 56:3-4", "Psalm 63:1", "Psalm 84:11", "Psalm 90:12", "Psalm 107:1",
  "Psalm 112:7", "Psalm 116:1-2", "Psalm 127:1", "Psalm 133:1", "Psalm 138:8",
  "Psalm 143:8", "Proverbs 1:7", "Proverbs 11:25", "Proverbs 15:1", "Proverbs 17:17",
  "Proverbs 22:6", "Isaiah 6:8", "Isaiah 12:2", "Isaiah 30:15", "Isaiah 40:8",
  "Isaiah 54:10", "Isaiah 61:1", "Jeremiah 17:7-8", "Ezekiel 36:26", "Daniel 3:17-18",
  "Nahum 1:7", "Malachi 3:10", "Matthew 4:19", "Matthew 5:14", "Matthew 5:44",
  "Matthew 6:14", "Matthew 22:37-39", "Luke 2:10-11", "Luke 10:27", "Luke 15:7",
  "John 3:17", "John 4:24", "John 7:38", "John 12:24", "John 17:3",
  "Acts 4:12", "Acts 16:31", "Romans 1:16", "Romans 3:23", "Romans 8:1",
  "Romans 8:31", "Romans 12:12", "Romans 12:21", "1 Corinthians 15:57-58", "2 Corinthians 9:7",
  "Galatians 6:9", "Ephesians 4:32", "Colossians 3:12-14", "1 Thessalonians 5:11", "1 Timothy 4:12",
  "Hebrews 10:23-24", "Hebrews 13:5-6", "James 1:2-3", "James 1:22", "1 Peter 1:3",
  "1 Peter 4:8", "1 John 3:1", "1 John 4:7-8", "Revelation 3:20", "Genesis 1:27",
  "Genesis 2:18", "Genesis 12:2", "Genesis 50:20", "Exodus 3:14", "Exodus 20:12",
  "Exodus 33:14", "Leviticus 19:18", "Numbers 23:19", "Deuteronomy 4:29", "Deuteronomy 10:12",
  "Deuteronomy 30:19-20", "Deuteronomy 32:4", "Joshua 24:15", "Judges 6:12", "Ruth 1:16",
  "1 Samuel 2:2", "1 Samuel 17:47", "2 Samuel 22:31", "1 Kings 8:56", "1 Chronicles 16:11",
  "1 Chronicles 16:34", "1 Chronicles 29:11", "2 Chronicles 16:9", "2 Chronicles 20:15", "Ezra 7:10",
  "Esther 4:14", "Job 1:21", "Job 12:10", "Job 23:10", "Job 42:2",
  "Psalm 3:3", "Psalm 4:8", "Psalm 5:11", "Psalm 9:1-2", "Psalm 9:10",
  "Psalm 10:17", "Psalm 13:5", "Psalm 17:8", "Psalm 20:7", "Psalm 24:1",
  "Psalm 25:1-2", "Psalm 29:11", "Psalm 31:24", "Psalm 32:8", "Psalm 34:4",
  "Psalm 34:18", "Psalm 36:5", "Psalm 37:5", "Psalm 37:23-24", "Psalm 39:7",
  "Psalm 43:5", "Psalm 46:10", "Psalm 47:1", "Psalm 48:14", "Psalm 50:15",
  "Psalm 57:1", "Psalm 59:16", "Psalm 61:2", "Psalm 66:1-2", "Psalm 68:19",
  "Psalm 71:5", "Psalm 77:11", "Psalm 85:10", "Psalm 86:5", "Psalm 86:11",
  "Psalm 89:1", "Psalm 90:1-2", "Psalm 92:1-2", "Psalm 94:19", "Psalm 95:6",
  "Psalm 96:1", "Psalm 97:12", "Psalm 98:1", "Psalm 104:33", "Psalm 105:1",
  "Psalm 106:1", "Psalm 108:1", "Psalm 111:10", "Psalm 113:3", "Psalm 119:11",
  "Psalm 119:50", "Psalm 119:130", "Psalm 121:7-8", "Psalm 122:1", "Psalm 124:8",
  "Psalm 125:1", "Psalm 126:5-6", "Psalm 130:5", "Psalm 136:1", "Psalm 138:3",
  "Psalm 139:23-24", "Psalm 141:3", "Psalm 142:5", "Psalm 143:10", "Psalm 144:1",
  "Psalm 146:5", "Psalm 147:3", "Psalm 148:13", "Psalm 150:6", "Proverbs 2:6",
  "Proverbs 3:9-10", "Proverbs 9:10", "Proverbs 10:12", "Proverbs 12:25", "Proverbs 13:20",
  "Proverbs 14:26", "Proverbs 16:9", "Proverbs 16:24", "Proverbs 17:22", "Proverbs 19:21",
  "Proverbs 20:7", "Proverbs 21:21", "Proverbs 22:1", "Proverbs 24:16", "Proverbs 29:25",
  "Proverbs 30:5", "Proverbs 31:30", "Ecclesiastes 4:9-10", "Ecclesiastes 12:13", "Isaiah 1:18",
  "Isaiah 7:14", "Isaiah 25:1", "Isaiah 26:4", "Isaiah 33:6", "Isaiah 35:4",
  "Isaiah 41:13", "Isaiah 42:3", "Isaiah 43:1", "Isaiah 43:18-19", "Isaiah 44:3",
  "Isaiah 46:4", "Isaiah 49:15-16", "Isaiah 52:7", "Isaiah 58:11", "Isaiah 60:1",
  "Isaiah 64:8", "Jeremiah 1:5", "Jeremiah 10:6", "Jeremiah 15:16", "Jeremiah 31:3",
  "Jeremiah 32:17", "Lamentations 3:25", "Daniel 12:3", "Hosea 6:3", "Hosea 10:12",
  "Joel 2:28", "Amos 5:24", "Jonah 2:2", "Micah 7:7", "Habakkuk 2:4",
  "Zechariah 4:6", "Malachi 4:2", "Matthew 1:21", "Matthew 5:6", "Matthew 5:8",
  "Matthew 6:9-10", "Matthew 6:21", "Matthew 6:34", "Matthew 7:12", "Matthew 7:24",
  "Matthew 9:37-38", "Matthew 10:32", "Matthew 11:29-30", "Matthew 14:27", "Matthew 16:24",
  "Matthew 17:20", "Matthew 18:3", "Matthew 19:26", "Matthew 20:28", "Matthew 24:35",
  "Matthew 25:40", "Matthew 26:41", "Mark 1:15", "Mark 8:36", "Mark 9:23",
  "Mark 10:45", "Mark 11:24", "Mark 16:15", "Luke 1:46-47", "Luke 2:14",
  "Luke 4:18",
];

async function fetchVerse(reference) {
  const res = await fetch("https://bible-api.com/" + encodeURIComponent(reference) + "?translation=web");
  if (!res.ok) throw new Error("bible-api.com answered " + res.status + " for " + reference);
  const data = await res.json();
  const text = String(data.text || "").replace(/\s+/g, " ").trim();
  if (!text) throw new Error("No verse text came back for " + reference);
  return {
    reference: String(data.reference || reference),
    text: text.slice(0, 600),
    translation: String(data.translation_name || "World English Bible"),
  };
}

// Writes today's English verse. It follows the church's own verse list: the same day number the app uses, so English
// and Afaan Oromoo readers get the same verse. (The church's list only needs the Afaan Oromoo text; the English comes
// from here, using the English reference in the list, or the suggested verse for that day.)
async function writeVerseOfTheDay() {
  const localDate = new Date().toLocaleDateString("en-CA", { timeZone: TIME_ZONE });
  const epochDay = Math.floor(Date.parse(localDate + "T00:00:00Z") / 86400000);
  const meta = await db.doc("settings/verseBank").get();
  const count = meta.exists ? Number(meta.data().count || 0) : 0;
  const candidates = [];
  if (count > 0) {
    const n = (epochDay % count) + 1;
    const entry = await db.doc(`verseBank/${n}`).get();
    if (entry.exists && entry.data().refEn) candidates.push(String(entry.data().refEn));
    candidates.push(VERSES[(n - 1) % VERSES.length]);
  } else {
    candidates.push(VERSES[epochDay % VERSES.length]);
  }
  let verse = null;
  let lastError = null;
  for (const reference of candidates) {
    try {
      verse = await fetchVerse(reference);
      break;
    } catch (err) {
      lastError = err;
    }
  }
  if (!verse) throw lastError || new Error("No verse could be fetched");
  await db.doc("settings/verseOfDay").set({ ...verse, date: localDate, updatedAt: FieldValue.serverTimestamp() });
}

exports.verseOfTheDay = onSchedule(
  { schedule: "5 0 * * *", timeZone: TIME_ZONE, region: REGION },
  async () => {
    try {
      await writeVerseOfTheDay();
    } catch (err) {
      // Keep yesterday's verse if today's cannot be fetched.
      logger.error("Could not write the verse of the day", err);
    }
  }
);

// ---------- Giving reminder: the 1st and the 15th ----------
exports.givingReminder = onSchedule(
  { schedule: GIVING_SCHEDULE, timeZone: TIME_ZONE, region: REGION },
  async () => {
    if (!REMIND_GIVING) return;
    await broadcast("giving", {
      notification: { title: GIVING_TITLE, body: GIVING_BODY },
      data: { target: "give" },
      android: { notification: { channelId: "announcements" } },
    });
  }
);

// ---------- A leader switches the live banner on by hand (for example a Facebook live) ----------
exports.notifyLiveManual = onDocumentWritten(
  { document: "settings/live", region: REGION },
  async (event) => {
    if (!event.data) return;
    const before = (event.data.before && event.data.before.data()) || {};
    const after = (event.data.after && event.data.after.data()) || {};
    if (after.source === "auto") return;           // the YouTube check announces its own
    if (before.live === true || after.live !== true) return;
    await broadcast("live", {
      notification: { title: "\uD83D\uDD34 We are live", body: shorten(after.title || "Join us now", 100) },
      android: { notification: { channelId: "announcements" } },
    });
  }
);

// ---------- Feedback to the leaders ----------
exports.notifyFeedback = onDocumentCreated(
  { document: "feedback/{feedbackId}", region: REGION },
  async () => {
    if (!NOTIFY_FEEDBACK) return;
    const entries = await tokensFor(await adminIds());
    if (entries.length === 0) return;
    // The message itself is never put in the notification, only that something arrived.
    await sendToTokens(entries, {
      notification: { title: "New feedback", body: "A member sent a message to the leaders." },
      android: { notification: { channelId: "announcements" } },
    });
  }
);

// ---------- Messages people scheduled to send later ----------
exports.sendScheduledMessages = onSchedule(
  { schedule: "every 5 minutes", timeZone: TIME_ZONE, region: REGION },
  async () => {
    const due = await db.collection("scheduledMessages").where("sendAt", "<=", new Date()).limit(50).get();
    for (const doc of due.docs) {
      const m = doc.data();
      try {
        const roomRef = db.doc(`chatRooms/${m.roomId}`);
        const room = await roomRef.get();
        if (room.exists) {
          const data = room.data();
          const stillMember = Array.isArray(data.memberIds) && data.memberIds.includes(m.senderId);
          // A channel only takes messages from leaders; check again at the moment of sending.
          const allowed = !data.channel || (await isAdminUid(m.senderId));
          if (stillMember && allowed && !data.direct) {
            await roomRef.collection("messages").add({
              text: m.text,
              senderId: m.senderId,
              senderName: m.senderName,
              role: await roleOf(m.senderId),
              createdAt: FieldValue.serverTimestamp(),
              deleted: false,
            });
            await roomRef.update({
              lastMessageAt: FieldValue.serverTimestamp(),
              lastMessageText: String(m.text).slice(0, 100),
              lastMessageBy: m.senderId,
            });
          }
        }
      } catch (err) {
        logger.error("Sending a scheduled message failed", err);
        continue;
      }
      await doc.ref.delete();
    }
  }
);

// ---------- Who counts as a leader: the owners (admins list) and anyone with leader access that has not ended ----------
async function adminIds() {
  const ids = new Set((await db.collection("admins").get()).docs.map((d) => d.id));
  const leaders = await db.collection("leaders").get();
  const now = Date.now();
  leaders.docs.forEach((d) => {
    const until = d.data().until;
    if (!until || (typeof until.toMillis === "function" && until.toMillis() > now)) ids.add(d.id);
  });
  return Array.from(ids);
}

async function isAdminUid(uid) {
  return (await adminIds()).includes(uid);
}

// The title shown next to a name in chat: "admin" for an owner, "leader" for someone with leader access, otherwise "".
async function roleOf(uid) {
  if ((await db.doc(`admins/${uid}`).get()).exists) return "admin";
  return (await isAdminUid(uid)) ? "leader" : "";
}

// ---------- Open groups: everyone in the church is in them (nobody joins on their own) ----------
// A leader can make a group "open". It then holds every approved member: the people who are approved when it is made, and
// everyone who is approved afterwards. Only this server and the leaders add people; nobody can add themselves.
exports.fillOpenGroup = onDocumentCreated(
  { document: "chatRooms/{roomId}", region: REGION },
  async (event) => {
    const room = event.data && event.data.data();
    if (!room || room.open !== true || room.direct === true) return;
    // Only a leader's group counts as an "everyone" group.
    const admins = new Set(await adminIds());
    if (!admins.has(String(room.createdBy || ""))) return;
    const members = await db.collection("approvedMembers").limit(1000).get();
    if (members.empty) return;
    const update = { memberIds: FieldValue.arrayUnion(...members.docs.map((d) => d.id)) };
    members.docs.forEach((d) => {
      update[`memberNames.${d.id}`] = String(d.data().name || "Member");
    });
    await event.data.ref.update(update);
  }
);

exports.addApprovedToOpenGroups = onDocumentCreated(
  { document: "approvedMembers/{uid}", region: REGION },
  async (event) => {
    const uid = event.params.uid;
    const data = (event.data && event.data.data()) || {};
    const name = String(data.name || "Member");
    const admins = new Set(await adminIds());
    const open = await db.collection("chatRooms").where("open", "==", true).get();
    const batch = db.batch();
    let count = 0;
    open.docs.forEach((doc) => {
      const room = doc.data();
      if (room.direct === true || !admins.has(String(room.createdBy || ""))) return;
      batch.update(doc.ref, { memberIds: FieldValue.arrayUnion(uid), [`memberNames.${uid}`]: name });
      count++;
    });
    if (count > 0) await batch.commit();
  }
);

// ---------- A visitor says hello ----------
exports.notifyVisitor = onDocumentCreated(
  { document: "visitors/{visitorId}", region: REGION },
  async () => {
    if (!NOTIFY_VISITORS) return;
    const entries = await tokensFor(await adminIds());
    if (entries.length === 0) return;
    // The visitor's details are never put in the notification, only that someone said hello.
    await sendToTokens(entries, {
      notification: { title: "New visitor", body: "Someone visiting the church said hello in the app." },
      android: { notification: { channelId: "announcements" } },
    });
  }
);

// ---------- Serving reminders (once a week) ----------
// Every Thursday evening each volunteer gets ONE notification listing what they are down to serve in the
// next few days. To use Friday instead, change "thursday" to "friday" and deploy again.
const SERVING_REMINDER_SCHEDULE = "every thursday 18:00";
const SERVING_LOOKAHEAD_DAYS = 5;

exports.servingReminders = onSchedule(
  { schedule: SERVING_REMINDER_SCHEDULE, timeZone: TIME_ZONE, region: REGION },
  async () => {
    if (!REMIND_SERVING) return;
    const from = new Date();
    const to = new Date(Date.now() + SERVING_LOOKAHEAD_DAYS * 24 * 3600 * 1000);
    const snap = await db.collection("servingSlots").where("start", ">=", from).where("start", "<=", to).get();

    // Collect each person's slots.
    const perPerson = new Map(); // uid -> [{ start, title }]
    for (const doc of snap.docs) {
      const slot = doc.data();
      if (!slot.start || typeof slot.start.toDate !== "function") continue;
      const volunteers = Array.isArray(slot.volunteers) ? slot.volunteers : [];
      for (const uid of volunteers) {
        if (!perPerson.has(uid)) perPerson.set(uid, []);
        perPerson.get(uid).push({ start: slot.start.toDate(), title: slot.title });
      }
    }

    for (const [uid, slots] of perPerson) {
      const entries = await tokensFor([uid]);
      if (entries.length === 0) continue;
      slots.sort((a, b) => a.start - b.start);
      const lines = slots.slice(0, 4).map((s) => {
        const day = s.start.toLocaleDateString("en-GB", { timeZone: TIME_ZONE, weekday: "long" });
        const time = s.start.toLocaleTimeString("en-GB", { timeZone: TIME_ZONE, hour: "2-digit", minute: "2-digit" });
        return `${day} ${time}: ${shorten(s.title, 40)}`;
      });
      if (slots.length > 4) lines.push(`+${slots.length - 4} more`);
      await sendToTokens(entries, {
        notification: { title: "You are serving this week", body: lines.join("\n") },
        android: { notification: { channelId: "announcements" } },
      });
    }
  }
);

// ---------- YouTube live detection ----------
async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`YouTube API answered ${response.status}`);
  return response.json();
}

exports.checkYouTubeLive = onSchedule(
  { schedule: "every 5 minutes", region: REGION, timeZone: TIME_ZONE },
  async () => {
    const key = process.env.YOUTUBE_API_KEY;
    const channelId = process.env.YOUTUBE_CHANNEL_ID;
    if (!DETECT_YOUTUBE_LIVE || !key || !channelId || !channelId.startsWith("UC")) return;
    try {
      // A channel's uploads (live streams included) are listed in a playlist whose id is the channel id with UU in front.
      const uploads = "UU" + channelId.slice(2);
      const list = await getJson(
        `https://www.googleapis.com/youtube/v3/playlistItems?part=contentDetails&maxResults=5&playlistId=${uploads}&key=${key}`
      );
      const ids = (list.items || []).map((i) => i.contentDetails && i.contentDetails.videoId).filter(Boolean);
      let liveVideo = null;
      if (ids.length > 0) {
        const videos = await getJson(
          `https://www.googleapis.com/youtube/v3/videos?part=snippet&id=${ids.join(",")}&key=${key}`
        );
        liveVideo =
          (videos.items || []).find((v) => v.snippet && v.snippet.liveBroadcastContent === "live") || null;
      }

      const ref = db.doc("settings/live");
      const current = (await ref.get()).data() || {};
      if (liveVideo) {
        const url = `https://www.youtube.com/watch?v=${liveVideo.id}`;
        const title = shorten(liveVideo.snippet.title, 100);
        const justStarted = !(current.live && current.url === url);
        await ref.set({ live: true, url, title, source: "auto", updatedAt: FieldValue.serverTimestamp() });
        if (justStarted) {
          await broadcast("live", {
            notification: { title: "\uD83D\uDD34 We are live", body: title },
            android: { notification: { channelId: "announcements" } },
          });
        }
      } else if (current.live && current.source === "auto") {
        await ref.set({ live: false, url: "", title: "", source: "auto", updatedAt: FieldValue.serverTimestamp() });
      }
    } catch (err) {
      logger.error("YouTube live check failed", err);
    }
  }
);
