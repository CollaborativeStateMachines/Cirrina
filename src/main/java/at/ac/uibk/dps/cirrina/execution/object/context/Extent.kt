package at.ac.uibk.dps.cirrina.execution.object.context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Extent {

  private List<Context> extent;

  public Extent() {
    extent = new ArrayList<>();
  }

  public Extent(Context low) {
    extent = List.of(low);
  }

  public Extent(Context low, Context high) {
    extent = List.of(low, high);
  }

  public Extent(List<Context> low, Context high) {
    extent = Stream.concat(low.stream(), Stream.of(high)).toList();
  }

  public int setOrCreate(String name, Object value) throws IOException {
    final var last = extent.getLast();

    try {
      return last.assign(name, value);
    } catch (IOException e) {
      return last.create(name, value);
    }
  }

  public SetResult trySet(String name, Object value) throws IOException {
    IOException lastException = null;

    for (final var context : extent.reversed()) {
      try {
        final var size = context.assign(name, value);
        return new SetResult(size, context);
      } catch (IOException e) {
        lastException = e;
      }
    }

    if (lastException != null) {
      throw lastException;
    } else {
      throw new IOException("Could not set variable value, no context could be found to assign to");
    }
  }

  public Extent extend(Context high) {
    return new Extent(extent, high);
  }

  public Context getLow() {
    return extent.getFirst();
  }

  public Context getHigh() {
    return extent.getLast();
  }

  public Optional<Object> resolve(String name) {
    return extent
      .reversed()
      .stream()
      .map(context -> {
        try {
          return Optional.ofNullable(context.get(name));
        } catch (IOException e) {
          return Optional.empty();
        }
      })
      .filter(Optional::isPresent)
      .map(Optional::get)
      .findFirst();
  }

  public record SetResult(int size, Context context) {}
}
