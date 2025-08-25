package at.ac.uibk.dps.cirrina.observability.tracing;

import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class ContextStore {

  private static final Map<Object, Context> STORE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private ContextStore() {
  }

  static void put(Object key, Context context) {
    if (key != null && context != null) {
      STORE.put(key, context);
    }
  }

  static Context get(Object key) {
    return key == null ? null : STORE.get(key);
  }
}
