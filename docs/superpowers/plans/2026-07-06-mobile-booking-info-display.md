# Mobile Booking Information Display — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the ops-authored member-facing Booking Information note in the arcana-mobile app — a callout on the confirmed class-detail screen and a compact line on the Home "next up" tile — with zero regressions for existing users.

**Architecture:** Purely additive. The server exposes `member_note` on `BookingSerializer`; the mobile `BookingDto` gains a defaulted `memberNote` field; a single pure `bookingInfoOrNull(booking)` gate drives two gated UI surfaces. When the note is blank/absent, the UI is byte-identical to today.

**Tech Stack:** Django REST Framework (server); Kotlin Compose Multiplatform, kotlinx-serialization, kotlin-test (mobile).

**Spec:** `docs/superpowers/specs/2026-07-06-mobile-booking-info-display-design.md`

---

## ⚠️ Policy for this plan

- **Backwards compatibility is non-negotiable.** The shipped app has real users. Every change is additive and gated on a non-blank note. Never remove/rename an existing field or change an existing code path.
- **Commits:** per Cole's standing preference, do **not** commit or push during implementation. Implement + run tests per task, leaving the tree dirty. After all tasks pass and Cole has reviewed + verified on the emulator (Task 6), stop at the review gate. Commit/PR only on his explicit "go."
- **Two repos, two branches:**
  - Server (Task 1) → arcana-server branch **`ops-booking-info-and-evidence-edit`** (the branch that already adds `Booking.member_note`; the serializer belongs with it). Check it out first.
  - Mobile (Tasks 2–5) → a fresh arcana-mobile branch **`mobile-booking-info-display`** off its `main`.

---

## Task 0: Branch setup

**Files:** none

- [ ] **Step 1: Server branch (already has the model field)**

```bash
cd /Users/coletomlinson/Desktop/arcana/arcana-server
git checkout ops-booking-info-and-evidence-edit   # branch that adds Booking.member_note
git status --porcelain                             # expect the earlier uncommitted feature edits
```

- [ ] **Step 2: Mobile branch off latest main**

```bash
cd /Users/coletomlinson/Desktop/arcana/arcana-mobile
git checkout main && git pull origin main
git checkout -b mobile-booking-info-display
```

---

## Task 1: Server — expose `member_note` on `BookingSerializer`

**Files:**
- Modify: `arcana-server/bookings/serializers.py` (`BookingSerializer`)
- Test: `arcana-server/bookings/tests/test_views.py`

- [ ] **Step 1: Write the failing test**

Add to `bookings/tests/test_views.py` (mirrors `test_my_bookings_includes_spot_preference`, uses the `booking_member` fixture → `(client, user, session)`):

```python
@pytest.mark.django_db
def test_my_bookings_includes_member_note(booking_member):
    client, user, session = booking_member
    create = client.post('/api/v1/bookings/', {'session_id': session.id}, format='json')
    bid = create.data['booking_id']
    from bookings.models import Booking
    Booking.objects.filter(pk=bid).update(member_note='Door code 1234')
    resp = client.get('/api/v1/bookings/me/')
    assert resp.data['upcoming'][0]['member_note'] == 'Door code 1234'
    detail = client.get(f'/api/v1/bookings/{bid}/')
    assert detail.data['member_note'] == 'Door code 1234'
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/coletomlinson/Desktop/arcana/arcana-server && source .venv/bin/activate
pytest bookings/tests/test_views.py::test_my_bookings_includes_member_note -v
```
Expected: FAIL — `KeyError: 'member_note'` (field not serialized).

- [ ] **Step 3: Add the field**

In `bookings/serializers.py`, inside `BookingSerializer`, next to `spot_preference`:

```python
    spot_preference = serializers.CharField()
    # Member-facing note ops attaches at/after fulfillment (e.g. a door code).
    # Non-null TextField(blank=True) → serializes as "" when unset. Read-only.
    member_note = serializers.CharField()
    session = _SessionBriefSerializer(source='class_session')
```

- [ ] **Step 4: Run test to verify it passes**

```bash
pytest bookings/tests/test_views.py::test_my_bookings_includes_member_note -v
```
Expected: PASS

- [ ] **Step 5: Run the bookings suite (no regressions)**

```bash
pytest bookings/ -q
```
Expected: all pass.

---

