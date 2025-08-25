package at.ac.uibk.dps.cirrina.observability.tracing;

import io.opentelemetry.context.Context;
import java.net.http.HttpRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class HttpClientContextAspect {

  @Pointcut("call(* java.net.http.HttpClient+.send(..)) && args(request,..)")
  public void httpSend(HttpRequest request) {
  }

  @Pointcut("call(* java.net.http.HttpClient+.sendAsync(..)) && args(request,..)")
  public void httpSendAsync(HttpRequest request) {
  }

  @Around("httpSend(request) || httpSendAsync(request)")
  public Object captureContext(ProceedingJoinPoint pjp, HttpRequest request) throws Throwable {
    ContextStore.put(request, Context.current());
    return pjp.proceed();
  }
}
