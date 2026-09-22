"""Derive every icon asset, and the theme palette, from one source artwork.

brand/tide-icon-source.png is the master: a full-bleed 1024 square with no
rounded corners, because Android adaptive icons are masked by the launcher and
feeding them a pre-rounded image double-rounds it.

    python tools/render_brand.py

Writes the raster icon sizes, a mask and size test sheet, and a palette sheet
sampled from the artwork itself so the app theme and the icon cannot drift apart.
"""
from PIL import Image, ImageDraw, ImageFilter
import colorsys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BRAND = os.path.join(ROOT, "brand")
SOURCE = os.path.join(BRAND, "tide-icon-source.png")
SIZES = (512, 192, 96, 48)


def load():
    return Image.open(SOURCE).convert("RGB")


def mask(img, squircle=False, ss=4):
    n = img.size[0]
    m = Image.new("L", (n * ss, n * ss), 0)
    d = ImageDraw.Draw(m)
    box = [0, 0, n * ss - 1, n * ss - 1]
    if squircle:
        d.rounded_rectangle(box, radius=int(n * ss * 0.24), fill=255)
    else:
        d.ellipse(box, fill=255)
    out = img.convert("RGBA").copy()
    out.putalpha(m.resize((n, n), Image.LANCZOS))
    return out


def hexof(c):
    return "#{:02X}{:02X}{:02X}".format(*c[:3])


def palette(img, n=8):
    """Dominant colours, sorted dark to light. These are what the theme uses."""
    q = img.resize((256, 256), Image.LANCZOS).quantize(colors=n, method=Image.MAXCOVERAGE)
    pal = q.getpalette()[: n * 3]
    counts = dict(q.getcolors())
    cols = []
    for i in range(n):
        c = tuple(pal[i * 3: i * 3 + 3])
        cols.append((counts.get(i, 0), c))
    cols.sort(key=lambda t: sum(t[1]))
    return cols


def sheet(img):
    cell, pad = 192, 16
    cells = [(img.resize((cell, cell), Image.LANCZOS), "full bleed source"),
             (mask(img.resize((192, 192), Image.LANCZOS), True), "squircle mask"),
             (mask(img.resize((192, 192), Image.LANCZOS)), "circle mask"),
             (img.resize((96, 96), Image.LANCZOS), "96px"),
             (img.resize((48, 48), Image.LANCZOS), "48px, homescreen"),
             (img.resize((48, 48), Image.LANCZOS).resize((cell, cell), Image.NEAREST),
              "48px magnified")]
    w = len(cells) * (cell + pad) + pad
    sh = Image.new("RGBA", (w, cell + pad * 2 + 24), (28, 28, 30, 255))
    d = ImageDraw.Draw(sh)
    x = pad
    for c, lab in cells:
        c = c.convert("RGBA")
        sh.alpha_composite(c, (x + (cell - c.size[0]) // 2, pad + (cell - c.size[1]) // 2))
        d.text((x, pad + cell + 6), lab, fill=(180, 186, 190, 255))
        x += cell + pad
    return sh


def palette_sheet(cols):
    sw, h, pad = 150, 150, 12
    w = len(cols) * (sw + pad) + pad
    sh = Image.new("RGBA", (w, h + pad * 2 + 36), (28, 28, 30, 255))
    d = ImageDraw.Draw(sh)
    x = pad
    for _, c in cols:
        d.rectangle([x, pad, x + sw, pad + h], fill=c)
        r, g, b = [v / 255 for v in c]
        hh, l, s = colorsys.rgb_to_hls(r, g, b)
        d.text((x, pad + h + 6), hexof(c), fill=(225, 230, 234, 255))
        d.text((x, pad + h + 20), f"h{hh*360:.0f} s{s*100:.0f} l{l*100:.0f}",
               fill=(150, 158, 164, 255))
        x += sw + pad
    return sh


if __name__ == "__main__":
    img = load()
    assert img.size[0] == img.size[1], "source must be square"
    for s in SIZES:
        img.resize((s, s), Image.LANCZOS).save(os.path.join(BRAND, f"tide-{s}.png"))
    sheet(img).save(os.path.join(BRAND, "tide-icon-tests.png"))
    cols = palette(img)
    palette_sheet(cols).save(os.path.join(BRAND, "tide-palette.png"))
    print(f"source {img.size[0]}px, wrote {len(SIZES)} sizes, test sheet, palette sheet")
    print("\nsampled palette, dark to light:")
    for cnt, c in cols:
        r, g, b = [v / 255 for v in c]
        hh, l, s = colorsys.rgb_to_hls(r, g, b)
        print(f"  {hexof(c)}  h{hh*360:6.1f}  s{s*100:5.1f}%  l{l*100:5.1f}%")
