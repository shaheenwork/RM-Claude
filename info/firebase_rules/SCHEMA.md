# Firebase Realtime Database Schema

## Root Structure

```
{
  "presence": {
    "<sessionId>": {
      "online": true,
      "lastSeen": <serverTimestamp>
    }
  },

  "waitingQueue": {
    "<sessionId>": {
      "sessionId": "<sessionId>",
      "joinedAt": <serverTimestamp>,
      "platform": "android",
      "heartbeat": <serverTimestamp>   // updated every 20s
    }
  },

  "sessionAssignments": {
    "<sessionId>": {
      "roomId": "<roomId>",
      "assignedAt": <serverTimestamp>
    }
  },

  "rooms": {
    "<roomId>": {
      "id": "<roomId>",
      "participants": ["<sessionA>", "<sessionB>"],
      "status": "ACTIVE | ENDED | ABANDONED",
      "createdAt": <serverTimestamp>,
      "endedAt": <serverTimestamp>,

      "messages": {
        "<pushId>": {
          "id": "<pushId>",
          "senderId": "<sessionId>",
          "content": "<text>",         // empty for media
          "mediaUrl": "<url>",         // empty for text
          "type": "TEXT | IMAGE | AUDIO",
          "timestamp": <serverTimestamp>
        }
      },

      "typing": {
        "<sessionId>": <serverTimestamp>   // null when stopped typing
      }
    }
  }
}
```

## Indexing Rules (add to RTDB rules)

```json
{
  "rules": {
    "waitingQueue": {
      ".indexOn": ["joinedAt"]
    },
    "rooms": {
      "$roomId": {
        "messages": {
          ".indexOn": ["timestamp"]
        }
      }
    }
  }
}
```

## Firestore Schema

### subscriptions/{sessionId}
```json
{
  "sessionId": "string",
  "isPremium": true,
  "expiryMs": 0,
  "purchaseToken": "string",
  "updatedAt": 1700000000000
}
```

### reports/{autoId}
```json
{
  "reporter": "sessionId",
  "reported": "sessionId",
  "roomId": "roomId",
  "reason": "Harassment / Bullying",
  "timestamp": 1700000000000
}
```

### banned_sessions/{sessionId}
```json
{
  "bannedUntil": 1700000600000,
  "bannedAt": 1700000000000
}
```

### saved_chats/{sessionId}_{roomId}
```json
{
  "sessionId": "string",
  "roomId": "string",
  "savedAt": 1700000000000,
  "messageCount": 42,
  "preview": "Last message preview..."
}
```

### analytics/{autoId}
```json
{
  "sessionId": "string",
  "event": "chat_started | chat_ended | premium_viewed | premium_purchased",
  "timestamp": 1700000000000
}
```
