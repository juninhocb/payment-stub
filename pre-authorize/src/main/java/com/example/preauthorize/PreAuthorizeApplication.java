package com.example.preauthorize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@SpringBootApplication
public class PreAuthorizeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PreAuthorizeApplication.class, args);
    }

}

@Builder
record PaymentDto(
        @JsonProperty("id")
        UUID id,
        @JsonProperty(value = "payment_number")
        Integer paymentNumber,
        @JsonProperty("amount")
        BigDecimal amount,
        @JsonProperty("timestamp")
        Instant timestamp,
        @JsonProperty("payer_name")
        String payerName,
        String state

) implements Serializable {
    static long serialVersionUid = 1L;
}

@Builder
record PreAuthorizeMessageRequest(
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("payment") PaymentDto paymentDto,
        Instant timestamp
) { }

@Builder
record PreAuthorizeResponse(
        @JsonProperty("response_id") UUID responseId,
        @JsonProperty("payment") PaymentDto paymentDto,
        @JsonProperty("is_approved") Boolean paymentApprove,
        Instant timestamp
) { }

@Configuration
@RequiredArgsConstructor
class RabbitMessageConfig {

    @Value("${app.props.queue-name}")
    private String queue_name;

    @Value("${app.props.exchange-name}")
    private String exchange_name;

    @Value("${app.props.queue-resp-name}")
    private String queue_resp_name;

    private final JsonConverterForMessageQueue messageConverter;

    //sender
    @Bean
    public Queue queue(){
        return new Queue(queue_resp_name);
    }

    @Bean
    public TopicExchange topicExchange(){
        return new TopicExchange(exchange_name);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange topicExchange){
        return BindingBuilder
                .bind(queue)
                .to(topicExchange)
                .with("payment.stub.pre.auth.#");
    }


    //receiver
    @Bean
    public MessageListenerAdapter listenerAdapter(IncomeMessageHandler incomeMessageHandler){
        MessageListenerAdapter mla = new MessageListenerAdapter();
        mla.setDefaultListenerMethod("processPreAuthorize");
        mla.setMessageConverter(messageConverter);
        mla.setDelegate(incomeMessageHandler);
        return mla;
    }

    @Bean
    public SimpleMessageListenerContainer container(
            ConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter){

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(this.queue_name);
        container.setMessageListener(messageListenerAdapter);
        return container;
    }

}


@Component
@RequiredArgsConstructor
@Slf4j
class IncomeMessageHandler{

    @Value("${app.props.exchange-name}")
    private String exchange_name;

    private final RabbitTemplate rabbitTemplate;
    public void processPreAuthorize(PreAuthorizeMessageRequest request){
        log.info(
                "Start process request message from payer "
                + request.paymentDto().payerName()
                + " Request id is "
                + request.requestId()
                + " At "
                + request.timestamp()
                + " With payment number equals "
                + request.paymentDto().paymentNumber()
        );

        PreAuthorizeResponse response = PreAuthorizeResponse
                .builder()
                .responseId(request.requestId())
                .timestamp(Instant.now())
                .paymentDto(request.paymentDto())
                .paymentApprove(handlePreAuthorizeApprove())
                .build();

        try {
            rabbitTemplate.convertAndSend(this.exchange_name, "payment.stub.pre.auth.pre", response);
        } catch (Exception ex){
            log.error("A problem was raised on trying to sending message to RabbitMQ" + ex.getMessage());
        }


    }

    private Boolean handlePreAuthorizeApprove(){
        int randomInt = new Random().nextInt(0,5);
        return randomInt > 3;
    }
}

@Component
@RequiredArgsConstructor
class JsonConverterForMessageQueue implements MessageConverter {

    private final ObjectMapper objectMapper;
    @Override
    public Message toMessage(Object o, MessageProperties messageProperties) throws MessageConversionException {
        try {
            String json = objectMapper.writeValueAsString(o);
            messageProperties.setContentType("application/json");
            return new Message(json.getBytes(), messageProperties);
        } catch (JsonProcessingException e) {
            throw new MessageConversionException("Error converting object to JSON", e);
        }

    }
    @Override
    public PreAuthorizeMessageRequest fromMessage(Message message) throws MessageConversionException {
        try {
            return objectMapper.readValue(message.getBody(), PreAuthorizeMessageRequest.class);
        } catch (IOException e) {
            throw new MessageConversionException("Error converting JSON to object", e);
        }
    }
}




