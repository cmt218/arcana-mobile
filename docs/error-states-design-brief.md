# Arcana Mobile — Error States Design Brief

*For handoff to Claude Design (claude.ai/design). This was the input brief; the returned designs live in the Claude Design project "Error states design brief" (id `d5af5f1c-e4b2-47df-bec1-5a77c4901410`).*

---

## The ask

Design a **single, unified error-state system** for the Arcana mobile app, replacing the inconsistent per-screen error UI. There are exactly **two failure categories**, each appearing on a few surfaces. Direction is **type-forward minimal** (no illustration). Everything must be buildable from the existing component kit.

---

## Product & brand context

- **Arcana Fit** connects **Members** to elite **Studios**. The app is Kotlin Compose Multiplatform (**iOS + Android**).
- **Brand:** "Kinetic Luxury." Competent, Kinetic, Intense, Aesthetic, Devoted. "Less is more."
- **Voice:** direct, authoritative, dry wit, composed. Not budget, not casual, not cutesy. No "Oops!", no panic.
- **Terminology:** Members (never "users"), Studios, Reservations (never "bookings").
- **Hard copy rule:** **no em/en dashes anywhere.** Use a colon, period, or comma.

### Palette (tokens from `theme/AppColors.kt`)

| Token | Hex | Role |
|---|---|---|
| Moss | `#283B15` | Foundation, deep surfaces, CTAs |
| Burnt Nectar | `#F65713` | Accent, sparingly |
| Stone | `#F5F2ED` | Primary background |
| Wood | `#2E1B0F` | Dark accent surface |
| Lime | `#B6C24F` | Signal / active |
| Ink | `#161812` | Primary text |
| Ash | `#6B6E5F` | Secondary text |

Moss must never sit directly against Burnt Nectar (buffer with Stone or Wood).

### Type
- **League Spartan** — headlines. **DM Sans** — body, captions, buttons (line-height 1.4–1.6). ALL-CAPS labels get +80–100 letter spacing.

## Direction: type-forward minimal
- No illustration. Type + negative space + one accent. Slightly offset composition (avoid dead-center symmetry).
- Category difference by accent + copy, not imagery: **Server** = Burnt Nectar (owns the fault); **Connection** = calmer Lime/neutral. Subtle "kinetic line" motif OK (interrupted for Connection, solid for Server).
- Reuse existing primitives (`ui/Text.kt`, `ui/Buttons.kt`, `ui/DotMatrixLoader.kt` for the retry-loading motion, `ui/Shimmer.kt`). Light + dark.

## Deliverables (states × surfaces), light + dark
1. Full-screen — Connection
2. Full-screen — Server
3. Inline / section — Connection
4. Inline / section — Server
5. Toast / non-blocking — refresh failed (one shared treatment)
6. Retry button — idle / retrying (DotMatrixLoader) / failed-again

Plus redlines and token names, so it's build-ready.

## Locked copy (verbatim; keep the no-dashes rule)
- **Connection, full-screen:** "Can't reach Arcana." / "Check your connection and try again." / **TRY AGAIN**
- **Server, full-screen:** "Something's off on our end." / "Give it a moment and try again." / **TRY AGAIN**
- **Connection, inline:** "Can't load this right now." / "Check your connection." / **Retry**
- **Server, inline:** "This didn't load." / "On our end. Try again." / **Retry**
- **Toast:** "Couldn't refresh. Showing your last update." / **Retry**

(Connection headline is "Can't reach Arcana," not "You're offline" — the category covers offline *and* flaky/timed-out connections.)

## How it maps to code
- Full-screen replaces `ScheduleUiState.Error` / `HomeUiState.Error` and the ad-hoc blocks in Class Detail / My Bookings / Profile.
- Inline is for the Home pattern where the greeting loads but a sub-section fails.
- Toast covers the "keep content on refresh failure" behavior.
- Engineering adds one `ErrorType` classifier (Connection vs Server) and one `ui/ErrorState.kt` family; the designs define what those render.
