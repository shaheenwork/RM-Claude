# StrangerChat — Setup & Deployment Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Firebase project
- Google AdMob account
- Google Play Console account (for billing)

---

## 1. Firebase Project Setup

### 1a. Create Firebase Project
1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Create a new project — name it `StrangerChat` (or anything)
3. Enable **Google Analytics** (optional but recommended)

### 1b. Add Android App
1. In Firebase Console → Project Settings → Add App → Android
2. Package name: `com.randomchat.app`
3. Download `google-services.json`
4. Place it at: `app/google-services.json`

### 1c. Enable Services
- **Realtime Database**: Create database in **test mode** initially, then apply the rules from `firebase_rules/rtdb_rules.json`
- **Cloud Firestore**: Create database, then apply rules from `firebase_rules/firestore_rules.txt`
- **Firebase Storage**: Enable, then apply rules from `firebase_rules/storage_rules.txt`

### 1d. Apply RTDB Rules
```
Firebase Console → Realtime Database → Rules → paste rtdb_rules.json content → Publish
```

Also add these indexes to your RTDB rules (inside the rules JSON):
```json
"waitingQueue": { ".indexOn": ["joinedAt"] },
"rooms": { "$roomId": { "messages": { ".indexOn": ["timestamp"] } } }
```

### 1e. Apply Firestore Rules
```
Firebase Console → Firestore → Rules → paste firestore_rules.txt content → Publish
```

### 1f. Apply Storage Rules
```
Firebase Console → Storage → Rules → paste storage_rules.txt content → Publish
```

---

## 2. AdMob Setup

