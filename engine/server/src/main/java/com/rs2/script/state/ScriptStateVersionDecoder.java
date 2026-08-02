package com.rs2.script.state;

/**
 * Decodes or migrates one persisted script-state version into the current
 * immutable snapshot model.
 */
interface ScriptStateVersionDecoder {
	ScriptStateSnapshot decode(String encodedBody);
}
