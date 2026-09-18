package sms.com.sms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sms.com.sms.model.Users;

public interface UsersRepository extends JpaRepository<Users, String> {

  boolean existsByPhonenumber(String phonenumber);

  Optional<Users> findByPhonenumber(String phonenumber);

  Optional<Users> findByEmail(String email);

  Optional<Users> findByEmailVerificationCode(String code);

  Optional<Users> findByResetToken(String token);
}