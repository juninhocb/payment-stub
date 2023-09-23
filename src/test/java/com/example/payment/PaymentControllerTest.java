package com.example.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentControllerTest extends PaymentTest{

    @Autowired
    TestRestTemplate restTemplate;

    final String path = "/api/v1/payment";
    public PaymentControllerTest(){
        this.enableAutoCreate = true;
    }
    @Test
    void shouldCreateAndGetResourceById() {
        ResponseEntity<Void> getPost = restTemplate
                .postForEntity(path, dto, Void.class);

        assertThat(getPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        URI resourceLocate = getPost.getHeaders().getLocation();
        ResponseEntity<PaymentDto> getResponse = restTemplate
                .getForEntity(resourceLocate, PaymentDto.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().payerName()).isEqualTo("John Green");
    }

    @Test
    void shouldGetByPaymentNumber() {
        ResponseEntity<PaymentDto> getResponse = restTemplate
                .getForEntity(
                        String.format("%s/find?number=%d", path, paymentList.get(1).getPaymentNumber()),
                        PaymentDto.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().payerName()).isEqualTo("Anne Purple");

    }

    @Test
    void getAllByPayerName() {
        ResponseEntity<Set<PaymentDto>> getResponse = restTemplate
                .exchange(
                        String.format("%s/find/name/%s", path, paymentList.get(0).getPayerName()),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Set<PaymentDto>>() {});
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().size()).isEqualTo(2);
    }

}