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
const { getAuth } = require("firebase-admin/auth");
const { onRequest } = require("firebase-functions/v2/https");
const bibleLib = require("./bibleLib");
const yvLib = require("./yvLib");

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

// ---------- The verse of the day from API.Bible: the same verse in English, Norsk and Afaan Oromoo ----------
// Needs a free key and the three Bible IDs in functions/.env (see .env.example). Without them the English verse comes from
// bible-api.com (World English Bible) as before, and only English.
//
// Some Bibles in API.Bible have only a part of the Bible (for example only the New Testament). So each night the server asks
// each Bible which books it has and picks the verse of the day only from books that the DECIDING Bibles have. The deciding
// languages are set with APIBIBLE_PICK_FROM in .env (default "en,om"). A language that is not deciding (for example Norsk
// when its Bible has only the New Testament) shows its own text on the days its Bible has the verse, and the English text of
// the same verse on the other days: the verse is always the same for everyone.
const APIBIBLE_BASE = "https://api.scripture.api.bible/v1";

// English book names (as in the verse list) and the codes API.Bible uses for them.
const BOOK_LIST = [
  ["GEN", "genesis"], ["EXO", "exodus"], ["LEV", "leviticus"], ["NUM", "numbers"], ["DEU", "deuteronomy"], ["JOS", "joshua"],
  ["JDG", "judges"], ["RUT", "ruth"], ["1SA", "1 samuel"], ["2SA", "2 samuel"], ["1KI", "1 kings"], ["2KI", "2 kings"],
  ["1CH", "1 chronicles"], ["2CH", "2 chronicles"], ["EZR", "ezra"], ["NEH", "nehemiah"], ["EST", "esther"], ["JOB", "job"],
  ["PSA", "psalms", "psalm"], ["PRO", "proverbs"], ["ECC", "ecclesiastes"], ["SNG", "song of solomon", "song of songs"],
  ["ISA", "isaiah"], ["JER", "jeremiah"], ["LAM", "lamentations"], ["EZK", "ezekiel"], ["DAN", "daniel"], ["HOS", "hosea"],
  ["JOL", "joel"], ["AMO", "amos"], ["OBA", "obadiah"], ["JON", "jonah"], ["MIC", "micah"], ["NAM", "nahum"],
  ["HAB", "habakkuk"], ["ZEP", "zephaniah"], ["HAG", "haggai"], ["ZEC", "zechariah"], ["MAL", "malachi"],
  ["MAT", "matthew"], ["MRK", "mark"], ["LUK", "luke"], ["JHN", "john"], ["ACT", "acts"], ["ROM", "romans"],
  ["1CO", "1 corinthians"], ["2CO", "2 corinthians"], ["GAL", "galatians"], ["EPH", "ephesians"], ["PHP", "philippians"],
  ["COL", "colossians"], ["1TH", "1 thessalonians"], ["2TH", "2 thessalonians"], ["1TI", "1 timothy"], ["2TI", "2 timothy"],
  ["TIT", "titus"], ["PHM", "philemon"], ["HEB", "hebrews"], ["JAS", "james"], ["1PE", "1 peter"], ["2PE", "2 peter"],
  ["1JN", "1 john"], ["2JN", "2 john"], ["3JN", "3 john"], ["JUD", "jude"], ["REV", "revelation"],
];
const BOOK_BY_NAME = {};
for (const [code, ...names] of BOOK_LIST) for (const name of names) BOOK_BY_NAME[name] = code;

