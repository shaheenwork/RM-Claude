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

const PACKAGE_NAME = "com.randomchat.shnapp";
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
// Daily "people online" nudge
//
// Picks ONE random minute per day (at 00:00 UTC) — same minute applied per-TZ in
// each user's local clock-time. So everyone in IST gets pinged at e.g. 20:47 IST,
// everyone in PST at 20:47 PST, etc. Different real-world UTC moments, identical
// local-time experience.
//
// Window per day-of-week (user-local):
//   • Mon–Sat: 20:00 – 23:00
//   • Sunday : 11:00 – 21:00
//
// Cap: 1 push per local day. Tracked via Firestore lock doc per (tzOffset, localDate).
// ─────────────────────────────────────────────────────────────────────────────

// Common IANA timezone offsets in MINUTES (covers ~all populated areas).
// Topic naming: `tz_p330` for +330 (IST), `tz_n300` for -300 (EST).
const TZ_OFFSETS_MIN = [
    -720, -660, -600, -570, -540, -480, -420, -360, -300, -240, -210, -180, -150, -120, -60,
    0,
    60, 120, 180, 210, 240, 270, 300, 330, 345, 360, 390, 420, 480, 525, 540, 570, 600, 630, 660, 720, 765, 780, 825, 840,
];

function tzTopic(offsetMin) {
    const sign = offsetMin >= 0 ? "p" : "n";
    return `tz_${sign}${Math.abs(offsetMin)}`;
}

