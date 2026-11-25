package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Session
import com.librosleidos.domain.port.SessionStore
import kotlinx.coroutines.flow.Flow

/** Spec: "Session observed". */
class ObserveSessionUseCase(private val store: SessionStore) {
    operator fun invoke(): Flow<Session> = store.session
}
