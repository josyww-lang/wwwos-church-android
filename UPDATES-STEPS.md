# New in this update – steps

The **Study** tab is unchanged. Events and Prayer requests live inside the **Updates** tab (three buttons under the title: **Announcements | Events | Prayer**). Sermons is still "coming soon".

## What's new

### 1. Chat notifications
See `NOTIFICATION-SERVER-STEPS.md`. This needs the paid Firebase plan and a one-time setup. The app side is already included: it saves each phone's notification address, opens the right conversation when you tap a notification, and doesn't notify you about a chat you have open.

### 2. Reply to a message
Long-press a message > **Reply**. A bar above the message box shows what you are answering (tap the x to cancel). The reply shows a small quote of the original above your text.

### 3. Emoji reactions
Long-press a message and tap one of the emojis at the top of the menu. Reactions appear under the message with a count. Tap one again to take yours back.

### 4. Pinned message
Group owners and admins: long-press a message > **Pin**. It stays at the top of the group. Tap the pinned bar to read it in full. Long-press the pinned message > **Unpin**, or unpin from the bar.

### 5. Prayer requests (Updates > Prayer)
- Tap **New prayer request**. Choose whether to **post without your name**, and who sees it: **Everyone in the church** or **Leaders only**.
- Others tap **I prayed**; the request shows how many people prayed.
- The person who wrote a request, or an admin, can mark it **Answered** or delete it, from the three-dot menu.
- Leaders-only requests are seen only by admins and the person who wrote them.
- Prayer requests are for **approved members** (the same approval as chat).

### 6. Events and "will you come?" (Updates > Events)
- Admins tap **New event** to add a title, date and time, place and description.
- Everyone sees upcoming events, and answers **Going / Maybe / Can't**. Tap an event to see who is coming.
- **Add to calendar** puts the event in the phone's calendar app.

### 7. Read and unread ticks (already built; now clearer)
On your own messages: a clock means sending, **one tick** means sent and not yet read, **two green ticks** mean read. In a group, long-press your message > **Seen by** to see who.

## Apply it
1. **Publish the new database rules.** Firebase console > **Firestore Database > Rules**, paste `firestore.rules` from this zip, **Publish**.
2. Unzip `ChurchApp-all-sources.zip`, copy the `app` folder into your project folder, choose **Replace**, and press **Run**. (Your church name, package name and `google-services.json` are left alone.)
3. For chat notifications, follow `NOTIFICATION-SERVER-STEPS.md` using `ChurchApp-notification-server.zip`.

## Test it
1. **Menu:** the bottom menu still shows Updates, Sermons, Chat, Study, Give. Open Updates and check the three buttons.
2. **Reply, reactions, pin:** in a group with two accounts, long-press a message and try each option.
3. **Prayer:** post a request, then use another account to tap **I prayed**. Try **post without my name** and **Leaders only**.
4. **Events:** as admin, add an event. As another account, tap **Going** and check the count changes.
5. **Ticks:** send a message from one account, open the conversation on the other, and look for the two green ticks.

## Good to know
- **"Without my name" hides your name in the app.** Admins can still tell who wrote a request, and the database stores who wrote it.
- **Everything from the earlier updates stays.** Nothing was removed.
- Wording in Norsk and Afaan Oromoo is my draft. Please have it checked.
