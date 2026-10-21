# Admin App Reintroduction & Restoration Guide

> **Note for AI Agent / Developer:**
> This document contains everything needed to re-enable the Admin App backend (`:admin` module and Firebase Cloud Functions / Firestore / RTDB triggers) in a single step if you decide to use the Admin App again in the future.

---

## Quick Prompt for AI Agent
If you want an AI agent to restore the Admin App, copy and paste the following prompt:

> "Please restore the Admin App backend based on the instructions in `ADMIN_APP_REINTRODUCTION_GUIDE.md`. Re-add the admin triggers (`onChatMessageCreated`, `onReportCreated`), admin index updates in `matchOnQueue`, `cleanupRooms` archiving, and all admin Cloud Functions in `functions/index.js`. Update RTDB rules if needed."

---

## Architectural Overview

The Admin App operates using:
1. **Firestore Index Collection:** `adminChatIndex` (stores chat metadata, message counts, genders, premium statuses, reported flags, and unread state).
2. **Firestore Photos Collection:** `adminPhotos` (stores photo gallery items linked to message IDs).
3. **Firestore Admin Auth Collections:** `adminCredentials`, `adminSessions`, `adminInterventions`.
4. **RTDB Branches:** `rooms`, `reportedRooms`, `archivedRooms`, `adminSessions`.

---

## 1. Cloud Functions (`functions/index.js`)

To re-enable the Admin App backend, restore the following Cloud Functions and triggers in `functions/index.js`:

### A. Real-time Message Indexing (`onChatMessageCreated`)
```javascript
exports.onChatMessageCreated = onValueCreated(
    { ref: "rooms/{roomId}/messages/{messageId}", region: "us-central1" },
    async (event) => {
        const { roomId, messageId } = event.params;
        const msg = event.data.val() || {};
        const type = msg.type || "TEXT";
        const update = {
            messageCount: FieldValue.increment(1),
            lastMessageAt: FieldValue.serverTimestamp(),
            lastMessagePreview: type === "TEXT" ? String(msg.content || "").slice(0, 80) : `[${type.toLowerCase()}]`,
        };
        if (type === "IMAGE") update.hasPhoto = true;
        if (type === "AUDIO") update.hasAudio = true;
        if (msg.adminSender !== true) update.adminUnread = true;
        try {
            const fs = getFirestore("main");
            await fs.collection("adminChatIndex").doc(roomId).set(update, { merge: true });
            if (type === "IMAGE" && msg.mediaUrl) {
                await writeAdminPhoto(fs, messageId, roomId, msg);
            }
        } catch (e) {
            console.warn(`onChatMessageCreated index update failed room=${roomId}:`, e.message);
        }
    }
);

async function writeAdminPhoto(fs, messageId, roomId, msg) {
    const senderId = String(msg.senderId || "");
    let gender = "UNSPECIFIED";
    let premium = false;
    try {
        const idx = await fs.collection("adminChatIndex").doc(roomId).get();
        if (idx.exists) {
            gender = (idx.get("genders") || {})[senderId] || "UNSPECIFIED";
            premium = (idx.get("premium") || {})[senderId] === true;
        }
    } catch (e) {
        console.warn(`writeAdminPhoto index read failed room=${roomId}:`, e.message);
    }
    const createdAtMs = typeof msg.timestamp === "number" ? msg.timestamp : Date.now();
    await fs.collection("adminPhotos").doc(messageId).set({
        url: String(msg.mediaUrl),
        senderId,
        roomId,
        gender,
        premium,
        createdAt: Timestamp.fromMillis(createdAtMs),
    });
}
```

### B. Report Indexing (`onReportCreated`)
```javascript
exports.onReportCreated = onDocumentCreated(
    { document: "reports/{reportId}", database: "main", region: "us-central1" },
    async (event) => {
        const data = event.data?.data();
        const roomId = data?.roomId;
        if (!roomId) return;
        try {
            await getFirestore("main").collection("adminChatIndex").doc(roomId).set(
                { reported: true, reportCount: FieldValue.increment(1), lastReportedAt: FieldValue.serverTimestamp() },
                { merge: true }
            );
        } catch (e) {
            console.warn(`onReportCreated index update failed room=${roomId}:`, e.message);
        }
    }
);
```

### C. Admin Matchmaking Indexing (Inside `matchOnQueue`)
In `matchOnQueue`, re-add writing to `adminChatIndex`:
```javascript
await fs.collection("adminChatIndex").doc(roomId).set({
    roomId,
    participants: [newUid, partnerId],
    genders: { [newUid]: newcomerGender, [partnerId]: partnerGender },
    premium: {
        [newUid]: subA.exists && subA.get("isPremium") === true,
        [partnerId]: subB.exists && subB.get("isPremium") === true,
    },
    status: "ACTIVE",
    createdAt: FieldValue.serverTimestamp(),
    lastMessageAt: FieldValue.serverTimestamp(),
    lastMessagePreview: "",
    messageCount: 0,
    hasPhoto: false,
    hasAudio: false,
    reported: false,
    reportCount: 0,
    adminUnread: true,
    favorite: false,
});
```

### D. Room Archiving (Inside `cleanupRooms`)
In `cleanupRooms`, re-add copying rooms with $\ge 20$ messages to `archivedRooms` and updating `adminChatIndex`:
```javascript
if (msgCount >= ARCHIVE_MSG_THRESHOLD) {
    await db.ref(`archivedRooms/${roomId}`).set({
        ...data,
        archivedAt: now,
        archiveReason: isEnded ? "ended" : "stale",
    });
}
await db.ref(`rooms/${roomId}`).remove();
await fs.collection("adminChatIndex").doc(roomId).set(
    {
        status: "ARCHIVED",
        archivedAt: Timestamp.fromMillis(now),
        archiveReason: isEnded ? "ended" : "stale",
        messageCount: msgCount,
    },
    { merge: true }
);
```

### E. Admin Callable Functions
Re-add the following callables to `functions/index.js`:
- `adminLogin`
- `getAdminDashboardCounts`
- `listAdminChats`
- `getAdminChatMessages`
- `backfillAdminChatIndex`
- `startIntervention`
- `sendInterventionMessage`
- `endIntervention`

---

## 2. Realtime Database Security Rules (`firebase-rtdb-rules.json`)

Ensure the following paths allow admin session reads/writes:
```json
{
  "rules": {
    "rooms": {
      "$roomId": {
        ".read": "auth != null && (data.child('participants/0').val() == auth.uid || data.child('participants/1').val() == auth.uid || root.child('adminSessions').child(auth.uid).exists())"
      }
    },
    "reportedRooms": {
      "$roomId": {
        ".read": "auth != null && root.child('adminSessions').child(auth.uid).exists()",
        ".write": "auth != null"
      }
    },
    "archivedRooms": {
      ".write": false,
      "$roomId": {
        ".read": "auth != null && root.child('adminSessions').child(auth.uid).exists()"
      }
    },
    "adminSessions": {
      ".read": false,
      ".write": false
    }
  }
}
```

---

## 3. Deployment Command
When ready to redeploy:
```bash
cd functions
firebase deploy --only functions,database
```
