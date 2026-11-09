package org.sterl.open.aifactory.config;

import java.util.List;

import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.open.aifactory.config.agent.AgentConfigFile;
import org.sterl.open.aifactory.config.agent.AgentSlots;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The agent section of the config page (R-WP-23): one slot's file is its whole storage, so this service
 * only reads and writes through {@link AgentConfigFile} — no second state, nothing cached, a
 * save is visible to the next read and the next turn without a restart (D-11).
 *
 * <p>What a field of a save may hold is a rule of the request shape, checked before this service is
 * entered (R-WP-55) — a second check of the same sentence here would only be a second place to keep it
 * in step.</p>
 *
 * <p>The wire never carries a key: a slot answers with {@code keySet} only, exactly like the default
 * level (R-WP-16), and the key keeps the default level's exception — an empty field means unchanged,
 * {@code removeApiKey} deletes, and both in one request are refused.</p>
 */
@Service
public class AgentSettingsService {

    /** Names the field, never the submitted value — a slot id is user input too (R-WP-16). */
    private static final String SLOT_HINT = "agent must name one of the slots the factory runs";

    private final FactoryHome home;
    private final AgentToolPreview toolPreview;

    public AgentSettingsService(FactoryHome home, AgentToolPreview toolPreview) {
        this.home = home;
        this.toolPreview = toolPreview;
    }

    /** Every slot the factory runs, in the doc's order — reading creates no file and no directory. */
    public List<AgentSettings> list() {
        return AgentSlots.all().stream()
                .map(slot -> read(slot.id(), slot.name()))
                .toList();
    }

    /**
     * Saves one slot and answers its fresh state. An unchecked box is not "seven empty strings" but a
     * clear: it reaches every config key of the file, including one this request never named, and leaves
     * the prompt body alone (UC-WP-45).
     */
    public AgentSettings update(String id, AgentSettingsUpdate update) {
        if (!AgentSlots.knows(id)) {
            throw new IllegalArgumentException(SLOT_HINT);
        }
        AgentConfigFile file = AgentConfigFile.forSlot(home, id);
        if (!update.overridden()) {
            file.write(AgentConfigFile.Changes.clearing());
        } else {
            file.write(new AgentConfigFile.Changes(false, update.model(), update.url(), keyChange(update),
                    update.think(), update.temperature(), update.extraBody(), update.tools()));
        }
        return read(id, nameOf(id));
    }

    /**
     * The tools the slot would run with the typed allowlist (R-WP-52). The typed value is answered as
     * typed — nothing is stored for a preview — and {@code compact} is refused: its core agent names no
     * allowlist slot, so a preview would promise a field the page must not show.
     */
    public List<String> previewTools(String id, String rawAllowlist) {
        if (!AgentSlots.knows(id) || AgentModelConfig.COMPACT.equals(id)) {
            throw new IllegalArgumentException(SLOT_HINT);
        }
        return toolPreview.activeTools(id, rawAllowlist);
    }

    private AgentSettings read(String id, String name) {
        AgentConfigFile.Values values = AgentConfigFile.forSlot(home, id).read();
        return new AgentSettings(id, name, values.overridden(), values.model(), values.url(), values.think(),
                values.temperature(), values.extraBody(), values.tools(), present(values.apiKey()));
    }

    private static String nameOf(String id) {
        return AgentSlots.all().stream().filter(slot -> slot.id().equals(id))
                .map(AgentSlots.AgentSlot::name).findFirst().orElseThrow();
    }

    /**
     * The key rule of R-WP-16, one slot at a time: an empty field is unchanged, only {@code removeApiKey}
     * deletes. Written as the file's three states so {@link AgentConfigFile} keeps one vocabulary.
     */
    private static String keyChange(AgentSettingsUpdate update) {
        if (update.removeApiKey()) {
            return "";
        }
        return present(update.apiKey()) ? update.apiKey() : null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