## Task 2: Mobile — `BookingDto.memberNote` + `bookingInfoOrNull` gate

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/org/arcana/mobile/data/BookingDto.kt`
- Create: `composeApp/src/commonMain/kotlin/org/arcana/mobile/booking/BookingInfo.kt`
- Test: `composeApp/src/commonTest/kotlin/org/arcana/mobile/booking/BookingDtoTest.kt`
- Test: `composeApp/src/commonTest/kotlin/org/arcana/mobile/booking/BookingInfoTest.kt` (create)

- [ ] **Step 1: Write the failing tests**

Add to `composeApp/src/commonTest/kotlin/org/arcana/mobile/booking/BookingDtoTest.kt` (matches the file's existing `json`/style):

```kotlin
    @Test
    fun `parses a booking with a member note`() {
        val raw = """
          {"id":20,"status":"confirmed",
           "member_note":"Door code 1234",
           "session":{"id":482,"start_at":"2026-07-07T10:00:00Z","end_at":"2026-07-07T10:50:00Z","name":"RUN x LIFT","studio":"Barry's"},
           "cancel_policy":{"will_forfeit_credit":false}}
        """.trimIndent()
        val b = json.decodeFromString(BookingDto.serializer(), raw)
        assertEquals("Door code 1234", b.memberNote)
    }

    @Test
    fun `booking without a member note defaults to null`() {
        val raw = """
          {"id":21,"status":"confirmed",
           "session":{"id":482,"start_at":"2026-07-07T10:00:00Z","end_at":"2026-07-07T10:50:00Z","name":"RUN x LIFT","studio":"Barry's"},
           "cancel_policy":{"will_forfeit_credit":false}}
        """.trimIndent()
        val b = json.decodeFromString(BookingDto.serializer(), raw)
        assertNull(b.memberNote)
    }
```

Create `composeApp/src/commonTest/kotlin/org/arcana/mobile/booking/BookingInfoTest.kt`:

```kotlin
package org.arcana.mobile.booking

