package com.cinehub.player

import androidx.media3.common.Player

/** Telemetry hooks kept local and inert; the player must not depend on Saimo's monitoring service. */
object Telemetria {
    fun observar(player: Player) = Unit
    fun comecou(kind: String, titulo: String, url: String, fonte: Int, nova: Boolean = true) = Unit
    fun tocou(kind: String, titulo: String, url: String, fonte: Int, ms: Long) = Unit
    fun falhou(kind: String, titulo: String, url: String, fonte: Int, detalhe: String) = Unit
    fun caiu(kind: String, titulo: String, fontes: Int) = Unit
    fun parou() = Unit
}
