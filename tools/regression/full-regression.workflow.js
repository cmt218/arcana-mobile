/**
 * Arcana Mobile — full-regression orchestrator (Claude Code Workflow script)
 * ============================================================================
 *
 * HOW TO INVOKE
 *   Workflow tool:
 *     {
 *       scriptPath: 'tools/regression/full-regression.workflow.js',
 *       args: { runDate: '2026-08-15', mode: 'sequential' }
 *     }
 *
 *   `args` MUST be a real JSON object, never a stringified string. On the
 *   2026-08-11 run a stringified `args` made `args.runDate` come back
 *   `undefined`, and the run wrote itself into a folder literally named
 *   `~/arcana-regression-runs/undefined/`. This script now validates
 *   `runDate` up front and throws rather than repeating that.
 *
 *   args:
 *     runDate  (required) 'YYYY-MM-DD' — names the run folder.
 *     mode     'sequential' (default) | 'parallel'
 *              sequential: iOS26 → iOS18 → Android, one lane at a time,
 *                          all three against ONE server on :8000.
 *              parallel:   three device lanes concurrently, each with its own
 *                          server — ios26 :8000, ios18 :8001, android :8002 —
 *                          and its own Developer-Settings base-URL override.
 *     devices  (optional) subset of ['ios26','ios18','android'].
 *              Omit for all three. A subset is a deliberate narrowing (e.g.
 *              re-running one device after a toolchain fix); the runbook's
 *              iron rule 3 still says a real pass runs all three.
 *
 * WHAT GOVERNS WHAT
 *   `docs/regression/runbook.md` is the source of truth for procedure, and
 *   `docs/regression/driver-playbook.md` for driving technique. This script
 *   only *orchestrates* them — it mirrors the runbook's phases and hands each
 *   agent the rules verbatim. **If this script and the runbook ever disagree,
 *   the runbook is right and this script is the bug: fix the script.** Never
 *   resolve a conflict by editing the runbook to match the script.
 *
 * OUTPUTS
 *   Everything lands in ~/arcana-regression-runs/<runDate>/ :
 *     manifest.json          the one seed_regression manifest for the run
 *     results-<device>.log   append-only, one line per (entry, device), in BOTH
 *                            modes — nothing writes a shared log in Phase 3
 *     results.log            merged in Phase 4 from the per-device logs
 *     screenshots/           FAIL / suspect-driver / checkpoint captures
 *     report-draft.md        phase notes accumulated during the run
 *     report.md             the deliverable
 *   Nothing this run produces is ever staged, committed or pushed. The single
 *   sanctioned repo write is Phase 5's doc fold-back, which edits the working
 *   tree and LEAVES IT DIRTY for human review — it never commits either.
 */

export const meta = {
  name: 'full-regression',
  description:
    'Arcana mobile full regression: setup + self-audit + per-device shift loops (sequential or parallel) + deferred fault-injection tail + report + triage/fold-back + Trello filing',
  // Dispatch phases map onto the runbook's numbered Phases 0-6. Two sanctioned
  // regroupings, both documented in the runbook's "Orchestration" note:
  // Phase 0 + Phase 2 are one Setup agent, and Phase 1 (device-independent,
  // never-halting) is dispatched after it rather than between them.
  phases: [
    { title: 'Setup', detail: 'runbook Phase 0 + 2: preflight, ops notifier, server(s), seed once, build + install' },
    { title: 'Audit', detail: 'runbook Phase 1 self-audit (tools/regression/self_audit.sh)' },
    { title: 'Lanes', detail: 'runbook Phase 3, parallel mode only: the three device lanes running concurrently' },
    { title: 'iOS26', detail: 'runbook Phase 3 shift loop — iPhone 17 Pro Max, iOS 26.x' },
    { title: 'iOS18', detail: 'runbook Phase 3 shift loop — iPhone 16 Pro, iOS 18.5' },
    { title: 'Android', detail: 'runbook Phase 3 shift loop — Pixel_9_Pro AVD (emulator only)' },
    { title: 'Deferred', detail: "runbook Phase 3's serialized tail: the DEFERRED environment-wide fault entries, after every lane is done (both modes)" },
    { title: 'Report', detail: 'runbook Phase 4: merge logs, write the human-first report.md, clean up' },
    { title: 'Triage', detail: 'runbook Phase 5: adjudicate FAILs, fold learnings back into the docs (uncommitted)' },
    { title: 'Trello', detail: 'runbook Phase 6: file the §2 digest items, only if a Trello tool exists' },
  ],
}

// ---------------------------------------------------------------------------
// args — validated before anything touches the filesystem
// ---------------------------------------------------------------------------

const A = typeof args === 'string' ? JSON.parse(args) : args && typeof args === 'object' ? args : {}

const INVOKE_HINT =
  "Invoke as {scriptPath:'tools/regression/full-regression.workflow.js', args:{runDate:'YYYY-MM-DD', mode:'sequential'}} with args as a REAL JSON object, not a stringified string."

if (typeof A.runDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(A.runDate)) {
  throw new Error(
    'full-regression: args.runDate is REQUIRED and must be "YYYY-MM-DD". Got: ' +
      JSON.stringify(A.runDate) +
      '. ' +
      INVOKE_HINT +
      ' (A missing runDate is what produced the run folder literally named "undefined" on 2026-08-11.)'
  )
}

const MODE = A.mode === undefined || A.mode === null ? 'sequential' : A.mode
if (MODE !== 'sequential' && MODE !== 'parallel') {
  throw new Error('full-regression: args.mode must be "sequential" or "parallel". Got: ' + JSON.stringify(A.mode))
}

const ALL_KEYS = ['ios26', 'ios18', 'android']
const DEVICE_KEYS = A.devices === undefined || A.devices === null ? ALL_KEYS : A.devices
if (!Array.isArray(DEVICE_KEYS) || DEVICE_KEYS.length === 0) {
  throw new Error('full-regression: args.devices, when given, must be a non-empty array — a subset of ' + JSON.stringify(ALL_KEYS))
}
for (const k of DEVICE_KEYS) {
  if (!ALL_KEYS.includes(k)) {
    throw new Error('full-regression: unknown device ' + JSON.stringify(k) + '. Valid: ' + JSON.stringify(ALL_KEYS))
  }
}

// ---------------------------------------------------------------------------
// constants
// ---------------------------------------------------------------------------

const RUN_DATE = A.runDate
const REPO = '/Users/coletomlinson/Desktop/arcana/arcana-mobile'
const SERVER_REPO = '/Users/coletomlinson/Desktop/arcana/arcana-server'
const RUN = '/Users/coletomlinson/arcana-regression-runs/' + RUN_DATE
const SHOTS = RUN + '/screenshots'
const SHIFT_CAP = 12

