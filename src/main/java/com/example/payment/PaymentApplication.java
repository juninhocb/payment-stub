package com.example.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }

}
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
class Payment {
    @Id
    @GeneratedValue
    @GenericGenerator(name = "UUID")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;
    @Column(name = "payment_number", unique = true, nullable = false, length = 9)
    private Integer paymentNumber;
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;
    @Column(name = "payer", nullable = false, columnDefinition = "VARCHAR(100)")
    private String payerName;
}

interface PaymentRepository extends JpaRepository<Payment, UUID>{
    Optional<Payment> findByPaymentNumber(Integer paymentNumber);
}



