# Brand

## The mark

One full tidal cycle, drawn against a chart datum.

![Tide icon tests](../brand/tide-icon-tests.png)

A tide curve is never drawn bare. It is always plotted against a datum, because the curve on
its own tells you nothing: what matters is where the water sits relative to a known level.
That is also how this app reads a life. A single night's sleep is noise. Where it sits
against your own baseline is the reading.

The datum line is doing all the work here. Without it the mark is a tilde, which is the
generic water glyph every app in the category already uses. With it, the mark becomes a
chart, and it picks up two enclosed counters that give it a shape you can remember.

## What this replaced, and why

The app was called Keel first, and the mark was a keel. It went through six versions and
every one of them failed the same way: a hull with a fin below it is a horizontal element on
top of a vertical stem on top of a base, which is structurally a table. The variants read in
turn as a tuning fork, a leaf, a goblet, a shot glass and a champagne coupe. Tuning the
proportions never escaped it, because the silhouette family itself is furniture.

The lesson kept: **a name whose referent cannot be drawn is a liability for something that
needs a home screen icon.** Drawability is now a naming criterion, not a downstream problem.

## Why it survives Android

Adaptive icons get masked by the launcher into a circle, a squircle, a rounded square or a
teardrop, and the app does not choose which. Android 13 and later also render a themed icon
from a flat single-colour silhouette.

This mark is compact, centred, and sits entirely inside the 72dp safe zone, so every mask
produces the same result. It is two strokes of one colour, so the monochrome variant is the
same drawing.

`brand/tide-icon-tests.png` is generated, not drawn: the real mark at 192, 96 and 48px, under
both masks, and monochrome on dark and on light. Regenerate it and look at it after any
geometry change:

```
python tools/render_brand.py
```

48px is the size that decides. A mark checked only at 512 is not checked.

## Geometry

On the 108dp adaptive-icon grid, entirely within the 72dp safe zone (18 to 90).

| | |
|---|---|
| Datum | y 54, x 21 to 87, 6 wide, round caps |
| Curve | x 20 to 88, one full period, amplitude 18, 11 wide, round caps |
| High water | y 36 |
| Low water | y 72 |
| Occupied | x 18 to 90, y 30.5 to 77.5 |

The datum overhangs the curve slightly at each end, the way a chart axis runs past its data.
It is thinner than the curve so it reads as a reference rather than a second wave.

`brand/tide-mark.svg` is the source of truth. `tools/render_brand.py` holds the same geometry
as numbers and produces the PNGs. If the two disagree, the SVG wins.

## Colour

`#3FA98B` on `#0E1114`, the same accent and surface as the app, never recoloured. The
monochrome variant uses `currentColor` and inherits.

## Files

| File | Use |
|---|---|
| `brand/tide-mark.svg` | The mark alone, transparent. Source of truth. |
| `brand/tide-mark-mono.svg` | Single colour via `currentColor`, for themed icons. |
| `brand/tide-icon.svg` | Mark on the app ground, for the launcher icon. |
| `brand/tide-512.png` `tide-192.png` `tide-96.png` `tide-48.png` | Raster renders. |
| `brand/tide-icon-tests.png` | The mask and size checks above. |

## Wordmark

Not yet drawn. When it is: Roboto Flex, tight tracking, lowercase, mark to the left at cap
height. Nothing decorative between them.