const CATALOG = {
  ios26: {
    key: 'ios26',
    phase: 'iOS26',
    desc: 'iPhone 17 Pro Max on an iOS 26.x runtime (runbook Phase 0.3 pins the UDID — re-verify it, never hardcode)',
    tags: '`shared`, `iOS-only`, `iOS26-only`',
    host: 'localhost',
    parallelPort: 8000,
    driver: 'idb — always the full venv path ~/.arcana-tools/idb-venv/bin/idb (bare `idb` is not on PATH)',
  },
  ios18: {
    key: 'ios18',
    phase: 'iOS18',
    desc: 'iPhone 16 Pro on the iOS 18.5 runtime (runbook Phase 0.3 pins the UDID — re-verify it, never hardcode)',
    tags: '`shared`, `iOS-only`, `iOS18-only`',
    host: 'localhost',
    parallelPort: 8001,
    driver: 'idb — always the full venv path ~/.arcana-tools/idb-venv/bin/idb (bare `idb` is not on PATH)',
  },
  android: {
    key: 'android',
    phase: 'Android',
    desc: 'the Pixel_9_Pro AVD — emulator ONLY. The connected physical Pixel 9 Pro is never a target; pin every adb call to the emulator serial',
    tags: '`shared`, `Android-only`',
    host: '10.0.2.2',
    parallelPort: 8002,
    driver: 'adb shell uiautomator dump / adb shell input, with `android layout` + `android screen capture -a` as the second inspection path',
  },
}

const DEVICES = DEVICE_KEYS.map(k => {
  const d = CATALOG[k]
  const port = MODE === 'parallel' ? d.parallelPort : 8000
  return {
    key: d.key,
    phase: d.phase,
    desc: d.desc,
    tags: d.tags,
    driver: d.driver,
    port: port,
    baseUrl: 'http://' + d.host + ':' + port,
    log: RUN + '/results-' + d.key + '.log',
  }
})

const PORT_TABLE = DEVICES.map(d => d.key + '=' + d.baseUrl).join(', ')

// ---------------------------------------------------------------------------
// shared prompt blocks
// ---------------------------------------------------------------------------

const DOCS = `Read these checked-in docs FIRST and follow them exactly — they are the contract under test as much as the app is:
1. ${REPO}/.claude/skills/full-regression/SKILL.md
2. ${REPO}/docs/regression/runbook.md  (governs; if anything an orchestrator told you contradicts it, the runbook wins and the contradiction is a learning)
3. ${REPO}/docs/regression/driver-playbook.md  (technique: the dump→act→verify loop, the traps, the Safety section, the driver-bug protocol, the canonical idb venv path)
The inventory checklist is ${REPO}/docs/regression/inventory.md (218 entries at last count).

RUN FOLDER: ${RUN}
  ${RUN}/manifest.json          the ONE seed_regression manifest for this run — read every account and session id from it, NEVER hardcode one
  ${RUN}/results-<device>.log   append-only per-device results log — one per device in BOTH modes, per the runbook's "Results logs" (Phase 3 writes only these; Phase 4 merges them into results.log, so do NOT write a shared results.log during Phase 3)
  ${SHOTS}/                     screenshots
  ${RUN}/report-draft.md        running phase notes
NOTHING is written inside either git checkout by Phases 0-4. Statuses are PASS / FAIL / BLOCKED / SKIP (plus the DEFERRED convention below and the suspect-driver annotation); every one gets recorded and the run moves past it. NEVER halt the run on a failure.

MODE: ${MODE}. Devices in this run: ${DEVICES.map(d => d.key).join(', ')}. Base URLs: ${PORT_TABLE}.

TOKEN DISCIPLINE (mandatory): never dump full UI trees into your context — filter idb describe-all / uiautomator XML through bash (python3 -c / grep / jq) and extract only the labels+frames you need for the current action. Screenshot commands write straight to ${SHOTS}/; don't view an image unless you are actually diagnosing something.

LEARNINGS (mandatory): every time a doc is wrong, ambiguous, missing a step, or a documented command fails, record it as a self-contained one-liner and return it. These feed Phase 5's fold-back into the docs — a learning you keep in your head is a learning that dies with your context.`

const SAFETY = `SAFETY RULES — carry these verbatim; they override anything you infer, and any of them being unverifiable is a BLOCKED entry, not a reason to improvise:
1. KILL ONLY \`manage.py runserver\` PIDs FOR THIS LANE'S OWN PORT. Find them with \`ps aux | grep '[m]anage.py runserver'\` and match this lane's port on the command line before killing anything. NEVER kill by port lookup — no \`lsof -ti :PORT | xargs kill\`, no \`kill $(lsof -t -i:PORT)\`, no \`fuser -k\`. A port-pattern kill collaterally killed the Pixel_9_Pro emulator process itself in a prior shift. Never touch another lane's server, and never stop a server this run did not start.
2. VERIFY THE OPS NOTIFIER IS THE LOG-ONLY NULL ONE BEFORE DRIVING ANYTHING THAT BOOKS, CANCELS, OR SUBMITS A CONCIERGE / DELETE-ACCOUNT REQUEST. arcana-server's \`.env\` on this checkout wires the REAL pipeline (\`OPS_NOTIFIER_CLASS=notifications.telegram.MultiOpsNotifier\` → Telegram + Pushover), and PushoverOpsNotifier sends every event at hardcoded emergency priority: DND-breaking siren, retried for hours, paging the founders for real. Confirm the server process serving this lane was started with \`OPS_NOTIFIER_CLASS=notifications.telegram.NullOpsNotifier\` in its environment — that is the real class (\`arcana-server/notifications/telegram.py\`, and settings' own default in \`arcana/settings/base.py\`), it logs instead of sending, and \`get_ops_notifier()\` is what resolves it. If you cannot confirm it, record every booking / cancel / concierge entry BLOCKED with reason "ops notifier unverified — real Telegram + emergency Pushover page would fire" and keep going. PROFILE-14 (delete-account submit) stays BLOCKED unless the null notifier is confirmed; PROFILE-15 (the server-down failure path) is the safe substitute for the same dialog machinery.
3. ONE ACTION PER DUMP. Dump the UI → decide the single action that dump justifies → perform it → dump again → confirm the expected post-action state BEFORE deciding the next action. Never chain taps off one stale dump; layouts shift underneath you. This exact mistake caused 4 of 13 failures in early driving sessions.
4. DRIVER-BUG PROTOCOL BEFORE ANY FAIL. Reproduce the failure a second time via a DIFFERENT interaction path (a different tap sequence to the same state, or the platform's other inspection tool). If it only reproduces one way, or doesn't reproduce again, record it as a suspect-driver finding — naming the exact suspect action, dump staleness, or playbook trap — NOT as a FAIL. Roughly half the "app bugs" in early sessions were driver mistakes; a report full of false alarms is worse than a shorter, trustworthy one.
5. THIS DEVICE'S ACCOUNT SET ONLY. Use \`accounts.<this device>.claim\` and \`accounts.<this device>.member\` from ${RUN}/manifest.json and nothing else. Never sign another device's account into this build — the three sets exist precisely so devices don't collide on account-level state.
6. SCREENSHOT AT THE MOMENT OF EVERY FAIL AND EVERY SUSPECT-DRIVER FINDING, before navigating anywhere else, into ${SHOTS}/ named \`<device>-<ENTRY-ID>-fail.png\` / \`<device>-<ENTRY-ID>-suspect.png\`, and reference that filename from the entry's result line. A failure with no image is a failure the reader cannot adjudicate. Also take the runbook's three checkpoint screenshots (post-login home, post-claim success, booking confirmation) even when the entry PASSes.
7. NEVER HALT. A failed check, a missing tool, a FAILed entry, a dead device — none of it stops the run. Record it, classify it, keep going. Write what you have before you stop for any reason.
Also standing: nothing is staged, committed or pushed, ever. \`pm clear org.arcana.mobile\` wipes the Developer Settings base-URL override back to the prod default — re-set and re-verify the base URL immediately after any \`pm clear\`, or subsequent requests silently hit production. A relaunched AVD resumes a quick-boot snapshot, so \`am force-stop\` + relaunch, then re-check the signed-in account and the base URL, before trusting anything after a crash.`

