package com.example.docs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RolesTest {
  @Test
  void onlyOwnerAndEditorCanEdit() {
    assertThat(Roles.canEdit("owner")).isTrue();
    assertThat(Roles.canEdit("editor")).isTrue();
    assertThat(Roles.canEdit("viewer")).isFalse();
  }

  @Test
  void onlyOwnerCanShare() {
    assertThat(Roles.canShare("owner")).isTrue();
    assertThat(Roles.canShare("editor")).isFalse();
    assertThat(Roles.canShare("viewer")).isFalse();
  }
}
