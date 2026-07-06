# Surface Booking Information in the Arcana mobile app

**Date:** 2026-07-06
**Author:** Cole (with Claude)
**Repos:** arcana-server (1 field) + arcana-mobile (UI)
**Status:** Approved design — ready for implementation plan

## Context & motivation

Ops can now attach a member-facing **Booking Information** note to a booking (e.g. a
Solidcore door code) that is included in the booking-confirmation and 24-hour-reminder
emails (see `arcana-server` spec `2026-07-06-ops-booking-information-and-evidence-edit`).
We want a member on the go — who doesn't want to dig back through email — to see that
same note in the mobile app.

## Hard constraint: zero regressions, fully backwards-compatible

The shipped app has real users. Every change here is **purely additive** and gated:

- The API gains one field; the currently-shipped app sets `ignoreUnknownKeys = true`
  (`networking/ArcanaApiClient.kt`), so it silently ignores the new key. Verified.
- The mobile DTO field is defaulted (`= null`), so it deserializes whether or not the
  server sends it.
- Every new UI element renders **only when the note is non-blank**. No note → the app is
  pixel-identical to today.
- No new endpoints, no migration, no contract change, no mobile behavior change for
  bookings without a note.

## Dependency / sequencing

The `Booking.member_note` model field lives on the arcana-server branch
`ops-booking-info-and-evidence-edit` (under review, not yet merged). The serializer change
below depends on that field, so it belongs on / after that branch. The mobile work is
independent once the API returns the field.

## Design

### 1. Server — expose the note (arcana-server)

`bookings/serializers.py::BookingSerializer`: add

```python
member_note = serializers.CharField()
```

`member_note` is a non-null `TextField(blank=True)`, so it serializes as `""` when unset —
no `allow_blank`/`allow_null` needed for read. It flows through both `/bookings/me/`
(upcoming + past) and `/bookings/<id>/`. This addition goes on the
`ops-booking-info-and-evidence-edit` branch (same feature family).

### 2. Mobile — DTO + gate helper (commonMain)

`data/BookingDto.kt`: add to `BookingDto`

```kotlin
@SerialName("member_note") val memberNote: String? = null
```

New tiny pure helper (its own small file, e.g. `booking/BookingInfo.kt`):

```kotlin
/** The member-facing booking note, or null when there is nothing to show.
 *  The single gate every Booking-info UI surface uses. */
fun bookingInfoOrNull(booking: BookingDto?): String? =
    booking?.memberNote?.trim()?.takeIf { it.isNotEmpty() }
```

### 3. Mobile — class-detail callout (primary surface)

`schedule/ClassDetailScreen.kt`: a new `item("booking-info")` in the `LazyColumn`, inserted
right after the `item("summary")` block, rendered only when
`bookingInfoOrNull(existing) != null` (`existing` is the member's `BookingDto?` for this
session, already collected from `bookingVm.existingBooking`). It renders a reusable
`BookingInfoCallout(note)` composable built from existing design-system primitives:

- `SectionRule(label = "Booking info")` (matches the existing "Cancelled" section pattern)
- a `BodyText` note on a subtle Stone/Paper surface card
- no new icon asset

Because ops only sets the note at/after confirm, in practice it appears on confirmed
bookings; the gate is simply "note is non-blank," so no status special-casing.

### 4. Mobile — Home "next up" tile (compact surface)

`home/HomeScreen.kt::NextUpCard`: when `bookingInfoOrNull(booking) != null`, add a compact
labeled line inside the card — `Overline("Booking info")` + the note as `BodyText`/`Caption`
truncated to ~2 lines (`maxLines = 2`, `TextOverflow.Ellipsis`). The whole card already
navigates to the class detail (`onClick = onOpenClass(session.id)`), where the full callout
lives — so truncation is safe and the full text is one tap away.

No other Home surfaces (secondary upcoming rows) and no My Bookings changes — explicitly
out of scope per the design decision.

### 5. Error handling

None beyond the gate: a missing/blank note simply renders nothing. No network, parsing, or
state paths change.

## Testing

**Server (`bookings/tests/test_views.py`)** — mirror `test_my_bookings_includes_spot_preference`:
- After ops sets `member_note`, `/bookings/me/` `upcoming[0]['member_note']` equals it, and
  `/bookings/<id>/` `member_note` equals it.

**Mobile (`commonTest`)**:
- `BookingDto` deserializes JSON **with** `member_note` (value preserved) and **without** it
  (defaults to `null`) — proves backwards-compat.
- `bookingInfoOrNull` returns the trimmed note when non-blank, and `null` for
  `null` / `""` / whitespace.
- Compile **both** targets after touching commonMain:
  `:composeApp:compileDebugKotlinAndroid` + `:composeApp:compileKotlinIosSimulatorArm64`
  (per arcana-mobile/CLAUDE.md — JVM-only APIs pass Android but break iOS).

## Files touched

- arcana-server: `bookings/serializers.py`, `bookings/tests/test_views.py`
- arcana-mobile: `data/BookingDto.kt`, new `booking/BookingInfo.kt` (helper +
  `BookingInfoCallout`), `schedule/ClassDetailScreen.kt`, `home/HomeScreen.kt`, and
  `commonTest` additions.

## Out of scope

- My Bookings list, Home secondary rows (decided: next-tile + class detail only).
- Any new icon asset, endpoint, migration, or Booking-model change.
- Editing the note from the app (it's ops-authored, read-only for members).
