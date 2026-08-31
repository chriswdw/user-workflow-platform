# Design Review: Workflow Platform Frontend

**Review ID:** platform_frontend_20260507
**Reviewed:** 2026-05-07
**Target:** platform-frontend/src/
**Focus:** Visual design, usability, code quality, performance
**Platform:** Desktop

---

## Summary

The frontend has a solid visual foundation — well-structured CSS design tokens, clean component decomposition, and consistent use of the design system in the parts of the UI that are wired up. However, a significant number of CSS classes referenced in JSX are not defined in `index.css`, which means core navigation, form inputs, table layouts, and admin views likely render unstyled. Beyond that, the detail panel and wizard overlay lack keyboard dismissal support, and there are several accessibility gaps in the tab and live-region patterns.

**Issues Found:** 20

- Critical: 3
- Major: 8
- Minor: 5
- Suggestions: 4

---

## Critical Issues

### Issue 1: Missing CSS classes break core UI components

**Severity:** Critical
**Category:** Visual / Code
**Locations:**
- `App.tsx` — `.app-header-nav`, `.app-header-nav-btn`, `.app-header-nav-btn--active`
- `App.tsx` — `.badge--count`
- `StepBasicInfo.tsx` — `.form-input`, `.form-label`, `.form-input--error`, `.required`
- `WizardShell.tsx` — `.btn-icon`
- `AllDraftsAdminView.tsx` — `.data-table`, `.view-container`, `.view-title`, `.status-text`, `.status-text--error`, `.submission-detail-actions`, `.admin-split--open`

**Problem:**
`index.css` uses descendant selectors for form controls (`.form-field input`, `.form-field label`) but the components reference standalone class names (`.form-input`, `.form-label`). The nav buttons, admin table, and several other UI regions reference classes that don't exist anywhere in the stylesheet.

**Impact:**
The header navigation, form inputs in the wizard, the admin drafts table, and all count badges render with no custom styling. This is likely the most visible defect in the application right now.

**Recommendation:**
Add the missing classes to `index.css`. For the form controls, either:
- Add explicit `.form-input` / `.form-label` classes that mirror the descendant-selector styles, or
- Update the components to drop the class names and rely on the existing descendant selectors.

```css
/* Add to index.css */

/* ── Header nav ── */
.app-header-nav { display: flex; align-items: center; gap: 4px; }

.app-header-nav-btn {
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: rgba(255,255,255,.8);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background .15s, color .15s;
  white-space: nowrap;
}
.app-header-nav-btn:hover { background: rgba(255,255,255,.1); color: #fff; }
.app-header-nav-btn--active {
  background: rgba(255,255,255,.15);
  color: #fff;
  border-color: rgba(255,255,255,.25);
}

/* ── Count badge ── */
.badge--count {
  background: var(--color-primary);
  color: #fff;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 100px;
  font-size: 11px;
  font-weight: 700;
}

/* ── Form inputs (standalone classes, mirrors descendant selectors) ── */
.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}
.form-input {
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-family: inherit;
  color: var(--color-text);
  background: var(--color-surface);
  transition: border-color .15s, box-shadow .15s;
  width: 100%;
}
.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37,99,235,.1);
}
.form-input--error { border-color: var(--color-danger); }
.form-input--error:focus { box-shadow: 0 0 0 3px rgba(220,38,38,.1); }

.required { color: var(--color-danger); margin-left: 2px; }

/* ── Icon button ── */
.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px; height: 30px;
  border-radius: var(--radius-sm);
  border: none;
  background: none;
  font-size: 16px;
  color: rgba(255,255,255,.7);
  cursor: pointer;
  transition: background .15s, color .15s;
}
.btn-icon:hover { background: rgba(255,255,255,.15); color: #fff; }

/* ── Data table ── */
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.data-table th {
  padding: 10px 14px;
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .4px;
  text-transform: uppercase;
  color: var(--color-text-muted);
  background: var(--color-bg);
  border-bottom: 2px solid var(--color-border);
}
.data-table td {
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border-light);
  color: var(--color-text);
}
.data-table tbody tr:last-child td { border-bottom: none; }

/* ── View container ── */
.view-container { padding: 20px; }
.view-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 16px;
}

/* ── Status text ── */
.status-text { font-size: 14px; color: var(--color-text-muted); padding: 16px 0; }
.status-text--error { color: var(--color-danger); }

/* ── Submission detail actions ── */
.submission-detail-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 20px;
}

/* ── Admin split open state ── */
.admin-split--open .admin-split-main { border-right: 1px solid var(--color-border); }
```

---

### Issue 2: No keyboard dismissal for wizard overlay or detail panel

