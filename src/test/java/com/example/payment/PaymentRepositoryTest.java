package com.example.payment;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    PaymentRepository repository;

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
    }

    @Test
    @DirtiesContext
    @Order(1)
    void shouldCreatePayments() {
        repository.saveAll(paymentList);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DirtiesContext
    @Order(2)
    void shouldRetrieveAndEnsureNonNullGeneratedFields(){
        repository.save(paymentList.get(2));
        Payment payment = repository.findByPaymentNumber(184482843).get();
        assertThat(payment.getTimestamp()).isNotNull();
        assertThat(payment.getId()).isNotNull();
    }
}