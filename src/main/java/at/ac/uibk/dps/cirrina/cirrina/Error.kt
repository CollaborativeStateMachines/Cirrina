package at.ac.uibk.dps.cirrina.cirrina

sealed class ConfigurationError(message: String) : RuntimeException(message) {
  class Unknown(what: String, `is`: Any) : ConfigurationError("unknown $what which is '$`is`'")
}

sealed class EnvironmentVariableError(message: String) : ConfigurationError(message) {
  class Missing(name: String) :
    EnvironmentVariableError("missing required environment variable '$name'")

  class Invalid(name: String, value: String, allowed: String) :
    EnvironmentVariableError(
      "invalid value for environment variable '$name', the value is '$value', allowed values are '${allowed}'"
    )
}