import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.SessionBriefDto
import org.arcana.mobile.data.CancelPolicyDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookingInfoTest {
    private fun booking(note: String?) = BookingDto(
        id = 1, status = "confirmed",
        session = SessionBriefDto(
            id = 1, startAt = "2026-07-07T10:00:00Z", endAt = "2026-07-07T10:50:00Z",
            name = "RUN x LIFT", studio = "Barry's",
        ),
        cancelPolicy = CancelPolicyDto(willForfeitCredit = false),
        memberNote = note,
    )

    @Test fun `returns trimmed note when present`() {
        assertEquals("Door code 1234", bookingInfoOrNull(booking("  Door code 1234  ")))
    }

    @Test fun `null when null, blank, or whitespace`() {
        assertNull(bookingInfoOrNull(booking(null)))
        assertNull(bookingInfoOrNull(booking("")))
        assertNull(bookingInfoOrNull(booking("   ")))
        assertNull(bookingInfoOrNull(null))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/coletomlinson/Desktop/arcana/arcana-mobile
./gradlew :composeApp:testDebugUnitTest --tests "org.arcana.mobile.booking.BookingDtoTest" --tests "org.arcana.mobile.booking.BookingInfoTest"
```
Expected: FAIL — `memberNote` unresolved / `bookingInfoOrNull` unresolved.

- [ ] **Step 3: Add the DTO field**

In `data/BookingDto.kt`, inside `data class BookingDto`, after the `spotPreference` line:

```kotlin
    @SerialName("spot_preference") val spotPreference: String? = null,
    // Member-facing note ops attaches (e.g. a door code). Null/absent when none.
    // Defaulted so old server responses (no field) still deserialize.
    @SerialName("member_note") val memberNote: String? = null,
```

- [ ] **Step 4: Add the gate helper**

Create `composeApp/src/commonMain/kotlin/org/arcana/mobile/booking/BookingInfo.kt`:

```kotlin
package org.arcana.mobile.booking

import org.arcana.mobile.data.BookingDto

/** The member-facing booking note, or null when there is nothing to show.
 *  The single gate every Booking-info UI surface uses. */
fun bookingInfoOrNull(booking: BookingDto?): String? =
    booking?.memberNote?.trim()?.takeIf { it.isNotEmpty() }
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "org.arcana.mobile.booking.BookingDtoTest" --tests "org.arcana.mobile.booking.BookingInfoTest"
```
Expected: PASS

---

## Task 3: Mobile — class-detail callout

**Files:**
- Create: add `BookingInfoCallout` composable to `composeApp/src/commonMain/kotlin/org/arcana/mobile/booking/BookingInfo.kt`
- Modify: `composeApp/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt`

- [ ] **Step 1: Add the callout composable**

Append to `booking/BookingInfo.kt`:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.SectionRule

/** Class-detail "Booking info" section. Caller gates on [bookingInfoOrNull]. */
@Composable
fun BookingInfoCallout(note: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionRule(label = "Booking info")
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Paper)
                .padding(16.dp),
        ) {
            BodyText(text = note, size = 15, color = Ink)
        }
    }
}
```

(If `Paper` is not a member of `theme/AppColors.kt`, use `Stone2` instead — both are defined there; pick whichever exists, verify with `grep -n "val Paper\|val Stone2" composeApp/src/commonMain/kotlin/org/arcana/mobile/theme/AppColors.kt`.)

- [ ] **Step 2: Render it in the class detail after the summary**

In `schedule/ClassDetailScreen.kt`, immediately after the `item("summary") { ... }` block (the `SummaryStrip`), add:

```kotlin
            bookingInfoOrNull(existing)?.let { note ->
                item("booking-info") {
                    Spacer(Modifier.height(24.dp))
                    BookingInfoCallout(
                        note = note,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
```

Add the imports at the top of `ClassDetailScreen.kt` if not already present:

```kotlin
import org.arcana.mobile.booking.bookingInfoOrNull
import org.arcana.mobile.booking.BookingInfoCallout
```

(`existing` is already in scope — `val existing by bookingVm.existingBooking.collectAsState()`.)

- [ ] **Step 3: Compile both targets**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL (both). If iOS fails on a JVM-only API, replace it with a multiplatform equivalent (none expected here).

---

## Task 4: Mobile — Home "next up" tile line

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt` (`NextUpCard`)

- [ ] **Step 1: Add the compact booking-info line**

In `home/HomeScreen.kt::NextUpCard`, inside the outer `Column` (the one with `verticalArrangement = Arrangement.spacedBy(24.dp)`), add as the **last** child — immediately after the `Row { Column{name/meta} + IconCircle }` block closes and before the Column's closing brace:

```kotlin
            bookingInfoOrNull(booking)?.let { note ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Overline(text = "Booking info", size = 10, color = Lime)
                    BodyText(
                        text = note,
                        size = 13,
                        color = StoneAlpha65,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
```

Add imports at the top of `HomeScreen.kt` if missing:

```kotlin
import androidx.compose.ui.text.style.TextOverflow
import org.arcana.mobile.booking.bookingInfoOrNull
```

(`Overline`, `BodyText`, `Lime`, `StoneAlpha65`, `Arrangement`, `Column` are already imported/used in this file.)

- [ ] **Step 2: Compile both targets**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL (both).

---

## Task 5: Mobile — full test + build

**Files:** none

- [ ] **Step 1: Run the shared test suite**

```bash
./gradlew :composeApp:testDebugUnitTest
```
Expected: all pass (new BookingDto + BookingInfo tests + existing).

- [ ] **Step 2: Assemble the debug APK**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

---

## Task 6: End-to-end verification (Cole)

**Files:** none — hands-on with local data. Nothing committed yet.

- [ ] **Step 1: Run the server (with `member_note` serialized) + emulator**

```bash
cd /Users/coletomlinson/Desktop/arcana/arcana-server && source .venv/bin/activate
docker compose up -d && python manage.py runserver
```
Point the Android emulator at `http://10.0.2.2:8000` (Developer Settings override), log in as the test member (`tomlinson631+and1@gmail.com` / `testpass1234`).

- [ ] **Step 2: Confirm a booking with a note in local ops**

In `http://localhost:8000/ops/` (`ops-dev@arcana.local` / `opsdev12345`): open the member's requested Solidcore booking, **Confirm** it with a door code in **Booking Information**.

- [ ] **Step 3: Verify both mobile surfaces**

In the app: the Home **next-up tile** shows a "Booking info" line with the door code (truncated to 2 lines); tapping the tile opens the class detail, which shows the full **Booking info** callout under the summary.

- [ ] **Step 4: Verify the no-note case is unchanged**

Confirm a *different* booking with **no** Booking Information → its Home tile and class detail render exactly as before (no callout, no line).

- [ ] **Step 5: Review gate — STOP**

Cole reviews both repos' diffs (`git diff`) and the app. Do not commit/push until he says go. On go: commit each repo on its branch (Co-Authored-By trailer), push, open PRs; the server serializer change rides the `ops-booking-info-and-evidence-edit` PR.

---

## Self-review notes

- **Spec coverage:** server field (Task 1), DTO + gate (Task 2), class-detail callout (Task 3), Home tile line (Task 4), backwards-compat via defaulted field + gate (Tasks 2–4 + Step 6.4), both-target compile + tests (Tasks 3–5). No My Bookings / secondary-row changes (out of scope). All covered.
- **Type/name consistency:** `member_note` (server) ↔ `memberNote` (DTO, `@SerialName("member_note")`) ↔ `bookingInfoOrNull(...)` gate ↔ `BookingInfoCallout(note)` — consistent across all tasks.
- **No-regression:** every UI surface is wrapped in `bookingInfoOrNull(...)?.let { }`; blank/absent note → no rendering. Server field is additive; shipped app ignores unknown keys.
- **Placeholder scan:** the only conditional is the `Paper`/`Stone2` color check in Task 3 Step 1, with an explicit grep to resolve it — not an open TODO.
