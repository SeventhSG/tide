"""Render the Keel mark from explicit geometry so the shape can be looked at
rather than assumed. v5: hull shell with flat deck edges, plus a stubby keel blade. Shipped.

v1 failed: a uniform-weight arc with a short centre stroke read as a tuning
fork. Hull sections are wide and shallow, and the keel must be clearly
subordinate to the hull it hangs from.
"""
from PIL import Image, ImageDraw
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "brand")
CANVAS = 108.0          # Android adaptive-icon grid, safe zone 18..90
SS = 8                  # supersample factor for clean curves

ACCENT = (63, 169, 139, 255)    # #3FA98B starboard green
GROUND = (14, 17, 20, 255)      # #0E1114
LIGHT = (232, 237, 240, 255)

# Hull section, drawn as a shell of roughly constant wall thickness rather
# than a wedge. Outer curve is the wetted hull, inner curve the inside of the
# shell, and both terminate at the same deck height, leaving a flat deck edge
# at each end instead of a knife point. Beam is wider than draft, as on a hull.
DECK_Y = 34.0
OUT_L, OUT_R = 20.0, 88.0
IN_L, IN_R = 28.0, 80.0
CTRL_OUTER = 86.0       # outer centre bottom y = 60, so draft 26 against beam 68
CTRL_INNER = 62.0       # inner centre bottom y = 48, so wall 12 thick

# Keel blade: narrow, subordinate, rooted inside the shell wall.
FIN_TOP_Y, FIN_BOT_Y = 52.0, 80.0
FIN_TOP_W, FIN_BOT_W = 9.0, 6.0


def quad(p0, c, p2, n=160):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((u*u*p0[0] + 2*u*t*c[0] + t*t*p2[0],
                    u*u*p0[1] + 2*u*t*c[1] + t*t*p2[1]))
    return out


def hull_polygon():
    outer = quad((OUT_L, DECK_Y), (54, CTRL_OUTER), (OUT_R, DECK_Y))
    inner = quad((IN_L, DECK_Y), (54, CTRL_INNER), (IN_R, DECK_Y))
    return outer + inner[::-1]


def fin_polygon():
    ht, hb = FIN_TOP_W / 2, FIN_BOT_W / 2
    return [(54 - ht, FIN_TOP_Y), (54 + ht, FIN_TOP_Y),
            (54 + hb, FIN_BOT_Y), (54 - hb, FIN_BOT_Y)]


def draw_mark(size, colour, bg=None):
    n = size * SS
    s = n / CANVAS
    img = Image.new("RGBA", (n, n), bg if bg else (0, 0, 0, 0))
    layer = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    sc = lambda pts: [(x * s, y * s) for x, y in pts]

    d.polygon(sc(fin_polygon()), fill=colour)
    r = FIN_BOT_W / 2 * s                       # round the blade tip
    cx, cy = 54 * s, FIN_BOT_Y * s
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=colour)
    d.polygon(sc(hull_polygon()), fill=colour)

    img.alpha_composite(layer)
    return img.resize((size, size), Image.LANCZOS)


def mask_circle(img):
    n = img.size[0]
    m = Image.new("L", (n * SS, n * SS), 0)
    ImageDraw.Draw(m).ellipse([0, 0, n * SS - 1, n * SS - 1], fill=255)
    out = img.copy(); out.putalpha(m.resize((n, n), Image.LANCZOS)); return out


def mask_squircle(img):
    n = img.size[0]
    m = Image.new("L", (n * SS, n * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [0, 0, n * SS - 1, n * SS - 1], radius=int(n * SS * 0.24), fill=255)
    out = img.copy(); out.putalpha(m.resize((n, n), Image.LANCZOS)); return out


def contact_sheet():
    cell, pad = 192, 16
    cells = [(draw_mark(192, ACCENT, GROUND), "192px"),
             (draw_mark(96, ACCENT, GROUND), "96px"),
             (draw_mark(48, ACCENT, GROUND), "48px, homescreen"),
             (mask_circle(draw_mark(192, ACCENT, GROUND)), "circle mask"),
             (mask_squircle(draw_mark(192, ACCENT, GROUND)), "squircle mask"),
             (draw_mark(192, LIGHT, GROUND), "mono on dark"),
             (draw_mark(192, GROUND, LIGHT), "mono on light")]
    w = len(cells) * (cell + pad) + pad
    sheet = Image.new("RGBA", (w, cell + pad * 2 + 24), (28, 28, 30, 255))
    d = ImageDraw.Draw(sheet)
    x = pad
    for img, lab in cells:
        c = img if img.size[0] == cell else img.resize((cell, cell), Image.NEAREST)
        sheet.alpha_composite(c, (x, pad))
        d.text((x, pad + cell + 6), lab, fill=(180, 186, 190, 255))
        x += cell + pad
    return sheet


if __name__ == "__main__":
    for sz in (512, 192, 96, 48):
        draw_mark(sz, ACCENT, GROUND).save(os.path.join(OUT, f"keel-{sz}.png"))
    draw_mark(512, ACCENT).save(os.path.join(OUT, "keel-512-transparent.png"))
    contact_sheet().save(os.path.join(OUT, "keel-icon-tests.png"))
    ys = [y for _, y in hull_polygon()] + [y for _, y in fin_polygon()]
    xs = [x for x, _ in hull_polygon()]
    print(f"bounds x {min(xs):.1f}..{max(xs):.1f}  y {min(ys):.1f}..{max(ys) + FIN_BOT_W/2:.1f}"
          f"  (safe zone 18..90)")
