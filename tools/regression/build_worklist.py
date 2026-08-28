#!/usr/bin/env python3
"""Emit per-device work lists from inventory.md so a driving shift never reads
the whole inventory.

Re-reading runbook + playbook + inventory (~5k lines) at the top of every shift
was the single largest cost in the 2026-08-27 run: ~150k tokens per shift,
~15 shifts, most of a 6M-token bill spent re-reading prose that had not changed.
A shift needs the entries it is about to drive, not the corpus.

    python3 tools/regression/build_worklist.py --out ~/arcana-regression-runs/<date>
    python3 tools/regression/build_worklist.py --out <dir> --tier 1

Writes worklist-{ios26,ios18,android}.tsv — one row per applicable entry, in
inventory order: id, tier, platforms, steps, expected, source. Tab-separated so
a shift can cut -f without a parser. Also writes worklist-summary.txt.
"""
import argparse
import os
import re
import sys

DEVICE_TAGS = {
    'ios26': ('shared', 'ios-only', 'ios26-only'),
    'ios18': ('shared', 'ios-only', 'ios18-only'),
    'android': ('shared', 'android-only'),
}

# Tier 1 = release-blocking: a member cannot sign in, onboard, browse, book or
# cancel if these regress. Everything else is Tier 2. Whole areas where every
# entry sits on that critical path are listed by area; the mixed areas are
# listed by explicit id so the split is auditable rather than a guess.
TIER1_AREAS = {'LAUNCH', 'AUTH', 'SIGNUP', 'FAV'}
TIER1_IDS = {
    # Booking, cancellation and the class detail that gates them.
    'CLASS-01', 'CLASS-02', 'CLASS-03', 'CLASS-05', 'CLASS-06', 'CLASS-08',
    'CLASS-10', 'CLASS-12', 'CLASS-13', 'CLASS-14', 'CLASS-15', 'CLASS-16',
    'CLASS-17', 'CLASS-18', 'CLASS-19', 'CLASS-20', 'CLASS-21', 'CLASS-22',
    'CLASS-25',
    # Schedule: reaching a class at all.
    'SCHED-01', 'SCHED-02', 'SCHED-03', 'SCHED-04', 'SCHED-07', 'SCHED-08',
    'SCHED-11', 'SCHED-14', 'SCHED-16', 'SCHED-18',
    # Home: credits, next-up, the booking entry point.
    'HOME-01', 'HOME-02', 'HOME-04', 'HOME-05', 'HOME-12', 'HOME-13',
    'HOME-16', 'HOME-19',
    # Profile: session end and the member card.
    'PROFILE-01', 'PROFILE-02', 'PROFILE-04', 'PROFILE-12', 'PROFILE-19',
    # Session/transport failure handling — the class of bug this suite exists for.
    'ERR-01', 'ERR-02', 'ERR-03', 'ERR-04', 'ERR-05', 'ERR-06', 'ERR-07',
    'ERR-08', 'ERR-09', 'ERR-10', 'ERR-11', 'ERR-21', 'ERR-22',
    # Navigation spine.
    'NAV-01', 'NAV-02', 'NAV-04', 'NAV-05', 'NAV-13',
}

FIELD_RE = re.compile(r'^- \*\*(Steps|Expected|Source|Platforms):\*\*\s*(.*)$')
HEAD_RE = re.compile(r'^### ([A-Z]+-[0-9]+[a-z]?)\s*(?:—|-)?\s*(.*)$')


def parse(path):
    entries, cur = [], None
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        m = HEAD_RE.match(line)
        if m:
            if cur:
                entries.append(cur)
            cur = {'id': m.group(1), 'title': m.group(2).strip(),
                   'Steps': '', 'Expected': '', 'Source': '', 'Platforms': ''}
            continue
        if cur:
            f = FIELD_RE.match(line)
            if f:
                cur[f.group(1)] = f.group(2).strip()
    if cur:
        entries.append(cur)
    return entries


def tier_of(entry_id):
    area = entry_id.split('-')[0]
    if area in TIER1_AREAS or entry_id in TIER1_IDS:
        return 1
    return 2


def applies(platforms, device):
    # Match on the leading token only: the field often carries a parenthetical.
    tag = platforms.split('(')[0].strip().lower().rstrip('.,')
    tag = tag.split()[0] if tag else 'shared'
    return tag in DEVICE_TAGS[device]


def clean(s):
    return s.replace('\t', ' ').replace('\r', ' ').strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inventory', default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), '..', '..',
        'docs', 'regression', 'inventory.md'))
    ap.add_argument('--out', required=True)
    ap.add_argument('--tier', type=int, choices=[1, 2], default=None,
                    help='only emit this tier; omit for the full inventory')
    a = ap.parse_args()

    entries = parse(a.inventory)
    if not entries:
        sys.exit('no entries parsed from %s — check the ### <ID> format' % a.inventory)
    os.makedirs(a.out, exist_ok=True)

    retired = [e for e in entries if 'RETIRED' in e['title'].upper()]
    live = [e for e in entries if e not in retired]
    summary = ['inventory: %d entries (%d live, %d retired)'
               % (len(entries), len(live), len(retired)),
               'tier filter: %s' % (a.tier or 'none (all)')]

    for device in ('ios26', 'ios18', 'android'):
        rows = []
        for e in live:
            if not applies(e['Platforms'], device):
                continue
            t = tier_of(e['id'])
            if a.tier and t != a.tier:
                continue
            rows.append('\t'.join([e['id'], str(t), e['Platforms'].split('(')[0].strip(),
                                   clean(e['title']), clean(e['Steps']),
                                   clean(e['Expected']), clean(e['Source'])]))
        p = os.path.join(a.out, 'worklist-%s.tsv' % device)
        with open(p, 'w', encoding='utf-8') as fh:
            fh.write('id\ttier\tplatforms\ttitle\tsteps\texpected\tsource\n')
            fh.write('\n'.join(rows) + '\n')
        t1 = sum(1 for r in rows if r.split('\t')[1] == '1')
        summary.append('%-8s %3d applicable (%d tier-1, %d tier-2) -> %s'
                       % (device, len(rows), t1, len(rows) - t1, os.path.basename(p)))

    txt = '\n'.join(summary)
    open(os.path.join(a.out, 'worklist-summary.txt'), 'w', encoding='utf-8').write(txt + '\n')
    print(txt)


if __name__ == '__main__':
    main()
