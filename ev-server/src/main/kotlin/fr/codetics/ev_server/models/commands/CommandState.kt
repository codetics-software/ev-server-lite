package fr.codetics.ev_server.models.commands

enum class CommandState {
    QUEUED,
    SENT,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED;

    companion object {
        val ACTIVE = setOf(QUEUED, SENT)
    }
}
