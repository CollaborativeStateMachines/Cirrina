package at.ac.uibk.dps.cirrina.observability.logging;

import at.ac.uibk.dps.cirrina.observability.MethodName;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
  MethodName name();
}