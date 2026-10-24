---
name: Dayflow
colors:
  surface: '#121316'
  surface-dim: '#121316'
  surface-bright: '#38393c'
  surface-container-lowest: '#0d0e11'
  surface-container-low: '#1b1b1f'
  surface-container: '#1f1f23'
  surface-container-high: '#292a2d'
  surface-container-highest: '#343538'
  on-surface: '#e3e2e6'
  on-surface-variant: '#cac4d4'
  inverse-surface: '#e3e2e6'
  inverse-on-surface: '#2f3034'
  outline: '#948e9d'
  outline-variant: '#494552'
  surface-tint: '#cebdff'
  primary: '#cebdff'
  on-primary: '#381385'
  primary-container: '#a78bfa'
  on-primary-container: '#3c1989'
  inverse-primary: '#674bb5'
  secondary: '#6cdbac'
  on-secondary: '#003826'
  secondary-container: '#2ca379'
  on-secondary-container: '#003120'
  tertiary: '#a4c9ff'
  on-tertiary: '#00315d'
  tertiary-container: '#6a9fe5'
  on-tertiary-container: '#003564'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e8ddff'
  primary-fixed-dim: '#cebdff'
  on-primary-fixed: '#21005e'
  on-primary-fixed-variant: '#4f319c'
  secondary-fixed: '#89f8c7'
  secondary-fixed-dim: '#6cdbac'
  on-secondary-fixed: '#002115'
  on-secondary-fixed-variant: '#005139'
  tertiary-fixed: '#d4e3ff'
  tertiary-fixed-dim: '#a4c9ff'
  on-tertiary-fixed: '#001c39'
  on-tertiary-fixed-variant: '#004883'
  background: '#121316'
  on-background: '#e3e2e6'
  surface-variant: '#343538'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '600'
    lineHeight: 44px
    letterSpacing: -0.03em
  headline-lg:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0.005em
  label-lg:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Inter
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 12px
    letterSpacing: 0.06em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  margin: 1.25rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1.25rem
  space-xl: 2rem
---

## Brand & Style

This design system channels an intentional, contemplative focus environment crafted for modern mobile workflow rituals. Drawing tactile clarity from Things 3, kinetic precision from Linear, visual rhythm from Structured, and digital sanctuary principles from Opal, the aesthetic merges utility-first productivity with an atmospheric, calming digital shelter. 

The aesthetic is rooted in dark-first tactile minimalism layered with modern ambient luminescence. Rather than cold utility, it balances technical efficiency with soft, meditative tactility. Surfaces feel physically milled, resting softly on one another through tone shifts and hairline luminescence rather than aggressive dropshadows. The emotional intent is to decelerate cognitive strain: zero visual friction, distraction-free visual cadence, and satisfying micro-interactions that make time blocking, task execution, and deep work feel effortless.

## Colors

The palette operates under an atmospheric low-luminance architecture designed to reduce eye fatigue across extended focus sessions while maintaining surgical contrast hierarchy.

- **Background Canvas (`#101114`)**: A grounded, deep volcanic charcoal canvas that eliminates pure-black OLED smearing while creating perceived infinite depth.
- **Surface Level 1 (`#191B20`)**: Base layer for cards, grouped modules, and inactive panels.
- **Surface Level 2 (`#22252C`)**: Elevated contextual sheet, dialog, floating island, or active timeline module.
- **Hairline / Border (`#272A33`)**: Low-energy boundaries that define shape edges without fragmenting the canvas.
- **Primary Accent (`#A78BFA`)**: Vibrant lavender representing active focus states, primary triggers, and deep work intervals.
- **Secondary Accent (`#73E2B3`)**: Mint green reserved for flow completions, progress streaks, and restorative micro-breaks.
- **Tertiary Accent (`#82B7FF`)**: Soft atmospheric blue for scheduling, future time markers, and calm calendar projections.
- **Text & Semantics**: Primary text (`#F4F4F6`) commands immediate legibility; secondary text (`#999DA8`) retreats to prioritize visual hierarchy. Ambient warning is handled with warm amber (`#F6BE68`) and critical disruption or destructive flows with muted coral (`#FF7777`).

## Typography

The typography relies on Inter set with surgical geometric precision. Numerical counters, timelines, and time blocks make use of tabular figures (`tnum`) and stylistic sets to prevent visual jitter during active timer tick-overs. 

Headings carry tighter letter spacing to maintain visual authority without taking up vertical canvas. Body copy emphasizes line-height breathing room to guarantee frictionless readability against dark surfaces. Micro-labels, meta tags, and keyboard accelerators leverage uppercase tracking with medium-to-semibold weights for rapid visual scanning.

## Layout & Spacing

Layouts adhere to an 8pt rhythmic grid system, scaled downward to 4pt for micro-alignments, pill elements, and icon-to-label gaps. 

