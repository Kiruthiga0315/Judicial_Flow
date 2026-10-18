# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
- Primary: Registry Officers and Listing Registrars managing case intake, cause lists, courtroom allocation, and hearing rosters.
- Secondary: Trial Court Judges (reviewing personal daily schedules, cause lists, case summaries, leave windows).
- Tertiary: Court Administrators (configuring weights, batch runs, auditing overrides, monitoring fairness and aging).

## Product Purpose
JudicialFlow is an explainable, algorithmic court scheduling and priority scoring system for trial courts. It eliminates opaque scheduling bottlenecks, ensures statutory priority compliance (e.g. bail urgency, special acts), prevents systemic starvation of ordinary civil filings via calibrated aging, and records an immutable cryptographic audit log for every scheduling decision and override.

## Positioning
Unlike commercial case management software or generic ERPs, JudicialFlow couples an explainable priority scoring engine calibrated to aggregate judicial backlog statistics (NJDG) with a multi-criteria optimization scheduling engine that provides human-in-the-loop proposals, immutable audit trails, and provable non-starvation guarantees.

## Operating Context
- Fast-paced court registry counter and judicial chambers.
- High-stakes scheduling environments where every hearing assignment affects citizen liberty, statutory timelines, and court capacity.
- Workflows: intake & scoring, automated draft scheduling proposals, registrar approval/reassignment, judge leave management, nightly batch optimization, audit review.

## Capabilities and Constraints
- Strict role-based access control (ADMIN, REGISTRAR, JUDGE).
- Hard constraint enforcement: no judge double-booking, courtroom capacity limits, working hour limits, judge leave exclusion.
- Purely synthetic demonstration dataset calibrated to Indian judiciary aggregate distributions; no confidential or real court records.
- In-memory authenticated credentials session; zero credential persistence in local storage.

## Brand Commitments
- Name: JudicialFlow
- Voice: Authoritative, dignified, transparent, explainable, deeply respectful of judicial process.
- Palette tokens: Lavender (#C4C3E3), Violet (#504E76), Cream (#FDF8E2), Sage (#A3B565), Apricot (#FCDD9D), Orange hot accent (#F1642E).

## Evidence on Hand
- Full synthetic case generator calibrated to statutory targets.
- 21 Flyway database migrations and verified PostgreSQL schema.
- Automated simulation benchmark with bootstrap confidence intervals and survivorship metrics.

## Product Principles
1. Every score shows its reasoning.
2. Every override leaves an audit trail.
3. Same inputs, same schedule.
4. Transparent, humane, and explainable court administration.

## Accessibility & Inclusion
- Target: WCAG 2.1 AA across all surfaces.
- High contrast typography (minimum 4.5:1 text, 3:1 graphical elements).
- Color is never the single conveyor of state (redundant text + iconography for urgency scales).
- Full keyboard navigation and visible focus rings (2px violet with 2px offset).
