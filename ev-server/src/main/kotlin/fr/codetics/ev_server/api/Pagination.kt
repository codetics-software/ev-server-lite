package fr.codetics.ev_server.api

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

fun bounds(offset: Int, limit: Int) {
    require(offset >= 0 && limit in 1..500 && offset % limit == 0) {
        "Use a limit between 1 and 500 and an offset aligned to the page size"
    }
}

fun pageRequest(offset: Int, limit: Int, sort: Sort): PageRequest {
    bounds(offset, limit)
    return PageRequest.of(offset / limit, limit, sort)
}