// More New Testament verses. They are used (after the verses of the list above) when a Bible has only the New Testament,
// so that the verse of the day does not start repeating after a few months.
const EXTRA_NT_VERSES = [
  "Matthew 5:3", "Matthew 5:4", "Matthew 5:5", "Matthew 5:6", "Matthew 5:7", "Matthew 5:8", "Matthew 5:9", "Matthew 5:14-16",
  "Matthew 5:44", "Matthew 6:6", "Matthew 6:14", "Matthew 6:21", "Matthew 6:26", "Matthew 6:33", "Matthew 6:34", "Matthew 7:7-8",
  "Matthew 7:11", "Matthew 7:12", "Matthew 7:24", "Matthew 9:36", "Matthew 9:37-38", "Matthew 10:32", "Matthew 11:28-30",
  "Matthew 12:50", "Matthew 14:27", "Matthew 16:24-25", "Matthew 17:20", "Matthew 18:20", "Matthew 19:14", "Matthew 19:26",
  "Matthew 20:28", "Matthew 21:22", "Matthew 22:37-39", "Matthew 24:35", "Matthew 25:21", "Matthew 25:40", "Matthew 28:19-20",
  "Mark 1:15", "Mark 2:17", "Mark 4:39", "Mark 8:36", "Mark 9:23", "Mark 10:14", "Mark 10:27", "Mark 10:45", "Mark 11:24",
  "Mark 12:30-31", "Mark 16:15",
  "Luke 1:37", "Luke 1:46-47", "Luke 2:10-11", "Luke 2:14", "Luke 4:18", "Luke 6:27-28", "Luke 6:31", "Luke 6:36", "Luke 6:38",
  "Luke 8:50", "Luke 9:23", "Luke 10:27", "Luke 11:9-10", "Luke 11:13", "Luke 12:7", "Luke 12:34", "Luke 15:7", "Luke 15:10",
  "Luke 18:27", "Luke 19:10", "Luke 23:34", "Luke 24:6",
  "John 1:1", "John 1:3-4", "John 1:12", "John 1:14", "John 1:29", "John 3:3", "John 3:16-17", "John 3:30", "John 4:14", "John 4:24",
  "John 5:24", "John 6:35", "John 6:37", "John 6:47", "John 7:38", "John 8:12", "John 8:31-32", "John 8:36", "John 10:9-10",
  "John 10:11", "John 10:27-28", "John 11:25-26", "John 12:46", "John 13:34-35", "John 14:1-3", "John 14:6", "John 14:13-14",
  "John 14:15", "John 14:26", "John 14:27", "John 15:4-5", "John 15:7", "John 15:9", "John 15:11-12", "John 15:13", "John 15:16",
  "John 16:13", "John 16:33", "John 17:3", "John 17:17", "John 20:21", "John 20:29", "John 20:31",
  "Acts 1:8", "Acts 2:38", "Acts 2:42", "Acts 3:19", "Acts 4:12", "Acts 4:31", "Acts 5:29", "Acts 10:43", "Acts 16:31",
  "Acts 17:28", "Acts 20:24", "Acts 20:35",
  "Romans 1:16", "Romans 3:23-24", "Romans 5:1", "Romans 5:3-5", "Romans 5:8", "Romans 6:4", "Romans 6:23", "Romans 8:1",
  "Romans 8:14", "Romans 8:18", "Romans 8:26", "Romans 8:28", "Romans 8:31", "Romans 8:37-39", "Romans 10:9-10", "Romans 10:13",
  "Romans 10:17", "Romans 12:1-2", "Romans 12:9-10", "Romans 12:12", "Romans 12:18", "Romans 12:21", "Romans 13:8", "Romans 14:8",
  "Romans 15:4", "Romans 15:13",
  "1 Corinthians 1:18", "1 Corinthians 2:9", "1 Corinthians 6:19-20", "1 Corinthians 9:24", "1 Corinthians 10:13",
  "1 Corinthians 10:31", "1 Corinthians 12:27", "1 Corinthians 13:4-7", "1 Corinthians 13:13", "1 Corinthians 15:3-4",
  "1 Corinthians 15:10", "1 Corinthians 15:58", "1 Corinthians 16:13-14",
  "2 Corinthians 1:3-4", "2 Corinthians 3:17", "2 Corinthians 4:16-18", "2 Corinthians 5:7", "2 Corinthians 5:17",
  "2 Corinthians 5:21", "2 Corinthians 9:7", "2 Corinthians 9:8", "2 Corinthians 12:9", "2 Corinthians 13:14",
  "Galatians 2:20", "Galatians 5:1", "Galatians 5:22-23", "Galatians 6:2", "Galatians 6:9",
  "Ephesians 1:3", "Ephesians 2:8-9", "Ephesians 2:10", "Ephesians 3:20", "Ephesians 4:2-3", "Ephesians 4:32", "Ephesians 5:1-2",
  "Ephesians 6:10", "Ephesians 6:11", "Ephesians 6:18",
  "Philippians 1:6", "Philippians 1:21", "Philippians 2:3-4", "Philippians 2:5", "Philippians 3:13-14", "Philippians 4:4",
  "Philippians 4:6-7", "Philippians 4:8", "Philippians 4:11", "Philippians 4:13", "Philippians 4:19",
  "Colossians 1:17", "Colossians 2:6-7", "Colossians 3:2", "Colossians 3:12", "Colossians 3:13", "Colossians 3:14",
  "Colossians 3:15", "Colossians 3:17", "Colossians 3:23", "Colossians 4:2", "Colossians 4:6",
  "1 Thessalonians 5:11", "1 Thessalonians 5:16-18", "1 Thessalonians 5:24", "2 Thessalonians 3:3", "2 Thessalonians 3:16",
  "1 Timothy 1:15", "1 Timothy 2:1", "1 Timothy 4:12", "1 Timothy 6:6", "1 Timothy 6:12",
  "2 Timothy 1:7", "2 Timothy 1:9", "2 Timothy 2:15", "2 Timothy 3:16-17", "2 Timothy 4:7", "2 Timothy 4:17",
  "Titus 2:11", "Titus 3:5",
  "Hebrews 4:12", "Hebrews 4:16", "Hebrews 6:19", "Hebrews 10:23", "Hebrews 10:24-25", "Hebrews 11:1", "Hebrews 11:6",
  "Hebrews 12:1-2", "Hebrews 13:5", "Hebrews 13:8", "Hebrews 13:15-16",
  "James 1:2-3", "James 1:5", "James 1:12", "James 1:17", "James 1:19", "James 1:22", "James 2:17", "James 3:17", "James 4:7-8",
  "James 4:10", "James 5:16",
  "1 Peter 1:3", "1 Peter 1:8-9", "1 Peter 2:9", "1 Peter 2:24", "1 Peter 3:15", "1 Peter 4:8", "1 Peter 4:10",
  "1 Peter 5:6-7", "1 Peter 5:10", "2 Peter 1:3", "2 Peter 3:9", "2 Peter 3:18",
  "1 John 1:7", "1 John 1:9", "1 John 3:1", "1 John 3:16", "1 John 3:18", "1 John 4:4", "1 John 4:7-8", "1 John 4:10",
  "1 John 4:18-19", "1 John 5:4", "1 John 5:11-12", "1 John 5:14", "2 John 1:6", "3 John 1:2", "Jude 1:24-25",
  "Revelation 1:8", "Revelation 3:20", "Revelation 21:3-4", "Revelation 21:5", "Revelation 22:12", "Revelation 22:17",
];

// "John 3:16", "Psalms 23:1-3", "1 John 4:8" -> { first: "JHN.3.16", last: "JHN.3.16" }. Null if it cannot be read.
function parseReference(reference) {
  const m = String(reference || "").trim().match(/^(.+?)\s+(\d+):(\d+)(?:\s*[-\u2013\u2014]\s*(?:(\d+):)?(\d+))?\s*$/);
  if (!m) return null;
  const code = BOOK_BY_NAME[m[1].toLowerCase().replace(/\./g, "").replace(/\s+/g, " ").trim()];
  if (!code) return null;
  const first = `${code}.${Number(m[2])}.${Number(m[3])}`;
  if (!m[5]) return { first, last: first };
  const lastChapter = m[4] ? Number(m[4]) : Number(m[2]);
  return { first, last: `${code}.${lastChapter}.${Number(m[5])}` };
}

