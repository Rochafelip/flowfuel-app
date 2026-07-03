package com.flowfuel.app.feature.station.domain.model

val STATION_RADIUS_PRESETS_METERS = listOf(1_000, 3_000, 5_000, 10_000)
const val DEFAULT_STATION_RADIUS_METERS = 3_000

/** Teto de busca do último preset, que não tem um próximo degrau para delimitar a faixa. */
private const val FARTHEST_BAND_QUERY_RADIUS_METERS = 20_000

/** Faixa de distância (em metros) coberta por um preset do filtro. */
data class StationDistanceBand(val minMeters: Int, val maxMeters: Int)

/**
 * Cada preset de [STATION_RADIUS_PRESETS_METERS] representa uma faixa exclusiva de distância,
 * não um raio cumulativo: o preset "3 km" mostra só postos entre 3000m e o começo do próximo
 * preset (4999m), por exemplo — não repete os postos já cobertos pelo preset "1 km". O primeiro
 * preset é o único que começa em 0 (senão os postos mais próximos de todos ficariam de fora).
 */
fun stationDistanceBand(presetMeters: Int): StationDistanceBand {
    val isFirstPreset = STATION_RADIUS_PRESETS_METERS.firstOrNull() == presetMeters
    val minMeters = if (isFirstPreset) 0 else presetMeters
    val nextPreset = STATION_RADIUS_PRESETS_METERS.firstOrNull { it > presetMeters }
    val maxMeters = nextPreset?.minus(1) ?: FARTHEST_BAND_QUERY_RADIUS_METERS
    return StationDistanceBand(minMeters = minMeters, maxMeters = maxMeters)
}
