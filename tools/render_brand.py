"""Render the Tide mark from the same geometry as brand/tide-mark.svg, so the
shape can be looked at rather than assumed.

The output sheet is the check that matters: 48px is roughly what a home screen
shows, and a mark only verified at 512 is not verified. Regenerate and look at
it after any geometry change.

    python tools/render_brand.py
"""
from PIL import Image, ImageDraw
import math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "brand")
CANVAS, SS = 108.0, 8                  # adaptive-icon grid, 8x supersample

ACCENT = (63, 169, 139, 255)           # #3FA98B
GROUND = (14, 17, 20, 255)             # #0E1114
LIGHT = (232, 237, 240, 255)

WAVE_L, WAVE_R = 20.0, 88.0            # one full tidal cycle
DATUM_L, DATUM_R = 21.0, 87.0          # chart datum, overhangs the curve
MID_Y, AMP = 54.0, 18.0
WAVE_W, DATUM_W = 11.0, 6.0


def sine(n=240):
    pts = []
    for i in range(n + 1):
        t = i / n
        pts.append((WAVE_L + (WAVE_R - WAVE_L) * t,
                    MID_Y - AMP * math.sin(2 * math.pi * t)))
    return pts


def stroke(d, pts, w, s, colour):
    sp = [(x * s, y * s) for x, y in pts]
    d.line(sp, fill=colour, width=max(1, int(round(w * s))), joint="curve")
    r = w * s / 2.0
    for x, y in (sp[0], sp[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=colour)


def draw(size, colour, bg=None):
    n = size * SS
    s = n / CANVAS
    img = Image.new("RGBA", (n, n), bg if bg else (0, 0, 0, 0))
    lay = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    stroke(d, [(DATUM_L, MID_Y), (DATUM_R, MID_Y)], DATUM_W, s, colour)
    stroke(d, sine(), WAVE_W, s, colour)
    img.alpha_composite(lay)
    return img.resize((size, size), Image.LANCZOS)


def mask(img, squircle=False):
    n = img.size[0]
    m = Image.new("L", (n * SS, n * SS), 0)
    dd = ImageDraw.Draw(m)
    box = [0, 0, n * SS - 1, n * SS - 1]
    if squircle:
        dd.rounded_rectangle(box, radius=int(n * SS * 0.24), fill=255)
    else:
        dd.ellipse(box, fill=255)
    out = img.copy()
    out.putalpha(m.resize((n, n), Image.LANCZOS))
    return out


def sheet():
    cell, pad = 192, 16
    cells = [(draw(192, ACCENT, GROUND), "192px"),
             (draw(96, ACCENT, GROUND), "96px"),
             (draw(48, ACCENT, GROUND), "48px, homescreen"),
             (mask(draw(192, ACCENT, GROUND)), "circle mask"),
             (mask(draw(192, ACCENT, GROUND), True), "squircle mask"),
             (draw(192, LIGHT, GROUND), "mono on dark"),
             (draw(192, GROUND, LIGHT), "mono on light")]
    w = len(cells) * (cell + pad) + pad
    sh = Image.new("RGBA", (w, cell + pad * 2 + 24), (28, 28, 30, 255))
    dd = ImageDraw.Draw(sh)
    x = pad
    for img, lab in cells:
        c = img if img.size[0] == cell else img.resize((cell, cell), Image.NEAREST)
        sh.alpha_composite(c, (x, pad))
        dd.text((x, pad + cell + 6), lab, fill=(180, 186, 190, 255))
        x += cell + pad
    return sh


if __name__ == "__main__":
    for sz in (512, 192, 96, 48):
        draw(sz, ACCENT, GROUND).save(os.path.join(OUT, f"tide-{sz}.png"))
    draw(512, ACCENT).save(os.path.join(OUT, "tide-512-transparent.png"))
    sheet().save(os.path.join(OUT, "tide-icon-tests.png"))
    top = MID_Y - AMP - WAVE_W / 2
    bot = MID_Y + AMP + WAVE_W / 2
    print(f"bounds x {DATUM_L - DATUM_W/2:.1f}..{DATUM_R + DATUM_W/2:.1f}"
          f"  y {top:.1f}..{bot:.1f}  (safe zone 18..90)")
