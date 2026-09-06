#!/usr/bin/env python3
"""
Sample a screenshot's background band for the atmosphere spec.

Crop a region with no foreground UI on it (e.g. the empty area under the Home
cards) and check it against the Task 4 atmosphere spec: luminance stays within
205..245, no pixel has red > green + 2 (the brown check, catching flat Stone),
and no pixel exceeds luminance 250 (the white check).

    atmosphere_sample.py <shot.png> <x0 y0 x1 y1>

Coordinates are screenshot pixels in measure_centering.py's order, but the
crop is half-open (x1/y1 exclusive) where that tool's box is inclusive.
"""
import sys
from PIL import Image


def main() -> int:
    if len(sys.argv) != 6:
        print(f"usage: {sys.argv[0]} <shot.png> <x0 y0 x1 y1>")
        return 2
    path = sys.argv[1]
    try:
        x0, y0, x1, y1 = (int(v) for v in sys.argv[2:6])
    except ValueError:
        print(f"usage: {sys.argv[0]} <shot.png> <x0 y0 x1 y1>")
        return 2

    if x1 <= x0 or y1 <= y0:
        print(f"empty crop: x0 {x0} x1 {x1} y0 {y0} y1 {y1}")
        return 2
    src = Image.open(path).convert("RGB")
    w, h = src.size
    if x0 >= w or y0 >= h or x1 > w or y1 > h:
        # PIL pads an out-of-bounds crop with black, which reads as a real
        # measurement rather than the mistyped box it is.
        print(f"crop {x0} {y0} {x1} {y1} exceeds image {w}x{h}")
        return 2
    img = src.crop((x0, y0, x1, y1))
    # getdata() is deprecated, removal targeted for Pillow 14; the fallback keeps
    # this runnable on Pillow 11 and earlier, where the new name does not exist.
    px = getattr(img, "get_flattened_data", img.getdata)()
    lums = [0.299 * r + 0.587 * g + 0.114 * b for r, g, b in px]
    brown = sum(1 for r, g, b in px if r > g + 2)
    white = sum(1 for l in lums if l > 250)
    lo, hi = min(lums), max(lums)
    print(f"luminance {lo:.0f}..{hi:.0f}  brown px {brown}  white px {white}  n {len(lums)}")
    ok = 205 <= lo and hi <= 245 and brown == 0 and white == 0
    print("PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