const DEFERRED_RULE = `DEFERRED ENTRIES — DO NOT DRIVE THESE INSIDE A LANE. Any entry whose repro requires an exclusive, environment-wide fault — taking the shared dev Postgres or the whole DB layer down (\`docker stop arcana_postgres\`-style injection, ERR-08's 5xx hunt), or a \`manage.py shell\` mutation of shared rows such as the late-cancel fulfilment step — is deferred. The DB is shared by every device and, in parallel mode, by all three servers at once; in sequential mode the reason is the same tail agent's single stop/restore cycle with one verified restore rather than several scattered through the pass. DEFERRED applies in BOTH modes and is NEVER a final status. When you reach such an entry, append a line \`<device>  <ENTRY-ID>  DEFERRED  <the exclusive fault it needs>\` to your results log, return the ID in \`deferred_ids\`, and move on. A single tail agent drives all of them after every lane has finished and APPENDS the real status as a new line (append-only log; Phase 4 takes the last line for a (device, entry) pair as authoritative), so no DEFERRED status reaches the report.`

const SHIFT_SCHEMA = {
  type: 'object',
  required: ['complete', 'recorded', 'learnings'],
  properties: {
    complete: {
      type: 'boolean',
      description:
        'true ONLY if every applicable inventory entry for this device now has a line in its results log (including the deferred sign-out entries and every earlier gap found by the ID diff). DEFERRED lines count as recorded.',
    },
    recorded: { type: 'number', description: 'entries recorded this shift' },
    last_entry_id: { type: 'string' },
    pass: { type: 'number' },
    fail: { type: 'number' },
    blocked: { type: 'number' },
    skip: { type: 'number' },
    fail_ids: { type: 'array', items: { type: 'string' }, description: 'entry IDs recorded FAIL or suspect-driver this shift' },
    deferred_ids: { type: 'array', items: { type: 'string' }, description: 'entry IDs punted to the cross-lane deferred tail agent' },
    gaps_filled: { type: 'array', items: { type: 'string' }, description: 'earlier unrecorded IDs the full ID-diff surfaced and this shift filled' },
    learnings: { type: 'array', items: { type: 'string' }, description: 'doc gaps / ambiguities / hiccups hit this shift, each self-contained' },
    notes: { type: 'string' },
  },
}

const SETUP_SCHEMA = {
  type: 'object',
  required: ['ready', 'skips', 'learnings'],
  properties: {
    ready: { type: 'boolean', description: 'true if at least one device is fully prepared: built, installed, base URL overridden and verified reaching its own server' },
    devices_ready: { type: 'array', items: { type: 'string' } },
    servers: {
      type: 'array',
      description: 'one entry per server this run is using',
      items: {
        type: 'object',
        properties: {
          port: { type: 'number' },
          pid: { type: 'string' },
          started_by_this_run: { type: 'boolean' },
          ops_notifier: { type: 'string', description: 'the OPS_NOTIFIER_CLASS this process is actually running with' },
        },
      },
    },
    ops_notifier_null: { type: 'boolean', description: 'true only if EVERY server serving this run is confirmed on NullOpsNotifier' },
    manifest_path: { type: 'string' },
    skips: { type: 'array', items: { type: 'string' }, description: 'preflight/environment SKIPs with reasons' },
    learnings: { type: 'array', items: { type: 'string' } },
    notes: { type: 'string' },
  },
}

// One §2 digest item. The same shape backs all four of §2's subsections, which
// is what lets Phase 6 file one card per item without re-deriving anything.
const DIGEST_ITEM = {
  type: 'object',
  properties: {
    entry_ids: { type: 'array', items: { type: 'string' } },
    devices: { type: 'array', items: { type: 'string' } },
    severity: { type: 'string', enum: ['Low', 'Medium', 'High'], description: "the runbook's severity vocabulary — a judgement about user impact" },
    title: { type: 'string', description: '≤80 chars, prefixed `<PRIMARY-ENTRY-ID> · ` (or SUITE/HAZARD), lifted verbatim into the Phase 6 card title' },
    summary: { type: 'string', description: 'symptom + root cause (or "not yet established") + user impact' },
    evidence: { type: 'string', description: 'screenshot paths, results.log refs, source file:line' },
    disposition: { type: 'string' },
  },
  required: ['entry_ids', 'severity', 'title', 'summary'],
}

const TRIAGE_SCHEMA = {
  type: 'object',
  required: ['app_bugs', 'doc_edits', 'learnings'],
  properties: {
    // §2 subsection 1 — verdict APP BUG.
    app_bugs: { type: 'array', items: DIGEST_ITEM },
    // §2 subsection 2 — behavior that matches the code but a reviewer may not want shipped.
    ux_observations: { type: 'array', items: DIGEST_ITEM },
    // §2 subsection 3 — verdict FIXTURE/ENVIRONMENT GAP, plus harness shortfalls.
    fixture_gaps: { type: 'array', items: DIGEST_ITEM, description: 'suite & tooling gaps: BLOCKEDs needing a seed/server change, not a driving fix' },
    // §2 subsection 4 — things that can hurt a person or an environment.
    hazards: { type: 'array', items: DIGEST_ITEM },
    driver_artifacts: { type: 'array', items: { type: 'string' }, description: 'verdict DRIVER ARTIFACT: FAILs manufactured by how they were driven, with what gave each away (§4, not §2)' },
    inventory_corrections: { type: 'array', items: { type: 'string' }, description: 'verdict INVENTORY-EXPECTED-WRONG: entry ID + what its Expected now says' },
    doc_edits: { type: 'array', items: { type: 'string' }, description: 'file:section — what changed and why (all UNCOMMITTED)' },
    learnings: { type: 'array', items: { type: 'string' } },
    notes: { type: 'string' },
  },
}

