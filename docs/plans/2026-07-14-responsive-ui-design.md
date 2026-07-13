# Responsive Admin UI Design

## Goal

Make the core administration frontend and every official remote plugin usable at 1440, 1280, 1024, 768, and 390 CSS pixels without clipped controls, overlapping content, or accidental horizontal page overflow.

## Context

The core frontend has 57 route pages but responsive behavior is inconsistent. The official plugin workspace contains ten packages and only a small number of responsive rules. Some operational screens preserve desktop multi-column layouts below the available content width. The skin texture library is the clearest case: its inspector remains a fixed 360px side column until the viewport is already narrow.

## Chosen Approach

Use a layered responsive contract rather than global visual scaling.

1. The host layout provides a reliable content-width boundary and common responsive utility styles for application toolbars, filters, and table scroll regions.
2. Each core page family and each plugin package owns its responsive breakpoints. Desktop information density is retained where possible; only structures that cannot fit collapse into a single column.
3. Data-heavy tables retain stable column dimensions inside an explicit horizontal scroll region. Cards, filters, and forms reflow instead of squeezing text and controls.

## Breakpoints

| Width | Expected behavior |
| --- | --- |
| 1440px | Full desktop layout |
| 1280px | Compact desktop, no clipping with side navigation visible |
| 1024px | Narrow laptop: secondary panels move below primary work areas |
| 768px | Tablet: filter/action rows wrap and two-column forms become one column when needed |
| 640px and 390px | Phone: vertical workflows, full-width controls, horizontal table scroll only where semantics require it |

Breakpoints are local to the component's usable container where supported. No viewport-proportional font scaling is introduced.

## Component Rules

### Shared host behavior

- Content wrappers, grid children, and remote plugin hosts must permit shrinkage with `min-width: 0`.
- Toolbars and filter rows wrap before their controls overlap.
- Long code, IDs, and names use ellipsis or wrapping according to whether copying the full value is operationally important.
- Tables are wrapped in an explicit scroll region instead of imposing a page-level minimum width.

### Core system pages

- Audit `system`, `platform`, `dashboard`, `forms`, and `wiki` pages by layout pattern, not as a bulk CSS override.
- Two- and three-column grids collapse at the first width at which their minimum control widths no longer fit.
- Fixed editor or inspector rails become stacked panels or existing drawers on tablet widths.

### Official plugins

- Each `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-*` package owns its responsive CSS.
- Plugin source must not rely on host-private CSS selectors.
- Pages using tables expose a labelled local scroll region; pages using forms, cards, and actions reflow at the common breakpoints.
- Package-specific visual components such as 3D skin preview remain stable through constrained dimensions rather than being arbitrarily scaled.

### Skin plugin priority

- At narrow-laptop widths, the texture library and player pages move their 360px inspector/character panels below the list content.
- Administrative filters reduce from four to two columns and then one, while retaining reachable actions.
- The dashboard and card grids reduce columns without truncating action controls.
- Texture/player tables continue to scroll locally rather than expanding the page.

## Verification

- Type-check the core frontend and every changed plugin package.
- Capture browser screenshots at 1440, 1280, 1024, 768, and 390 pixels for the representative system page, skin library, and each plugin's primary route.
- Assert that `document.documentElement.scrollWidth` does not exceed the viewport for non-table page regions.
- Inspect table pages separately to verify horizontal scrolling is contained to their table wrapper.

## Non-goals

- No redesign of product workflows or information architecture.
- No global `zoom`, transform scaling, or viewport-based font-size rule.
- No attempt to force wide operational tables into unreadable card lists.
