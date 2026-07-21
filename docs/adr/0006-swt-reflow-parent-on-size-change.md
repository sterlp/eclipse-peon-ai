# ADR 0006 — SWT: reflow the parent chain when a control's content changes size

**Status:** Accepted

**Context:** A **recurring, non-obvious bug** (hit at least four times): after changing a control's
text / image / visibility at runtime in a way that alters its **preferred size**, calling
`control.layout(true, true)` on the control *itself* — or not laying out at all — does **not** update
that control's position/size within its parent's `GridLayout`/`RowLayout`. The parent computed the
child's cell from the *old* preferred size, so a label that grows from `""` to `↑ 12k ↓ 3k` stays at
its old (often ~0) width and the new text is clipped/invisible until something else forces a resize.

The most recent instance was `TokenHeaderWidget`: the readout never appeared after a chat because
`refresh()` only laid out the widget itself. The fix — and the existing correct examples in the
codebase — reflow **up the parent chain**:
- `ActionsBarWidget.updateCompact(...)` calls `btnCompact.getParent().layout(false, false)`.
- `UserInputWidget.requestReflow()` does `layout(true,true)` → `parent.layout(...)` →
  `grandparent.layout(new Control[]{ parent })`.

**Decision:** Whenever a widget mutates content that can change its preferred size, **re-layout its
parent (and up the chain), not just itself.** Standard shape:

```java
private void requestReflow() {
    layout(true, true);
    Composite p = getParent();
    if (p == null || p.isDisposed()) return;
    p.layout(true, true);
    Composite pp = p.getParent();
    if (pp != null && !pp.isDisposed()) pp.layout(new Control[] { p });
}
```

Complementary safeguard: give a variable-width readout a `GridData(SWT.FILL, …)` (or a width hint)
so its cell always has width and the inner label just aligns within it — removing the 0-width trap.

**Consequences:** Growing labels/badges become visible immediately; no "invisible until the view is
resized" bugs. Reflow runs on infrequent events (per LLM response), so the extra `layout()` cost is
negligible. This is a general SWT rule for this project, independent of any one feature.
