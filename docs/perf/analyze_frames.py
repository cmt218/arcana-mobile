#!/usr/bin/env python3
"""Analyze a FrameTimeRecorder CSV over one or more wall-clock windows.

Usage: analyze_frames.py <frame_log.csv> <label> <start_epoch> <end_epoch> [<label> <start> <end> ...]

Frame deltas are computed from media timestamps (monotonic). The nominal frame
interval is the mode of deltas rounded to 1ms (60Hz sim -> ~16.7ms). A frame is:
  - a "hitch" if delta > 1.5x nominal (at least one frame missed)
  - "severe" if delta > 2.5x nominal
Dropped-frame estimate: sum over frames of (round(delta/nominal) - 1), i.e. how
many vsync slots produced no new frame while content was animating.
"""
import csv
import sys
from collections import Counter


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    idx = min(len(sorted_vals) - 1, int(round(p / 100 * (len(sorted_vals) - 1))))
    return sorted_vals[idx]


def analyze(rows, label, start, end):
    frames = [(w, m) for (w, m) in rows if start <= w <= end]
    if len(frames) < 30:
        print(f"{label}: too few frames ({len(frames)})")
        return None
    deltas = [
        (frames[i][1] - frames[i - 1][1]) * 1000.0 for i in range(1, len(frames))
    ]
    # Nominal interval: mode of rounded deltas (robust to hitches)
    mode_ms = Counter(round(d) for d in deltas).most_common(1)[0][0]
    nominal = float(mode_ms)
    s = sorted(deltas)
    hitches = [d for d in deltas if d > 1.5 * nominal]
    severe = [d for d in deltas if d > 2.5 * nominal]
    dropped = sum(max(0, round(d / nominal) - 1) for d in deltas)
    total_slots = sum(max(1, round(d / nominal)) for d in deltas)
    duration = frames[-1][0] - frames[0][0]
    result = {
        "label": label,
        "duration_s": duration,
        "frames": len(frames),
        "nominal_ms": nominal,
        "p50": pct(s, 50),
        "p95": pct(s, 95),
        "p99": pct(s, 99),
        "max": s[-1],
        "hitch_count": len(hitches),
        "severe_count": len(severe),
        "dropped_frames": dropped,
        "total_slots": total_slots,
        "dropped_pct": 100.0 * dropped / total_slots if total_slots else 0.0,
        "hitch_time_ms": sum(d - nominal for d in hitches),
    }
    print(
        f"{label}: {duration:.1f}s, {len(frames)} frames @ nominal {nominal:.0f}ms | "
        f"p50 {result['p50']:.1f} p95 {result['p95']:.1f} p99 {result['p99']:.1f} max {result['max']:.0f}ms | "
        f"hitches {len(hitches)} (severe {len(severe)}) | "
        f"dropped {dropped}/{total_slots} slots = {result['dropped_pct']:.2f}% | "
        f"hitch time {result['hitch_time_ms']:.0f}ms"
    )
    return result


def main():
    path = sys.argv[1]
    rows = []
    with open(path) as f:
        reader = csv.reader(f)
        next(reader)
        for r in reader:
            try:
                rows.append((float(r[0]), float(r[1])))
            except (ValueError, IndexError):
                continue
    args = sys.argv[2:]
    out = []
    for i in range(0, len(args), 3):
        label, start, end = args[i], float(args[i + 1]), float(args[i + 2])
        res = analyze(rows, label, start, end)
        if res:
            out.append(res)
    return out


if __name__ == "__main__":
    main()
