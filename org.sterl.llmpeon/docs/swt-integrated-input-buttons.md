# SWT: Flat Icon Buttons Next to a StyledText

How to place icon buttons beside a text input so the whole area reads as one flat white field — on **both macOS and Windows** — without CSS.

## Phase 1 Target

```
┌─────────────────────────────────────────────┐  ← SWT.BORDER on outer wrapper
│  StyledText (native white)      [🎤]  [▶]   │  ← flat icon Buttons, no chrome
└─────────────────────────────────────────────┘
```

## Key Lesson — What NOT to do

A first intuition is to force a white background on the wrapper so everything inherits it:

```java
wrapper.setBackground(white);                        // ❌ don't
wrapper.setBackgroundMode(SWT.INHERIT_FORCE);        // ❌ don't
```

On **macOS** this *overrides* `StyledText`'s native rendering and produces a **gray** field instead of white. `StyledText` only paints correctly when no background is set.

Rule: **never call `setBackground` or `setBackgroundMode` on any composite that contains a `StyledText`**, and never on the `StyledText` itself.

## The Right Pattern

1. **Leave backgrounds alone.** `StyledText` picks up native OS white. Sibling composites inherit the same native white from their parent. No calls needed.

2. **One `SWT.BORDER`** — on the outermost wrapper composite only. None on inner composites, none on the text widget.

3. **Flat icon buttons** — a regular `Button` would draw the OS push-button frame. To suppress it, use `SWT.PUSH | SWT.NATIVE` plus a `PaintListener` that takes over rendering:

   ```java
   Button btn = new Button(parent, SWT.PUSH | SWT.NATIVE);
   btn.setImage(icon);

   btn.addPaintListener(e -> {
       Rectangle b = btn.getBounds();
       e.gc.fillRectangle(0, 0, b.width, b.height);   // fills with btn.getBackground()
       Image img = btn.getImage();
       if (img != null) {
           Rectangle ib = img.getBounds();
           e.gc.drawImage(img,
               (b.width  - ib.width)  / 2,
               (b.height - ib.height) / 2);
       }
   });
   ```

   Because `fillRectangle` uses the button's inherited background (native white), and the OS frame is suppressed, only the icon is visible.

4. **Hover highlight** — add a `MouseTrackListener` that switches the background to a light gray and restores it on exit. The PaintListener repaints automatically:

   ```java
   btn.addMouseTrackListener(new MouseTrackAdapter() {
       Color original;
       Color hoverBg = new Color(display, 232, 232, 232);
       public void mouseEnter(MouseEvent e) {
           original = btn.getBackground();
           btn.setBackground(hoverBg);
       }
       public void mouseExit(MouseEvent e) {
           btn.setBackground(original);
       }
   });
   ```

5. **Dynamic state (e.g. recording red)** — call `btn.setBackground(red)` then `btn.redraw()`. The PaintListener fills with red. Reset to `null` to restore the inherited native white.

6. **Kill GridLayout seams.** A `horizontalSpacing` or `verticalSpacing` of even 2 px between the StyledText and the button column shows up as a thin gray line on macOS — the parent composite's native gray bleeds through the gap. Set both to `0` between the text widget and the button column, and between stacked buttons in the right column. Use `marginWidth` / `marginHeight` on the *outer* row layout for breathing room instead.

## Reusable helper

`SwtUtil.createIconButton(parent, icon, tooltip)` wraps the pattern above. See `org.sterl.llmpeon.parts.shared.SwtUtil`.

## Why this mirrors Copilot

This is the same technique used by the GitHub Copilot Eclipse plugin (`copilot_src/.../utils/UiUtils.java` `createIconButton`). Their tree contains **zero** `setBackground` or `setBackgroundMode` calls on the composites around their `StyledText` — the native white shows through on both macOS and Windows.