**Severity:** Critical
**Category:** Usability / Accessibility
**Locations:** `WizardShell.tsx`, `DetailPanel.tsx`

**Problem:**
The wizard renders as a fixed full-screen overlay (`z-index: 1000`) and the detail panel slides in from the right (`z-index: 100`). Neither component listens for the Escape key, so keyboard-only users and power users have no shortcut to close them.

**Impact:**
Keyboard-only users are trapped in the overlay until they tab to the close button. Power users on a financial blotter expect Escape to dismiss panels without reaching for the mouse.

**Recommendation:**

```tsx
// In WizardShell.tsx — add inside the component:
useEffect(() => {
  function handleKeyDown(e: KeyboardEvent) {
    if (e.key === 'Escape') onClose();
  }
  document.addEventListener('keydown', handleKeyDown);
  return () => document.removeEventListener('keydown', handleKeyDown);
}, [onClose]);

// Same pattern in DetailPanel.tsx
useEffect(() => {
  function handleKeyDown(e: KeyboardEvent) {
    if (e.key === 'Escape') onClose();
  }
  document.addEventListener('keydown', handleKeyDown);
  return () => document.removeEventListener('keydown', handleKeyDown);
}, [onClose]);
```

---

### Issue 3: Missing `type="button"` on DetailPanel buttons

**Severity:** Critical
**Category:** Usability
**Location:** `DetailPanel.tsx:25, 34, 38`

**Problem:**
The close button and both tab buttons in `DetailPanel` omit `type="button"`. In HTML, buttons without a `type` attribute default to `type="submit"` when inside a form. If the detail panel is ever rendered inside a form context (or if a future refactor introduces one), these buttons will submit the form.

**Impact:**
Silent data submission risk. Also a linting/code-quality signal — `type="button"` should always be explicit on non-submit buttons.

**Recommendation:**

```tsx
// Before
<button className="detail-panel-close" onClick={onClose} aria-label="Close">×</button>
<button className={tab === 'details' ? 'tab tab--active' : 'tab'} onClick={() => setTab('details')}>
<button className={tab === 'audit' ? 'tab tab--active' : 'tab'} onClick={() => setTab('audit')}>

// After
<button type="button" className="detail-panel-close" onClick={onClose} aria-label="Close">×</button>
<button type="button" className={tab === 'details' ? 'tab tab--active' : 'tab'} onClick={() => setTab('details')}>
<button type="button" className={tab === 'audit' ? 'tab tab--active' : 'tab'} onClick={() => setTab('audit')}>
```

---

## Major Issues

### Issue 4: Blotter hardcoded to 600px height

**Severity:** Major
**Category:** Visual / Usability
**Location:** `Blotter.tsx:42`

**Problem:**
`style={{ height: 600 }}` is hardcoded on the ag-Grid container. On a 1440p monitor the blotter occupies roughly 40% of the vertical space below the header, leaving a large dead area. On a 13" laptop at 768px viewport height the blotter may feel cramped.

**Recommendation:**

```tsx
// Before
<div className="ag-theme-alpine" style={{ height: 600, width: '100%' }}>

// After
<div className="ag-theme-alpine blotter-grid">
```

```css
/* Add to index.css */
.blotter-grid {
  width: 100%;
  height: calc(100vh - 52px - 40px - 2px); /* viewport - header - main padding - border */
}
```

Or expose it as a CSS variable on `:root` so it can be overridden per deployment.

---

### Issue 5: No empty state for the Blotter

**Severity:** Major
**Category:** Usability
**Location:** `App.tsx:84–91`

**Problem:**
When `items.length === 0` (workflow type has no work items), the ag-Grid renders a blank table body. There is no message telling the user why the table is empty or what to do.

**Recommendation:**

```tsx
// In App.tsx, inside the blotter view block:
{!isLoading && !isError && config && items.length === 0 && (
  <div className="blotter-empty">
    <p>No work items found for <strong>{workflowType.replaceAll('_', ' ')}</strong>.</p>
  </div>
)}
{!isLoading && !isError && config && items.length > 0 && (
  <div className="blotter-container">
    <Blotter ... />
  </div>
)}
```

```css
/* Add to index.css */
.blotter-empty {
  padding: 60px 20px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
```

---

### Issue 6: Workflow type selector causes header layout shift

**Severity:** Major
**Category:** Visual / Usability
**Location:** `App.tsx:65–72`

**Problem:**
The `<select>` for workflow type is conditionally rendered only when `view === 'blotter'`. Its appearance and disappearance causes the `margin-left: auto` user info span and logout button to shift position.

**Recommendation:**
Render the select always, but disable it in non-blotter views. This keeps the header layout stable:

