package com.datataxi.domain

enum class BackfillPhase { IDLE, ARMED, RUNNING, DONE }

/** État de la récupération automatique de l'historique (défilement de la liste de Favoris). */
data class BackfillState(
    val phase: BackfillPhase = BackfillPhase.IDLE,
    val steps: Int = 0,
    val added: Int = 0,
    val message: String = "",
)
