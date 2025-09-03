package at.ac.uibk.dps.cirrina.utils;

public class BuildVersion {

  private BuildVersion() {}

  public static String getBuildVersion() {
    return BuildVersion.class.getPackage().getImplementationVersion();
  }
}