```tsx
<select
  className="app-header-select"
  value={workflowType}
  onChange={e => setWorkflowType(e.target.value)}
  disabled={view !== 'blotter'}
  aria-label="Workflow type"
>
  {WORKFLOW_TYPES.map(t => (
    <option key={t} value={t}>{t.replaceAll('_', ' ')}</option>
  ))}
</select>
```

Add to CSS:
```css
.app-header-select:disabled { opacity: .4; cursor: not-allowed; }
```

---

### Issue 7: "Create Workflow Type" nav button has no active state

**Severity:** Major
**Category:** Visual / Usability
**Location:** `App.tsx:54–58`

**Problem:**
Every other nav button uses `view === '<view>'` to apply the `--active` modifier. The wizard button calls `openWizard()` which sets `view = 'wizard'`, but the button's class is a plain `app-header-nav-btn` with no conditional.

**Recommendation:**

```tsx
// Before
<button type="button" className="app-header-nav-btn" onClick={openWizard}>
  Create Workflow Type
</button>

// After
<button
  type="button"
  className={`app-header-nav-btn${view === 'wizard' ? ' app-header-nav-btn--active' : ''}`}
  onClick={openWizard}
>
  Create Workflow Type
</button>
```

---

### Issue 8: No `aria-live` region for loading/error states

**Severity:** Major
**Category:** Accessibility
**Locations:** `App.tsx:78–82`, `DetailPanel.tsx:38–40`

**Problem:**
Loading and error messages are plain `<p>` elements. Screen readers are not notified when these messages appear because there is no live region.

**Recommendation:**

```tsx
// In App.tsx:
{isLoading && <div role="status" aria-live="polite" className="status-text">Loading…</div>}
{isError && <div role="alert" className="status-text status-text--error">Failed to load work items.</div>}

// In DetailPanel.tsx:
{loading && <div role="status" aria-live="polite" style={{ padding: 24 }} className="status-text">Loading…</div>}
{itemError && <div role="alert" style={{ padding: 24 }} className="status-text status-text--error">Failed to load item.</div>}
```

---

### Issue 9: DetailPanel tabs lack ARIA tab semantics

**Severity:** Major
**Category:** Accessibility
**Location:** `DetailPanel.tsx:32–40`

**Problem:**
The tab bar uses plain `<button>` elements without `role="tablist"`, `role="tab"`, `aria-selected`, or `role="tabpanel"`. Screen readers cannot identify this as a tab widget.

**Recommendation:**

```tsx
<nav className="detail-panel-tabs" role="tablist" aria-label="Work item details">
  <button
    type="button"
    role="tab"
    id="tab-details"
    aria-selected={tab === 'details'}
    aria-controls="panel-details"
    className={tab === 'details' ? 'tab tab--active' : 'tab'}
    onClick={() => setTab('details')}
  >
    Details
  </button>
  <button
    type="button"
    role="tab"
    id="tab-audit"
    aria-selected={tab === 'audit'}
    aria-controls="panel-audit"
    className={tab === 'audit' ? 'tab tab--active' : 'tab'}
    onClick={() => setTab('audit')}
  >
    Audit Trail
  </button>
</nav>

<div
  role="tabpanel"
  id="panel-details"
  aria-labelledby="tab-details"
  className="detail-panel-body"
  hidden={tab !== 'details'}
>
  {/* details content */}
</div>
<div
  role="tabpanel"
  id="panel-audit"
  aria-labelledby="tab-audit"
  className="detail-panel-body"
  hidden={tab !== 'audit'}
>
  {/* audit content */}
</div>
```

---

### Issue 10: Inline styles bypass the design token system

**Severity:** Major
**Category:** Code quality
**Locations:** `App.tsx:78, 81`, `DetailPanel.tsx:38, 40`

**Problem:**
`style={{ color: 'var(--color-text-muted)' }}` and `style={{ padding: 24, color: 'var(--color-danger)' }}` are scattered throughout. When design tokens are renamed or new themes are introduced, these inline references are invisible to global find/replace.

**Recommendation:**
Replace with the `.status-text` and `.status-text--error` utility classes described in Issue 1, which already express the same intent.

---

### Issue 11: `console.warn` fires on every blotter render

**Severity:** Major
**Category:** Performance
**Location:** `Blotter.tsx:38`

**Problem:**
`console.warn(...)` inside `valueGetter` fires on every ag-Grid render pass for every row with a missing field. With 100 rows and one misconfigured column, this emits 100 console entries per render cycle.

**Recommendation:**

