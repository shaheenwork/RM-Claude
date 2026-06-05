const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onValueCreated } = require("firebase-functions/v2/database");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { GoogleAuth } = require("google-auth-library");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getStorage } = require("firebase-admin/storage");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

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

        const fs = getFirestore();

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
        const fs = getFirestore();
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
exports.dailyNudge = onSchedule(
    { schedule: "0-29 16 * * *", timeZone: "UTC" },
    async () => {
        const now = new Date();
        // Deterministic "random" target minute (0-29) seeded by the UTC date.
        const dateStr = now.toISOString().slice(0, 10);
        let hash = 0;
        for (const ch of dateStr) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
        const targetMinute = hash % 30;
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