const TRELLO_SCHEMA = {
  type: 'object',
  required: ['trello_available'],
  properties: {
    trello_available: { type: 'boolean' },
    cards: { type: 'array', items: { type: 'string' }, description: 'title — url, one per §2 digest item: newly created cards AND existing open cards annotated with "seen again" (say which is which)' },
    notes: { type: 'string' },
  },
}

// ---------------------------------------------------------------------------
// Phase 0 + 2 — setup
// ---------------------------------------------------------------------------

phase('Setup')
log('run ' + RUN_DATE + ' — mode=' + MODE + ', devices=' + DEVICES.map(d => d.key).join('/') + ', run folder ' + RUN)

const serverPlan =
  MODE === 'parallel'
    ? `START THREE SERVERS, one per lane, so the lanes never contend for a single process: port 8000 (ios26), 8001 (ios18), 8002 (android). All three point at the SAME dev Postgres — that is intended; the seed is still run exactly once. Start each in the background, capture each PID, and record which ports this run started versus which were already held by a pre-existing \`manage.py runserver\` (runbook Phase 0.7: a pre-existing arcana-server runserver on 8000 is the good case, not a conflict — but confirm its OPS_NOTIFIER_CLASS; if it is not the null notifier, do not reuse it, start this run's own process on that port only after stopping ONLY that specific manage.py PID, and record the decision as a learning).`
    : `START ONE SERVER on port 8000 for all devices (runbook Phase 2.1). Check first per Phase 0.7: if a \`manage.py runserver\` already holds 8000, verify it answers and confirm its OPS_NOTIFIER_CLASS rather than starting a second one — but a pre-existing server inherited this checkout's \`.env\` and is therefore probably on the REAL notifier; if it is not the null notifier, do not reuse it: stop ONLY that specific manage.py PID (never a port lookup) and start this run's own process on 8000 with the null notifier in its environment, recording the decision as a learning. If some other process holds 8000, record a SKIP with the holder's command and do not kill it.`

const overridePlan = DEVICES.map(d => '  - ' + d.key + ' → ' + d.baseUrl).join('\n')

const setup = await agent(
  `You are the SETUP agent for a real full-regression run of the Arcana mobile app (run date ${RUN_DATE}).
${DOCS}

${SAFETY}

Execute runbook Phase 0 (preflight — record-and-SKIP, never abort) and Phase 2 (environment) EXACTLY as written:

1. Create ${RUN}/, ${SHOTS}/, ${RUN}/report-draft.md, and one EMPTY results log per device in this run: ${DEVICES.map(d => d.log).join(', ')}.
2. Phase 0 preflight, every check: Xcode/toolchain, both simulator runtimes, the two pinned iPhone UDIDs (re-verify — never trust the runbook's snapshot table), idb from its venv path, the Pixel_9_Pro AVD, arcana-server's checkout + \`.venv\` + docker Postgres/Redis, and port availability for every port this run needs. Each check passes or becomes a SKIP-with-reason. Nothing here aborts.
3. OPS NOTIFIER + EMAIL SENDER — do this BEFORE the server(s) start. Read ${SERVER_REPO}/.env, report what \`OPS_NOTIFIER_CLASS\` and \`EMAIL_SENDER_CLASS\` are actually set to, and start every server process this run owns with BOTH \`OPS_NOTIFIER_CLASS=notifications.telegram.NullOpsNotifier\` AND \`EMAIL_SENDER_CLASS=notifications.email.ConsoleEmailSender\` in its environment (env vars on the process, never a code or .env edit). The email override matters: the 2026-08-11 run's password-reset entries sent REAL Loops transactional emails to @example.com addresses (hard bounces on the real sending domain) because .env wires LoopsEmailSender. Then PROVE it took effect — e.g. resolve \`get_ops_notifier()\` in that process's own settings via a \`manage.py shell -c\` run under the same env — and set \`ops_notifier_null\` accordingly. Every downstream shift agent gates real bookings/cancels/concierge submits on this being true.
4. SERVERS. ${serverPlan}
   Sanity-check each with the runbook's liveness check before moving on, and record port → PID → started_by_this_run → resolved notifier class.
5. SEED EXACTLY ONCE, against the shared dev DB, before any device starts Phase 3: \`python manage.py seed_regression > ${RUN}/manifest.json\`. Confirm the file parses as JSON and contains \`accounts.{ios26,ios18,android}\` and the five \`classes.*\` ids. Re-running the seed later would purge every regression booking and burn every id — do not re-seed for any reason. If the seed errors, do NOT halt: record it, note which entries become SKIPs per the runbook's fixture table, and continue.
6. BUILD + INSTALL every device in this run. iOS: build Debug once and install on each pinned UDID (the product is \`Arcana.app\`, not \`iosApp.app\` — resolve the real .app from the build's own Products dir; grep the build output for BUILD SUCCEEDED/FAILED rather than trusting \$? through a pipe). Android: \`:androidApp:assembleDebug\` then \`adb -s emulator-<port> install -r\` — pin the emulator serial explicitly, never the physical device's serial.
7. BASE URL OVERRIDE per device, via the in-app Developer Settings screen (10 taps on the wordmark on the signed-out Auth screen; the field pre-fills and MUST be cleared before typing or you get concatenated-URL corruption). Set:
${overridePlan}
   A reused simulator/emulator is not behaviorally fresh: it may already be signed in and already carry an override. If a device boots to Home instead of Auth, sign out via Profile first. Then VERIFY each install actually reaches its OWN server (drive one authenticated or health request and confirm it lands on the expected port — in parallel mode a lane silently talking to another lane's port is a failure mode this step exists to catch).
8. Append a '## Phase 0/2 — setup' section to ${RUN}/report-draft.md: the preflight table, the server/port/PID/notifier table, the manifest summary, and the per-device install + override state.

Record every deviation between what the docs say and what you actually had to do as a learning.`,
  { label: 'setup', phase: 'Setup', model: 'sonnet', effort: 'high', schema: SETUP_SCHEMA }
)

const setupReady = !!(setup && setup.ready)
const opsNotifierNull = !!(setup && setup.ops_notifier_null)
log(setupReady ? 'setup ready: ' + (setup.devices_ready || []).join(', ') : 'SETUP NOT READY — lanes will be skipped, a partial report is still written')
log('ops notifier null-confirmed: ' + opsNotifierNull + (opsNotifierNull ? '' : ' — booking/cancel/concierge entries will be BLOCKED by the shift agents'))

// ---------------------------------------------------------------------------
// Phase 1 — self-audit (device-independent; runs even if setup failed)
// ---------------------------------------------------------------------------