```tsx
// Before: inside valueGetter
console.warn(`blotter: field path not found: ${col.field}`);

// After: warn once per column outside valueGetter
const warnedFields = useMemo(() => new Set<string>(), []);

// inside valueGetter:
if (raw === undefined) {
  if (!warnedFields.has(col.field)) {
    console.warn(`blotter: field path not found: ${col.field}`);
    warnedFields.add(col.field);
  }
  return '—';
}
```

---

## Minor Issues

### Issue 12: Inconsistent close button characters

`DetailPanel.tsx` uses `×` (HTML entity ×, U+00D7); `WizardShell.tsx` and `AllDraftsAdminView.tsx` use `✕` (U+2715). Standardise on one character or use a shared SVG close icon component across all dismiss buttons.

---

### Issue 13: Raw Unicode arrows in wizard footer button labels

`WizardShell.tsx` uses `← Prev` and `Next →` as button text. Arrow characters in localised text strings are fragile. Prefer CSS-generated arrows or an SVG icon, keeping the button label text plain (`Previous` / `Next`).

---

### Issue 14: Wizard checkmark rendered as plain text

`WizardShell.tsx:80` renders `{status === 'done' ? '✓' : stepNum}` inside a `<span>`. Add `aria-hidden="true"` to this span and provide a visually-hidden screen-reader label (e.g. `<span className="sr-only">Completed</span>`) so users on screen readers know the step is done.

---

### Issue 15: `globalThis.confirm()` used for discard confirmation

**Location:** `AllDraftsAdminView.tsx:39`

The native browser confirm dialog is not styleable, is blocked in sandboxed iframes, and breaks the visual design. The project already has a `ConfirmModal` component at `components/detail/ConfirmModal.tsx`. Use it instead.

---

### Issue 16: Login page has no dev-mode indicator

`LoginPage.tsx` presents role-selection buttons as the authentication UI with no indication that this is a dev/demo flow. In screen recordings, demos, or handoffs this is indistinguishable from a real auth screen. Add a small `DEV MODE` pill or banner.

---

## Suggestions

### Suggestion 1: Make completed wizard steps click-navigable

Users who want to revisit step 2 from step 6 must press "← Prev" four times. Completed steps (`status === 'done'`) should be clickable — `onClick={() => setStep(stepNum)}` on the step circle. This is a standard wizard UX pattern and particularly valuable for a 7-step form.

---

### Suggestion 2: Add an explicit "Save Draft" button in the wizard footer

Auto-save on "Next →" is not discoverable. Users may close the wizard thinking their work is lost. A "Save Draft" button (left of the nav buttons) makes the save action explicit and builds trust in a long-form financial workflow.

---

### Suggestion 3: Replace plain "Loading…" text with a content skeleton

The blotter and detail panel show `Loading…` as plain text. A skeleton (grey pulsing placeholder rows matching the approximate content layout) reduces perceived load time and keeps the layout stable during loading. This is standard practice for data-dense financial UIs.

---

### Suggestion 4: Add a toast system for async action feedback

Workflow transitions, draft saves, and approval submissions show errors inline but show no success confirmation. A lightweight toast notification (e.g. `react-hot-toast`) would confirm success without blocking the UI or requiring the user to navigate away. Errors could also be surfaced as dismissible toasts rather than inline in each component, reducing code duplication.

---

## Positive Observations

- Design tokens in `index.css` `:root` are well-named and comprehensive — the system is clearly designed for consistency
- Hexagonal architecture keeps React components thin; they call ports, not infrastructure
- `wizardStore` (Zustand) cleanly separates multi-step form state from the `WizardShell` render tree
- `BlotterConfig`-driven column definitions with role-based masking are cleanly abstracted — the Blotter component has no business logic
- ag-Grid column definitions are correctly memoised with `useMemo` — no unnecessary column rebuilds
- The `panel-slide-in` CSS animation on the detail panel is a nice touch that aids spatial orientation

---

## Next Steps

1. **[Immediate]** Add all missing CSS classes to `index.css` — this is the highest-impact single change
2. **[Immediate]** Add `type="button"` to `DetailPanel.tsx` close and tab buttons
3. **[This sprint]** Add Escape key handlers to `WizardShell` and `DetailPanel`
4. **[This sprint]** Fix nav active state for "Create Workflow Type" and header layout shift
5. **[This sprint]** Add ARIA tab semantics to `DetailPanel`
6. **[Next sprint]** Replace inline styles with CSS classes; add `aria-live` to loading/error states
7. **[Next sprint]** Fix blotter height to be viewport-relative; add empty state
8. **[Backlog]** Replace `globalThis.confirm()` with `ConfirmModal`; add wizard step navigation; add toast system

---

_Generated by ui-design:design-review. Run `/ui-design:design-review` again after fixes to track progress._
