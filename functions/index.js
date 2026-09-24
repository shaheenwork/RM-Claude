const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onValueCreated } = require("firebase-functions/v2/database");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { GoogleAuth } = require("google-auth-library");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getStorage } = require("firebase-admin/storage");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { createHash } = require("crypto");

initializeApp();

// ─────────────────────────────────────────────────────────────────────────────
// Purchase verification
//
// Called by the Android client immediately after a successful Play Billing
// purchase. Verifies the purchaseToken against the Google Play Developer API
// (server-side) and writes the authoritative premium status to Firestore.
//
// Clients CANNOT write to subscriptions/ directly (Firestore rules block it).
// Admin SDK used here bypasses those rules.
//
// Requires the service account to have the "Google Play Android Developer" role
// in Google Cloud IAM, or the Android Developer API enabled + Play Console
// linked to the project.
// ─────────────────────────────────────────────────────────────────────────────

const PACKAGE_NAME = "com.shaheen.randomchat";
const PLAY_ACTIVE_STATES = new Set([
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
]);
const VALID_PRODUCTS = new Set([
    "premium_weekly",
    "premium_monthly",
    "premium_yearly",
]);

exports.verifyPurchase = onCall(
    { region: "us-central1" },
    async (request) => {
        if (!request.auth) {
            throw new HttpsError("unauthenticated", "Sign in required");
        }
        const uid = request.auth.uid;
        const { purchaseToken, productId } = request.data || {};

        if (!purchaseToken || !productId) {
            throw new HttpsError("invalid-argument", "purchaseToken and productId required");
        }
        if (!VALID_PRODUCTS.has(productId)) {
            throw new HttpsError("invalid-argument", "Unknown productId");
        }

        const fs = getFirestore("main");

        try {
            // Application Default Credentials — auto-available in Cloud Functions
            const auth = new GoogleAuth({
                scopes: ["https://www.googleapis.com/auth/androidpublisher"],
            });
            const client = await auth.getClient();
            const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/subscriptionsv2/tokens/${purchaseToken}`;
            const { data: sub } = await client.request({ url });

            const isActive = PLAY_ACTIVE_STATES.has(sub.subscriptionState);
            const lineItem = sub.lineItems?.find((item) => VALID_PRODUCTS.has(item.productId));
            const verifiedProductId = lineItem?.productId ?? productId;
            const expiryMs = lineItem?.expiryTime
                ? new Date(lineItem.expiryTime).getTime()
                : 0;

            await fs.collection("subscriptions").doc(uid).set({
                isPremium: isActive,
                expiryMs: isActive ? expiryMs : 0,
                purchaseToken,
                productId: verifiedProductId,
                subscriptionState: sub.subscriptionState,
                updatedAt: FieldValue.serverTimestamp(),
                verifiedAt: FieldValue.serverTimestamp(),
            });

            console.log(`verifyPurchase uid=${uid} product=${verifiedProductId} state=${sub.subscriptionState}`);
            return { isPremium: isActive, expiryMs: isActive ? expiryMs : 0 };

        } catch (e) {
            if (e.response?.status === 404) {
                // Token not found = invalid or refunded
                await fs.collection("subscriptions").doc(uid).set(
                    { isPremium: false, updatedAt: FieldValue.serverTimestamp() },
                    { merge: true }
                );
                return { isPremium: false, expiryMs: 0 };
            }
            console.error(`verifyPurchase error uid=${uid}:`, e.message);
            throw new HttpsError("internal", "Verification failed");
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
// Server-side matchmaking — eliminates client-side race conditions.
//
// Triggered every time a new entry appears in waitingQueue/{uid}.
// Runs an RTDB transaction on the whole queue to atomically:
//   1. Pick the oldest other waiter.
//   2. Remove both this user and the partner from the queue.
//   3. Multi-path-update: create room + write both sessionAssignments.
//
// Admin SDK bypasses RTDB rules, so it can safely write to foreign uid paths.
// Clients only ever write/remove their OWN queue entry (rules tightened).
// ─────────────────────────────────────────────────────────────────────────────
exports.matchOnQueue = onValueCreated(
    { ref: "waitingQueue/{uid}", region: "us-central1" },
    async (event) => {
        const db = getDatabase();
        const fs = getFirestore("main");
        const newUid = event.params.uid;
        const queueRef = db.ref("waitingQueue");

        // ── Pre-fetch newcomer's recent partners + block sets (outside tx) ───
        const [recentSnap, blocksOutSnap, blocksInSnap] = await Promise.all([
            db.ref(`recentPairs/${newUid}`).once("value"),
            db.ref(`blocks/${newUid}`).once("value"),
            db.ref(`blockedBy/${newUid}`).once("value"),
        ]);
        const recentSet = new Set(recentSnap.exists()    ? Object.keys(recentSnap.val())    : []);
        const blockSet  = new Set([
            ...(blocksOutSnap.exists() ? Object.keys(blocksOutSnap.val()) : []),
            ...(blocksInSnap.exists()  ? Object.keys(blocksInSnap.val())  : []),
        ]);

        let partnerId      = null;
        let partnerGender  = "UNSPECIFIED";
        let newcomerGender = "UNSPECIFIED";
        let waitTimeMs     = 0;
        let pickTier       = "none";

        // ── Atomic transaction: tiered pick with last-resort FIFO fallback ───
        const result = await queueRef.transaction((currentData) => {
            if (!currentData) return currentData;
            if (!currentData[newUid]) return currentData; // already removed

            const others = Object.keys(currentData).filter((k) => k !== newUid);
            if (others.length === 0) return currentData; // empty — wait

            // Oldest (earliest joinedAt) with deterministic uid tie-break.
            const oldestOf = (uids) => uids.reduce((acc, uid) => {
                const a = currentData[uid]?.joinedAt || 0;
                const b = currentData[acc]?.joinedAt || 0;
                if (a < b) return uid;
                if (a > b) return acc;
                return uid < acc ? uid : acc; // tie-break: lexicographic uid
            }, uids[0]);

            const isClean  = (uid) => !recentSet.has(uid) && !blockSet.has(uid);
            const isFemale = (uid) => currentData[uid]?.gender === "FEMALE";

            const newcomerIsFemale = isFemale(newUid);
            const ffRoll = newcomerIsFemale && Math.random() < 0.6;

            let pick = null;

            // Tier 1a — 60% bias: clean F-F
            if (ffRoll) {
                const cleanF = others.filter((u) => isFemale(u) && isClean(u));
                if (cleanF.length > 0) { pick = oldestOf(cleanF); pickTier = "ff_clean"; }
            }
            // Tier 1b — 60% bias: any F-F (honor bias even if dirty)
            if (!pick && ffRoll) {
                const anyF = others.filter(isFemale);
                if (anyF.length > 0) { pick = oldestOf(anyF); pickTier = "ff_dirty"; }
            }
            // Tier 2 — clean FIFO (any gender, no recent/block)
            if (!pick) {
                const clean = others.filter(isClean);
                if (clean.length > 0) { pick = oldestOf(clean); pickTier = "clean"; }
            }
            // Tier 3 — LAST RESORT: oldest of all (allows recent/blocked).
            // Guarantees: if any other user is in the queue, they get matched.
            // No one ever waits indefinitely just because of rules.
            if (!pick) {
                pick = oldestOf(others);
                pickTier = "last_resort";
            }

            partnerId      = pick;
            partnerGender  = currentData[pick]?.gender   || "UNSPECIFIED";
            newcomerGender = currentData[newUid]?.gender || "UNSPECIFIED";
            waitTimeMs     = Date.now() - (currentData[pick]?.joinedAt || Date.now());
            delete currentData[newUid];
            delete currentData[pick];
            return currentData;
        });

        if (!result.committed || !partnerId) return;

        // ── Single multi-path write: room + assignments + recentPairs both ways
        const roomId = [...[newUid, partnerId].sort(), Date.now()].join("_");
        const now    = Date.now();
        await db.ref().update({
            [`rooms/${roomId}`]: {
                id: roomId,
                participants: [newUid, partnerId],
                status: "ACTIVE",
                createdAt: now,
            },
            [`sessionAssignments/${newUid}`]:    { roomId, assignedAt: now },
            [`sessionAssignments/${partnerId}`]: { roomId, assignedAt: now },
            [`recentPairs/${newUid}/${partnerId}`]: now,
            [`recentPairs/${partnerId}/${newUid}`]: now,
        });

        // ── Cap recentPairs to last 5 per uid (best-effort, non-blocking) ────
        Promise.all([
            evictOldRecentPairs(db, newUid, 5),
            evictOldRecentPairs(db, partnerId, 5),
        ]).catch((e) => console.warn("evictOldRecentPairs:", e.message));

        // ── Admin moderation index — fire-and-forget, like matchStats below.
        // Genders are captured HERE because this is the only place either
        // participant's gender is ever in scope: waitingQueue/{uid}.gender is
        // deleted the instant this transaction removes it, and no other
        // record of it exists anywhere in the app. Premium is looked up
        // best-effort so a slow read never delays matching.
        (async () => {
            const [subA, subB] = await Promise.all([
                fs.collection("subscriptions").doc(newUid).get(),
                fs.collection("subscriptions").doc(partnerId).get(),
            ]);
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
        })().catch((e) => console.warn(`adminChatIndex create failed room=${roomId}:`, e.message));

        // ── Telemetry: pair type + wait time + which tier picked ─────────────
        const pairType = `${(newcomerGender[0] || "?")}${(partnerGender[0] || "?")}`;
        fs.collection("matchStats").add({
            pairType,
            waitTimeMs,
            pickTier,
            matchedAt: FieldValue.serverTimestamp(),
        }).catch((e) => console.warn("matchStats write:", e.message));

        console.log(`Matched ${newUid} <-> ${partnerId} → ${roomId} (tier=${pickTier}, type=${pairType}, wait=${waitTimeMs}ms)`);
    }
);

/**
 * Caps recentPairs/<uid> to N most-recent partner entries; drops the oldest.
 * Best-effort — failures are logged, not thrown.
 */
async function evictOldRecentPairs(db, uid, max) {
    const snap = await db.ref(`recentPairs/${uid}`).once("value");
    if (!snap.exists()) return;
    const entries = Object.entries(snap.val()); // [[partnerUid, timestamp], ...]
    if (entries.length <= max) return;
    entries.sort((a, b) => a[1] - b[1]); // oldest first
    const toRemove = entries.slice(0, entries.length - max);
    const updates = {};
    for (const [partner] of toRemove) {
        updates[`recentPairs/${uid}/${partner}`] = null;
    }
    await db.ref().update(updates);
}

/**
 * Runs every minute. Removes waitingQueue/* entries whose heartbeat (or
 * joinedAt if no heartbeat) is older than 180s — handles client crashes /
 * network drops that bypass the onDisconnect cleanup. Prevents ghost matches.
 */
exports.cleanupStaleQueue = onSchedule(
    { schedule: "every 1 minutes", timeZone: "UTC" },
    async () => {
        const db = getDatabase();
        const cutoff = Date.now() - 180_000; // 180 seconds
        const snap = await db.ref("waitingQueue").once("value");
        if (!snap.exists()) return;

        const updates = {};
        let removed = 0;
        snap.forEach((child) => {
            const data = child.val() || {};
            const lastSeen = data.heartbeat || data.joinedAt || 0;
            if (lastSeen < cutoff) {
                updates[`waitingQueue/${child.key}`] = null;
                removed++;
            }
        });
        if (removed > 0) await db.ref().update(updates);
        console.log(`cleanupStaleQueue: removed ${removed} stale entries`);
    }
);

const ARCHIVE_MSG_THRESHOLD = 20;
const ROOM_MAX_AGE_MS = 2 * 60 * 60 * 1000; // 2 hours

/**
 * Runs every hour.
 * - Rooms with status=ENDED or older than 2h:
 *     if messages >= 20  →  copy to archivedRooms/ then delete
 *     else               →  delete
 */
exports.cleanupRooms = onSchedule("every 60 minutes", async () => {
    const db = getDatabase();
    const fs = getFirestore("main");
    const roomsRef = db.ref("rooms");
    const now = Date.now();

    const snapshot = await roomsRef.once("value");
    if (!snapshot.exists()) return;

    const cleanupPromises = [];

    snapshot.forEach((roomSnap) => {
        const roomId = roomSnap.key;
        const data = roomSnap.val();

        const isEnded = data.status === "ENDED";
        const createdAt = data.createdAt || 0;
        const isStale = now - createdAt > ROOM_MAX_AGE_MS;

        if (!isEnded && !isStale) return;

        const messages = data.messages ? Object.keys(data.messages) : [];
        const msgCount = messages.length;

        const promise = (async () => {
            if (msgCount >= ARCHIVE_MSG_THRESHOLD) {
                await db.ref(`archivedRooms/${roomId}`).set({
                    ...data,
                    archivedAt: now,
                    archiveReason: isEnded ? "ended" : "stale",
                });
            }
            await db.ref(`rooms/${roomId}`).remove();
            // Moderation index outlives the RTDB room data — the admin app's
            // record of this chat must not disappear just because the raw
            // messages did (or were never archived past the threshold).
            await fs.collection("adminChatIndex").doc(roomId).set(
                {
                    status: "ARCHIVED",
                    archivedAt: Timestamp.fromMillis(now),
                    archiveReason: isEnded ? "ended" : "stale",
                    messageCount: msgCount,
                },
                { merge: true }
            ).catch((e) => console.warn(`adminChatIndex archive update failed room=${roomId}:`, e.message));
            console.log(`Cleaned room ${roomId}: ${msgCount} msgs, archived=${msgCount >= ARCHIVE_MSG_THRESHOLD}`);
        })();

        cleanupPromises.push(promise);
    });

    await Promise.all(cleanupPromises);
    console.log(`cleanupRooms done: processed ${cleanupPromises.length} rooms`);
});

/**
 * Extracts Storage file path from a Firebase Storage download URL.
 * e.g. "https://firebasestorage.googleapis.com/v0/b/bucket/o/images%2FsessionId%2Ffile.jpg?..."
 * → "images/sessionId/file.jpg"
 */
function storagePathFromUrl(url) {
    try {
        const match = url.match(/\/o\/(.+?)(\?|$)/);
        return match ? decodeURIComponent(match[1]) : null;
    } catch {
        return null;
    }
}

/**
 * Collects all mediaUrl values from a RTDB branch (reportedRooms or archivedRooms).
 * Returns a Set of Storage file paths.
 */
async function collectProtectedPaths(db, branch) {
    const snap = await db.ref(branch).once("value");
    const paths = new Set();
    if (!snap.exists()) return paths;
    snap.forEach((roomSnap) => {
        const messages = roomSnap.child("messages").val() || {};
        Object.values(messages).forEach((msg) => {
            if (msg.mediaUrl) {
                const path = storagePathFromUrl(msg.mediaUrl);
                if (path) paths.add(path);
            }
        });
    });
    return paths;
}

/**
 * Runs weekly (every Sunday at 02:00 UTC).
 * Deletes all files under images/ and audio/ in Storage that are:
 *   - older than 7 days AND
 *   - NOT referenced in reportedRooms or archivedRooms
 */
exports.cleanupStorage = onSchedule("every sunday 02:00", async () => {
    const db = getDatabase();
    const bucket = getStorage().bucket();

    const [reportedPaths, archivedPaths] = await Promise.all([
        collectProtectedPaths(db, "reportedRooms"),
        collectProtectedPaths(db, "archivedRooms"),
    ]);
    const protectedPaths = new Set([...reportedPaths, ...archivedPaths]);
    console.log(`Protected file count: ${protectedPaths.size}`);

    const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
    let deleted = 0;
    let skipped = 0;

    for (const prefix of ["images/", "audio/"]) {
        const [files] = await bucket.getFiles({ prefix });
        for (const file of files) {
            if (protectedPaths.has(file.name)) {
                skipped++;
                continue;
            }
            const [metadata] = await file.getMetadata();
            const created = new Date(metadata.timeCreated).getTime();
            if (created > sevenDaysAgo) {
                skipped++;
                continue;
            }
            await file.delete();
            deleted++;
        }
    }

    console.log(`cleanupStorage done: deleted=${deleted} skipped=${skipped}`);
});

// ─────────────────────────────────────────────────────────────────────────────
// Daily "Malayalis are online" nudge
//
// One broadcast per day to the `all_users` topic at a RANDOM minute between
// 9:30 PM and 10:00 PM IST (= 16:00–16:29 UTC). Kerala night peak + Gulf evening.
//
// Cron fires every minute across the window; the function only sends on ONE
// minute per day, chosen deterministically from the date (so it fires exactly
// once — no extra lock needed). The randomness keeps the ping from feeling
// robotic and spreads server load.
//
// To shift the window, edit the cron ("0-29 16" = minutes 0-29 of hour 16 UTC).
// ─────────────────────────────────────────────────────────────────────────────
const NUDGE_WINDOW_MINUTES = 30; // must match the minute range in the cron below

exports.dailyNudge = onSchedule(
    { schedule: "0-29 16 * * *", timeZone: "UTC" },
    async (event) => {
        // Use the scheduled fire time, not the wall clock, so a slow cold start
        // that spills into the next minute can't make us miss today's slot.
        const now = new Date(event.scheduleTime || Date.now());
        // Deterministic "random" target minute seeded by the UTC date. SHA-256
        // scatters consecutive dates across the window; a simple rolling hash
        // just shifts the minute by +1 each day (date strings differ by 1 char).
        const dateStr = now.toISOString().slice(0, 10);
        const digest = createHash("sha256").update(`dailyNudge:${dateStr}`).digest();
        const targetMinute = digest.readUInt32BE(0) % NUDGE_WINDOW_MINUTES;
        if (now.getUTCMinutes() !== targetMinute) return; // not this minute today

        const messaging = getMessaging();
        try {
            await messaging.send({
                topic: "all_users",
                notification: {
                    title: "Random Malayali",
                    body: "Interesting Malayalis are online now — open and say hi 👋",
                },
                android: {
                    priority: "normal",
                    collapseKey: "daily_nudge",
                    notification: {
                        channelId: "activity",
                        // No clickAction — there's no intent-filter for a custom action,
                        // so setting one makes the tap resolve to nothing (notification
                        // just dismisses). Omitting it = tap opens the launcher activity
                        // (MainActivity) with the data payload as intent extras.
                    },
                },
                data: {
                    // NB: "from" is a reserved FCM data key — using it makes send() throw
                    // "Invalid data payload key: from". Use "src" instead.
                    src: "push",
                    type: "daily_nudge",
                },
            });
            console.log("dailyNudge sent to all_users");
        } catch (e) {
            console.error("dailyNudge failed:", e.message);
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
// Paywall-abandon reminder
//
// The Android paywall calls logPaywallView each time a non-premium user opens
// it. Once a day, paywallReminder sends ONE push to users who opened the
// paywall at least twice, did not subscribe, and last looked 1–4 days ago.
// At most one reminder per user per 7 days. Delivered via the per-user topic
// `user_<uid>`, which the app only subscribes while notifications are enabled.
// ─────────────────────────────────────────────────────────────────────────────
const DAY_MS = 24 * 60 * 60 * 1000;
const PAYWALL_MIN_VIEWS = 2;
const PAYWALL_REMIND_AFTER_MS = 1 * DAY_MS;    // quiet period after the last view
const PAYWALL_REMIND_WINDOW_MS = 4 * DAY_MS;   // views older than this are too cold
const PAYWALL_REMIND_COOLDOWN_MS = 7 * DAY_MS; // max one reminder per week
const PAYWALL_LOG_TTL_MS = 30 * DAY_MS;        // purge untouched view logs

exports.logPaywallView = onCall(
    { region: "us-central1" },
    async (request) => {
        if (!request.auth) {
            throw new HttpsError("unauthenticated", "Sign in required");
        }
        const raw = typeof request.data?.source === "string" ? request.data.source : "";
        const source = raw.replace(/[^a-z0-9_]/gi, "").slice(0, 40) || "unknown";
        await getFirestore("main").collection("paywallViews").doc(request.auth.uid).set(
            {
                count: FieldValue.increment(1),
                lastViewAt: FieldValue.serverTimestamp(),
                lastSource: source,
            },
            { merge: true }
        );
        return { ok: true };
    }
);

exports.paywallReminder = onSchedule(
    // 13:00 UTC = 6:30 PM IST / 5 PM Gulf — well before the ~10 PM IST dailyNudge.
    { schedule: "0 13 * * *", timeZone: "UTC", timeoutSeconds: 300 },
    async () => {
        const fs = getFirestore("main");
        const messaging = getMessaging();
        const now = Date.now();

        const snap = await fs.collection("paywallViews")
            .where("lastViewAt", "<=", Timestamp.fromMillis(now - PAYWALL_REMIND_AFTER_MS))
            .where("lastViewAt", ">=", Timestamp.fromMillis(now - PAYWALL_REMIND_WINDOW_MS))
            .limit(500)
            .get();

        let sent = 0;
        let skipped = 0;
        for (const doc of snap.docs) {
            const data = doc.data();
            const lastPushMs = data.lastPushAt ? data.lastPushAt.toMillis() : 0;
            if ((data.count || 0) < PAYWALL_MIN_VIEWS || now - lastPushMs < PAYWALL_REMIND_COOLDOWN_MS) {
                skipped++;
                continue;
            }
            // Subscribed since the last view — no reminder, drop the log.
            const sub = await fs.collection("subscriptions").doc(doc.id).get();
            if (sub.exists && sub.get("isPremium") === true) {
                await doc.ref.delete();
                skipped++;
                continue;
            }
            try {
                await messaging.send({
                    topic: `user_${doc.id}`,
                    notification: {
                        title: "Random Malayali Premium ✨",
                        body: "Send photos & voice notes, see live typing and chat without ads.",
                    },
                    android: {
                        priority: "normal",
                        collapseKey: "paywall_reminder",
                        notification: { channelId: "activity" },
                    },
                    data: {
                        src: "push",
                        type: "paywall_reminder", // app opens the paywall on tap
                    },
                });
                await doc.ref.update({ count: 0, lastPushAt: FieldValue.serverTimestamp() });
                sent++;
            } catch (e) {
                console.warn(`paywallReminder send failed uid=${doc.id}:`, e.message);
            }
        }

        // Data minimisation — drop view logs nobody touched for 30 days.
        const stale = await fs.collection("paywallViews")
            .where("lastViewAt", "<", Timestamp.fromMillis(now - PAYWALL_LOG_TTL_MS))
            .limit(500)
            .get();
        if (!stale.empty) {
            const batch = fs.batch();
            stale.docs.forEach((d) => batch.delete(d.ref));
            await batch.commit();
        }

        console.log(`paywallReminder: sent=${sent} skipped=${skipped} purged=${stale.size}`);
    }
);

// ─────────────────────────────────────────────────────────────────────────────
// Admin moderation index — chat-level aggregates
//
// The admin app never reads RTDB messages just to know a message count or
// whether a chat has media, and never reads `reports` directly (that
// collection is create-only for clients — see firestore.rules). These two
// triggers keep adminChatIndex/{roomId} in sync with the data that already
// exists; matchOnQueue above creates the doc, these only update it.
// ─────────────────────────────────────────────────────────────────────────────

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
        // New activity makes the chat unread for admins — except an admin's own intervention messages.
        if (msg.adminSender !== true) update.adminUnread = true;
        try {
            const fs = getFirestore("main");
            // merge:true — defensive only; matchOnQueue always creates the doc
            // first, but this survives a message on a room from before deploy.
            await fs.collection("adminChatIndex").doc(roomId).set(update, { merge: true });
            // Feed the admin Photos gallery (adminPhotos). Keyed by message id, so
            // this and the backfill can't create duplicates.
            if (type === "IMAGE" && msg.mediaUrl) {
                await writeAdminPhoto(fs, messageId, roomId, msg);
            }
        } catch (e) {
            console.warn(`onChatMessageCreated index update failed room=${roomId}:`, e.message);
        }
    }
);

/**
 * One photo in the admin Photos gallery. Gender/premium are copied from the
 * chat's index doc (best-effort) so the gallery can group and label by user
 * without reading the room. adminPhotos is server-written only.
 */
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

exports.onReportCreated = onDocumentCreated(
    // database: "main" is required here specifically — a Firestore trigger
    // (unlike a plain getFirestore() call) validates its target database
    // exists at DEPLOY time. Omitting this defaults to "(default)", which
    // doesn't exist for this project (its only database is "main") and is
    // exactly what threw "database '(default)' does not exist" on deploy.
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

// ─────────────────────────────────────────────────────────────────────────────
// Admin authentication — deliberately simple, on request: one static
// username/password pair, stored in Firestore, no Firebase Auth provider,
// no roles/tiers, no custom claims.
//
// The admin app signs in ANONYMOUSLY (same mechanism the consumer app
// already uses — no extra sign-in provider to enable), then calls
// adminLogin with a typed username/password. The very first successful
// call — whatever username/password is typed — becomes the permanent
// credential (adminCredentials/main); every call after that must match it
// exactly. There is only ever ONE credential document: this is a shared
// static login, not a multi-account system.
//
// A match marks the caller's anonymous uid as a logged-in session
// (adminSessions/{uid}); every other admin callable below checks that
// session doc exists instead of checking a role. Firestore rules block
// clients from ever reading adminCredentials or adminSessions directly —
// the password (hashed, not plaintext) and the session list are only ever
// touched server-side.
//
// To change the password later: edit adminCredentials/main directly in
// the Firestore console (passwordHash = sha256 hex of the new password).
// ─────────────────────────────────────────────────────────────────────────────
exports.adminLogin = onCall({ region: "us-central1" }, async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
    const { username, password } = request.data || {};
    if (!username || !password) {
        throw new HttpsError("invalid-argument", "username and password required");
    }

    const fs = getFirestore("main");
    const credRef = fs.collection("adminCredentials").doc("main");
    const credSnap = await credRef.get();
    const passwordHash = createHash("sha256").update(password).digest("hex");

    if (!credSnap.exists) {
        // Bootstrap — only when NO credential exists yet, ever. Whoever gets
        // here first sets the permanent login for everyone after them.
        await credRef.set({ username, passwordHash, createdAt: FieldValue.serverTimestamp() });
    } else if (credSnap.get("username") !== username || credSnap.get("passwordHash") !== passwordHash) {
        throw new HttpsError("permission-denied", "Wrong username or password");
    }

    await fs.collection("adminSessions").doc(request.auth.uid).set({
        username,
        loggedInAt: FieldValue.serverTimestamp(),
    });
    // RTDB rules can't read Firestore, so the session is mirrored here. This is
    // what lets the admin app read rooms/archivedRooms/reportedRooms in real time
    // (see firebase-rtdb-rules.json). Clients can never write this path.
    await getDatabase().ref(`adminSessions/${request.auth.uid}`).set({ loggedInAt: Date.now() });
    console.log(`adminLogin: uid=${request.auth.uid} username=${username}`);
    return { ok: true, username };
});

async function requireAdminSession(request) {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
    const snap = await getFirestore("main").collection("adminSessions").doc(request.auth.uid).get();
    if (!snap.exists) throw new HttpsError("permission-denied", "Admin sign-in required");
    return { uid: request.auth.uid };
}

/**
 * Dashboard counts (spec section 4) via Firestore count() aggregation
 * queries — server-side, never downloads the underlying documents.
 */
exports.getAdminDashboardCounts = onCall({ region: "us-central1" }, async (request) => {
    await requireAdminSession(request);
    const col = getFirestore("main").collection("adminChatIndex");
    const countOf = async (query) => (await query.count().get()).data().count;

    const [active, archived, reported, withPhoto, withAudio, unread, favorites] = await Promise.all([
        countOf(col.where("status", "==", "ACTIVE")),
        countOf(col.where("status", "==", "ARCHIVED")),
        countOf(col.where("reported", "==", true)),
        countOf(col.where("hasPhoto", "==", true)),
        countOf(col.where("hasAudio", "==", true)),
        countOf(col.where("adminUnread", "==", true)),
        countOf(col.where("favorite", "==", true)),
    ]);

    // Premium/non-premium involvement — Firestore can't query inside a map's
    // values, so this scans active chats (the small, bounded set) rather
    // than the whole index. Revisit with a denormalized boolean flag if the
    // index grows large enough for this scan to matter.
    const activeSnap = await col.where("status", "==", "ACTIVE").get();
    let premiumUsers = 0;
    let nonPremiumUsers = 0;
    activeSnap.forEach((doc) => {
        const premium = doc.get("premium") || {};
        Object.values(premium).forEach((isPremium) => {
            if (isPremium) premiumUsers++; else nonPremiumUsers++;
        });
    });

    return {
        activeChats: active,
        archivedChats: archived,
        reportedChats: reported,
        chatsWithPhoto: withPhoto,
        chatsWithAudio: withAudio,
        unreadChats: unread,
        favoriteChats: favorites,
        premiumUsers,
        nonPremiumUsers,
    };
});

/**
 * Cursor-paginated chat list for the admin inbox (spec sections 5, 8, 31).
 * `tab`: "ACTIVE" | "ARCHIVED" | "REPORTED" — REPORTED reads the `reported`
 * flag regardless of lifecycle status, so an archived-and-reported chat
 * surfaces in both tabs. Sorted latest-first — the only order implemented in
 * this pass; gender/premium/media filters and other sorts are a later phase,
 * additive on top of fields this index already carries.
 */
exports.listAdminChats = onCall({ region: "us-central1" }, async (request) => {
    await requireAdminSession(request);
    const { tab, pageSize, cursorMs } = request.data || {};
    const limit = Math.min(Math.max(Number(pageSize) || 30, 1), 100);

    const col = getFirestore("main").collection("adminChatIndex");
    const baseQuery = tab === "REPORTED"
        ? col.where("reported", "==", true)
        : col.where("status", "==", tab === "ARCHIVED" ? "ARCHIVED" : "ACTIVE");

    let docs;
    try {
        let query = baseQuery.orderBy("lastMessageAt", "desc").limit(limit);
        if (cursorMs) query = query.startAfter(Timestamp.fromMillis(Number(cursorMs)));
        docs = (await query.get()).docs;
    } catch (e) {
        if (e.code !== FIRESTORE_FAILED_PRECONDITION) {
            console.error("listAdminChats failed:", e.message);
            throw new HttpsError("internal", e.message);
        }
        // Composite index missing or still building (takes minutes after
        // `firebase deploy`). Fall back to the equality filter alone — served by
        // Firestore's automatic single-field index — and sort in memory. Capped so
        // it can never read the whole collection.
        console.warn("listAdminChats: composite index not ready, using fallback");
        const scanned = (await baseQuery.limit(FALLBACK_SCAN_LIMIT).get()).docs;
        scanned.sort((a, b) => (toMs(b.get("lastMessageAt")) ?? 0) - (toMs(a.get("lastMessageAt")) ?? 0));
        const afterCursor = cursorMs
            ? scanned.filter((d) => (toMs(d.get("lastMessageAt")) ?? 0) < Number(cursorMs))
            : scanned;
        docs = afterCursor.slice(0, limit);
    }

    const chats = docs.map((doc) => {
        const data = doc.data();
        return {
            roomId: doc.id,
            participants: data.participants ?? [],
            genders: data.genders ?? {},
            premium: data.premium ?? {},
            status: data.status ?? "ACTIVE",
            messageCount: data.messageCount ?? 0,
            hasPhoto: !!data.hasPhoto,
            hasAudio: !!data.hasAudio,
            reported: !!data.reported,
            reportCount: data.reportCount ?? 0,
            lastMessagePreview: data.lastMessagePreview ?? "",
            lastMessageAtMs: toMs(data.lastMessageAt),
            createdAtMs: toMs(data.createdAt),
            archivedAtMs: toMs(data.archivedAt),
        };
    });
    const lastDoc = docs[docs.length - 1];
    // Only hand out a cursor when the page was full — saves the client an empty round trip.
    return { chats, nextCursorMs: docs.length === limit && lastDoc ? toMs(lastDoc.get("lastMessageAt")) : null };
});

const FIRESTORE_FAILED_PRECONDITION = 9;
const FALLBACK_SCAN_LIMIT = 500;
const MAX_ADMIN_MESSAGES = 500;
const BACKFILL_PAGE = 200;
const BACKFILL_TIME_BUDGET_MS = 420 * 1000;
const BACKFILL_BRANCHES = ["archivedRooms", "reportedRooms", "rooms"];

/** Firestore Timestamp or epoch-ms number to epoch ms; null when absent. */
function toMs(value) {
    if (value == null) return null;
    if (typeof value === "number") return value;
    if (typeof value.toMillis === "function") return value.toMillis();
    return null;
}

function previewOf(message) {
    if (!message) return "";
    const type = message.type || "TEXT";
    return type === "TEXT" ? String(message.content || "").slice(0, 80) : `[${type.toLowerCase()}]`;
}

function participantsOf(room) {
    const raw = Array.isArray(room.participants) ? room.participants : Object.values(room.participants || {});
    return raw.filter((uid) => typeof uid === "string" && uid);
}

/**
 * Message history for one chat, for the admin chat viewer. Read with the Admin
 * SDK because RTDB rules only let a room's two participants read it. Live
 * chats come from rooms/{roomId}; ended ones from archivedRooms/reportedRooms.
 * cleanupRooms deletes rooms/{roomId} after a chat ends and only archives chats
 * with 20+ messages, so short ended chats return no messages.
 */
exports.getAdminChatMessages = onCall({ region: "us-central1" }, async (request) => {
    await requireAdminSession(request);
    const roomId = request.data?.roomId;
    if (typeof roomId !== "string" || !roomId || roomId.includes("/")) {
        throw new HttpsError("invalid-argument", "roomId required");
    }
    const db = getDatabase();
    for (const branch of ["rooms", "archivedRooms", "reportedRooms"]) {
        const snap = await db.ref(`${branch}/${roomId}/messages`).once("value");
        if (!snap.exists()) continue;
        const messages = Object.values(snap.val() || {})
            .map((m) => ({
                id: String(m?.id || ""),
                senderId: String(m?.senderId || ""),
                content: String(m?.content || ""),
                mediaUrl: String(m?.mediaUrl || ""),
                type: String(m?.type || "TEXT"),
                timestampMs: typeof m?.timestamp === "number" ? m.timestamp : 0,
                adminSender: m?.adminSender === true,
                replyToPreview: String(m?.replyToPreview || ""),
                replyToSenderId: String(m?.replyToSenderId || ""),
                replyToType: String(m?.replyToType || "TEXT"),
            }))
            .sort((a, b) => a.timestampMs - b.timestampMs)
            .slice(-MAX_ADMIN_MESSAGES);
        return { source: branch, messages };
    }
    return { source: null, messages: [] };
});

/**
 * Imports chats that existed before adminChatIndex did — matchOnQueue only
 * indexes chats created after deploy. Resumable: stops before the function
 * timeout and returns { done: false }; the admin app calls again until done.
 * Idempotent: docs the triggers already maintain keep their real genders and
 * live counts. Genders were never stored for old chats, so those stay
 * "UNSPECIFIED".
 */
exports.backfillAdminChatIndex = onCall(
    { region: "us-central1", timeoutSeconds: 540, memory: "1GiB" },
    async (request) => {
        await requireAdminSession(request);
        const startedAt = Date.now();
        const db = getDatabase();
        const fs = getFirestore("main");
        const col = fs.collection("adminChatIndex");
        const photosCol = fs.collection("adminPhotos");
        const progressRef = fs.collection("adminMeta").doc("backfill");

        const saved = await progressRef.get();
        const progress = saved.exists && saved.get("done") === false
            ? { branchIndex: saved.get("branchIndex") || 0, lastKey: saved.get("lastKey") || null, imported: saved.get("imported") || 0 }
            : { branchIndex: 0, lastKey: null, imported: 0 };
        const saveProgress = (done) =>
            progressRef.set({ ...progress, done, updatedAt: FieldValue.serverTimestamp() });

        const premiumCache = new Map();
        const loadPremium = async (uids) => {
            const missing = [...new Set(uids)].filter((uid) => !premiumCache.has(uid));
            if (missing.length === 0) return;
            const snaps = await fs.getAll(...missing.map((uid) => fs.collection("subscriptions").doc(uid)));
            snaps.forEach((s) => { premiumCache.set(s.id, s.exists && s.get("isPremium") === true); });
        };

        const indexRooms = async (branch, entries) => {
            const existing = await fs.getAll(...entries.map(([roomId]) => col.doc(roomId)));
            // A doc counts as complete only if matchOnQueue created it; onChatMessageCreated
            // can leave partial docs (no participants/status) for pre-deploy rooms.
            const complete = new Set(existing.filter((s) => s.exists && Array.isArray(s.get("participants"))).map((s) => s.id));
            // Docs created before the read/unread flag existed: default them to unread.
            const missingUnread = new Set(existing.filter((s) => s.exists && s.get("adminUnread") === undefined).map((s) => s.id));
            await loadPremium(entries.flatMap(([, room]) => participantsOf(room)));

            const batch = fs.batch();
            // Photos from pre-deploy rooms, for the Photos gallery. Complete rooms are
            // post-deploy, so onChatMessageCreated already wrote their photos live — skip
            // them here to avoid rewriting. Committed in their own chunked batches below.
            const photoDocs = [];
            for (const [roomId, room] of entries) {
                const ref = col.doc(roomId);
                const branchFields = {};
                if (branch === "archivedRooms") {
                    branchFields.status = "ARCHIVED";
                    branchFields.archivedAt = Timestamp.fromMillis(room.archivedAt || room.endedAt || room.createdAt || Date.now());
                    branchFields.archiveReason = room.archiveReason || "ended";
                }
                if (branch === "reportedRooms") branchFields.reported = true;

                const messages = Object.values(room.messages || {});
                const last = messages.reduce((acc, m) => ((m?.timestamp || 0) >= (acc?.timestamp || 0) ? m : acc), null);
                const lastMs = last?.timestamp || room.endedAt || room.createdAt || room.archivedAt || Date.now();

                // Photos for the gallery — for EVERY room, complete or not. Photos in chats
                // already in the index were never written (onChatMessageCreated only fires for
                // new messages), so this is the one place they get backfilled. Idempotent:
                // keyed by message id, merge:true.
                for (const [msgKey, m] of Object.entries(room.messages || {})) {
                    if (m?.type === "IMAGE" && m?.mediaUrl) {
                        const senderId = String(m.senderId || "");
                        const indexed = existing.find((s) => s.id === roomId);
                        const gender = (indexed?.exists && (indexed.get("genders") || {})[senderId]) || "UNSPECIFIED";
                        photoDocs.push({
                            id: msgKey,
                            data: {
                                url: String(m.mediaUrl),
                                senderId,
                                roomId,
                                gender,
                                premium: premiumCache.get(senderId) === true,
                                createdAt: Timestamp.fromMillis(m.timestamp || lastMs),
                            },
                        });
                    }
                }

                if (complete.has(roomId)) {
                    const fields = branch !== "rooms" ? { ...branchFields } : {};
                    if (missingUnread.has(roomId)) fields.adminUnread = true;
                    if (Object.keys(fields).length > 0) batch.set(ref, fields, { merge: true });
                    continue;
                }
                const participants = participantsOf(room);
                batch.set(ref, {
                    roomId,
                    participants,
                    genders: Object.fromEntries(participants.map((uid) => [uid, "UNSPECIFIED"])),
                    premium: Object.fromEntries(participants.map((uid) => [uid, premiumCache.get(uid) === true])),
                    status: branch === "rooms" && room.status === "ACTIVE" ? "ACTIVE" : "ARCHIVED",
                    createdAt: Timestamp.fromMillis(room.createdAt || lastMs),
                    lastMessageAt: Timestamp.fromMillis(lastMs),
                    lastMessagePreview: previewOf(last),
                    messageCount: messages.length,
                    hasPhoto: messages.some((m) => m?.type === "IMAGE"),
                    hasAudio: messages.some((m) => m?.type === "AUDIO"),
                    reported: false,
                    reportCount: 0,
                    adminUnread: true,
                    favorite: false,
                    backfilled: true,
                    ...branchFields,
                }, { merge: true });
            }
            await batch.commit();
            // Separate chunked batches: one room can hold many photos, so folding these
            // into the room batch could exceed the 500-write limit.
            for (let i = 0; i < photoDocs.length; i += 400) {
                const photoBatch = fs.batch();
                photoDocs.slice(i, i + 400).forEach((p) => photoBatch.set(photosCol.doc(p.id), p.data, { merge: true }));
                await photoBatch.commit();
            }
        };

        while (progress.branchIndex < BACKFILL_BRANCHES.length) {
            if (Date.now() - startedAt > BACKFILL_TIME_BUDGET_MS) {
                await saveProgress(false);
                return { done: false, imported: progress.imported };
            }
            const branch = BACKFILL_BRANCHES[progress.branchIndex];
            const query = progress.lastKey
                ? db.ref(branch).orderByKey().startAt(progress.lastKey).limitToFirst(BACKFILL_PAGE + 1)
                : db.ref(branch).orderByKey().limitToFirst(BACKFILL_PAGE);
            const snap = await query.once("value");
            const entries = [];
            snap.forEach((child) => {
                if (child.key !== progress.lastKey) entries.push([child.key, child.val() || {}]);
            });
            if (entries.length === 0) {
                progress.branchIndex++;
                progress.lastKey = null;
                continue;
            }
            await indexRooms(branch, entries);
            progress.imported += entries.length;
            progress.lastKey = entries[entries.length - 1][0];
            await saveProgress(false);
        }

        // Reports — the authoritative count per chat, including chats whose RTDB
        // data is already gone.
        const reportsSnap = await fs.collection("reports").select("roomId", "reporter", "reported", "timestamp").get();
        const byRoom = new Map();
        reportsSnap.forEach((doc) => {
            const roomId = doc.get("roomId");
            if (typeof roomId !== "string" || !roomId) return;
            const entry = byRoom.get(roomId) || { count: 0, reporter: doc.get("reporter"), reported: doc.get("reported"), lastMs: 0 };
            entry.count++;
            entry.lastMs = Math.max(entry.lastMs, Number(doc.get("timestamp")) || 0);
            byRoom.set(roomId, entry);
        });
        const reportedIds = [...byRoom.keys()];
        for (let i = 0; i < reportedIds.length; i += BACKFILL_PAGE) {
            const chunk = reportedIds.slice(i, i + BACKFILL_PAGE);
            const snaps = await fs.getAll(...chunk.map((roomId) => col.doc(roomId)));
            const batch = fs.batch();
            snaps.forEach((s) => {
                const report = byRoom.get(s.id);
                if (s.exists) {
                    batch.set(s.ref, { reported: true, reportCount: report.count }, { merge: true });
                    return;
                }
                const lastMs = report.lastMs || Date.now();
                const participants = [report.reporter, report.reported].filter((uid) => typeof uid === "string" && uid);
                batch.set(s.ref, {
                    roomId: s.id,
                    participants,
                    genders: Object.fromEntries(participants.map((uid) => [uid, "UNSPECIFIED"])),
                    premium: {},
                    status: "ARCHIVED",
                    createdAt: Timestamp.fromMillis(lastMs),
                    lastMessageAt: Timestamp.fromMillis(lastMs),
                    lastMessagePreview: "",
                    messageCount: 0,
                    hasPhoto: false,
                    hasAudio: false,
                    reported: true,
                    reportCount: report.count,
                    adminUnread: true,
                    favorite: false,
                    backfilled: true,
                });
            });
            await batch.commit();
        }

        progress.branchIndex = 0;
        progress.lastKey = null;
        await saveProgress(true);
        console.log(`backfillAdminChatIndex done: imported=${progress.imported} reportedChats=${reportedIds.length}`);
        return { done: true, imported: progress.imported };
    }
);

// ─────────────────────────────────────────────────────────────────────────────
// Admin intervention — an admin joins a live chat and sends messages AS one of
// the two participants (confirmed product decision, see
// info/ADMIN_ARCHITECTURE.md). The admin picks who to send as: a message "as A"
// is written with A's own uid, so the OTHER participant (B) sees it as a normal
// message from their stranger. The consumer app can't hide a message from one
// side (both read the same rooms/{id}/messages list), so A also sees an "as A"
// message as their own — this is a known, accepted limit of not changing the
// consumer app. Each message carries adminSender:true, a field the consumer app
// ignores; the triggers and admin app use it to tell admin messages apart.
// Every start, message and end is recorded in adminInterventions/{id} —
// written only here, never readable or writable by any client.
// ─────────────────────────────────────────────────────────────────────────────
const MAX_INTERVENTION_TEXT = 500;

async function requireActiveIntervention(request) {
    const { uid } = await requireAdminSession(request);
    const interventionId = request.data?.interventionId;
    if (typeof interventionId !== "string" || !interventionId || interventionId.includes("/")) {
        throw new HttpsError("invalid-argument", "interventionId required");
    }
    const ref = getFirestore("main").collection("adminInterventions").doc(interventionId);
    const snap = await ref.get();
    if (!snap.exists || snap.get("adminUid") !== uid) {
        throw new HttpsError("not-found", "Intervention not found");
    }
    if (snap.get("active") !== true) {
        throw new HttpsError("failed-precondition", "You already left this chat");
    }
    return { ref, snap };
}

exports.startIntervention = onCall({ region: "us-central1" }, async (request) => {
    const { uid } = await requireAdminSession(request);
    const roomId = request.data?.roomId;
    if (typeof roomId !== "string" || !roomId || roomId.includes("/")) {
        throw new HttpsError("invalid-argument", "roomId required");
    }
    const roomSnap = await getDatabase().ref(`rooms/${roomId}`).once("value");
    if (roomSnap.child("status").val() !== "ACTIVE") {
        throw new HttpsError("failed-precondition", "This chat is not live anymore");
    }
    const participants = participantsOf(roomSnap.val() || {});
    if (participants.length < 2) {
        throw new HttpsError("failed-precondition", "This chat has no two participants to send as");
    }
    const ref = getFirestore("main").collection("adminInterventions").doc();
    await ref.set({
        roomId,
        adminUid: uid,
        participants,
        active: true,
        messageCount: 0,
        startedAt: FieldValue.serverTimestamp(),
    });
    console.log(`startIntervention: id=${ref.id} room=${roomId} admin=${uid}`);
    // participants is [A, B] in room order — the admin app labels them A and B.
    return { interventionId: ref.id, participants };
});

exports.sendInterventionMessage = onCall({ region: "us-central1" }, async (request) => {
    const { ref, snap } = await requireActiveIntervention(request);
    const text = typeof request.data?.text === "string" ? request.data.text.trim() : "";
    if (!text) throw new HttpsError("invalid-argument", "Message is empty");
    if (text.length > MAX_INTERVENTION_TEXT) throw new HttpsError("invalid-argument", "Message is too long");

    // Which participant to send as. Must be one of this room's two participants,
    // so the admin can never inject an arbitrary sender id.
    const sendAsUid = request.data?.sendAsUid;
    const participants = snap.get("participants") || [];
    if (typeof sendAsUid !== "string" || !participants.includes(sendAsUid)) {
        throw new HttpsError("invalid-argument", "Choose which participant to send as");
    }

    const roomId = snap.get("roomId");
    const db = getDatabase();
    const status = (await db.ref(`rooms/${roomId}/status`).once("value")).val();
    if (status !== "ACTIVE") {
        await ref.update({ active: false, endedAt: FieldValue.serverTimestamp(), endReason: "chat_ended" });
        throw new HttpsError("failed-precondition", "This chat has ended");
    }

    const msgRef = db.ref(`rooms/${roomId}/messages`).push();
    // Same shape the consumer app writes (RealtimeDbManager.sendMessage), so the
    // OTHER participant renders it as a normal message from their stranger.
    // adminSender is an extra field the consumer app never reads.
    await msgRef.set({
        id: msgRef.key,
        senderId: sendAsUid,
        content: text,
        mediaUrl: "",
        type: "TEXT",
        timestamp: { ".sv": "timestamp" },
        adminSender: true,
    });
    await Promise.all([
        ref.collection("messages").doc(msgRef.key).set({
            text,
            actorType: "ADMIN",
            sentAsUid,
            sentAt: FieldValue.serverTimestamp(),
        }),
        ref.update({ messageCount: FieldValue.increment(1), lastMessageAt: FieldValue.serverTimestamp() }),
    ]);
    return { messageId: msgRef.key };
});

exports.endIntervention = onCall({ region: "us-central1" }, async (request) => {
    const { ref } = await requireActiveIntervention(request);
    await ref.update({ active: false, endedAt: FieldValue.serverTimestamp(), endReason: "admin_left" });
    console.log(`endIntervention: id=${ref.id}`);
    return { ok: true };
});
