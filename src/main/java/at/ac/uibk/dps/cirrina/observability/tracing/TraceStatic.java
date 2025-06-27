package at.ac.uibk.dps.cirrina.observability.tracing;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceStatic {

}
