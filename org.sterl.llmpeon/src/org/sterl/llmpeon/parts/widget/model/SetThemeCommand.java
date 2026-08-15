package org.sterl.llmpeon.parts.widget.model;

/** Switch the chat UI between light and dark theme. */
public record SetThemeCommand(String theme) implements UiCommand {

    public static final SetThemeCommand LIGHT = new SetThemeCommand("light");
    public static final SetThemeCommand DARK  = new SetThemeCommand("dark");

    @Override
    public String type() { return "setTheme"; }
}
