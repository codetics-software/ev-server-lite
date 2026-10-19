package fr.codetics.ev_server.models.billing

enum class PaymentState {
    PENDING_PAYMENT,
    PAID,
    START_REQUESTED,
    CHARGING,
    STOP_REQUESTED,
    SETTLING,
    REFUND_PENDING,
    COMPLETED,
    CANCELLED,
    FAILED,
    DISPUTED;

    companion object {
        val OPEN = setOf(PENDING_PAYMENT, PAID, START_REQUESTED, CHARGING, STOP_REQUESTED)
        val WORKABLE = OPEN + setOf(SETTLING, REFUND_PENDING)
    }
}
