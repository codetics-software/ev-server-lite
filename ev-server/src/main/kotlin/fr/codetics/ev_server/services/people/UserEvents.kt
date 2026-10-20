package fr.codetics.ev_server.services.people

import fr.codetics.ev_server.models.entities.UserEntity

data class UserRegistered(val user: UserEntity)

data class UserStatusChanged(val user: UserEntity, val enabled: Boolean)
