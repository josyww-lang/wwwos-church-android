# WWWOS Church (Oromo Stavanger Evangelical Church app)

An Android app for our church family: announcements, events, sermons, prayer, serving, chat, reading plans, Study, About the church, giving details, and a verse of the day, in English, Norsk and Afaan Oromoo.

- App: Kotlin and Jetpack Compose (`app/`)
- Backend: Firebase (Authentication, Firestore, Storage, Cloud Messaging, Cloud Functions, Hosting, Crashlytics)
- Notification server and web pages: `server/` (Node.js Cloud Functions, privacy and delete-account pages)
- Rules: `firestore.rules` and `storage.rules`
- Font: Figtree (SIL Open Font License, see `legal/`)

## What is where
| Folder or file | What it is |
|---|---|
| `app/` | The Android app |
| `server/functions/index.js` | Notifications, reminders, verse of the day |
| `server/public/` | Privacy policy and delete-account pages |
| `firestore.rules`, `storage.rules` | Security rules (publish them in the Firebase console) |
| `NEW-FEATURES-STEPS.md` | Every update, step by step |
| `ADMIN-CHECKLIST.md` | What the church admins fill in |
| `GOOGLE-PLAY-CHECKLIST.md`, `STORE-LISTING-AND-DATA-SAFETY.md` | For publishing |

## Set up on a new computer
1. Install Android Studio and use **JDK 21** (Settings > Build > Gradle).
2. Clone this repository **outside OneDrive or Dropbox** (for example `C:\Dev\ChurchApp`).
3. Get `google-services.json` from the Firebase console (Project settings > Your apps > Android app `org.osec.app`) and put it in the `app/` folder. It is not stored here on purpose.
4. Open the folder in Android Studio, press **Sync**, then **Run**.

## The server
```
cd server
firebase login
firebase deploy --only functions --project android-osec
firebase deploy --only hosting --project android-osec
```
Copy `server/functions/.env.example` to `server/functions/.env` for the optional YouTube "live now" key. The `.env` file is never stored here.

## Release
- Raise `versionCode` and `versionName` in `app/build.gradle.kts` for every version.
- Build a signed APK (Build > Generate Signed App Bundle / APK). Keep the keystore file and passwords **outside** this repository, in two safe places.
- Send test versions with Firebase App Distribution.

## Never put in this repository
`google-services.json`, keystores (`.jks`), passwords, `.env` files, service-account keys, member data.
