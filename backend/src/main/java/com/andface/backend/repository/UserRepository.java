package com.andface.backend.repository;

import com.andface.backend.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByUsername(String username);

  Optional<UserAccount> findByUserCode(String userCode);

  boolean existsByUsernameOrUserCode(String username, String userCode);
}
