package at.ac.uibk.dps.cirrina.util

import java.util.UUID
import kotlin.random.Random

fun getBuildVersion(): String = object {}.javaClass.getPackage().implementationVersion ?: "unknown"

fun getInsecureUuid(): UUID = UUID(Random.nextLong(), Random.nextLong())
