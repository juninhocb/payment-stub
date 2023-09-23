package com.example.payment;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
public abstract class PaymentTest {

    @Autowired
    PaymentRepository repository;
    boolean enableAutoCreate = false;

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