phase('Audit')
const audit = await agent(
  `You are the SELF-AUDIT agent for a real full-regression run (run date ${RUN_DATE}).
${DOCS}

Execute runbook Phase 1 (inventory self-audit). ${REPO}/tools/regression/self_audit.sh is the mechanical implementation of it — run it first (\`bash ${REPO}/tools/regression/self_audit.sh\`) and work from its output rather than re-deriving the greps by hand. It always exits 0 and prints \`FINDINGS: N\`, so a zero exit code means nothing on its own: read N and the finding lines. If the script is missing or errors, fall back to the runbook's Phase 1 forward and reverse passes as written (including its prescribed extraction rules and sanity numbers) and record the script's absence/breakage as a learning.

FORWARD: user-facing surfaces (screens, ViewModels, nav destinations) with no inventory entry covering them.
REVERSE: inventory entries whose \`Source:\` line points at a file/symbol that no longer exists.

Findings NEVER halt anything — they are Phase 4 drift findings, nothing more. Append a '## Phase 1 — inventory drift' section to ${RUN}/report-draft.md with every finding named by file and/or entry ID. Return: forward count, reverse count, one line per finding, plus learnings.`,
  { label: 'self-audit', phase: 'Audit', model: 'sonnet', effort: 'medium' }
)
log('self-audit done')

// ---------------------------------------------------------------------------
// Phase 3 — execution: per-device shift loops
// ---------------------------------------------------------------------------

function shiftPrompt(d, n) {
  return `You are EXECUTION shift #${n} for device ${d.key} in a real full-regression run (run date ${RUN_DATE}, mode ${MODE}).
Device: ${d.desc}
Driver: ${d.driver}
This lane's server: ${d.baseUrl} (port ${d.port}). This lane's results log: ${d.log}
You drive ONLY ${d.key}. ${MODE === 'parallel' ? 'The other device lanes are running RIGHT NOW in parallel against their own servers on their own ports — never touch another lane\'s server, device, app install, or results log, and expect to see other lanes\' bookings on the shared studios/sessions as ordinary prior state.' : 'The other devices are handled in their own lanes before/after this one; expect earlier devices\' state-mutating effects on shared studios/sessions as prior state, not as a race.'}

${DOCS}

${SAFETY}

${DEFERRED_RULE}

RESUME BY A FULL ID-DIFF, NOT BY A HIGH-WATER MARK. Before driving anything:
  a. Extract the inventory-order ID list: \`grep -o '^### [A-Z0-9-]*' ${REPO}/docs/regression/inventory.md | sed 's/^### //'\`
  b. Extract the IDs already recorded for this device: \`awk '{print \$2}' ${d.log}\`
  c. Diff (a) against (b) preserving INVENTORY ORDER (python3 one-liner or \`comm\` on sorted lists plus an order-preserving re-walk), and drive the first unrecorded ID, then the next, and so on.
The last recorded line is NOT a safe resume point on its own: a prior shift can leave mid-block holes an "resume after the last line" jump never revisits. Verified 2026-08-11 — one android pass had roughly 50 earlier entries with no log line at all, found only by a full ID diff. Fill every earlier gap you find (record SKIP if the entry's \`Platforms:\` field excludes this device, driven-and-recorded otherwise) as well as continuing forward. Report the gaps you filled in \`gaps_filled\`.

This device runs entries tagged ${d.tags}; anything else is recorded SKIP (recorded, not omitted). Tombstoned \`— RETIRED\` entries are recorded SKIP with reason "retired". Cross-reference entries take the referenced entry's status with the notation \`see <ID>\` — never driven separately, never SKIP.

Then execute runbook Phase 3 for this device, honoring its state-ordering rules — they are not optional and several are one-shot:
  - the SIGNUP/claim block runs FIRST (its \`welcome_token\` is consumed by the claim-form submit, and consuming it forecloses SIGNUP-08/09/10/19/20, NAV-06/07/09 and TEL-10/11 for the rest of this device's pass);
  - drive ERR-18 and ERR-19 BEFORE the successful claim submit, and drive SIGNUP-07/08/09/10 contiguously with SIGNUP-01-06 while still pre-survey-completion;
  - sign-out entries (AUTH-11, AUTH-12, and PROFILE-12 by cross-reference) run LAST; the DEVSET block is the sanctioned exception, since Developer Settings is only reachable signed-out — an unrecorded sign-out/sign-in round trip for it is allowed on both platforms;
  - booking/cancel entries mutate credits and reservations: drive them in inventory-ID order and treat each entry's noted after-effect as the next one's expected starting state;
  - the forfeit-warning path needs the runbook's manual fulfilment step on \`classes.late_cancel_active\` (substitute the real id from this run's manifest) — note in the result line when you applied it;
  - the runbook's "reads as a bug but isn't" list (seeded favorites flipping Schedule's default scope, SCHED-02's scope after retry, CLASS-25 needing a Home pull-to-refresh, HOME-01's uncatchable shimmer) and its Known-BLOCKED table are pre-adjudicated — do not re-derive them and do not record them as FAILs.

RECORD EVERY RESULT THE MOMENT IT HAPPENS — never batch. APPEND one line per entry to ${d.log} in the runbook's format, whitespace-aligned, IMMEDIATELY as the entry completes and BEFORE starting the next one:
    ${d.key}  SCHED-14  PASS
    ${d.key}  SCHED-15  FAIL   overline read "AVAILABLE"; screenshots/${d.key}-SCHED-15-fail.png
    ${d.key}  PROFILE-12 PASS  see AUTH-11
Nothing may live only in your head; Phase 4's report is assembled FROM this log, so the log is the primary artifact.

CONTEXT BUDGET: you cannot finish 200+ entries in one shift, and a shift that dies mid-entry loses whatever it hadn't yet written. Drive steadily and watch your own consumption: when you judge you are at roughly 70% of your usable context, STOP CLEANLY at an entry boundary — finish the entry you are on, write its line, then return with \`complete: false\` and a full \`learnings\` list. Do not try to squeeze in one more entry near the ceiling. Set \`complete: true\` ONLY when your ID diff comes back empty for this device (every applicable entry has a line, including the deferred sign-out entries; DEFERRED lines count).`
}

async function runLane(d) {
  let shifts = 0
  let complete = false
  let recorded = 0
  const tally = { pass: 0, fail: 0, blocked: 0, skip: 0 }
  const failIds = []
  const deferredIds = []
  const gapsFilled = []
  const learnings = []

  while (!complete && shifts < SHIFT_CAP) {
    shifts++
    const r = await agent(shiftPrompt(d, shifts), {
      label: d.key + ':shift' + shifts,
      phase: d.phase,
      model: 'sonnet',
      effort: 'medium',
      schema: SHIFT_SCHEMA,
    })
    if (!r) {
      learnings.push('[' + d.key + '] shift ' + shifts + ' returned null (died mid-shift) — the next shift resumed from the results log via the ID diff')
      log(d.key + ' shift ' + shifts + ': DIED (null result) — resuming next shift')
      continue
    }
    recorded += Number(r.recorded) || 0
    tally.pass += Number(r.pass) || 0
    tally.fail += Number(r.fail) || 0
    tally.blocked += Number(r.blocked) || 0
    tally.skip += Number(r.skip) || 0
    failIds.push(...(r.fail_ids || []))
    deferredIds.push(...(r.deferred_ids || []))
    gapsFilled.push(...(r.gaps_filled || []))
    learnings.push(...(r.learnings || []).map(x => '[' + d.key + '] ' + x))
    complete = !!r.complete
    log(
      d.key +
        ' shift ' +
        shifts +
        ': +' +
        (Number(r.recorded) || 0) +
        ' entries (through ' +
        (r.last_entry_id || '?') +
        ')' +
        (r.fail_ids && r.fail_ids.length ? ' — ' + r.fail_ids.length + ' fail/suspect' : '') +
        (complete ? ' — DEVICE COMPLETE' : '')
    )
  }

  if (!complete) {
    const why = 'shift cap ' + SHIFT_CAP + ' reached without completing ' + d.key + ' — Phase 4 must report the unrecorded remainder as a run gap, not as SKIPs'
    learnings.push('[' + d.key + '] ' + why)
    log('WARNING: ' + why)
  }

  return { device: d.key, shifts, complete, recorded, ...tally, fail_ids: failIds, deferred_ids: deferredIds, gaps_filled: gapsFilled, learnings }
}

