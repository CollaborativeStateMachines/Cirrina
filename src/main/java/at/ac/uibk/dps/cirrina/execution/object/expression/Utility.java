package at.ac.uibk.dps.cirrina.execution.object.expression;

import java.util.Random;

public final class Utility {

  public static byte[] genRandPayload(int[] sizes) {
    final var rand = new Random();

    final var randomIndex = rand.nextInt(sizes.length);
    final var selectedSize = sizes[randomIndex];

    return new byte[selectedSize];
  }
}
