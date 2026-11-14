package sms.com.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import sms.com.sms.enums.TokenPurchaseStatus;
import sms.com.sms.model.TokenPurchase;

import java.util.Optional;

public interface TokenPurchaseRepository extends JpaRepository<TokenPurchase, String> {

    Optional<TokenPurchase> findByTransactionReferenceAndPhoneNumber(String transactionReference,
                                                                      String phoneNumber);

    @Query("select p.status from TokenPurchase p where p.transactionReference = :reference")
    Optional<TokenPurchaseStatus> findStatusByTransactionReference(@Param("reference") String reference);

    @Modifying
    @Transactional
    @Query("update TokenPurchase p set p.status = :status " +
            "where p.transactionReference = :reference and p.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("reference") String reference,
                              @Param("expectedStatus") TokenPurchaseStatus expectedStatus,
                              @Param("status") TokenPurchaseStatus status);
}