let lanes = []
if (!setupReady) {
  log('skipping all device lanes: setup reported not ready')
} else if (MODE === 'parallel') {
  phase('Lanes')
  log('parallel mode: launching ' + DEVICES.length + ' device lanes concurrently — ' + PORT_TABLE)
  lanes = (await parallel(DEVICES.map(d => () => runLane(d)))).filter(Boolean)
} else {
  for (const d of DEVICES) {
    phase(d.phase)
    log('sequential mode: starting ' + d.key + ' lane against ' + d.baseUrl)
    lanes.push(await runLane(d))
  }
}

const allLearnings = [...((setup && setup.learnings) || []), ...lanes.flatMap(l => l.learnings || [])]
const allDeferred = lanes.flatMap(l => (l.deferred_ids || []).map(id => ({ device: l.device, id })))
const allFailIds = lanes.flatMap(l => (l.fail_ids || []).map(id => l.device + ':' + id))

// ---------------------------------------------------------------------------
// Deferred — cross-lane DB/Postgres fault injection, after every lane is done
// ---------------------------------------------------------------------------

let deferredResult = null
if (allDeferred.length === 0) {
  log('no DEFERRED entries collected — skipping the cross-lane fault-injection tail')
} else {
  phase('Deferred')
  log('driving ' + allDeferred.length + ' deferred cross-lane entries: ' + allDeferred.map(x => x.device + ':' + x.id).join(', '))
  deferredResult = await agent(
    `You are the DEFERRED tail agent for a real full-regression run (run date ${RUN_DATE}). Every device lane has FINISHED — you are the only thing driving now, which is exactly why these entries were saved for you.
${DOCS}

${SAFETY}

These entries were deferred by the lanes because their repro takes down state shared by every device (the dev Postgres, the whole DB layer, or an entire server process), which no lane could safely do while another lane was mid-entry:
${allDeferred.map(x => '  - ' + x.device + '  ' + x.id).join('\n')}

Drive each one now, on the device it was deferred from, using that device's own account set and its own base URL (${PORT_TABLE}).
- The documented lever for a clean, fast 5xx from authenticated endpoints is \`docker stop arcana_postgres\` → drive the entry → \`docker start arcana_postgres\` → wait for the server to reconnect before the next entry. The runbook's Known-BLOCKED table notes this is NOT yet confirmed to produce a 5xx on the LOGIN endpoint specifically (ERR-08) — try it, and if it only yields connection-refused, record BLOCKED with exactly what you observed rather than forcing a status.
- ALWAYS restore Postgres (and any server you stopped) before finishing, and re-verify each affected device still reaches its own server afterwards.
- Append each result to that device's own results log (${DEVICES.map(d => d.key + '→' + d.log).join(', ')}) in the same one-line format, REPLACING nothing — append a new line whose status is the real outcome; Phase 4 takes the last line for an ID as authoritative and you must say so in your notes.
- Same rules as any shift: driver-bug protocol before any FAIL, screenshot every FAIL/suspect-driver into ${SHOTS}/, never halt.

Return: one line per entry with its final status, what lever you used, whether Postgres/servers were restored and verified, and your learnings.`,
    { label: 'deferred-tail', phase: 'Deferred', model: 'sonnet', effort: 'medium', schema: SHIFT_SCHEMA }
  )
  if (deferredResult && deferredResult.learnings) allLearnings.push(...deferredResult.learnings.map(x => '[deferred] ' + x))
  log('deferred tail done: ' + ((deferredResult && deferredResult.recorded) || 0) + ' entries recorded')
}

// ---------------------------------------------------------------------------
// Phase 4 — report
// ---------------------------------------------------------------------------

