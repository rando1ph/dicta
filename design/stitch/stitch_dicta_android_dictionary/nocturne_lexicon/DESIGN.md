---
name: Nocturne Lexicon
colors:
  surface: '#141218'
  surface-dim: '#141218'
  surface-bright: '#3b383f'
  surface-container-lowest: '#0f0d13'
  surface-container-low: '#1d1b21'
  surface-container: '#211f25'
  surface-container-high: '#2b292f'
  surface-container-highest: '#36343a'
  on-surface: '#e7e0e9'
  on-surface-variant: '#c8c4d6'
  inverse-surface: '#e7e0e9'
  inverse-on-surface: '#322f36'
  outline: '#928f9f'
  outline-variant: '#474554'
  surface-tint: '#c6bfff'
  primary: '#c6bfff'
  on-primary: '#28049d'
  primary-container: '#8c80ff'
  on-primary-container: '#22008d'
  inverse-primary: '#594acb'
  secondary: '#f5bd6c'
  on-secondary: '#442b00'
  secondary-container: '#754d00'
  on-secondary-container: '#f8c06f'
  tertiary: '#76d1ff'
  on-tertiary: '#003548'
  tertiary-container: '#379ac6'
  on-tertiary-container: '#002e3f'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e4dfff'
  primary-fixed-dim: '#c6bfff'
  on-primary-fixed: '#160066'
  on-primary-fixed-variant: '#402db2'
  secondary-fixed: '#ffddb2'
  secondary-fixed-dim: '#f5bd6c'
  on-secondary-fixed: '#291800'
  on-secondary-fixed-variant: '#624000'
  tertiary-fixed: '#c2e8ff'
  tertiary-fixed-dim: '#76d1ff'
  on-tertiary-fixed: '#001e2b'
  on-tertiary-fixed-variant: '#004d67'
  background: '#141218'
  on-background: '#e7e0e9'
  surface-variant: '#36343a'
typography:
  headline-lg:
    fontFamily: Newsreader
    fontSize: 36px
    fontWeight: '500'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Newsreader
    fontSize: 28px
    fontWeight: '500'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Newsreader
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 30px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Newsreader
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
  body-lg:
    fontFamily: Newsreader
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Roboto Flex
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Roboto Flex
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.03em
  label-sm:
    fontFamily: Roboto Flex
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.05em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system targets avid readers, scholars, and digital bibliophiles who demand an unobtrusive, hyper-focused reference overlay while engaged in long-form literary reading on modern Android devices. 

The aesthetic is rooted in Material 3 Expressive merged with refined editorial luxury. It bridges the classic tactile comfort of fine book typography with the fluidity and mechanical precision of contemporary Android systems. The emotional response must evoke serene intellect, tactile craftsmanship, and zero-friction comprehension.

Key visual attributes include:
- Floating sheets and popups configured as contextual micro-destinations.
- High-contrast legibility that preserves visual hierarchy without producing glare during prolonged reading.
- Expressive editorial contrast: grounding authoritative literature through an elegant transitional serif, anchored by a systematic modern sans-serif for UI metadata and linguistic anatomy.

## Colors

The palette operates in a native dark mode calibrated specifically for e-book and literary overlays, preventing eye fatigue while maintaining deep tonal richness.

- **Primary (`#897CFF` - Luminous Violet):** The interactive focal point. Used for primary audio actions, active phonetic toggles, and primary navigation states. It balances high luminance against dark reader backdrops without chromatic aberration.
- **Secondary (`#F5BD6C` - Warm Amber):** The semantic accent for etymology, word frequency badges, and bookmarked annotations. Evokes aged marginalia and gilded book bindings.
- **Tertiary (`#76D1FF` - Crisp Sky):** Used sparingly for grammatical labels, syntactic flags (e.g., transitive vs. intransitive), and secondary cross-references.
- **Neutral (`#141218` - Obsidian Paper):** The foundational dark tone derived from deep violet-tinted black. It eliminates the stark severity of pure `#000000` while providing immense contrast against content tiers.

Surface container tokens scale subtly across three elevation steps (`#1D1A22` -> `#282430` -> `#332E3D`) to demarcate layered definition panels without heavy strokes.

## Typography

The typographic strategy pairs **Newsreader** (a transitional literary serif designed specifically for immersive reading) with **Roboto Flex** (a hyper-engineered, adaptable sans-serif providing crystal-clear legibility at small scale).

