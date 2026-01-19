package at.ac.uibk.dps.cirrina.execution.object.exchange;

import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableProtos.ValueCollection;
import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableProtos.ValueMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Value exchange, responsible for converting a value object to a consistent exchange format, using Protocol Buffers.
 * <p>
 * See the exchange protos for a protocol description.
 * <p>
 * The supported value types for exchange are:
 * <ul>
 *   <li>Integer</li>
 *   <li>Floating-point</li>
 *   <li>Long integer</li>
 *   <li>Double-precision floating-point</li>
 *   <li>String</li>
 *   <li>Boolean</li>
 *   <li>Array</li>
 *   <li>List</li>
 * </ul>
 */
public class ValueExchange {

  /**
   * The value object.
   */
  private final Object value;

  /**
   * Initializes this value exchange instance.
   *
   * @param value Value object.
   */
  public ValueExchange(Object value) {
    this.value = value;
  }

  /**
   * Construct a value exchange from byte data.
   *
   * @param data Byte data.
   * @return Value exchange.
   * @throws UnsupportedOperationException If the value could not be read.
   * @throws UnsupportedOperationException If the value type is unknown.
   */
  public static ValueExchange fromBytes(byte[] data) throws UnsupportedOperationException {
    try {
      return new ValueExchange(fromProto(ContextVariableProtos.Value.parseFrom(data)));
    } catch (InvalidProtocolBufferException e) {
      throw new UnsupportedOperationException("Could not read value from bytes");
    }
  }

  /**
   * Construct a value object from a proto.
   *
   * @param proto Value proto.
   * @return Value object.
   * @throws UnsupportedOperationException If the value type is unknown.
   */
  public static Object fromProto(ContextVariableProtos.Value proto)
    throws UnsupportedOperationException {
    switch (proto.getValueCase()) {
      case INTEGER -> {
        return proto.getInteger();
      }
      case FLOAT -> {
        return proto.getFloat();
      }
      case LONG -> {
        return proto.getLong();
      }
      case DOUBLE -> {
        return proto.getDouble();
      }
      case STRING -> {
        return proto.getString();
      }
      case BOOL -> {
        return proto.getBool();
      }
      case BYTES -> {
        return proto.getBytes().toByteArray();
      }
      case ARRAY -> {
        return fromCollectionProto(proto.getArray()).toArray();
      }
      case LIST -> {
        // Convert to ArrayList to ensure mutability
        return fromCollectionProto(proto.getList()).collect(
          Collectors.toCollection(ArrayList::new)
        );
      }
      case MAP -> {
        return fromMapProto(proto.getMap());
      }
      default -> throw new UnsupportedOperationException(
        "Context variable value type could not be read"
      );
    }
  }

  /**
   * Converts a {@link ValueCollection} proto message into a stream.
   *
   * @param collection {@link ValueCollection} proto message to convert
   * @return stream of objects
   * @throws UnsupportedOperationException if an entry could not be parsed
   */
  private static Stream<Object> fromCollectionProto(
    ContextVariableProtos.ValueCollection collection
  ) throws UnsupportedOperationException {
    return collection.getEntryList().stream().map(ValueExchange::fromProto);
  }

  /**
   * Converts a {@link ValueMap} proto message into a map.
   *
   * @param map {@link ValueMap} proto message to convert
   * @return map with objects as key and value
   * @throws UnsupportedOperationException if an entry could not be parsed
   */
  private static Map<Object, Object> fromMapProto(ContextVariableProtos.ValueMap map)
    throws UnsupportedOperationException {
    return map
      .getEntryList()
      .stream()
      .collect(
        Collectors.toMap(
          valueMapEntry -> fromProto(valueMapEntry.getKey()),
          valueMapEntry -> fromProto(valueMapEntry.getValue())
        )
      );
  }

  /**
   * Converts a stream into a {@link ValueCollection} proto message.
   *
   * @param stream stream to convert
   * @return {@link ValueCollection} proto message
   */
  private static ContextVariableProtos.ValueCollection toCollectionProto(Stream<?> stream) {
    return ContextVariableProtos.ValueCollection.newBuilder()
      .addAllEntry(
        stream.map(entry -> new ValueExchange(entry).toProto()).collect(Collectors.toList())
      )
      .build();
  }

  /**
   * Converts a map into a {@link ValueMap} proto message.
   *
   * @param map map to convert
   * @return {@link ValueMap} proto message
   */
  private static ContextVariableProtos.ValueMap toMapProto(Map<?, ?> map) {
    return ContextVariableProtos.ValueMap.newBuilder()
      .addAllEntry(
        map
          .entrySet()
          .stream()
          .map(entry ->
            ContextVariableProtos.ValueMapEntry.newBuilder()
              .setKey(new ValueExchange(entry.getKey()).toProto())
              .setValue(new ValueExchange(entry.getValue()).toProto())
              .build()
          )
          .collect(Collectors.toList())
      )
      .build();
  }

  /**
   * Converts this exchange instance to bytes.
   *
   * @return Bytes.
   */
  public byte[] toBytes() throws UnsupportedOperationException {
    return toProto().toByteArray();
  }

  /**
   * Returns a proto from this exchange.
   *
   * @return Proto.
   * @throws UnsupportedOperationException If the value type is unknown.
   */
  public ContextVariableProtos.Value toProto() throws UnsupportedOperationException {
    final var builder = ContextVariableProtos.Value.newBuilder();

    switch (value) {
      case Integer i -> builder.setInteger(i);
      case Float f -> builder.setFloat(f);
      case Long l -> builder.setLong(l);
      case Double d -> builder.setDouble(d);
      case String s -> builder.setString(s);
      case Boolean b -> builder.setBool(b);
      case byte[] bytes -> builder.setBytes(ByteString.copyFrom(bytes));
      case Object[] array -> builder.setArray(toCollectionProto(Arrays.stream(array)));
      case List<?> list -> builder.setList(toCollectionProto(list.stream()));
      case Map<?, ?> map -> builder.setMap(toMapProto(map));
      default -> throw new UnsupportedOperationException(
        "Value type could not be converted to proto"
      );
    }

    return builder.build();
  }

  /**
   * Returns the value object.
   *
   * @return Value object.
   */
  public Object getValue() {
    return value;
  }
}
