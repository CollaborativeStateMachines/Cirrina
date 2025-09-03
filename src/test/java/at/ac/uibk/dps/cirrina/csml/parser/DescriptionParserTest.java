package at.ac.uibk.dps.cirrina.csml.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription;
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.io.description.DescriptionParser;
import org.junit.jupiter.api.Test;

class DescriptionParserTest {

  @Test
  void testDescriptionPositive() {
    var pkl = DefaultDescriptions.complete;

    var parser = new DescriptionParser<>(CollaborativeStateMachineDescription.class);
    assertDoesNotThrow(() -> {
      var csm = parser.parse(pkl);
    });
  }

  @Test
  void testDescriptionNegative() {
    var json = DefaultDescriptions.empty;

    var parser = new DescriptionParser<>(CollaborativeStateMachineDescription.class);
    assertThrows(IllegalArgumentException.class, () -> {
      var csm = parser.parse(json);
      System.out.println(csm);
    });
  }
}