phase('Report')
const report = await agent(
  `You are the REPORT assembler for a full-regression run (run date ${RUN_DATE}, mode ${MODE}).
${DOCS}

Execute runbook Phase 4 EXACTLY as it reads right now — read that section before writing anything; it is the authority on structure and this brief only summarizes it. It calls for a HUMAN-FIRST report: the verdict and the digest come first and must be readable on their own, with the exhaustive per-item audit trail relegated to an appendix. Do not invert that.

1. MERGE THE LOGS (runbook Phase 4's merge step; it is the same in both modes). This run wrote one log per device (${DEVICES.map(d => d.log).join(', ')}). Concatenate them into ${RUN}/results.log in device order, each device's lines in inventory order, leaving the per-device files intact. Where an entry ID appears more than once for a device (the deferred tail appends a second line), the LAST line is authoritative — say so in the appendix header, and verify no DEFERRED status survived the merge.
2. RECONCILE against the inventory before you count anything: recompute the applicable-entry count per device from ${REPO}/docs/regression/inventory.md's \`Platforms:\` fields rather than trusting any number in the runbook, and explicitly call out any applicable entry with NO line at all as a run gap (an unrecorded entry is NOT a SKIP — mislabeling it hides the hole).
3. WRITE ${RUN}/report.md with the runbook's FIVE sections, in this order and no other:
   §1 Verdict + per-device summary — one-line run verdict, then the per-device table (PASS/FAIL/BLOCKED/SKIP counts + a one-line verdict each), the run mode and the wall-clock. One screen, nothing else.
   §2 ISSUES DIGEST — the body of the report, in four subsections: Confirmed app bugs · Potential issues & UX observations · Suite & tooling gaps · Operational hazards. EVERY item in EVERY subsection carries the runbook's seven fields: Title (≤80 chars, prefixed \`<PRIMARY-ENTRY-ID> · \`) · Symptom · Root cause (or "not yet established") · User impact · Severity (Low/Medium/High) · Evidence (entry IDs, screenshot paths, results.log line refs) · Proposed disposition. Quote the entry's Steps/Expected from inventory.md against what was actually observed. §2 is a first cut here — Phase 5 rewrites it and Phase 6 adds card URLs.
   §3 Inventory-drift findings from Phase 1 — every uncovered-surface and stale-entry finding by file/entry ID. State the zero explicitly ("0 forward, 0 reverse") if there are none; a silent section reads like a skipped phase.
   §4 Suspect-driver findings — every entry the driver-bug protocol annotated, what was suspected, and (after Phase 5) how it was adjudicated.
   §5 APPENDIX: full per-item results — every entry, every applicable device, in inventory order, ALWAYS LAST and wrapped in a collapsed \`<details><summary>Full per-item results (218 entries × 3 devices)</summary>\` block. Note in its header that the last line for a (device, entry) pair is authoritative.
4. Fold in ${RUN}/report-draft.md's Phase 0/2 and Phase 1 sections, and append a '## Run learnings (doc feedback)' section listing these VERBATIM — Phase 5 consumes it:
${JSON.stringify(allLearnings).slice(0, 8000)}
5. Orchestrator data — reconcile it against the logs and report any disagreement as a finding rather than trusting either blindly:
   mode=${MODE}; devices=${JSON.stringify(lanes.map(l => ({ device: l.device, shifts: l.shifts, complete: l.complete, recorded: l.recorded, pass: l.pass, fail: l.fail, blocked: l.blocked, skip: l.skip })))}
   setup skips=${JSON.stringify((setup && setup.skips) || [])}; ops notifier null-confirmed=${opsNotifierNull}; servers=${JSON.stringify((setup && setup.servers) || [])}
   self-audit summary: ${String(audit).slice(0, 1500)}
   deferred tail: ${deferredResult ? String(deferredResult.notes || deferredResult.last_entry_id || 'ran').slice(0, 600) : 'none collected'}
6. CLEAN UP THE MACHINE, last: stop ONLY the \`manage.py runserver\` PIDs this run started (${JSON.stringify((setup && setup.servers) || [])} — match the specific PID from \`ps aux | grep '[m]anage.py runserver'\`, NEVER an lsof/port kill, which has collaterally killed the emulator before), leave any pre-existing server this run did not start alone, restore Postgres if the deferred tail left it stopped, and shut down the simulators/emulator.

Return: the verdict line, the per-device summary table as text, the top 5 failures (or 'none'), any run gaps, and the absolute path to report.md.`,
  { label: 'assemble-report', phase: 'Report', model: 'sonnet', effort: 'medium' }
)
log('report assembled')

// ---------------------------------------------------------------------------
// Phase 5 — triage / fold-back
// ---------------------------------------------------------------------------

phase('Triage')
const triage = await agent(
  `You are the TRIAGE + FOLD-BACK agent for a completed full-regression run (run date ${RUN_DATE}). Execute runbook Phase 5 exactly as written — read that section first; this brief summarizes it and the runbook governs.
${DOCS}

INPUTS: ${RUN}/report.md, ${RUN}/results.log and the per-device logs, ${SHOTS}/, ${RUN}/manifest.json, and the run's learnings section. Orchestrator-side fail/suspect IDs: ${JSON.stringify(allFailIds).slice(0, 3000)}.

PART 1 — ADJUDICATE. For every FAIL and every suspect-driver finding, decide which of the runbook 5.1 verdicts it actually is — use these four names exactly, they are the canonical vocabulary and the schema keys mirror them — and say what decided it:
  APP BUG (\`app_bugs\`) — read the actual source in ${REPO} to confirm the observed behavior contradicts the code's intent, not just the inventory's prose. Goes to §2 "Confirmed app bugs" and is filed in Phase 6.
  INVENTORY-EXPECTED-WRONG (\`inventory_corrections\`) — the app is right and the entry's Expected is stale or wrong (per this repo's CLAUDE.md rule, exactly the drift the inventory exists to prevent). Corrected in PART 2, with the runbook's mandatory dated note on the entry.
  DRIVER ARTIFACT (\`driver_artifacts\`) — the failure was manufactured by how it was driven: a stale dump, a pre-filled field, an off-screen control, a playbook trap the shift missed. Goes to §4, NOT §2, and leaves a trap behind in driver-playbook.md.
  FIXTURE/ENVIRONMENT GAP (\`fixture_gaps\`) — nothing is wrong with the app, the entry, or the driving; the state the entry needs is unreachable from the current seed/server. Goes to §2 "Suite & tooling gaps" and gets a Known-BLOCKED row.
Be adversarial about APP BUG: a report full of false alarms is worse than a shorter, trustworthy one. Quote source file:line for anything you promote to one. Also sweep the run's BLOCKEDs for any NEW fixture gap the Known-BLOCKED table does not already explain.
Then complete §2's other two subsections so Phase 6 has the full digest to file: \`ux_observations\` (behavior that matches the code — so it PASSed or was adjudicated not-a-bug — that a reviewer might not want shipped as-is) and \`hazards\` (things that can hurt a person or an environment: real pages fired at the founders, a kill pattern that takes out the emulator), whether or not they were mitigated during the run. Give every §2 item all seven runbook fields, and a title of the shape \`<PRIMARY-ENTRY-ID> · <one line ≤80 chars>\` (use SUITE / HAZARD as the prefix when no entry covers it, and propose the entry ID that should exist).

PART 2 — FOLD BACK INTO THE DOCS. Edit the checked-in docs in ${REPO} so the next run does not re-learn any of this: docs/regression/runbook.md, docs/regression/driver-playbook.md, docs/regression/inventory.md, .claude/skills/full-regression/SKILL.md, and tools/regression/full-regression.workflow.js if this run exposed an orchestration bug (the runbook governs — if the script and the runbook disagree, the SCRIPT is what gets fixed). Fold in: every learning that is a durable fact, new pre-adjudicated "reads as a bug but isn't" cases, new Known-BLOCKED rows with their real reason, corrected commands/paths, and any Expected that (c) showed is stale. Keep each doc's existing voice and structure; do not bolt on a changelog section.
  THE ONE SANCTIONED REPO WRITE: these edits stay UNCOMMITTED. Do not \`git add\`, commit, push, or create a branch — leave the working tree dirty for human review, and list every file you touched. Run outputs under ${RUN}/ still never enter git at all.

Append a '## Phase 5 — triage & fold-back' section to ${RUN}/report.md: the adjudication table (finding → verdict → evidence), the doc edits made, and anything you deliberately left alone.`,
  { label: 'triage-foldback', phase: 'Triage', model: 'opus', effort: 'high', schema: TRIAGE_SCHEMA }
)

