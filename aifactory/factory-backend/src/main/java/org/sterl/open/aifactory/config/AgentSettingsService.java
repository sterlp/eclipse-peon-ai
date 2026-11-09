package org.sterl.open.aifactory.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.open.aifactory.config.agent.AgentConfigFile;
import org.sterl.open.aifactory.config.agent.AgentSlots;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The agent section of the config page (R-WP-23): one slot's file is its whole storage, so this service
 * only reads, validates and writes through {@link AgentConfigFile} — no second state, nothing cached, a
 * save is visible to the next read and the next turn without a restart (D-11).
 *
 * <p>The wire never carries a key: a slot answers with {@code keySet} only, exactly like the default
 * level (R-WP-16), and the key keeps the default level's exception — an empty field means unchanged,
 * {@code removeApiKey} deletes, and both in one request are refused.</p>
 */
@Service
public class AgentSettingsService {

    /** Names the field, never the submitted value — a slot id is user input too (R-WP-16). */
    private static final String SLOT_HINT = "agent must name one of the slots the factory runs";
    private static final String TEMPERATURE_HINT = "temperature must be a number";
    private static final String EXTRA_BODY_HINT = "extraBody must be a JSON object";

    /** Reads the submitted body the way the core selects it — an object or nothing, see {@link #isJsonObject}. */
    private static final ObjectMapper BODY_JSON = new ObjectMapper();

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
        validate(update);
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

    /**
     * Before any write, and value-free in its messages. A temperature the core cannot read would be
     * stored happily and ignored silently at turn time ({@code AgentTemperature} only warns), so the
     * number is checked here where a 400 can still name the field. The same holds for a body the core
     * does not select: {@code ExtraBody} drops anything that is not an object without a trace (A1).
     */
    private static void validate(AgentSettingsUpdate update) {
        if (update.removeApiKey() && present(update.apiKey())) {
            throw new IllegalArgumentException(FactoryConfigService.KEY_INTENT_HINT);
        }
        if (present(update.temperature()) && !isNumber(update.temperature())) {
            throw new IllegalArgumentException(TEMPERATURE_HINT);
        }
        if (present(update.extraBody()) && !isJsonObject(update.extraBody())) {
            throw new IllegalArgumentException(EXTRA_BODY_HINT);
        }
    }

    /**
     * The core's own selection ({@code ExtraBody} keeps an object, ignores the rest) — but parsed here
     * instead of through the core, because the core logs the value it discards and a submitted body must
     * never be repeated (A7). {@code tools} is deliberately not checked: the factory stores the wording,
     * the core interprets it (D9).
     */
    private static boolean isJsonObject(String value) {
        try {
            return BODY_JSON.readValue(value, Object.class) instanceof Map;
        } catch (IOException | IllegalArgumentException malformed) {
            return false;
        }
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
