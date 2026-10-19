package fr.codetics.ev_server.persistence

enum class DatabaseKind(val property: String) {
    SQL("sql"),
    MONGODB("mongodb");

    companion object {
        const val PROPERTY = "app.persistence"

        fun of(url: String): DatabaseKind {
            val scheme = url.trim().lowercase()
            return when {
                scheme.startsWith("mongodb://") || scheme.startsWith("mongodb+srv://") -> MONGODB
                scheme.startsWith("jdbc:postgresql:") || scheme.startsWith("jdbc:h2:") -> SQL
                else ->
                    throw IllegalStateException(
                        "DATABASE_URL doit commencer par mongodb://, mongodb+srv:// ou jdbc:postgresql:."
                    )
            }
        }
    }
}
