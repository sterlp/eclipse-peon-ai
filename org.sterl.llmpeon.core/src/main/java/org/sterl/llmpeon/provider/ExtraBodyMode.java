package org.sterl.llmpeon.provider;

/**
 * How a provider consumes extra body fields (provider.md R3).
 */
public enum ExtraBodyMode {

    /**
     * Per-request: the body's entries are merged into the request's {@code customParameters}
     * (user entries win on key conflicts, see the merge rule in the provider Javadoc).
     */
    PER_REQUEST,

    /**
     * Build-time only: the body is baked into the model at build time (e.g. Anthropic's
     * {@code customParameters} builder); the body is part of the connection identity.
     */
    BUILD_TIME,

    /** Not supported: {@code extraBody} is ignored silently (the UI gate ships in a later cycle). */
    NONE
}
