# TumiArt font provenance — Stage 4 P2

The application owns all four static, upright TTF resources. Shared modules
receive Compose FontFamily values; they have no dependency on these resources.
No font downloader, network font provider or additional runtime dependency is used.

## Exact upstreams and bytes

Newsreader comes directly from Production Type's
[repository at cfcb4f7](https://github.com/productiontype/Newsreader/tree/cfcb4f7af0e52c25e8df2a2431814c8e5fe2e155),
`fonts/static/ttf/Newsreader16pt-Regular.ttf`. Embedded version: **1.003**,
ttfautohint 1.8.3; OS/2 weight 400. The 16pt optical cut retains more robust
strokes for short 20–28sp editorial copy than the upstream display cut.

Karla comes directly from the authors' Google Fonts
[repository at 69b25f6](https://github.com/googlefonts/karla/tree/69b25f663101efb4113dd7ed416c120dd2dce56a),
`fonts/ttf/Karla-Regular.ttf`, `Karla-Medium.ttf`, `Karla-SemiBold.ttf`.
Embedded version: **2.004**, ttfautohint 1.8.4.7-5d5b / gftools 0.9.33;
OS/2 weights 400/500/600. The upstream repository is archived; updating the
font is an explicit future source/license/hash review, not an automatic download.

| Android resource | Bytes | SHA-256 |
|---|---:|---|
| `karla_regular.ttf` | 66964 | `1a4e409e44eb3c3c541cac5e885219bd66d43262214186634f5811449100a090` |
| `karla_medium.ttf` | 66540 | `6f13001c17a8df1081836e9a625f6aca49041b66a1cca5995527ba8672a3a6b3` |
| `karla_semibold.ttf` | 66808 | `cfd540e5f9d55ce84d2ba45d0efa2b8697c321d25dfb54a522e1a977c68e017d` |
| `newsreader_regular.ttf` | 121824 | `0636887c9f72f77ce188f0b31029be58e65adae5a47e6d2887ad5020c2c75036` |

Downloaded bytes are unmodified: no conversion, instantiation, subsetting,
hinting, renaming of internal family records or glyph edits. Android resource
filenames differ from upstream filenames only. Static TTFs avoid depending on
variable-font axes on the supported Android API-23 floor. No italic or unbundled
editorial weight is selected by the theme.

## License and distribution

Both upstream OFL.txt files were read in full before packaging. They grant
bundling/embedding/redistribution with software while requiring retention of
their copyright and OFL notices; the font software stays under OFL 1.1, cannot
be sold alone, and is not relicensed under Apache-2.0. Neither downloaded
copyright heading specifies a Reserved Font Name; the fonts are unmodified
in any event. No endorsement is claimed.

The complete upstream notices are tracked and packaged with trailing end-of-line
spaces normalized; license and copyright wording is unchanged in
`apps/tumiart/src/main/res/raw/newsreader_ofl.txt` and `karla_ofl.txt`.
The application's offline Font licenses dialog references both resources,
retaining them through release resource shrinking and making them readable
from setup and Legal/Support. Their upstream license URLs are
[Newsreader OFL](https://github.com/productiontype/Newsreader/blob/cfcb4f7af0e52c25e8df2a2431814c8e5fe2e155/OFL.txt)
and [Karla OFL](https://github.com/googlefonts/karla/blob/69b25f663101efb4113dd7ed416c120dd2dce56a/OFL.txt).

Font licensing does not grant rights to merchant logos, website photography,
reference artwork or catalog images. None is included in this package.

## Coverage and Android proof

fontTools 4.61.1 inspected the exact TTF `name`, `OS/2`, Unicode `cmap` and
absence of `fvar` tables. Every packaged file includes all twelve requested
Turkish code points: `İ ı Ş ş Ğ ğ Ç ç Ö ö Ü ü` (U+0130/U+0131, U+015E/U+015F,
U+011E/U+011F, U+00C7/U+00E7, U+00D6/U+00F6, U+00DC/U+00FC).
This is binary coverage evidence, separate from Android rendering.

`TumiArtThemeTest` verifies the installed raw font hashes, readable notices,
Android Paint glyph support and Compose-resolved typeface metrics against the
packaged resources. Its offline specimen additionally exercises long Turkish
text, functional/editorial roles, form errors and 48dp controls. Actual runtime
executions and screenshot limits are recorded in the P2 handoff; these test
definitions alone are not a claim of a passed device run.


The packaged Karla files cover the requested Turkish letters, digits and price
punctuation, but their cmap omits U+20BA (Turkish lira). The offline specimen
therefore includes the lira symbol and digits explicitly, and Android glyph
checks cover it at every packaged weight. A rendered lira glyph uses Android's
platform fallback, not an additional selected or bundled font family; the
fallback face itself is not identified. Newsreader includes U+20BA, but prices
remain in the functional role. No price formatting or commerce data is changed.