1. Go to [admob.google.com](https://admob.google.com)
2. Create an app for Android
3. Get your **App ID** (format: `ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX`)
4. Create ad units:
   - **Banner** → copy ID → replace `Constants.ADMOB_BANNER_ID`
   - **Interstitial** → copy ID → replace `Constants.ADMOB_INTERSTITIAL_ID`
5. Replace the test App ID in `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.gms.ads.APPLICATION_ID"
       android:value="ca-app-pub-YOUR_REAL_APP_ID" />
   ```

> ⚠️ **Never use test IDs in production.** The test App ID in the manifest must be replaced.

---

## 3. Google Play Billing Setup

### 3a. Play Console Setup
1. Create your app in [Play Console](https://play.google.com/console)
2. Go to **Monetization → Subscriptions**
3. Create a subscription:
   - Product ID: `premium_monthly` (must match `Constants.PRODUCT_PREMIUM_MONTHLY`)
   - Price: $4.99/month (or your preferred price)
   - Billing period: Monthly
4. Publish the subscription

### 3b. Testing Billing
- Add test accounts in Play Console → Setup → License Testing
- Use internal test track for billing integration testing

---

## 4. Cloud Functions (Recommended for Production)

The current implementation uses **client-side matchmaking** which is acceptable for MVP. For production scale, deploy a Cloud Function to handle matchmaking:

```javascript
// functions/index.js
const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.matchUsers = functions.database
  .ref('/waitingQueue/{sessionId}')
  .onCreate(async (snapshot, context) => {
    const db = admin.database();
    const newSessionId = context.params.sessionId;

    const queueRef = db.ref('waitingQueue');
    const snapshot2 = await queueRef.orderByChild('joinedAt').limitToFirst(5).once('value');

    let partnerId = null;
    snapshot2.forEach(child => {
      if (child.key !== newSessionId && !partnerId) {
        partnerId = child.key;
      }
    });

    if (!partnerId) return null;

    const roomId = `${[newSessionId, partnerId].sort().join('_')}_${Date.now()}`;

    await db.ref(`rooms/${roomId}`).set({
      id: roomId,
      participants: [newSessionId, partnerId],
      status: 'ACTIVE',
      createdAt: admin.database.ServerValue.TIMESTAMP
    });

    await db.ref(`sessionAssignments/${newSessionId}`).set({
      roomId, assignedAt: admin.database.ServerValue.TIMESTAMP
    });
    await db.ref(`sessionAssignments/${partnerId}`).set({
      roomId, assignedAt: admin.database.ServerValue.TIMESTAMP
    });

    await db.ref(`waitingQueue/${newSessionId}`).remove();
    await db.ref(`waitingQueue/${partnerId}`).remove();

    return null;
  });

// Dead queue cleanup — runs every 2 minutes
exports.cleanDeadQueue = functions.pubsub
  .schedule('every 2 minutes')
  .onRun(async () => {
    const db = admin.database();
    const cutoff = Date.now() - 90_000; // 90 second TTL
    const snapshot = await db.ref('waitingQueue').orderByChild('joinedAt').endAt(cutoff).once('value');
    const updates = {};
    snapshot.forEach(child => { updates[child.key] = null; });
    if (Object.keys(updates).length > 0) {
      await db.ref('waitingQueue').update(updates);
    }
    return null;
  });
```

Deploy with: `firebase deploy --only functions`

---

## 5. Build & Run

### Debug build
```bash
./gradlew assembleDebug
```

### Release build
```bash
./gradlew assembleRelease
```

### Sign for Play Store
1. Generate keystore: `keytool -genkey -v -keystore stranger_chat.jks -keyAlg RSA -keysize 2048 -validity 10000 -alias strangerchat`
2. Add signing config to `app/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("stranger_chat.jks")
        storePassword = "your_password"
        keyAlias = "strangerchat"
        keyPassword = "your_key_password"
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## 6. Play Store Deployment Checklist

- [ ] `google-services.json` added
- [ ] Real AdMob IDs replaced (manifest + Constants.kt)
- [ ] Real Play Billing product ID matches `premium_monthly`
- [ ] Firebase rules deployed (RTDB + Firestore + Storage)
- [ ] Release keystore generated and configured
- [ ] App signed with release keystore
- [ ] `versionCode` incremented in `build.gradle.kts`
- [ ] Screenshots captured on multiple device sizes
- [ ] Privacy Policy URL added to SettingsScreen
- [ ] Terms of Service URL added to SettingsScreen
- [ ] App icon (all densities) added to `res/mipmap/`
- [ ] Tested on physical device with real Play billing in test mode

---

## 7. App Icon

Replace the default launcher icons in:
```
res/mipmap-mdpi/ic_launcher.png       (48x48)
res/mipmap-hdpi/ic_launcher.png       (72x72)
res/mipmap-xhdpi/ic_launcher.png      (96x96)
res/mipmap-xxhdpi/ic_launcher.png     (144x144)
res/mipmap-xxxhdpi/ic_launcher.png    (192x192)
res/mipmap-xxxhdpi/ic_launcher_round.png
```

Use Android Studio's **Image Asset Studio**: Right-click `res` → New → Image Asset.

---

## 8. Key Constants to Customise

`utils/Constants.kt`:
```kotlin
// Replace with real AdMob IDs
const val ADMOB_BANNER_ID = "ca-app-pub-YOUR_REAL_ID/XXXXXXXXXX"
const val ADMOB_INTERSTITIAL_ID = "ca-app-pub-YOUR_REAL_ID/XXXXXXXXXX"

// Must match Play Console subscription product ID
const val PRODUCT_PREMIUM_MONTHLY = "premium_monthly"

// Add profanity words for your target language
val PROFANITY_LIST = setOf("word1", "word2", ...)

// Tune matchmaking feel
const val MIN_MATCH_DELAY_MS = 4_000L  // 4 seconds minimum
const val MAX_MATCH_DELAY_MS = 8_000L  // 8 seconds maximum
```

---

## 9. Architecture Notes

### Matchmaking Flow
```
User taps "Start Chat"
  → ChatViewModel.startSearch()
      → MatchmakingManager.startSearch()
          → RTDB: setOnline(), joinWaitingQueue()
          → Listens on sessionAssignments/{sessionId}
          → Attempts client-side match after 1.5s delay
  → MatchmakingDialog shown (4-8s UX delay)
  → Always navigate to ChatScreen after delay
  → If match found: Matched state emitted → ChatManager.attachRoom()
  → If no match: User can still send messages (queued as pending)
  → When eventually matched: pending messages flushed to live room
```

### Premium Detection Flow
```
App launch
  → BillingManager.connect()
  → BillingManager.restorePurchases()
  → If active subscription found:
      → SessionManager.setPremium(true)
      → FirestoreManager.savePremiumStatus()
  → All screens observe SessionManager.isPremiumFlow
```

### Cost Optimisation Notes
- RTDB persistence disabled (`setPersistenceEnabled(false)`) — ephemeral only
- Messages limited to last 50 per room (`limitToLast(50)`)
- Rooms cleaned up after chat ends
- Firestore used minimally — only for durable business data
- No global presence listeners — only targeted path listeners
- Heartbeat runs only while in waiting queue

---

## 10. Troubleshooting

| Issue | Fix |
|-------|-----|
| `google-services.json` not found | Add it to `app/` directory |
| Billing not working in debug | Use internal test track, add tester email |
| Ads not showing | Check AdMob App ID in manifest; allow 24h for new accounts |
| RTDB permission denied | Check rules are published correctly |
| Matchmaking never connects | Verify RTDB rules allow read/write to `waitingQueue` |
| Premium not restoring | Check product ID matches exactly: `premium_monthly` |
