package at.ac.uibk.dps.cirrina.execution.object.exchange;

import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Context variable exchange, responsible for converting a context variable object to a consistent exchange format, using Protocol Buffers.
 * <p>
 * See the exchange protos for a protocol description.
 */
public class ContextVariableExchange {

  /**
   * The context variable object.
   */
  private final ContextVariable contextVariable;

  /**
   * Initializes this context variable exchange instance.
   *
   * @param contextVariable Context variable object.
   */
  public ContextVariableExchange(ContextVariable contextVariable) {
    this.contextVariable = contextVariable;
  }

  /**
   * Construct a context variable exchange from byte data.
   *
   * @param data Byte data.
   * @return Context variable exchange.
   * @throws UnsupportedOperationException If the context variable could not be read.
   */
  public static ContextVariableExchange fromBytes(byte[] data)
    throws UnsupportedOperationException {
    try {
      return new ContextVariableExchange(
        fromProto(ContextVariableProtos.ContextVariable.parseFrom(data))
      );
    } catch (InvalidProtocolBufferException e) {
      throw new UnsupportedOperationException("Could not read context variable from bytes");
    }
  }

  /**
   * Construct a context variable object from a proto.
   *
   * @param proto Context variable proto.
   * @return Context variable object.
   */
  public static ContextVariable fromProto(ContextVariableProtos.ContextVariable proto)
    throws UnsupportedOperationException {
    return new ContextVariable(proto.getName(), ValueExchange.fromProto(proto.getValue()));
  }

  /**
   * Converts this exchange instance to bytes.
   *
   * @return Bytes.
   */
  public byte[] toBytes() {
    return toProto().toByteArray();
  }

  /**
   * Returns a proto from this exchange.
   *
   * @return Proto.
   */
  public ContextVariableProtos.ContextVariable toProto() {
    return ContextVariableProtos.ContextVariable.newBuilder()
      .setName(contextVariable.name())
      .setValue(new ValueExchange(contextVariable.value()).toProto())
      .build();
  }

  /**
   * Returns the context variable object.
   *
   * @return Context variable object.
   */
  public ContextVariable getContextVariable() {
    return contextVariable;
  }
}
