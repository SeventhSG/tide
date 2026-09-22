"""Prototype: deep ocean ground plus Apple-style continuous corners.

Renders the Today screen two ways so the tradeoff is visible rather than argued:
content sitting straight on the ocean, and content sitting on a scrim over it.
Also renders a corner comparison, because the difference between a circular
corner and a continuous one is the whole point and it is easy to miss in prose.
"""
from PIL import Image, ImageDraw, ImageFilter
import numpy as np
import math, os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "images")
W, H = 760, 1500

SURFACE      = (0x07, 0x10, 0x16)
RAISED       = (0x0E, 0x1C, 0x24)
HAIRLINE     = (0x1B, 0x30, 0x38)
TEXT         = (0xE4, 0xF2, 0xF0)
MUTED        = (0x7F, 0x9A, 0x9C)
ACCENT       = (0x3F, 0xD3, 0xB6)
WARNING      = (0xD2, 0x95, 0x2F)
CRITICAL     = (0xE0, 0x5A, 0x4E)

SHALLOW = np.array([0x14, 0x3C, 0x44], float)   # near the surface
ABYSS   = np.array([0x03, 0x07, 0x0B], float)   # at depth


# ----------------------------------------------------------------- squircle
def squircle_points(w, h, r, n=5.0, seg=30):
    """Superellipse corners. n=2 is an ordinary circular corner, n~5 is Apple-ish.

    Each corner is swept from its horizontal tangent point to its vertical one in
    a consistent clockwise direction, otherwise the edges cross and the shape
    comes out as a parallelogram.
    """
    import math
    # cx, cy, sx, sy, reverse
    corners = [(r,     r,     -1, -1, False),   # top left
               (w - r, r,      1, -1, True),    # top right
               (w - r, h - r,  1,  1, False),   # bottom right
               (r,     h - r, -1,  1, True)]    # bottom left
    pts = []
    for cx, cy, sx, sy, rev in corners:
        arc = []
        for i in range(seg + 1):
            t = (i / seg) * (math.pi / 2)
            x = cx + sx * r * (math.cos(t) ** (2.0 / n))
            y = cy + sy * r * (math.sin(t) ** (2.0 / n))
            arc.append((x, y))
        pts.extend(reversed(arc) if rev else arc)
    return pts


def squircle_mask(w, h, r, n=5.0, ss=4):
    m = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(m).polygon(squircle_points(w * ss, h * ss, r * ss, n), fill=255)
    return m.resize((w, h), Image.LANCZOS)


# -------------------------------------------------------------------- ocean
def ocean(w, h, seed=11):
    rng = np.random.default_rng(seed)
    yy = np.linspace(0, 1, h)[:, None]
    # depth gradient, steeper near the top so the surface glow is localised
    t = yy ** 0.75
    img = (SHALLOW[None, None, :] * (1 - t)[:, :, None] +
           ABYSS[None, None, :] * t[:, :, None])
    img = np.repeat(img, w, axis=1)

    X = np.linspace(0, 1, w)[None, :]
    Y = np.linspace(0, 1, h)[:, None]

    # god rays: soft angled shafts, strongest at the surface, gone by mid depth
    rays = np.zeros((h, w))
    for cx, wd, amp in [(0.18, .055, .55), (0.36, .030, .40), (0.52, .075, .75),
                        (0.71, .040, .45), (0.88, .025, .30)]:
        d = X + (Y * 0.30) - cx                       # slant with depth
        rays += amp * np.exp(-(d ** 2) / (2 * wd ** 2))
    rays *= np.clip(1.0 - Y * 2.1, 0, 1) ** 1.6
    img += rays[:, :, None] * np.array([18, 52, 50])

    # caustics: interfering sine fields, surface only
    c = (np.sin(X * 34 + Y * 11) * np.sin(X * 17 - Y * 23) *
         np.sin(X * 9 + Y * 41 + 1.3))
    c = np.clip(c, 0, 1) ** 2.2
    c *= np.clip(1.0 - Y * 2.6, 0, 1) ** 2
    img += c[:, :, None] * np.array([26, 74, 68])

    # suspended particulate, denser with depth
    n_p = 900
    px_ = rng.uniform(0, w, n_p).astype(int)
    py_ = (rng.power(1.4, n_p) * h).astype(int)
    for x, y in zip(px_, py_):
        img[max(0, y - 1):y + 1, max(0, x - 1):x + 1] += 16

    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")

    # bubbles, drawn in a separate pass so they can glow
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    for _ in range(46):
        bx, by = rng.uniform(0, w), rng.uniform(0, h)
        r = rng.uniform(1.6, 8.0)
        a = int(np.interp(by, [0, h], [70, 26]))
        d.ellipse([bx - r, by - r, bx + r, by + r], outline=(180, 245, 235, a),
                  width=max(1, int(r * .34)))
        d.ellipse([bx - r * .42, by - r * .55, bx - r * .06, by - r * .2],
                  fill=(225, 255, 250, int(a * 1.5)))
    lay = lay.filter(ImageFilter.GaussianBlur(0.5))
    out = Image.alpha_composite(out.convert("RGBA"), lay)
    return out.convert("RGB")