function apiBibleSettings() {
  const clean = (value) => {
    const text = String(value || "").trim();
    return text && !text.toLowerCase().startsWith("paste-") ? text : "";   // the placeholder from .env.example is not a value
  };
  return {
    key: clean(process.env.APIBIBLE_KEY),
    ids: { en: clean(process.env.APIBIBLE_ID_EN), nb: clean(process.env.APIBIBLE_ID_NB), om: clean(process.env.APIBIBLE_ID_OM) },
    pickFrom: String(process.env.APIBIBLE_PICK_FROM || "en,om").split(",").map((code) => code.trim().toLowerCase()).filter(Boolean),
  };
}

async function apiBibleGet(key, path) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const res = await fetch(APIBIBLE_BASE + path, { headers: { "api-key": key }, signal: controller.signal });
    if (!res.ok) throw new Error(`API.Bible answered ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

// Only the words of the verse: no notes, titles or verse numbers. The words themselves are never changed.
function cleanScripture(raw, reference) {
  let text = String(raw || "").replace(/<[^>]+>/g, " ").replace(/[\u00b6\u2029]/g, " ").replace(/\s+/g, " ").trim();
  // Some Bibles print the chapter label (for example "Faarfannaa 23") before the first verse of a chapter. It is not part of the verse.
  const label = String(reference || "").replace(/:\d+.*$/, "").trim();
  if (label && text.startsWith(label + " ")) text = text.slice(label.length).trim();
  return text;
}

const NT_CODES = BOOK_LIST.slice(BOOK_LIST.findIndex((book) => book[0] === "MAT")).map((book) => book[0]);

// For each language that has a Bible ID: which books that Bible has, and its version and copyright (shown under the verse).
// If a Bible's list of books cannot be read tonight, the list saved on an earlier night is used; if there is none, only the
// New Testament is assumed (every Bible has it), so a verse that a Bible lacks is never chosen by mistake.
async function apiBibleDetails() {
  const { key, ids, pickFrom } = apiBibleSettings();
  const details = {};
  if (!key) return { key, details, pickFrom: [] };
  let saved = {};
  try {
    const snap = await db.doc("settings/verseBooks").get();
    saved = snap.exists ? snap.data() : {};
  } catch (err) {
    logger.warn("Could not read the saved book lists", err.message);
  }
  const fresh = {};
  await Promise.all(
    Object.entries(ids).filter(([, bibleId]) => bibleId).map(async ([language, bibleId]) => {
      const id = encodeURIComponent(bibleId);
      const entry = { bibleId, books: null, credit: "", version: "", copyright: "" };
      try {
        const books = (await apiBibleGet(key, `/bibles/${id}/books`)).data || [];
        if (books.length === 0) throw new Error("an empty list of books");
        entry.books = new Set(books.map((book) => book.id));
        fresh[language] = [...entry.books];
      } catch (err) {
        const earlier = Array.isArray(saved[language]) && saved[language].length > 0 ? saved[language] : null;
        logger.warn(`API.Bible: could not list the books of the ${language} Bible; using ${earlier ? "the saved list" : "the New Testament only"}`, err.message);
        entry.books = new Set(earlier || NT_CODES);
      }
      try {
        const info = (await apiBibleGet(key, `/bibles/${id}`)).data || {};
        const version = info.abbreviationLocal || info.abbreviation || info.nameLocal || info.name || "";
        const copyright = String(info.copyright || "").replace(/\s+/g, " ").trim();
        entry.version = String(version).slice(0, 200);
        entry.copyright = copyright.slice(0, 1200);
        entry.credit = [version, copyright].filter(Boolean).join(" \u00b7 ").slice(0, 220);
      } catch (err) {
        logger.warn(`API.Bible: no version details for the ${language} Bible`, err.message);
      }
      details[language] = entry;
    })
  );
  if (Object.keys(fresh).length > 0) {
    try {
      await db.doc("settings/verseBooks").set({ ...fresh, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    } catch (err) {
      logger.warn("Could not save the book lists", err.message);
    }
  }
  // The deciding languages that really have a Bible (if none of them has, all the Bibles decide).
  const deciding = pickFrom.filter((language) => details[language]);
  // Norsk from the YouVersion Platform (Bibel 1930, the whole Bible), if it is set up.
  const yv = yvLib.settings();
  if (yv.key && yv.bibleId) {
    try {
      const meta = await yvBooksPayload(yv);
      details.nb = {
        provider: "yv",
        bibleId: yv.bibleId,
        books: new Set(meta.books.map((book) => book.id)),
        names: Object.fromEntries(meta.books.map((book) => [book.id, book.name])),
        version: meta.version,
        copyright: meta.copyright,
        credit: [meta.version, meta.copyright].filter(Boolean).join(" \u00b7 ").slice(0, 220),
      };
    } catch (err) {
      logger.warn("YouVersion: the Norwegian Bible is not available for the verse of the day", err.message);
    }
  }
  // When the Norwegian Bible comes from YouVersion (the whole Bible), a verse is only chosen if Norsk has it too, so Norsk never falls back to English.
  const wanted = details.nb && details.nb.provider === "yv" ? [...new Set([...pickFrom, "nb"])] : pickFrom;
  const decidingNow = wanted.filter((language) => details[language]);
  return { key, details, pickFrom: decidingNow.length > 0 ? decidingNow : Object.keys(details) };
}

// The books that every deciding Bible has (null if we could not learn any of their books: then nothing is filtered out).
function commonBooksOf(details, pickFrom) {
  const sets = (pickFrom || Object.keys(details)).map((language) => details[language] && details[language].books).filter(Boolean);
  if (sets.length === 0) return null;
  return new Set([...sets[0]].filter((code) => sets.every((set) => set.has(code))));
}

// The verses to choose from: the list, without the books some Bible lacks. If that leaves much fewer than a year of verses
// (for example when a Bible has only the New Testament), more New Testament verses are added after them.
function buildVersePool(common) {
  const allowed = (reference) => {
    if (!common) return true;
    const range = parseReference(reference);
    return !!range && common.has(range.first.split(".")[0]);
  };
  const base = VERSES.filter(allowed);
  if (!common || base.length >= VERSES.length) return VERSES;
  const seen = new Set(base);
  const pool = base.concat(EXTRA_NT_VERSES.filter((reference) => allowed(reference) && !seen.has(reference)));
  return pool.length > 0 ? pool : VERSES;
}

// The same passage in each language that has a Bible ID. Returns { en: {...}, nb: {...}, om: {...} } with what was found.
async function fetchFromApiBible(key, details, range) {
  const found = {};
  if (!key) return found;
  const passageId = range.first === range.last ? range.first : `${range.first}-${range.last}`;
  const query = "content-type=text&include-notes=false&include-titles=false&include-chapter-numbers=false&include-verse-numbers=false";
  await Promise.all(
    Object.entries(details).map(async ([language, entry]) => {
      try {
        if (entry.provider === "yv") {
          const a = range.first.split(".");
          const b = range.last.split(".");
          if (a[0] !== b[0] || a[1] !== b[1]) throw new Error("a range across chapters is not read for Norsk");
          const id = range.first === range.last ? range.first : `${range.first}-${b[2]}`;
          const yvKey = yvLib.settings().key;
          const json = await yvLib.get(yvKey, `/bibles/${encodeURIComponent(entry.bibleId)}/passages/${id}?format=text`);
          const text = yvLib.tidyVerse(a[0], Number(a[2]), bibleLib.clean(json.content));
          if (!text) throw new Error("no text came back");
          const reference = `${entry.names[a[0]] || a[0]} ${a[1]}:${a[2]}` + (range.first === range.last ? "" : `-${b[2]}`);
          found[language] = { reference, text: text.slice(0, 700), credit: entry.credit };
          return;
        }
        const passage = await apiBibleGet(key, `/bibles/${encodeURIComponent(entry.bibleId)}/passages/${encodeURIComponent(passageId)}?${query}`);
        const text = cleanScripture(passage.data && passage.data.content, passage.data && passage.data.reference);
        if (!text) throw new Error("no text came back");
        found[language] = { reference: String((passage.data && passage.data.reference) || ""), text: text.slice(0, 700), credit: entry.credit };
      } catch (err) {
        logger.warn(`API.Bible: no ${language} verse for ${passageId}`, err.message);
      }
    })
  );
  return found;
}

// Writes today's verse. It follows the church's own list: the same day number the app uses.
//  - If the leaders typed text for today (in the verse list), their English reference is used as it is (their choice).
//  - Otherwise the verse is chosen from the verses that the deciding Bibles have (APIBIBLE_PICK_FROM), so English, Norsk and
//    Afaan Oromoo readers get the SAME verse. The text in each language comes from API.Bible (English also from
//    bible-api.com if needed). A language whose Bible lacks the verse shows the English text of it instead.
//  - If a verse is missing in one Bible (a different numbering, for example), the next verse of the pool is tried.
// A leader's own text for a day is shown by the app instead of the Bible's text for that language.
// The verse changes at 09:00 (Oslo time), not at midnight: before 09:00 the verse of the day before is still the verse of
// the day. The app uses the same rule, so the Bible's text and the leaders' own text change at the same moment.
const VERSE_HOUR = 9;
function verseDate(now = new Date()) {
  const date = now.toLocaleDateString("en-CA", { timeZone: TIME_ZONE });
  const hour = Number(new Intl.DateTimeFormat("en-GB", { timeZone: TIME_ZONE, hour: "numeric", hourCycle: "h23" }).format(now));
  if (hour >= VERSE_HOUR) return date;
  return new Date(Date.parse(date + "T00:00:00Z") - 86400000).toISOString().slice(0, 10);
}

async function writeVerseOfTheDay() {
  const localDate = verseDate();
  const epochDay = Math.floor(Date.parse(localDate + "T00:00:00Z") / 86400000);
  const { key, details, pickFrom } = await apiBibleDetails();
  const common = commonBooksOf(details, pickFrom);
  const pool = buildVersePool(common);
  // The leaders' own list of verses was taken out of the app (the verse is automatic), so it is not used any more.
  const count = 0;
  const candidates = [];
  if (count > 0) {
    const n = (epochDay % count) + 1;
    const entry = await db.doc(`verseBank/${n}`).get();
    const data = entry.exists ? entry.data() : {};
    if (data.refEn) {
      const typed = !!(data.textOm || data.textNb || data.textEn);
      const range = parseReference(String(data.refEn));
      const inCommon = !common || (range && common.has(range.first.split(".")[0]));
      if (typed || inCommon) candidates.push({ reference: String(data.refEn), forced: typed });
    }
  }
  for (let i = 0; i < 5; i++) candidates.push({ reference: pool[(epochDay + i) % pool.length], forced: false });
  const wanted = pickFrom;        // a verse is "complete" when every deciding language has it
  let best = null;
  let bestComplete = false;
  let lastError = null;
  for (const { reference, forced } of candidates) {
    const range = parseReference(reference);
    const fromApi = range ? await fetchFromApiBible(key, details, range) : {};
    let en = fromApi.en || null;
    if (!en) {
      try {
        const verse = await fetchVerse(reference);
        en = { reference: verse.reference, text: verse.text, credit: `${verse.translation} (public domain)` };
      } catch (err) {
        lastError = err;
      }
    }
    const result = { reference, usfm: range ? range.first : "", en, nb: fromApi.nb || null, om: fromApi.om || null };
    const hasSomething = !!(en || result.nb || result.om);
    const complete = !!en && wanted.every((language) => result[language]);
    if (complete || (forced && hasSomething)) {
      best = result;
      bestComplete = true;
      break;
    }
    if (!best && hasSomething) best = result;
  }
  if (!best) throw lastError || new Error("No verse could be fetched");
  if (!bestComplete && wanted.length > 0) {
    // No verse worked in every language tonight (an outage, for example). Keep yesterday's verse if it had every language,
    // so nobody gets another language by surprise. After two days of that the new verse is written anyway.
    try {
      const earlier = await db.doc("settings/verseOfDay").get();
      const old = earlier.exists ? earlier.data() : null;
      const fields = { en: "text", nb: "textNb", om: "textOm" };
      const age = old && old.date ? epochDay - Math.floor(Date.parse(old.date + "T00:00:00Z") / 86400000) : 99;
      if (old && age <= 2 && wanted.every((language) => old[fields[language]])) {
        logger.warn("No verse worked in every language today; keeping the earlier verse", { reference: best.reference });
        return;
      }
    } catch (err) {
      logger.warn("Could not read the earlier verse", err.message);
    }
  }
  const data = { date: localDate, updatedAt: FieldValue.serverTimestamp(), usfm: best.usfm || "" };
  if (best.en) {
    data.reference = best.en.reference || best.reference;
    data.text = best.en.text;
    data.creditEn = best.en.credit || "";
  }
  if (best.nb) {
    data.referenceNb = best.nb.reference || best.reference;
    data.textNb = best.nb.text;
    data.creditNb = best.nb.credit || "";
  }
  if (best.om) {
    data.referenceOm = best.om.reference || best.reference;
    data.textOm = best.om.text;
    data.creditOm = best.om.credit || "";
  }
  // The version and the full copyright of each Bible, every day (the app shows them on the page "Bible versions and copyright").
  const suffix = { en: "En", nb: "Nb", om: "Om" };
  for (const [language, entry] of Object.entries(details)) {
    data[`version${suffix[language]}`] = entry.version || "";
    data[`copyright${suffix[language]}`] = entry.copyright || "";
  }
  if (!details.en && best.en) {
    data.versionEn = "World English Bible";
    data.copyrightEn = "Public domain";
  }
  logger.info("Verse of the day", {
    reference: best.reference,
    en: !!best.en,
    nb: !!best.nb,
    om: !!best.om,
    commonBooks: common ? common.size : "all",
    decidedBy: pickFrom.join(","),
    pool: pool.length,
  });
  // Written as a whole, so a language that failed today does not keep yesterday's verse.
  await db.doc("settings/verseOfDay").set(data);
}

exports.verseOfTheDay = onSchedule(
  { schedule: "0 9 * * *", timeZone: TIME_ZONE, region: REGION },
  async () => {
    try {
      await writeVerseOfTheDay();
    } catch (err) {
      // Keep yesterday's verse if today's cannot be fetched.
      logger.error("Could not write the verse of the day", err);
    }
  }
);

// ---------- The verse of the day as a notification, 9:02 Oslo time (only for people who switched it on) ----------
const VERSE_TITLES = { en: "Verse of the day", nb: "Dagens vers", om: "Caqasa guyyaa" };

exports.verseReminder = onSchedule(
  { schedule: "2 9 * * *", timeZone: TIME_ZONE, region: REGION },
  async () => {
    const snap = await db.doc("settings/verseOfDay").get();
    if (!snap.exists) return;
    const verse = snap.data();
    const today = new Date().toLocaleDateString("sv-SE", { timeZone: TIME_ZONE });
    if (verse.date !== today) {
      logger.warn("Verse reminder: today's verse is not ready, nothing sent", { verseDate: verse.date, today });
      return;
    }
    const lines = {
      en: verse.text ? `${verse.text} \u2014 ${verse.reference || ""}` : "",
      nb: verse.textNb ? `${verse.textNb} \u2014 ${verse.referenceNb || ""}` : "",
      om: verse.textOm ? `${verse.textOm} \u2014 ${verse.referenceOm || ""}` : "",
    };
    // To test without telling everyone: put the user ids of the testers in VERSE_REMINDER_ONLY (separated by commas) in .env.
    const onlyThese = String(process.env.VERSE_REMINDER_ONLY || "").split(",").map((id) => id.trim()).filter(Boolean);
    const people = await db.collection("fcmTokens").get();
    const byLanguage = { en: [], nb: [], om: [] };
    for (const doc of people.docs) {
      const data = doc.data();
      const prefs = data.prefs || {};
      if (prefs.verse === false) continue;         // on from the start; only people who switched it off do not get it
      if (onlyThese.length > 0 && !onlyThese.includes(doc.id)) continue;   // test mode (VERSE_REMINDER_ONLY in .env)
      if (!wants(data, null, false)) continue;     // quiet hours
      const wished = prefs.lang === "nb" || prefs.lang === "om" ? prefs.lang : "en";
      const language = lines[wished] ? wished : "en";
      if (Array.isArray(data.tokens)) data.tokens.forEach((token) => byLanguage[language].push({ uid: doc.id, token }));
    }
    for (const language of ["en", "nb", "om"]) {
      const entries = byLanguage[language];
      const body = lines[language];
      if (entries.length === 0 || !body) continue;
      await sendToTokens(entries, {
        notification: { title: VERSE_TITLES[language], body: shorten(body, 240) },
        data: { target: `verse:${verse.usfm || ""}` },
        // High priority, and public: the verse can be read on the lock screen.
        android: { priority: "high", notification: { channelId: "verse_of_day", visibility: "public" } },
      });
    }
    logger.info("Verse reminder sent", { en: byLanguage.en.length, nb: byLanguage.nb.length, om: byLanguage.om.length });
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

// ---------- The Bible reader: the books and the chapters, from API.Bible, kept in Firestore ----------
// The app calls these through the website (firebase.json sends /api/bibleBooks and /api/bibleChapter here). The API.Bible key
// stays on the server. Each chapter is fetched once and kept in the collection "bibleCache" (only the server can read or write
// it), so the monthly limit of calls is not used up by many readers. Set APIBIBLE_CACHE=off in .env to switch the keeping off.
const BIBLE_LANGS = ["en", "nb", "om"];
const infoMemory = new Map();

// Who is calling: a signed-in person with a verified email, or an approved member. Returns the uid, or null (the answer is sent).
async function readerMember(req, res) {
  const header = String(req.get("Authorization") || "");
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) {
    res.status(401).json({ error: "Sign in first" });
    return null;
  }
  try {
    const decoded = await getAuth().verifyIdToken(token);
    if (decoded.email_verified) return decoded.uid;
    if ((await db.doc(`approvedMembers/${decoded.uid}`).get()).exists || (await isAdminUid(decoded.uid))) return decoded.uid;
    res.status(403).json({ error: "Verify your email first" });
  } catch (err) {
    res.status(401).json({ error: "Sign in again" });
  }
  return null;
}

function readerCacheOn() {
  return String(process.env.APIBIBLE_CACHE || "on").trim().toLowerCase() !== "off";
}

// The version name and copyright of the Bible for this language.
async function readerInfo(key, bibleId, language) {
  if (infoMemory.has(language)) return infoMemory.get(language);
  let info = { version: "", copyright: "" };
  try {
    const data = (await apiBibleGet(key, `/bibles/${encodeURIComponent(bibleId)}`)).data || {};
    info = {
      version: String(data.abbreviationLocal || data.abbreviation || data.nameLocal || data.name || "").slice(0, 200),
      copyright: String(data.copyright || "").replace(/\s+/g, " ").trim().slice(0, 1200),
    };
    infoMemory.set(language, info);
  } catch (err) {
    logger.warn(`Bible reader: no version details for ${language}`, err.message);
  }
  return info;
}

// Norsk can come from the YouVersion Platform (Bibel 1930, the whole Bible) when YVP_APP_KEY and YVP_BIBLE_ID_NB are in .env.
// That text is fetched live every time and is not stored in Firestore.
const yvMemory = { at: 0, payload: null };

async function yvBooksPayload(yv) {
  if (yvMemory.payload && Date.now() - yvMemory.at < 3600000) return yvMemory.payload;
  const list = await yvLib.books(yv.key, yv.bibleId);
  let chapters = {};
  if (!list.some((book) => yvLib.chaptersOf(book) > 0)) {
    try {
      chapters = yvLib.chaptersFromIndex(await yvLib.get(yv.key, `/bibles/${encodeURIComponent(yv.bibleId)}/index`));
    } catch (err) {
      logger.warn("YouVersion: no index for the chapter counts", err.message);
    }
  }
  const books = list
    .map((book) => ({ id: yvLib.codeOf(book), name: yvLib.nameOf(book), chapters: yvLib.chaptersOf(book) || chapters[yvLib.codeOf(book)] || 0 }))
    .filter((book) => /^[A-Z0-9]{3}$/.test(book.id) && book.chapters > 0);
  if (books.length === 0) throw new Error("YouVersion gave no books");
  const meta = await yvLib.info(yv.key, yv.bibleId);
  const payload = { lang: "nb", books, version: meta.version, copyright: meta.copyright };
  yvMemory.payload = payload;
  yvMemory.at = Date.now();
  return payload;
}

async function yvChapterPayload(yv, book, chapter) {
  const json = await yvLib.get(yv.key, `/bibles/${encodeURIComponent(yv.bibleId)}/passages/${book}.${chapter}?format=html`);
  const verses = yvLib.versesFromHtml(json.content).map((verse) => ({ n: verse.n, t: yvLib.tidyVerse(book, verse.n, verse.t) }));
  if (verses.length === 0) throw new Error("no verses came back");
  const meta = await yvBooksPayload(yv).catch(() => null);
  const found = meta ? meta.books.find((item) => item.id === book) : null;
  return {
    lang: "nb",
    book,
    chapter,
    reference: `${(found && found.name) || book} ${chapter}`,
    verses: verses.slice(0, 200).map((verse) => ({ n: verse.n, t: verse.t.slice(0, 1500) })),
    version: meta ? meta.version : "",
    copyright: meta ? meta.copyright : "",
    cache: false,   // the app must not keep this text on the phone
  };
}

// Word search in the reader (members only). API.Bible searches the Bible of the language. For Norsk it searches the Norwegian Bible
// that API.Bible has (the New Testament), because the Norwegian Bible from YouVersion has no search; the app says so.
const searchMemory = new Map();

exports.bibleSearch = onRequest({ region: REGION, timeoutSeconds: 30 }, async (req, res) => {
  const uid = await readerMember(req, res);
  if (!uid) return;
  const language = String(req.query.lang || "");
  const query = String(req.query.q || "").replace(/\s+/g, " ").trim().slice(0, 80);
  const offset = Math.max(0, Math.min(1000, parseInt(String(req.query.offset || "0"), 10) || 0));
  const { key, ids } = apiBibleSettings();
  if (!BIBLE_LANGS.includes(language) || !key || !ids[language]) {
    res.status(404).json({ error: "There is no Bible for this language" });
    return;
  }
  if (query.length < 2) {
    res.status(400).json({ error: "Type at least two letters" });
    return;
  }
  const memoryKey = `${language}|${offset}|${query.toLowerCase()}`;
  const kept = searchMemory.get(memoryKey);
  if (kept && Date.now() - kept.at < 600000) {
    res.set("Cache-Control", "private, max-age=600");
    res.json(kept.payload);
    return;
  }
  try {
    const data = await apiBibleGet(key, `/bibles/${encodeURIComponent(ids[language])}/search?query=${encodeURIComponent(query)}&limit=20&offset=${offset}&sort=relevance`);
    const found = (data && data.data) || {};
    const results = [];
    for (const verse of found.verses || []) {
      const parts = String(verse.id || "").split(".");
      const chapter = parseInt(parts[1], 10);
      const number = parseInt(parts[2], 10);
      if (!/^[A-Z0-9]{3}$/.test(parts[0] || "") || !(chapter >= 1) || !(number >= 1)) continue;
      results.push({
        book: parts[0],
        chapter,
        verse: number,
        reference: String(verse.reference || "").slice(0, 80),
        text: String(verse.text || "").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim().slice(0, 400),
      });
    }
    const payload = { lang: language, q: query, offset, total: Number(found.total || found.verseCount || results.length) || 0, results, partial: language === "nb" };
    searchMemory.set(memoryKey, { at: Date.now(), payload });
    if (searchMemory.size > 200) searchMemory.delete(searchMemory.keys().next().value);
    res.set("Cache-Control", "private, max-age=600");
    res.json(payload);
  } catch (err) {
    logger.error(`Bible search (${language}): ${err.message}`);
    res.status(502).json({ error: "The search did not work now" });
  }
});

exports.bibleBooks = onRequest({ region: REGION, timeoutSeconds: 30 }, async (req, res) => {
  const uid = await readerMember(req, res);
  if (!uid) return;
  const language = String(req.query.lang || "");
  const yv = yvLib.settings();
  if (language === "nb" && yv.key && yv.bibleId) {
    try {
      const payload = await yvBooksPayload(yv);
      res.set("Cache-Control", "private, max-age=86400");
      res.json(payload);
    } catch (err) {
      logger.error("Bible reader (YouVersion): books", err.message);
      res.status(502).json({ error: "The Bible could not be loaded now" });
    }
    return;
  }
  const { key, ids } = apiBibleSettings();
  if (!BIBLE_LANGS.includes(language) || !key || !ids[language]) {
    res.status(404).json({ error: "There is no Bible for this language" });
    return;
  }
  try {
    const cacheRef = db.doc(`bibleCache/books-${language}`);
    if (readerCacheOn()) {
      const hit = await cacheRef.get();
      if (hit.exists && hit.data().payload) {
        res.set("Cache-Control", "private, max-age=86400");
        res.json(hit.data().payload);
        return;
      }
    }
    const list = (await apiBibleGet(key, `/bibles/${encodeURIComponent(ids[language])}/books?include-chapters=true`)).data || [];
    const books = list
      .map((book) => ({
        id: String(book.id),
        name: String(book.name || book.nameLong || book.id),
        chapters: (book.chapters || []).filter((chapter) => /^\d+$/.test(String(chapter.number))).length,
      }))
      .filter((book) => book.chapters > 0);
    const info = await readerInfo(key, ids[language], language);
    const payload = { lang: language, books, version: info.version, copyright: info.copyright };
    if (readerCacheOn() && books.length > 0) await cacheRef.set({ payload, updatedAt: FieldValue.serverTimestamp() });
    res.set("Cache-Control", "private, max-age=86400");
    res.json(payload);
  } catch (err) {
    logger.error("Bible reader: books", err.message);
    res.status(502).json({ error: "The Bible could not be loaded now" });
  }
});

exports.bibleChapter = onRequest({ region: REGION, timeoutSeconds: 30 }, async (req, res) => {
  const uid = await readerMember(req, res);
  if (!uid) return;
  const language = String(req.query.lang || "");
  const book = String(req.query.book || "").toUpperCase();
  const chapter = parseInt(String(req.query.chapter || ""), 10);
  const yv = yvLib.settings();
  if (language === "nb" && yv.key && yv.bibleId) {
    if (!/^[A-Z0-9]{3}$/.test(book) || !(chapter >= 1 && chapter <= 150)) {
      res.status(400).json({ error: "Unknown chapter" });
      return;
    }
    try {
      const payload = await yvChapterPayload(yv, book, chapter);
      res.set("Cache-Control", "private, no-store");
      res.json(payload);
    } catch (err) {
      logger.error(`Bible reader (YouVersion): ${book} ${chapter}`, err.message);
      res.status(502).json({ error: "This chapter could not be loaded now" });
    }
    return;
  }
  const { key, ids } = apiBibleSettings();
  if (!BIBLE_LANGS.includes(language) || !key || !ids[language]) {
    res.status(404).json({ error: "There is no Bible for this language" });
    return;
  }
  if (!/^[A-Z0-9]{3}$/.test(book) || !(chapter >= 1 && chapter <= 150)) {
    res.status(400).json({ error: "Unknown chapter" });
    return;
  }
  try {
    const cacheRef = db.doc(`bibleCache/ch-${language}-${book}-${chapter}`);
    if (readerCacheOn()) {
      const hit = await cacheRef.get();
      if (hit.exists && hit.data().payload) {
        res.set("Cache-Control", "private, max-age=86400");
        res.json(hit.data().payload);
        return;
      }
    }
    const base = `/bibles/${encodeURIComponent(ids[language])}/chapters/${book}.${chapter}`;
    const flags = "include-notes=false&include-titles=false&include-chapter-numbers=false&include-verse-numbers=false&include-verse-spans=false";
    let data = (await apiBibleGet(key, `${base}?content-type=json&${flags}`)).data || {};
    let verses = bibleLib.versesFromChapter(data);
    if (verses.length === 0) {
      // The tree could not be read: ask for plain text with the verse numbers instead.
      const plain = (await apiBibleGet(key, `${base}?content-type=text&include-notes=false&include-titles=false&include-chapter-numbers=false&include-verse-numbers=true`)).data || {};
      verses = bibleLib.versesFromChapter(plain);
      data = { ...data, reference: data.reference || plain.reference };
    }
    if (verses.length === 0) throw new Error("no verses came back");
    const info = await readerInfo(key, ids[language], language);
    const payload = {
      lang: language,
      book,
      chapter,
      reference: String(data.reference || `${book} ${chapter}`),
      verses: verses.slice(0, 200).map((verse) => ({ n: verse.n, t: verse.t.slice(0, 1500) })),
      version: info.version,
      copyright: info.copyright,
    };
    if (readerCacheOn()) await cacheRef.set({ payload, updatedAt: FieldValue.serverTimestamp() });
    res.set("Cache-Control", "private, max-age=86400");
    res.json(payload);
  } catch (err) {
    logger.error(`Bible reader: ${language} ${book} ${chapter}`, err.message);
    res.status(502).json({ error: "This chapter could not be loaded now" });
  }
});

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
      const cant = new Set(Array.isArray(slot.cantMake) ? slot.cantMake : []);
      for (const uid of volunteers) {
        if (cant.has(uid)) continue;
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

// ---------- Serving schedules: only the people a leader chose are told ----------
/** "Sunday 2 November, 10:30" in the church's time zone. */
function whenText(date) {
  const day = date.toLocaleDateString("en-GB", { timeZone: TIME_ZONE, weekday: "long", day: "numeric", month: "long" });
  const time = date.toLocaleTimeString("en-GB", { timeZone: TIME_ZONE, hour: "2-digit", minute: "2-digit" });
  return `${day}, ${time}`;
}

// A leader asks (a document in servingNotices) that some people be told: they were added to a duty, a duty changed, or they were removed.
// Nobody else gets anything. The document is removed when it has been sent.
exports.notifyServingNotice = onDocumentCreated(
  { document: "servingNotices/{noticeId}", region: REGION },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    try {
      if (!REMIND_SERVING) return;
      const n = snap.data();
      const uids = Array.isArray(n.uids) ? n.uids.filter((u) => typeof u === "string").slice(0, 50) : [];
      const start = n.start && typeof n.start.toDate === "function" ? n.start.toDate() : null;
      if (uids.length === 0 || !start) return;
      const title = shorten(n.title, 60);
      const count = Number.isInteger(n.count) ? n.count : 1;
      let heading;
      let body;
      if (n.kind === "assigned") {
        heading = "You are on the schedule";
        body = count > 1 ? `${title}: ${count} dates, from ${whenText(start)}` : `${title}: ${whenText(start)}`;
      } else if (n.kind === "changed") {
        heading = "Your serving changed";
        body = `${title}: ${whenText(start)}`;
      } else if (n.kind === "removed") {
        heading = "You are no longer on the schedule";
        body = `${title}: ${whenText(start)}`;
      } else {
        return;
      }
      const entries = await tokensFor(uids);
      if (entries.length === 0) return;
      await sendToTokens(entries, {
        notification: { title: heading, body },
        android: { notification: { channelId: "announcements" } },
      });
    } finally {
      await snap.ref.delete().catch(() => {});
    }
  }
);

// The evening before: everyone who serves tomorrow gets one reminder (not those who said they cannot come).
// It runs at 18:00, so "6 to 30 hours from now" is exactly tomorrow.
exports.servingTomorrow = onSchedule(
  { schedule: "every day 18:00", timeZone: TIME_ZONE, region: REGION },
  async () => {
    if (!REMIND_SERVING) return;
    const from = new Date(Date.now() + 6 * 3600 * 1000);
    const to = new Date(Date.now() + 30 * 3600 * 1000);
    const snap = await db.collection("servingSlots").where("start", ">=", from).where("start", "<", to).get();
    const perPerson = new Map();
    for (const doc of snap.docs) {
      const slot = doc.data();
      if (!slot.start || typeof slot.start.toDate !== "function") continue;
      const cant = new Set(Array.isArray(slot.cantMake) ? slot.cantMake : []);
      for (const uid of Array.isArray(slot.volunteers) ? slot.volunteers : []) {
        if (cant.has(uid)) continue;
        if (!perPerson.has(uid)) perPerson.set(uid, []);
        perPerson.get(uid).push({ start: slot.start.toDate(), title: slot.title });
      }
    }
    for (const [uid, slots] of perPerson) {
      const entries = await tokensFor([uid]);
      if (entries.length === 0) continue;
      slots.sort((a, b) => a.start - b.start);
      const lines = slots.slice(0, 4).map((s) => {
        const time = s.start.toLocaleTimeString("en-GB", { timeZone: TIME_ZONE, hour: "2-digit", minute: "2-digit" });
        return `${time}: ${shorten(s.title, 40)}`;
      });
      await sendToTokens(entries, {
        notification: { title: "Tomorrow you serve", body: lines.join("\n") },
        android: { notification: { channelId: "announcements" } },
      });
    }
  }
);

// Someone on a schedule says "I can't make it": the leaders are told, so they can find someone else.
exports.notifyCantMake = onDocumentUpdated(
  { document: "servingSlots/{slotId}", region: REGION },
  async (event) => {
    if (!REMIND_SERVING) return;
    const before = event.data.before.data() || {};
    const after = event.data.after.data() || {};
    const was = new Set(Array.isArray(before.cantMake) ? before.cantMake : []);
    const added = (Array.isArray(after.cantMake) ? after.cantMake : []).filter((u) => !was.has(u));
    if (added.length === 0) return;
    const names = added.map((u) => (after.volunteerNames && after.volunteerNames[u]) || "Someone");
    const start = after.start && typeof after.start.toDate === "function" ? after.start.toDate() : null;
    // What they wrote, and a date that suits them better (if they gave one).
    const extra = [];
    for (const u of added) {
      const info = after.cantInfo && after.cantInfo[u];
      if (!info) continue;
      if (info.note) extra.push(`"${shorten(info.note, 100)}"`);
      if (info.suggest && typeof info.suggest.toDate === "function") {
        extra.push(`Suggests ${info.suggest.toDate().toLocaleDateString("en-GB", { timeZone: TIME_ZONE, weekday: "long", day: "numeric", month: "long" })}`);
      }
    }
    const entries = (await tokensFor(await adminIds())).filter((e) => !added.includes(e.uid));
    if (entries.length === 0) return;
    await sendToTokens(entries, {
      notification: {
        title: `${shorten(names.join(", "), 50)} can't make it`,
        body: [`${shorten(after.title, 50)}${start ? ": " + whenText(start) : ""}`, ...extra].join("\n"),
      },
      android: { notification: { channelId: "announcements" } },
    });
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
