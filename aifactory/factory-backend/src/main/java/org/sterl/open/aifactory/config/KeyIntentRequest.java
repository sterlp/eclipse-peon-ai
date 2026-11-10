package org.sterl.open.aifactory.config;

/**
 * The two fields a request answers the key with (R-WP-16): both update bodies of the config page state
 * that intent in the same words, so {@link WireKeyIntent} is one rule read by both instead of a copy
 * per level. Accessors only — no state, no behaviour.
 */
public interface KeyIntentRequest {

    String apiKey();

    Boolean removeApiKey();
}
