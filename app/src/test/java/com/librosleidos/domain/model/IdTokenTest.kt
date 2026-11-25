package com.librosleidos.domain.model

import org.junit.Assert.assertFalse
import org.junit.Test

/** Spec: "Token not logged". */
class IdTokenTest {

    @Test
    fun `toString does not contain the token value`() {
        val token = IdToken("super-secret-google-id-token")

        assertFalse(token.toString().contains("super-secret"))
        assertFalse("$token".contains(token.value))
    }
}
