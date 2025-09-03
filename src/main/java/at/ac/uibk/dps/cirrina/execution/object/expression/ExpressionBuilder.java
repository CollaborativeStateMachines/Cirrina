package at.ac.uibk.dps.cirrina.execution.object.expression;

/**
 * Expression builder, builds an expression based on an expression source string. Built expressions are cached, repeatedly building the same
 * expression will return the same expression.
 */
public final class ExpressionBuilder {

  private String expressionDescription;

  private ExpressionBuilder(String expressionDescription) {
    this.expressionDescription = expressionDescription;
  }

  public static ExpressionBuilder from(String expressionDescription) {
    return new ExpressionBuilder(expressionDescription);
  }

  /**
   * Builds the collaborative state machine.
   *
   * @return Built expression.
   * @throws IllegalArgumentException In case the expression could not be built.
   */
  public Expression build() throws IllegalArgumentException {
    return new JexlExpression(expressionDescription);
  }
}
