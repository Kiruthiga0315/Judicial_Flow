---
name: JudicialFlow
description: Institutional design system for trial court explainable scheduling
colors:
  lavender: "#C4C3E3"
  violet: "#504E76"
  violet-hover: "#424063"
  cream: "#FDF8E2"
  cream-darker: "#F6EECB"
  sage: "#A3B565"
  apricot: "#FCDD9D"
  orange: "#F1642E"
  ink: "#2B2A45"
  ink-muted: "#5B597B"
  text-orange: "#B8420F"
  border-subtle: "#E4E3F0"
  border-strong: "#B5B3CB"
typography:
  display:
    fontFamily: "'Plus Jakarta Sans', system-ui, sans-serif"
    fontSize: "clamp(1.75rem, 3vw, 2.25rem)"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  heading:
    fontFamily: "'Plus Jakarta Sans', system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.015em"
  body:
    fontFamily: "'Plus Jakarta Sans', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  mono:
    fontFamily: "'IBM Plex Mono', 'JetBrains Mono', monospace"
    fontSize: "0.8125rem"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "normal"
rounded:
  sm: "6px"
  md: "12px"
  lg: "16px"
  card: "28px"
  pill: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  xxl: "48px"
components:
  button-primary:
    backgroundColor: "{colors.violet}"
    textColor: "{colors.cream}"
    rounded: "{rounded.md}"
    padding: "12px 24px"
  button-primary-hover:
    backgroundColor: "{colors.violet-hover}"
  input:
    backgroundColor: "{colors.cream-darker}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    height: "48px"
---

# Design System: JudicialFlow

## Overview
JudicialFlow is an explainable scheduling intelligence platform designed for trial courts. The visual language balances solemn judicial dignity with contemporary clarity and human warmth. It moves deliberately away from cold, generic dark-slate tech aesthetics to an institutional palette grounded in warm cream surfaces, authoritative violet typography, soft lavender backdrops, and an intentional, strictly rationed orange accent for critical urgency.

## Colors
The brand follows an explicit 60 / 30 / 10 distribution rule:
- **60% Surfaces & Backdrops**: Warm Cream (`#FDF8E2`, `oklch(97.7% 0.026 95)`) for forms, cards, and content areas; Lavender (`#C4C3E3`, `oklch(82.3% 0.041 292)`) for page canvas, soft fills, and row hover tints.
- **30% Structure & Ink**: Violet (`#504E76`, `oklch(41.8% 0.059 285)`) for primary brand marks, primary buttons, borders, and focus rings; Deep Ink (`#2B2A45`, `oklch(26.2% 0.048 285)`) for high-contrast body and heading text (contrast ratio > 11:1 on cream).
- **10% Focused Accents**:
  - Hot Orange (`#F1642E`, `oklch(62.4% 0.198 42)`): Reserved strictly for critical urgency badges, key hover moments, directional arrows, and primary focal points.
  - Deep Text Orange (`#B8420F`, `oklch(47.8% 0.165 40)`): Specifically calibrated for text on cream surfaces to pass WCAG AA (> 5.0:1 contrast).
- **Supporting Statutory Scale**:
  - Sage (`#A3B565`, `oklch(73.5% 0.118 126)`): Routine urgency, positive disposal deltas, success states (fills/accents only).
  - Apricot (`#FCDD9D`, `oklch(90.1% 0.082 78)`): High urgency tints, statutory chips, highlights.

### Urgency Scale Tokens
Color is never the sole conveyor of information (protects color-blind users):
- **Critical**: Hot orange background + white text + `!` icon + "Critical" text label.
- **High**: Apricot background + deep ink text + alert triangle icon + "High" label.
- **Elevated**: Lavender background + deep ink text + clock icon + "Elevated" label.
- **Routine**: Sage tint (25% opacity) + dark sage text + check circle icon + "Routine" label.

