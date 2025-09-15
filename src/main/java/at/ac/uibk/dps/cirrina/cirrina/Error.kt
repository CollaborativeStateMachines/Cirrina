package at.ac.uibk.dps.cirrina.cirrina

sealed class EnvironmentVariableError(message: String) : RuntimeException(message) {
  class Missing(name: String) :
    EnvironmentVariableError("Missing required environment variable '$name'")

  class Invalid(name: String, value: String, allowed: String) :
    EnvironmentVariableError(
      "Invalid value for environment variable '$name', the value is '$value', allowed values are '${allowed}'"
    )
}

sealed class ConfigurationError(message: String) : RuntimeException(message) {
  class Unknown(what: String, `is`: Any) : ConfigurationError("Unknown $what which is '$`is`'")
}