const appBugs = (triage && triage.app_bugs) || []
const uxObservations = (triage && triage.ux_observations) || []
const fixtureGaps = (triage && triage.fixture_gaps) || []
const hazards = (triage && triage.hazards) || []
// Everything in report.md §2 is filed, not just the app bugs: runbook 5.3 is
// explicit that suite gaps and hazards are work someone has to do and are the
// reason a chunk of the inventory is permanently BLOCKED.
const digest = [
  ...appBugs.map(d => ({ ...d, category: 'bug' })),
  ...uxObservations.map(d => ({ ...d, category: 'ux-observation' })),
  ...fixtureGaps.map(d => ({ ...d, category: 'suite-gap' })),
  ...hazards.map(d => ({ ...d, category: 'hazard' })),
]
log(
  'triage: ' +
    appBugs.length +
    ' app bug(s), ' +
    uxObservations.length +
    ' ux observation(s), ' +
    fixtureGaps.length +
    ' suite gap(s), ' +
    hazards.length +
    ' hazard(s), ' +
    (((triage && triage.driver_artifacts) || []).length) +
    ' driver artifact(s), ' +
    (((triage && triage.doc_edits) || []).length) +
    ' doc edit(s) (uncommitted)'
)

// ---------------------------------------------------------------------------
// Phase 6 — Trello filing (guarded: only if a Trello tool exists)
// ---------------------------------------------------------------------------

let trello = null
if (digest.length === 0) {
  log('Phase 6 skipped: triage produced an empty §2 digest, so there is nothing to file')
} else {
  phase('Trello')
  trello = await agent(
    `You are the TRELLO FILING agent for a completed full-regression run (run date ${RUN_DATE}). Execute runbook Phase 6 exactly as written — read that section first; the runbook governs and this brief only summarizes it.

GUARD, DO THIS BEFORE ANYTHING ELSE: run ToolSearch with query "trello" (and, if that returns nothing useful, "+trello card board") to see whether a Trello MCP tool is actually available in this session. If NO Trello tool schema comes back, return \`{"trello_available": false}\` with a one-line note and DO NOTHING ELSE — do not write a file, do not improvise a substitute tracker, do not file into some other system, do not ask anyone. The absence of Trello is a no-op, not a fallback.

If a Trello tool IS available, the board contract is:
- BOARD \`Arcana Regressions\`. ONE LIST PER RUN named \`Run ${RUN_DATE}\` (matching the run folder). A board-level \`Done\` list persists across runs; this run never moves a card to Done.
- ONE CARD PER §2 DIGEST ITEM below — every subsection, not only the app bugs: suite gaps and hazards are filed too, because they are work someone has to do. Group entry IDs that share a root cause into one card; never one card per FAILed entry.
- TITLE: the item's \`title\` VERBATIM — ≤80 chars, prefixed with the primary entry ID and a \` · \` (e.g. \`PROFILE-22 · Edit Profile save blocked by optional fields\`; \`SUITE · No 5xx fault-injection path — ERR-08/ERR-10 permanently BLOCKED\`). Arcana's voice: direct, declarative, no em/en dashes.
- DESCRIPTION, in this order: 1. Symptom  2. Root cause (or "not yet established")  3. User impact  4. Repro steps, concrete enough to follow without the run folder  5. Evidence paths — ABSOLUTE paths to screenshots under ${SHOTS}/ and to ${RUN}/results.log  6. Entry IDs and learning references  7. Proposed disposition. Also link ${RUN}/report.md.
- LABELS, three groups, always: Severity \`Low\`/\`Medium\`/\`High\` (the run's argued severity; if Cole has re-labelled an existing card, his label is authoritative and must NOT be reverted) · Category \`bug\`/\`ux-observation\`/\`suite-gap\`/\`hazard\` (each item below carries its \`category\`) · Platform \`ios26\`/\`ios18\`/\`android\`/\`all\`. MCP CONSTRAINT (verified 2026-08-11): the Trello MCP can attach existing labels (trelloWriteCard action=attach_label) but CANNOT create or rename labels. So first trelloReadBoard action=list_labels; attach whatever named labels exist; for severity, if no named labels exist, use the board's default color labels red=High / orange=Medium / yellow=Low; carry any group with no board label as the description's FIRST LINE in the fixed form \`**[category] · [platform] · severity: X**\`. Board: https://trello.com/b/xfX4x4Vc/arcana-regressions (resolve ARIs via trelloReadBoard action=get with that URL; do not hardcode ARIs). Descriptions cap at 2048 chars: trim Evidence/Refs first, never Symptom/Root cause/Impact/Repro/Disposition.
- DEDUP, before creating anything: search the board's open cards for the same primary entry ID. MATCH FOUND AND OPEN → do NOT create a card; annotate it with \`seen again in Run ${RUN_DATE}\` plus anything that changed (new device, new severity argument, new evidence path), and report the card URL so the §2 item can link it. MATCH FOUND BUT IN \`Done\` → the issue regressed: create a new card AND annotate the Done card pointing at it. NO MATCH → create the card in this run's list. ANNOTATION MECHANICS: the Trello MCP has NO comment action — annotate by trelloReadCard (fetch desc) then trelloWriteCard action=update with the original desc plus an appended '---' + '**Run ${RUN_DATE}:** seen again …' line; append only, never overwrite Cole's edits; respect the 2048-char cap by trimming old Evidence/Refs first.

§2 DIGEST ITEMS (from Phase 5 triage — file these and nothing else):
${JSON.stringify(digest).slice(0, 6000)}

Then close the loop: append a '## Phase 6 — filing' section to ${RUN}/report.md AND write each card's URL back onto its §2 item, so no digest item is left without either a card URL or the "no Trello MCP available" note.`,
    { label: 'trello-filing', phase: 'Trello', model: 'sonnet', effort: 'medium', schema: TRELLO_SCHEMA }
  )
  if (trello && trello.trello_available) {
    log('Trello: filed ' + (((trello && trello.cards) || []).length) + ' card(s)')
  } else {
    log('Phase 6 no-op: no Trello MCP tool available in this session — defects are recorded in report.md only')
  }
}

// ---------------------------------------------------------------------------
// summary
// ---------------------------------------------------------------------------

return {
  runDate: RUN_DATE,
  mode: MODE,
  runFolder: RUN,
  setup: {
    ready: setupReady,
    devices_ready: (setup && setup.devices_ready) || [],
    ops_notifier_null: opsNotifierNull,
    skips: (setup && setup.skips) || [],
  },
  audit: String(audit).slice(0, 800),
  devices: lanes.map(l => ({
    device: l.device,
    shifts: l.shifts,
    complete: l.complete,
    recorded: l.recorded,
    pass: l.pass,
    fail: l.fail,
    blocked: l.blocked,
    skip: l.skip,
    fail_ids: l.fail_ids,
  })),
  deferred: allDeferred,
  learnings: allLearnings,
  triage: triage
    ? {
        digest: digest.map(d => d.category + '/' + d.severity + ': ' + d.title + ' (' + (d.entry_ids || []).join(', ') + ')'),
        app_bug_count: appBugs.length,
        driver_artifacts: triage.driver_artifacts || [],
        inventory_corrections: triage.inventory_corrections || [],
        doc_edits: triage.doc_edits || [],
      }
    : null,
  trello: trello ? { available: !!trello.trello_available, cards: trello.cards || [] } : 'skipped',
  report: String(report).slice(0, 2000),
}
