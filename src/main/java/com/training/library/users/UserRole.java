package com.training.library.users;

// Stored as the enum name (string) via @Enumerated(EnumType.STRING) — see UserEntity.role.
// Bound to Spring Security authorities later by prefixing "ROLE_" (e.g. "ROLE_MEMBER").
public enum UserRole {
  MEMBER,
  STAFF
}
