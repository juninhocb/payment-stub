package com.example.payment;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DataJpaTest
class PaymentRepositoryTest extends PaymentTest{

    @Autowired
    PaymentRepository repository;

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

    @Test
    @DirtiesContext
    @Order(3)
    void shouldCreateAndRetrieveAllByPayer(){
        repository.saveAll(paymentList);
        List<Payment> johnsPayments = repository
                .findAllByPayerName("John Green", PageRequest.of(0, 10));
        johnsPayments.forEach(System.out::println);
        assertThat(johnsPayments.size()).isEqualTo(2);
    }
}