package com.librosleidos.domain.model

/**
 * Google ID token. Held in memory only; [toString] is masked so the value
 * never reaches logs (spec: In-Memory Token Only, "Token not logged").
 */
@JvmInline
value class IdToken(val value: String) {
    override fun toString(): String = "IdToken(***)"
}
