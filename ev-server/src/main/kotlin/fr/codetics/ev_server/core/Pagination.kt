package fr.codetics.ev_server.core

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

fun bounds(offset: Int, limit: Int) {
    require(offset >= 0 && limit in 1..500 && offset % limit == 0) {
        "Choisissez une limite entre 1 et 500 et un décalage multiple de la taille de page."
    }
}

fun pageRequest(offset: Int, limit: Int, sort: Sort): PageRequest {
    bounds(offset, limit)
    return PageRequest.of(offset / limit, limit, sort)
}
