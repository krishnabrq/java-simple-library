package com.training.library.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  // @SQLRestriction on UserEntity adds "deleted_at IS NULL" — soft-deleted accounts are
  // invisible to login. New signups with a once-deleted email succeed (partial unique
  // index ignores tombstones), and the old credentials stay dormant.
  Optional<UserEntity> findByEmail(String email);
}