- **Android System Handling**: Layout containers strictly honor the Android Display Cutout, dynamic Edge-to-Edge System Bars, and Gesture Insets. Bottom interactive shells enforce safe-area bottom padding with a mandatory minimum of `24px` clearance above the Android system navigation pill.
- **Rhythmic Rhythm**: Content is structured around fluid single-column timelines on handheld portrait layouts, expanding into dynamic dual-pane structural views on foldables and tablets (left pane: schedule and timeline hierarchy; right pane: context drawer and task details).
- **Margins & Gutters**: Mobile layouts maintain a compact `margin-mobile` of `1rem` with `gutter-mobile` of `0.75rem`, guaranteeing maximum horizontal line-length efficiency for nested task hierarchies.

## Elevation & Depth

Visual hierarchy uses a refined hybrid approach: **tonal surfaces** paired with **crisp low-contrast hairline outlines** and **subtle ambient glow**.

1. **Layer 0 (Canvas Base - `#101114`)**: Ground plane; contains structural timeline tracks and passive day grids.
2. **Layer 1 (Card Rest - `#191B20`)**: Standard task containers, schedule cards, and inactive widgets. Defined by a continuous `1px` border of `#272A33`. Dropshadows are intentionally omitted to maintain clean geometric purity.
3. **Layer 2 (Elevated Active - `#22252C`)**: Active focus sessions, dragged tasks, modal bottom sheets, and floating dynamic controls. Accompanied by a muted ambient shadow: `0px 12px 32px -4px rgba(0, 0, 0, 0.45)` and an interior top-edge highlight stroke (`1px solid rgba(255, 255, 255, 0.05)`).
4. **Layer 3 (Radiant Focus)**: Ongoing active timers and focused items feature an ambient optical drop glow: `0px 0px 24px -2px rgba(167, 139, 250, 0.18)` anchoring the eye without producing interface glare.

## Shapes

The design system embraces a continuous squircle philosophy. Primary content cards, timeline intervals, and modal sheets carry generous radii ranging from `16px` to `24px` (`rounded-lg` through `rounded-xl`). 

Secondary elements—such as category chips, tag badges, status indicators, and micro-steppers—employ continuous full-pill geometries to balance the larger structural rectangular cards. Interactive controls never drop below an `8px` corner radius, ensuring the interface feels inviting, calm, and polished under thumb navigation.

## Components

### Buttons
- **Primary**: Pill or `16px` rounded container filled with vibrant lavender (`#A78BFA`), paired with a deep charcoal label (`#101114`) set in `label-lg` font weight. Active/pressed state drops opacity to `0.9` with an inner scale transform of `0.98`.
- **Secondary / Ghost**: Background `#191B20` with a subtle `#272A33` boundary stroke, typography in `#F4F4F6`. On touch feedback, the background transitions to `#22252C`.
- **Icon Actions**: 44×44px minimum tap target wrapped in a 40×40px visual bounds, keeping thumb hits effortless.

### Cards & Focus Modules
- Nested in `#191B20`, framed by a single-pixel `#272A33` border with `16px` or `20px` corner radii.
- Focus cards feature a left vertical indicator strip (3px wide) color-coded to the task context (`#A78BFA` for Deep Work, `#73E2B3` for Habit, `#82B7FF` for Plan).
- Subtle press feedback utilizes an instantaneous `bg-[#22252C]` tint with zero spring overshoot to preserve a grounded, snappy feel.

### Chips & Pill Tags
- Fully rounded pill geometry (`rounded-full`) with a height of `24px` to `28px`.
- Inactive tags: `#191B20` background, `#272A33` stroke, `#999DA8` text.
- Active tags: Tinted background at 12% opacity of the respective accent color with matching solid colored text and an accent border at 30% opacity.

### Timeline Track & Markers
- Vertical continuous line anchored along the left layout spine in `#272A33` (2px thickness).
- Past events dim down to `#999DA8` with surface transparency.
- The "Current Time" node features a pulsating ring: an inner solid `#A78BFA` dot (8px) enveloped by an animated outer ring with a 15% lavender halo.

### Checkboxes & Completion Nodes
- Circular check nodes inspired by Things 3: 20px diameter with a 1.5px border of `#999DA8`.
- State transitions: Instant checkmark fill using mint green (`#73E2B3`), triggering a gentle line-through strike across primary task labels and dimming to secondary text tone.

### Inputs & Time Selectors
- Monolithic background (`#191B20`), inset text padding of `16px`, border in `#272A33`. 
- Focused state triggers an immediate boundary transition to `#A78BFA` with an ambient glow of `rgba(167, 139, 250, 0.15)`.

### Bottom Navigation & Action Bar
- Floating glassmorphic dock suspended `16px` above the Android system navigation bar.
- Background uses elevated `#191B20` at 85% opacity with an intense backdrop blur (`20px`).
- Centered prominent quick-add action button that triggers fluid keyboard-focused entry with rapid tag shortcuts.