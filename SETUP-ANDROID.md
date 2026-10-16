# Church app for Android – setup

You need a computer (Windows, Mac or Linux) and an Android phone, or the free phone simulator that comes with Android Studio. You do not need a Mac.

## 1. Install Android Studio
Download it free from https://developer.android.com/studio and install it. The first launch downloads some extra tools, so allow 10 to 20 minutes.

## 2. Set up Firebase (the app's sign-in and database)
If you already did this for the web version, use that same project and skip to step 2.2.

1. Go to https://console.firebase.google.com and click **Add project**.
2. On the project home page, click the **Android icon** to add an Android app.
   - **Android package name:** `org.yourchurch.app` (type it exactly like this for now)
   - Skip the SHA-1 box.
3. Download **google-services.json** and keep it handy. Skip the remaining steps Firebase shows.
4. **Build > Authentication > Get started > Sign-in method**: turn on **Email/Password**.
5. **Build > Firestore Database > Create database**: production mode, pick a location near you.
6. In Firestore **Rules**, paste the contents of `firestore.rules` (included here) and click **Publish**.

## 3. Open the project
1. In Android Studio choose **File > Open** and select the `ChurchApp` folder.
2. Copy `google-services.json` into the `app` folder (next to `app/build.gradle.kts`). In Android Studio you can drag it onto the `app` folder in the Project panel.
3. Wait for **Gradle sync** to finish (progress bar at the bottom). The first sync takes several minutes. If Android Studio offers to update Gradle or plugin versions, accept.
4. Open `app/src/main/java/org/yourchurch/app/ChurchConfig.kt` and change `Our Church` to your church's name.

## 4. Run it
- **On a phone:** turn on Developer options and USB debugging, plug it in, pick it from the device list at the top of Android Studio, and press the green **Run** button.
- **On the simulator:** open **Device Manager** (right side), create a phone, and press **Run**.

## 5. Make yourself an admin
1. In the app, choose **Create an account** and sign up.
2. In Firebase, open **Authentication > Users** and copy your **User UID**.
3. In **Firestore Database**, start a collection named `admins`, with a document whose ID is your User UID. Add any field, for example `role` = `admin`.
4. Close and reopen the app. A **New** button now appears on the Updates tab.

## 6. Set up giving (Vipps and bank account)
1. Make sure you published the latest `firestore.rules` (step 2.6). The Give tab needs the new `settings` rule.
2. Sign in as an admin and open the **Give** tab.
3. Tap the **pencil** at the top and enter your church's **Vippsnummer**, the **account holder name** and **kontonummer**. Add an **IBAN** if people abroad give by bank transfer. Save.
4. Everyone now sees these details on the Give tab, with a **Copy** button for each number and an **Open Vipps** button. You can change them any time without updating the app.

## 7. Languages
The app is in **English, Norsk and Afaan Oromoo**. It uses the phone's language by default. Anyone can choose another language on the sign-in screen, or from the account icon on the Updates tab.

The Afaan Oromoo and Norsk wording is my draft. Please ask a fluent speaker from your church to read through it. All the text lives in three files, one per language:
- `app/src/main/res/values/strings.xml` (English)
- `app/src/main/res/values-nb/strings.xml` (Norsk)
- `app/src/main/res/values-om/strings.xml` (Afaan Oromoo)

A reviewer can fix wording by editing the text between the tags. Don't change the `name="..."` parts. Announcements you write yourself stay in whichever language you type them.

## 8. Give it to your congregation
- **Quick way (testing):** in Android Studio, **Build > Build APK(s)**. Send the file to people. They must allow installs from unknown sources, which is fine for a small group but awkward for everyone.
- **Proper way:** publish on Google Play. This needs a Google Play developer account (one-time fee, currently US$25) and a signed app bundle (**Build > Generate Signed App Bundle**). Before you publish, change the package name `org.yourchurch.app` to something unique to your church, and register that new name in Firebase.

## Good to know
- Anyone can create an account in the app. Announcements aren't secret, but you can limit sign-ups later.
- Today's announcements follow the phone's own date.
- Sermons, Chat and Study are placeholders for now. The Give tab works today. The sign-in and database are shared, so the web, Android and iPhone versions all read the same announcements.
