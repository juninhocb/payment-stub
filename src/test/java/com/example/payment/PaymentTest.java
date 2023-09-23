package com.example.payment;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
public abstract class PaymentTest {

    @Autowired
    PaymentRepository repository;
    boolean enableAutoCreate = false;

    final PaymentDto dto;
    final PaymentDto invalidDtoId;
    final PaymentDto invalidDtoNumber;
    final PaymentDto invalidDtoPayerName;
    final PaymentDto invalidDtoAmount;
    final PaymentDto invalidDtoTimeStamp;

    public PaymentTest(){

        dto = PaymentDto
                .builder()
                .amount(new BigDecimal("4.5"))
                .payerName("John Green")
                .build();
        invalidDtoId = PaymentDto
                .builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("4.5"))
                .payerName("John Green")
                .build();

        invalidDtoNumber = PaymentDto
                .builder()
                .paymentNumber(847238334)
                .amount(new BigDecimal("4.5"))
                .payerName("John Green")
                .build();

        invalidDtoPayerName = PaymentDto
                .builder()
                .amount(new BigDecimal("4.5"))
                .payerName("John Green123")
                .build();

        invalidDtoAmount = PaymentDto
                .builder()
                .amount(new BigDecimal("0.0"))
                .payerName("John Green")
                .build();

        invalidDtoTimeStamp = PaymentDto
                .builder()
                .timestamp(Instant.now())
                .amount(new BigDecimal("4.5"))
                .payerName("John Green")
                .build();
    }

    List<Payment> paymentList;
    @BeforeEach
    void setUp() {

        Payment payment1 = Payment
                .builder()
                .amount(new BigDecimal("2.6"))
                .paymentNumber(184482812)
                .payerName("John Green")
                .build();

        Payment payment2 = Payment
                .builder()
                .amount(new BigDecimal("5"))
                .paymentNumber(982834112)
                .payerName("Anne Purple")
                .build();

        Payment payment3 = Payment
                .builder()
                .amount(new BigDecimal("7.4"))
                .paymentNumber(184482843)
                .payerName("John Green")
                .build();

        paymentList = Arrays.asList(payment1, payment2, payment3);

        if (enableAutoCreate) {
            repository.deleteAll();
            repository.saveAll(paymentList);
            assertThat(repository.count()).isEqualTo(3);

        }
    }

}
