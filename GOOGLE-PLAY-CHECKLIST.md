# Google Play checklist

Do the steps in order. Items marked **YOU** need your own details.

## 1. Developer account
- Register at **play.google.com/console** (one-time fee of about US$25). Choose **Organisation** if the church has an organisation number (it needs a D-U-N-S number, which is free); otherwise **Personal**.
- Google has required **new personal accounts** to run a **closed test with at least 12 testers for 14 days** before they can publish. Check the current rule in Play Console when you register, and start the closed test early.

## 2. Target the new Android level (required)
Since **31 August 2026**, Google Play requires new apps and updates to **target Android 16 (API level 36)**. Your app currently targets 34, so Google will reject it until you raise it. (Google's page: support.google.com/googleplay/android-developer/answer/11926878.)
1. In Android Studio: **Tools > AGP Upgrade Assistant**. Choose the newest version and click **Run selected steps**. Let it finish and sync.
2. **Tools > SDK Manager > SDK Platforms**: tick **Android 16.0 (API 36)** and click **Apply**.
3. In `app/build.gradle.kts` set `compileSdk = 36` and `targetSdk = 36`. Sync.
4. Run the app on an Android 16 emulator and click through: sign in, chat, photos, sermons audio, the back gesture, and notifications.
5. If a red error appears, send me the message and I'll fix it.

## 3. Make the release file (AAB)
1. **Build > Generate Signed App Bundle / APK > Android App Bundle**.
2. **Create new...** key store. Save the `.jks` file somewhere safe (not only on one computer) and **write down both passwords and the key alias**. Back it up; you will need it for every update.
3. Choose the **release** build and finish. The file is in `app/release/app-release.aab`.
4. For every later upload, raise `versionCode` in `app/build.gradle.kts` by 1 (and change `versionName` if you like).
5. Your app's package name, `org.osec.app`, can never be changed after publishing.

## 4. Pages Google needs
- **Privacy policy:** `https://android-osec.web.app/privacy.html` (fill in the yellow placeholders first, then deploy: see `PHOTOS-MUTE-LIVE-STEPS.md`).
- **Account deletion page:** `https://android-osec.web.app/delete-account.html`. Play requires this because people can create accounts in the app. The app also has **Delete account** inside.

## 5. App content forms in Play Console (App content section)
- **Privacy policy:** paste the link above.
- **App access:** the app needs a sign-in. Create a **test account** (email verified and approved for chat) and put its email and password in the "Instructions" box so Google's reviewers can get in.
- **Ads:** No ads.
- **Content rating:** answer the questionnaire honestly. Say users can **interact and share content** (chat, photos). No violence, no gambling, no purchases.
- **Target audience:** choose **13 and over** (not aimed at children).
- **Data safety:** use `STORE-LISTING-AND-DATA-SAFETY.md`.
- **Foreground service permissions:** the app plays sermon audio in the background (type **Media playback**). Say so, and be ready to upload a short screen recording that shows playing a sermon with the screen locked.
- **Financial features:** the Give page only shows your Vipps and bank details and opens Vipps; the app does not process payments. Answer that it has no financial features, unless Play Console's wording clearly says otherwise.
- **Photos and videos permissions:** none are needed. The app uses the system photo picker.
- **User-generated content:** the app has what Google asks for: reporting a message, blocking a person, removing content, and a way to contact you. Mention the contact email in the store listing.

## 6. Store listing
- Texts for English, Norsk and Afaan Oromoo are in `STORE-LISTING-AND-DATA-SAFETY.md`.
- **YOU:** app icon **512 x 512 PNG**, feature graphic **1024 x 500**, and **at least 2 phone screenshots** (take them from the emulator: the Updates tab, a chat, the Sermons tab, the Give tab). Use screenshots without private names or messages.
- Category: **Lifestyle** (or **Social**). Contact email: yours. Website: `https://android-osec.web.app`.

## 7. Release path
1. **Internal testing** first (up to 100 testers, no review wait): upload the AAB, add yourself and a few members by email, install from the Play link, and test the real app.
2. **Closed testing** with the testers Google requires (see section 1).
3. Check **Pre-launch report** results for crashes.
4. Apply for **Production**, then submit. The first review can take several days.

## 8. Before you press publish
- Privacy policy placeholders filled in and the page opens in a browser.
- Test account works and is approved.
- Notifications work on a real phone (see the account menu > **Notification check**).
- Norsk and Afaan Oromoo wording checked by fluent speakers.
- A **safeguarding plan** for chat involving children and young people agreed with your church leaders.
