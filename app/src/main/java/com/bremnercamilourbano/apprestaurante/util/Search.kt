package com.bremnercamilourbano.apprestaurante.util

import com.bremnercamilourbano.apprestaurante.model.Product
import java.text.Normalizer

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

/** Minúsculas y sin tildes: "Clásica" y "clasica" se consideran iguales. */
fun String.foldForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

/** Búsqueda por nombre del producto. Un texto vacío muestra todo. */
fun Product.matchesQuery(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || title.foldForSearch().contains(q.foldForSearch())
}
