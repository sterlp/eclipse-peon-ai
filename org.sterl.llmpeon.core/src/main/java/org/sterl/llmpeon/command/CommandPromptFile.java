package org.sterl.llmpeon.command;

import java.nio.file.Path;

import org.sterl.llmpeon.shared.AbstractPromptFile;

public class CommandPromptFile extends AbstractPromptFile {

    public CommandPromptFile(String name, String description, Path promptFile) {
        super(name, description, promptFile, true);
    }

    @Override
    public String shortDescription() {
        return "Command[name=" + name() + (description() == null ? "" : ", description=" + description()) + "]";
    }
}