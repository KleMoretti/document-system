package com.example.docs;

import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnRoleCondition implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var role = ServiceRole.from(context.getEnvironment().getProperty("app.service-role", "all"));
    var attrs = metadata.getAnnotationAttributes(ConditionalOnRole.class.getName());
    if (attrs == null) {
      return true;
    }
    @SuppressWarnings("unchecked")
    var roles = (ServiceRole[]) attrs.get("value");
    if (roles == null || roles.length == 0) {
      return true;
    }
    for (var required : roles) {
      if (role == required) {
        return true;
      }
    }
    return false;
  }
}
