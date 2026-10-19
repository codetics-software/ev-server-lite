package fr.codetics.ev_server.repositories

import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.ListPagingAndSortingRepository
import org.springframework.data.repository.NoRepositoryBean

@NoRepositoryBean
interface StoreRepository<T : Any, ID : Any> :
    ListCrudRepository<T, ID>, ListPagingAndSortingRepository<T, ID>
