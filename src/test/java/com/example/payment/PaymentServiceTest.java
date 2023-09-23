package com.example.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
@ActiveProfiles({"test"})
@SpringBootTest
class PaymentServiceTest extends PaymentTest {

    public PaymentServiceTest() {
        super.enableAutoCreate = true;
    }

    @Autowired
    PaymentService paymentService;
    @Test
    void getPaymentByIdAfterCreate() {

        PaymentDto dto = PaymentDto
                .builder()
                .amount(new BigDecimal("4.5"))
                .payerName("John Green")
                .build();
        UUID resourceId = paymentService.createPayment(dto);

        assertThat(paymentService.getPaymentById(resourceId)).isNotNull();

    }
    @Test
    void getPaymentByPaymentNumber() {
        PaymentDto dto = paymentService
                .getPaymentByPaymentNumber(184482812);
        assertThat(dto.payerName()).isEqualTo("John Green");
    }

    @Test
    void getAllPaymentByPayer() {
        Set<PaymentDto> paymentDtoSet = paymentService
                .getAllPaymentByPayer(PageRequest.of(0, 5), "John Green");
        assertThat(paymentDtoSet.size()).isEqualTo(2);
    }

}