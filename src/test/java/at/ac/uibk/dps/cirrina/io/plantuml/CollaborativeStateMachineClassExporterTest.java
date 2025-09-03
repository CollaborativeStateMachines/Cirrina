package at.ac.uibk.dps.cirrina.io.plantuml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription;
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.io.description.DescriptionParser;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CollaborativeStateMachineClassExporterTest {

  private static CollaborativeStateMachineClass completeNestedCsm;

  @BeforeAll
  static void setUp() {
    var json = DefaultDescriptions.completeNested;

    var parser = new DescriptionParser<CollaborativeStateMachineDescription>(
      CollaborativeStateMachineDescription.class
    );
    var csm = assertDoesNotThrow(() -> parser.parse(json));
    completeNestedCsm = assertDoesNotThrow(() ->
      CollaborativeStateMachineClassBuilder.from(csm).build()
    );
  }

  @Test
  void exportCollaborativeStateMachine() {
    var out = new StringWriter();

    assertDoesNotThrow(() -> {
      CollaborativeStateMachineExporter.export(out, completeNestedCsm);

      var file = new File("test_complete_nested_csm.puml");
      assertDoesNotThrow(() -> {
        var writer = new FileWriter(file);
        writer.write(out.toString());
        writer.close();
      });
    });
  }

  @Test
  void exportStateMachine() {
    var out = new StringWriter();

    assertDoesNotThrow(() -> {
      CollaborativeStateMachineExporter.export(
        out,
        completeNestedCsm.getStateMachineClasses().getFirst()
      );

      var file = new File("test_complete_nested_sm.puml");
      assertDoesNotThrow(() -> {
        var writer = new FileWriter(file);
        writer.write(out.toString());
        writer.close();
      });
    });
  }
}