- **Newsreader** commands the word headwords (`headline-lg`, `headline-md`) and primary full-sentence usage citations (`body-lg`), conveying deep editorial credibility and traditional book aesthetic.
- **Roboto Flex** powers the mechanical data: IPA notation, grammatical classifications, sense numbers, semantic chips, and functional metadata.
- **Phonetic IPA string rule:** Always render International Phonetic Alphabet transcriptions in `Roboto Flex` with optical sizing set to text clarity, utilizing `label-lg` with a slight tabular weight spacing to preserve precise phonetic symbols.

## Layout & Spacing

This design system is tailored for Android mobile contexts, specifically floating dynamic popups, contextual modal sheets, and split-screen readers.

- **Grid Model:** Built on a flexible 4-column structure on mobile devices, expanding to an 8-column layout on foldables and tablets. 
- **Popup Footprint:** Floating definition cards occupy a maximum of 92% screen width on compact devices with a standard `1rem` (16dp) viewport margin. On large tablets or foldables, dictionary popups fix to a maximum width of 480dp, anchored contextually to the word selection trigger point.
- **Spacing Rhythm:** Adheres to a strict 4dp base unit (`space-xs` = 4dp, `space-sm` = 8dp, `space-md` = 16dp, `space-lg` = 24dp, `space-xl` = 32dp). Dense lexicographical content demands strict micro-spacing: definitions are separated by `space-md`, while sub-elements (sense numbers, usage tags) tuck neatly with `space-sm`.

## Elevation & Depth

Visual hierarchy adheres to Material 3 tonal elevation combined with selective frosted glass blurs, preserving context over reader text.

- **Backdrop Underlay:** The host application background receives a dynamic blur (`backdrop-filter: blur(12px) brightness(0.65)`), softening underlying book text while anchoring reader focus to the popup.
- **Surface Elevation Level 3:** The main dictionary popup container utilizes an ambient composite shadow paired with container fill:
  - Base surface fill: `#282430` at 92% opacity, backed by a localized `20px` surface blur.
  - Shadow configuration: Two-layer ambient diffusion — `0px 10px 32px rgba(10, 8, 14, 0.45)`, overlaid with `0px 2px 8px rgba(0, 0, 0, 0.25)`.
- **Low-Contrast Ghost Border:** A micro-stroke (`1px solid rgba(255, 255, 255, 0.08)`) encases elevated containers, preserving structural sharpness in pitch-black environments without introducing harsh outlines.

## Shapes

The design system embraces the expressive Material 3 corner geometry:

- **Popup Containers:** Default to `28dp` (`rounded-3xl` / scale `3`) continuous curvature, conveying a smooth, pebble-like tactile quality that feels natural to Android gestural navigation.
- **Phonetics & Metadata Badges:** Use pill geometries (full radii) to contrast clearly against rectangular definition blocks.
- **Sub-cards & Etymology Wells:** Settle at `16dp` inner rounding, nesting harmoniously within the parent `28dp` bounding curve.

## Components

### Definition Popup (Container Level 3)
- Encased in a `28dp` curved container with `backdrop-filter: blur(20px)` and `#282430` base tone.
- Top drag handle: 32dp x 4dp rounded pill, tinted `#FFFFFF` at 20% opacity.
- Internal padding: `space-lg` (24dp) for comfortable finger-touch margins.

### Phonetics & Audio Bar
- **IPA Badge:** Rendered in `label-lg` with `Roboto Flex`. Contained in a pill-shaped badge with subtle border (`1px solid rgba(255, 255, 255, 0.12)`) and background tint (`rgba(137, 124, 255, 0.08)`).
- **Pronunciation Audio Button:** A 40dp circular tactile touch target featuring the primary color (`#897CFF`) fill at 15% opacity with an active state bouncing to 100% solid fill and dark glyph on tap.

### Lexical Sense List
- Numbered indices (`1`, `2`, `3`) styled in `label-sm` bold Warm Amber (`#F5BD6C`).
- Definition text styled in `body-md` high-contrast crisp neutral (`#E6E0E9`).
- Usage examples nested directly below in `body-lg` italic Newsreader, rendered in muted tone (`#CAC4D0`) with a subtle `2dp` left indicator line colored in Primary Violet.

### Action Chips & Grammar Badges
- **Part of Speech Chips (noun, verb, adj):** Height 24dp, rounded full, background `rgba(118, 209, 255, 0.12)`, text `#76D1FF` in uppercase `label-sm`.
- **Frequency Metric:** Embedded micro-pill displaying relative frequency via a 3-bar miniature indicator using the secondary color.

### Form Inputs & Quick Search Inline Bar
- Minimalist docked search field at the popup bottom.
- Pill shaped (height 48dp), filled with `#1D1A22`, inset border `1px solid rgba(255, 255, 255, 0.06)`.
- Cursor and focus stroke: Solid `#897CFF`.