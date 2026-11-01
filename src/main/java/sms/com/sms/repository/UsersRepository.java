package sms.com.sms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import sms.com.sms.model.Users;

public interface UsersRepository extends JpaRepository<Users, String> {

  boolean existsByPhonenumber(String phonenumber);

  Optional<Users> findByPhonenumber(String phonenumber);

  Optional<Users> findByEmail(String email);

  Optional<Users> findByEmailVerificationCode(String code);

  Optional<Users> findByResetToken(String token);

  @Modifying
  @Transactional
  @Query("update Users u set u.AITokenBalance = u.AITokenBalance - :amount " +
      "where u.phonenumber = :phoneNumber and u.AITokenBalance >= :amount")
  int deductAiTokens(@Param("phoneNumber") String phoneNumber, @Param("amount") java.math.BigDecimal amount);
}