# ----------------------------------------------------------------------- UI
def content(base, scrim):
    im = base.copy()
    d = ImageDraw.Draw(im, "RGBA")
    pad = 40

    def panel(y0, y1, alpha):
        if not scrim:
            return
        w = W - pad * 2
        h = y1 - y0
        m = squircle_mask(w, h, 46, n=5.0)
        tile = Image.new("RGBA", (w, h), RAISED + (alpha,))
        blurred = im.crop((pad, y0, pad + w, y0 + h)).filter(ImageFilter.GaussianBlur(14))
        cell = Image.alpha_composite(blurred.convert("RGBA"), tile)
        im.paste(cell, (pad, y0), m)

    d.text((pad, 60), "TODAY", fill=MUTED)

    panel(100, 250, 214)
    d.text((pad + 28, 130), "No session today.", fill=TEXT)
    d.text((pad + 28, 164), "Last was Pull A, 2 days ago.", fill=MUTED)
    d.rounded_rectangle([pad + 28, 200, pad + 250, 232], radius=16, fill=ACCENT)
    d.text((pad + 66, 209), "Start session", fill=SURFACE)

    panel(280, 560, 214)
    d.text((pad + 28, 306), "DRIFT", fill=MUTED)
    rows = [("VOLUME 7d", "12 480 kg", TEXT), ("SLEEP avg", "6h 41m", TEXT),
            ("RESTING HR", "54 bpm", TEXT), ("RENEWS 30d", "4 items", WARNING),
            ("BACKUP", "19 days", CRITICAL)]
    y = 350
    for lab, val, col in rows:
        d.text((pad + 28, y), lab, fill=MUTED)
        d.text((W - pad - 190, y), val, fill=col)
        d.line([pad + 28, y + 30, W - pad - 28, y + 30], fill=HAIRLINE + (200,))
        y += 42

    panel(590, 760, 214)
    d.text((pad + 28, 616), "NEEDS YOU", fill=MUTED)
    d.text((pad + 28, 654), "Nothing. The week is clear.", fill=TEXT)
    d.text((pad + 28, 690), "Next renewal is in 6 days.", fill=MUTED)
    return im


def corner_strip():
    cell, pad = 230, 22
    sh = Image.new("RGB", (3 * (cell + pad) + pad, cell + pad * 2 + 26), (24, 26, 28))
    d = ImageDraw.Draw(sh)
    labels = [("circular, n=2", 2.0), ("continuous, n=5", 5.0), ("overlaid", None)]
    x = pad
    for lab, n in labels:
        tile = Image.new("RGB", (cell, cell), (24, 26, 28))
        if n:
            m = squircle_mask(cell, cell, 74, n)
            fill = Image.new("RGB", (cell, cell), ACCENT)
            tile.paste(fill, (0, 0), m)
        else:
            a = squircle_mask(cell, cell, 74, 2.0)
            b = squircle_mask(cell, cell, 74, 5.0)
            tile.paste(Image.new("RGB", (cell, cell), (0x2A, 0x4A, 0x52)), (0, 0), a)
            tile.paste(Image.new("RGB", (cell, cell), ACCENT), (0, 0), b)
        sh.paste(tile, (x, pad))
        d.text((x, pad + cell + 6), lab, fill=(185, 192, 196))
        x += cell + pad
    return sh


if __name__ == "__main__":
    base = ocean(W, H)
    a = content(base, scrim=False)
    b = content(base, scrim=True)
    gap = 26
    sh = Image.new("RGB", (W * 2 + gap * 3, H + gap * 2 + 30), (24, 26, 28))
    d = ImageDraw.Draw(sh)
    sh.paste(a, (gap, gap)); d.text((gap, H + gap + 6), "no scrim", fill=(185, 192, 196))
    sh.paste(b, (W + gap * 2, gap)); d.text((W + gap * 2, H + gap + 6),
                                            "scrim, squircle corners", fill=(185, 192, 196))
    sh.save(os.path.join(OUT, "today-ocean-mock.png"))
    corner_strip().save(os.path.join(OUT, "continuous-corners.png"))
    print("wrote ocean_mock.png and corner_strip.png")
