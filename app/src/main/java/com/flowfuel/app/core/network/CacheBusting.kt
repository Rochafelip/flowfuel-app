package com.flowfuel.app.core.network

/**
 * Anexa um parâmetro de cache-busting à URL. Usado nas respostas de upload de
 * foto (veículo/perfil), cuja URL é fixa por entidade (ex.: `/vehicles/{id}/photo`)
 * — sem isso, o Coil serve a imagem antiga do cache por a chave (a URL) não mudar.
 */
fun String.withCacheBust(): String =
    this + (if (contains("?")) "&" else "?") + "cb=" + System.currentTimeMillis()
