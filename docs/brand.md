# Brand

Trackr draws its colors from the standard **Your Average Chris** brand palette.
That palette is shared Your Average Chris brand material — it's reproduced here
for now, and once it moves to its shared home this file will link out to it
instead. Trackr's own mark (the gradient and EKG treatment below) is built *from*
that palette but is specific to Trackr, not part of the shared brand. Either way,
treat this document as the single source of truth for brand color — define the
values here, and reference this file everywhere else.

## Palette

| Name | Hex | Role |
|---|---|---|
| Brand light blue | `#47AADC` | Gradient start (top-left); the About "on-device" accent |
| Brand dark navy | `#04325C` | Gradient end (bottom-right); the primary brand seed |
| Brand yellow | `#FCD214` | Accent — the heartbeat R-peak dot, the bold "Fast." slogan word, the About "log fast" icon in dark mode |
| Brand darker yellow | `#EBC413` | The brand yellow at 92% brightness (V), same hue and saturation — for warm accents on light surfaces where the bright yellow vanishes (e.g. the About "log fast" icon in light mode) |
| Brand green | `#148244` | The About "no account required" / privacy-positive accent |

## Trackr's mark (specific to Trackr)

The palette above is the shared Your Average Chris brand; how Trackr combines it
is its own. Trackr's mark is a **135° gradient** from brand light blue (top-left)
to brand dark navy (bottom-right) — carried by the launcher icon, the store icon,
and the feature graphic — with the white EKG/heartbeat line and the brand-yellow
R-peak dot on top of it. The gradient and the EKG treatment are Trackr-specific:
if the palette ever moves to a shared Your Average Chris home, these stay with
Trackr.

## Not a brand color

`#37618E` is **not** part of this palette. It's the Material 3 fallback seed
borrowed from GolfScore that generates the dynamic-color fallback scheme (see
`llds/theme.md`); it predates the brand palette and is unrelated to it. Don't
fold it in here.

## Keeping copies in sync

Markdown can't be imported by Kotlin, XML, or SVG, so the hex values are
physically duplicated at each place that renders them. Every such copy carries a
`docs/brand.md` pointer in a nearby comment, so:

```
rg docs/brand.md
```

lists every file that must be updated by hand when a brand color changes. Change
the value here first, then walk that list. The copies that hold literal hex:

- `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` — the launcher / adaptive icon
- `docs/store-listing/icon-512.svg`, `docs/store-listing/feature-graphic.svg` — the Play store icon and feature graphic
- `app/src/main/java/net/clahey/trackr/ui/about/AboutScreen.kt` — the About screen's brand hero and point icons

Prose references (`llds/theme.md`, `llds/publishing.md`, `llds/app-shell.md`)
name the colors rather than restating their hex, so a value change doesn't touch
them.
