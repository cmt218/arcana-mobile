#!/usr/bin/env python3
"""
Measure how optically centred a button label is.

Cole's standing complaint about generated UI is labels that sit a hair high or
left. The corrections applied in ui/ErrorState.kt are principled (League
Spartan's caps sit ~0.0914em high in the em box; letter-spacing is applied after
the last glyph too, so centring the measured width pushes glyphs left by half a
letter-space) but principle is not proof. This measures the rendered pixels.

Method: find the button by its solid fill colour, then inside that region find
the ink (pixels that differ from the fill) and compare the ink's bounding-box
centre with the button's own centre. Target: under 0.5px at 1x, i.e. under
~1.5px on a 3x Retina screenshot.

    measure_centering.py <screenshot.png> <fill_hex> <scale> [x0 y0 x1 y1]

The crop box is in SCREENSHOT PIXELS and is effectively required on a real
screen: the brand fill (Moss) also appears in the tab bar, the wordmark and
elsewhere, so an unconstrained search silently unions them into one enormous
"button" and reports a meaningless 0.0px offset.
"""
import sys
from PIL import Image


def main() -> int:
    path, fill_hex = sys.argv[1], sys.argv[2].lstrip("#")
    scale = float(sys.argv[3]) if len(sys.argv) > 3 else 3.0
    fill = tuple(int(fill_hex[i:i + 2], 16) for i in (0, 2, 4))

    img = Image.open(path).convert("RGB")
    W, H = img.size
    cx0, cy0, cx1, cy1 = 0, 0, W - 1, H - 1
    if len(sys.argv) >= 8:
        cx0, cy0, cx1, cy1 = (int(v) for v in sys.argv[4:8])
    px = img.load()

    def is_fill(p, tol=18):
        return all(abs(p[i] - fill[i]) <= tol for i in range(3))

    # 1. Fill-coloured pixels inside the crop => the button box.
    xs, ys = [], []
    for y in range(cy0, cy1 + 1):
        for x in range(cx0, cx1 + 1):
            if is_fill(px[x, y]):
                xs.append(x)
                ys.append(y)
    if not xs:
        print(f"no pixels matching #{fill_hex} found in crop")
        return 1
    bx0, bx1, by0, by1 = min(xs), max(xs), min(ys), max(ys)

    # Sanity: a plausible button, not a union of unrelated brand-coloured things.
    w_pt, h_pt = (bx1 - bx0) / scale, (by1 - by0) / scale
    if h_pt > 120 or w_pt > 400:
        print(f"WARNING: region is {w_pt:.0f}x{h_pt:.0f}pt — too big for a button.")
        print("The crop is probably unioning several fill-coloured elements.")
        return 1

    # 2. Ink = non-fill pixels strictly inside the box (inset to skip the
    #    rounded corners, which are anti-aliased against the page background).
    inset = int(6 * scale)
    ix0 = iy0 = 10 ** 9
    ix1 = iy1 = -1
    for y in range(by0 + inset, by1 - inset + 1):
        for x in range(bx0 + inset, bx1 - inset + 1):
            if not is_fill(px[x, y], tol=60):
                ix0, ix1 = min(ix0, x), max(ix1, x)
                iy0, iy1 = min(iy0, y), max(iy1, y)
    if ix1 < 0:
        print("no ink found inside the button")
        return 1

    box_cx, box_cy = (bx0 + bx1) / 2, (by0 + by1) / 2
    ink_cx, ink_cy = (ix0 + ix1) / 2, (iy0 + iy1) / 2
    dx, dy = ink_cx - box_cx, ink_cy - box_cy

    print(f"button box : x {bx0}-{bx1}  y {by0}-{by1}   "
          f"({(bx1-bx0)/scale:.1f} x {(by1-by0)/scale:.1f} pt)")
    print(f"label ink  : x {ix0}-{ix1}  y {iy0}-{iy1}")
    print(f"box centre : ({box_cx:.1f}, {box_cy:.1f})")
    print(f"ink centre : ({ink_cx:.1f}, {ink_cy:.1f})")
    print()
    for axis, d in (("horizontal", dx), ("vertical", dy)):
        pts = d / scale
        # Positive dy = ink sits BELOW centre; negative = the classic "too high".
        if axis == "vertical":
            word = "low" if d > 0 else "high"
        else:
            word = "right" if d > 0 else "left"
        verdict = "PASS" if abs(pts) < 0.5 else "OFF"
        print(f"{axis:11}: {d:+.1f}px ({pts:+.2f}pt) {word:5} -> {verdict}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