## Typography
- **Primary Typeface**: `Plus Jakarta Sans`, self-hosted via Fontsource.
- **Weights**: 400 (Regular), 500 (Medium), 600 (SemiBold), 700 (Bold).
- **Tracking**: Headings use tight letter-spacing (`-0.02em` to `-0.015em`).
- **Tabular Numerals**: Enabled globally via `font-feature-settings: 'tnum' 1` for consistent alignment across case numbers, dates, times, and priority scores.
- **Display Type**: Never use italic serif display type; maintain crisp geometric clarity.

## Layout
- **Login Shell**: Centered rounded frame (28px radius) floating on a lavender-tinted background.
- **Seam Split**: Two panels with a gentle overlapping seam:
  - Left Panel (45%): Clean, focused cream form with clear visual hierarchy.
  - Right Panel (55%): Textured, layered generative art panel with frosted glass pills, 4-arc statutory score ring, and principles carousel.
- **Responsive Breakpoints**:
  - `> 900px`: 2-column split desktop view with full generative art and interactive widgets.
  - `<= 900px`: Stacked layout; art transforms into a 180px top banner band with logomark overlay.
  - `<= 600px`: Single column; 48px touch targets, full-width inputs and primary action buttons.

## Elevation & Depth
- **Canvas Backdrop**: Sweeping tactile brush texture (`bg-brush.jpeg`) blended at 25% multiply opacity over the lavender canvas, accented by soft radial vignette gradients and subtle focal ambient lights.
- **Page Frame**: Multi-stop atmospheric glow and directional shadow `0 25px 70px -15px rgba(80, 78, 118, 0.25), 0 0 80px -20px rgba(241, 100, 46, 0.14)` with crisp boundary border.
- **Form Controls**: Inputs transition on hover with border darkening and on focus with a soft violet luminous glow (`shadow-[0_0_16px_rgba(80,78,118,0.12)]`).
- **Primary Button**: Violet brand base with smooth lift (`-1px`), warm orange/violet ambient hover glow (`shadow-[0_8px_25px_-4px_rgba(80,78,118,0.45),0_0_20px_rgba(241,100,46,0.22)]`), and crisp pressed tactile feedback (`scale(0.985)`).
- **Factor Pillar Cards**: Factor cards on the right panel feature factor-tinted hover glows (Violet for Statutory Priority, Hot Orange for Anti-Starvation, Sage for Capacity Protection, and Apricot for Immutable Audit) with smooth 2px elevation.
- **Interactive Focus Ring**: 2px solid violet with 2px offset (`0 0 0 2px #FDF8E2, 0 0 0 4px #504E76`) ensuring visible accessibility on all interactives.

## Shapes
- **Outer Frame**: 28px border radius.
- **Input Fields**: 12px border radius, 48px height.
- **Primary Buttons**: 12px border radius, 48px height.
- **Pills / Badges**: 9999px full capsule radius.

## Components
- `FourPillars`: Explainable listing principles grid with factor-tinted hover glows and institutional icons.
- `PrinciplesCarousel`: Institutional principles rotation with quote glyphs, vertical progress indicators, hover pause, and subtle navigation glows.
- `JudicialLogomark`: Custom vector mark combining balanced judicial scales with fluid flow curves in brand violet and orange.

## Do's and Don'ts
### Do's
- Always maintain the 60 / 30 / 10 visual balance.
- Use Deep Text Orange (`#B8420F`) whenever orange text sits on a cream background.
- Combine color, iconography, and text labels for every status or urgency indicator.
- Ensure every interactive element has a visible 2px violet focus ring with 2px offset.
- Respect `prefers-reduced-motion` by disabling ambient loops and providing instant transitions.

### Don'ts
- Never put white or cream text on Hot Orange (`#F1642E`).
- Never use Hot Orange (`#F1642E`) or Sage (`#A3B565`) as text on cream surfaces.
- Never use generic clip-art scales of justice.
- Never use italic serif display type.
- Never allow ambient motion loops to run when tab is hidden or reduced motion is requested.
