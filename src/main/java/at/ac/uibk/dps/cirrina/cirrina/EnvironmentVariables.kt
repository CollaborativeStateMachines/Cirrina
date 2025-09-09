package at.ac.uibk.dps.cirrina.cirrina

enum class EventProvider {
    NATS
}

enum class PersistentContextProvider {
    NATS
}

data class EnvironmentVariable<T>(val name: String, val required: Boolean = false, val default: T? = null, val mapper: (String) -> T = {
    it as T
}) {
    fun get(): T? {
        val value = System.getenv(name)
        return when {
            value != null -> mapper(value)
            default != null -> default
            required -> throw IllegalStateException("Missing required environment variable: $name")
            else -> null
        }
    }
}

object EnvironmentVariables {
    // NATS event handler-specific environment variables
    val natsEventUrl = EnvironmentVariable<String>("NATS_EVENT_URL", default = "nats://localhost:4222/")

    // NATS persistent context-specific environment variables
    val natsPersistentContextUrl = EnvironmentVariable<String>("NATS_PERSISTENT_CONTEXT_URL", default = "nats://localhost:4222")
    val natsPersistentContextBucket = EnvironmentVariable<String>("NATS_PERSISTENT_CONTEXT_BUCKET", default = "persistent")

    // General environment variables
    val eventProvider = EnvironmentVariable<EventProvider>(name = "EVENT_PROVIDER", required = true, mapper = { value ->
        try {
            EventProvider.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid EVENT_PROVIDER: '$value'. Allowed: ${EventProvider.entries}")
        }
    })
    val persistentContextProvider = EnvironmentVariable<PersistentContextProvider>(name = "PERSISTENT_CONTEXT_PROVIDER", required = true, mapper = { value ->
        try {
            PersistentContextProvider.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid PERSISTENT_CONTEXT_PROVIDER: '$value'. Allowed: ${PersistentContextProvider.entries}")
        }
    })
    val healthPort = EnvironmentVariable<Int>("HEALTH_PORT", default = 0xCAFE) {
        it.toInt()
    }
}