/** Random integer minute-of-day in [startHour:00, endHour:00). e.g. pickMinute(20, 23) → 20:00–22:59. */
function pickMinute(startHour, endHour) {
    const totalMin = (endHour - startHour) * 60;
    const r = Math.floor(Math.random() * totalMin);
    const h = startHour + Math.floor(r / 60);
    const m = r % 60;
    return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

/** Parse "HH:MM" → minute of day. */
function hmToMin(hm) {
    const [h, m] = hm.split(":").map(Number);
    return h * 60 + m;
}

/** Returns YYYY-MM-DD for a given Date instance (interpreted as already shifted). */
function dateKey(d) {
    return d.toISOString().slice(0, 10);
}

/**
 * Runs daily at 00:00 UTC. Picks the random minute for both weekday and Sunday windows.
 * Stored at Firestore notifSchedule/{YYYY-MM-DD-UTC}.
 *
 * Note: we key by UTC date for simplicity. Each TZ bucket reads the schedule for its
 * current UTC-date when dispatching — works because the nudge windows (20:00 local
 * weekday, 11:00–21:00 local Sunday) are well inside any single UTC day for typical
 * offsets, and the schedule changes only once per UTC day.
 */
exports.pickDailyNotifTime = onSchedule(
    { schedule: "0 0 * * *", timeZone: "UTC" },
    async () => {
        const fs = getFirestore();
        // Write next 7 days. A positive-offset user (e.g. UTC+12) can hit their local
        // Sunday-window before that UTC date "begins" — pre-writing prevents the lookup
        // from missing. set() with merge:false writes idempotently within a single run;
        // for repeated daily runs we use create-if-missing semantics by reading first.
        const writes = [];
        for (let i = 0; i < 7; i++) {
            const d = new Date(Date.now() + i * 86_400_000);
            const key = dateKey(d);
            const ref = fs.doc(`notifSchedule/${key}`);
            const snap = await ref.get();
            if (snap.exists) continue;
            const schedule = {
                weekdayMinute: pickMinute(20, 23), // 20:00–22:59
                sundayMinute:  pickMinute(11, 21), // 11:00–20:59
                pickedAt:      FieldValue.serverTimestamp(),
            };
            writes.push(ref.set(schedule).then(() => console.log(`Scheduled ${key}:`, schedule)));
        }
        await Promise.all(writes);
        console.log(`pickDailyNotifTime done: wrote ${writes.length} new days`);
    }
);

/**
 * Runs every 5 minutes. For each TZ bucket: if the user-local clock currently sits
 * within the day's chosen send window AND no push has fired for that bucket today,
 * publishes one FCM message to the bucket's topic.
 */
exports.dispatchDailyNudge = onSchedule(
    { schedule: "*/5 * * * *", timeZone: "UTC" },
    async () => {
        const fs       = getFirestore();
        const rtdb     = getDatabase();
        const messaging = getMessaging();
        const nowMs    = Date.now();

        // Cached online count (computed lazily — only if we end up sending).
        let cachedOnline = null;
        const onlineCount = async () => {
            if (cachedOnline !== null) return cachedOnline;
            const snap = await rtdb.ref("waitingQueue").once("value");
            cachedOnline = snap.numChildren();
            return cachedOnline;
        };

        let sentCount = 0;
        // Tiny in-invocation cache: most TZ buckets share a localDate, only ~3 distinct
        // dates are seen across all offsets (yesterday/today/tomorrow UTC).
        const scheduleCache = new Map();
        const getSchedule = async (localDate) => {
            if (scheduleCache.has(localDate)) return scheduleCache.get(localDate);
            const snap = await fs.doc(`notifSchedule/${localDate}`).get();
            const data = snap.exists ? snap.data() : null;
            scheduleCache.set(localDate, data);
            return data;
        };
        for (const offsetMin of TZ_OFFSETS_MIN) {
            const localMs   = nowMs + offsetMin * 60_000;
            const localD    = new Date(localMs);
            const localHour = localD.getUTCHours();
            const localMin  = localD.getUTCMinutes();
            const localDow  = localD.getUTCDay(); // 0 = Sun, 1 = Mon …
            const localDate = dateKey(localD);

            // Pull the schedule for the user's current LOCAL date — that's the day whose
            // window applies. (Local date may differ from utcToday for far offsets.)
            const dailySchedule = await getSchedule(localDate);
            if (!dailySchedule) continue;
            const { weekdayMinute, sundayMinute } = dailySchedule;

            const targetHM  = localDow === 0 ? sundayMinute : weekdayMinute;
            const targetMin = hmToMin(targetHM);
            const curMin    = localHour * 60 + localMin;
            const delta     = curMin - targetMin;

            // Send window: [target, target+5min). Cron fires every 5 min, so each minute
            // is hit exactly once per local day, no risk of double-fire.
            if (delta < 0 || delta >= 5) continue;

            const lockId  = `${tzTopic(offsetMin)}_${localDate}`;
            const lockRef = fs.doc(`notifSent/${lockId}`);
            const lock    = await lockRef.get();
            if (lock.exists) continue;

            const count = await onlineCount();
            // Always inflate by +5 so the body never feels empty even on quiet hours.
            // Truthful-ish: there really are `count` waiting, plus the social-proof bump.
            const display = count + 5;
            const body    = `${display} strangers around to chat — say hi?`;

            try {
                await messaging.send({
                    topic: tzTopic(offsetMin),
                    notification: {
                        title: "🌙 Strcht",
                        body,
                    },
                    android: {
                        priority: "normal",
                        collapseKey: "daily_nudge",
                        notification: {
                            channelId: "activity",
                            clickAction: "OPEN_MATCHMAKING",
                        },
                    },
                    data: {
                        from: "push",
                        type: "daily_nudge",
                    },
                });
                await lockRef.set({
                    sentAt:      FieldValue.serverTimestamp(),
                    localDate,
                    onlineCount: count,
                    targetHM,
                });
                sentCount++;
                console.log(`Sent ${tzTopic(offsetMin)} @ ${targetHM} local (online=${count})`);
            } catch (e) {
                console.error(`Send failed ${tzTopic(offsetMin)}:`, e.message);
            }
        }

        console.log(`dispatchDailyNudge done: sent=${sentCount}`);
    }
);
