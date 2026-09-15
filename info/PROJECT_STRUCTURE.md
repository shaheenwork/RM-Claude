# StrangerChat - Anonymous Random Chat App

## Project Structure

```
StrangerChat/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json  (add your own)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/randomchat/app/
│           ├── MainActivity.kt
│           ├── RandomChatApp.kt
│           ├── theme/
│           │   ├── Color.kt
│           │   ├── Theme.kt
│           │   └── Type.kt
│           ├── model/
│           │   ├── ChatMessage.kt
│           │   ├── ChatRoom.kt
│           │   ├── WaitingUser.kt
│           │   └── PremiumStatus.kt
│           ├── realtime/
│           │   ├── RealtimeDbManager.kt
│           │   ├── MatchmakingManager.kt
│           │   └── ChatManager.kt
│           ├── firebase/
│           │   ├── FirestoreManager.kt
│           │   └── ModerationManager.kt
│           ├── ads/
│           │   └── AdMobManager.kt
│           ├── billing/
│           │   └── BillingManager.kt
│           ├── viewmodel/
│           │   ├── HomeViewModel.kt
│           │   ├── ChatViewModel.kt
│           │   └── PremiumViewModel.kt
│           ├── ui/
│           │   ├── components/
│           │   │   ├── PremiumCard.kt
│           │   │   ├── StatusChip.kt
│           │   │   ├── MessageBubble.kt
│           │   │   ├── LockedMediaButton.kt
│           │   │   └── AnimatedLoader.kt
│           │   ├── dialogs/
│           │   │   ├── MatchmakingDialog.kt
│           │   │   └── ReportDialog.kt
│           │   └── screens/
│           │       ├── SplashScreen.kt
│           │       ├── HomeScreen.kt
│           │       ├── ChatScreen.kt
│           │       ├── PremiumScreen.kt
│           │       └── SettingsScreen.kt
│           └── utils/
│               ├── SessionManager.kt
│               ├── Constants.kt
│               └── Extensions.kt
├── build.gradle.kts
├── settings.gradle.kts
└── firebase_rules/
    ├── rtdb_rules.json
    ├── firestore_rules.txt
    └── storage_rules.txt
```
