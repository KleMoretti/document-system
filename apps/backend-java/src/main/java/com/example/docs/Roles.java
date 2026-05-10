package com.example.docs;

public final class Roles {
  private Roles() {}

  public static boolean canEdit(String role) {
    return "owner".equals(role) || "editor".equals(role);
  }

  public static boolean canShare(String role) {
    return "owner".equals(role);
  }

  public static boolean valid(String role) {
    return "owner".equals(role) || "editor".equals(role) || "viewer".equals(role);
  }
}
