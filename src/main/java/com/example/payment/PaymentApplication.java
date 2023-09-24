package com.example.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_state")
    private States paymentState;
}

interface PaymentRepository extends JpaRepository<Payment, UUID>{
    Optional<Payment> findByPaymentNumber(Integer paymentNumber);
    List<Payment> findAllByPayerName(String name, Pageable pageable);
}

@Builder
record PaymentDto(
        @Null(message = "Id must be null")
        @JsonProperty("id")
        UUID id,
        @Null(message = "Payment number must be null")
        @JsonProperty(value = "payment_number")
        Integer paymentNumber,
        @Positive(message = "Amount must be more than zero")
        @NotNull(message = "Amount must no be null")
        @JsonProperty("amount")
        BigDecimal amount,
        @Null(message = "Timestamp must be null")
        @JsonProperty("timestamp")
        Instant timestamp,
        @NotBlank(message = "Payer name must be not null and not blank")
        @Pattern(regexp = "^[a-zA-ZÀ-ÖØ-öø-ÿ\s]+$", message = "Payer name must contian only letters")
        @JsonProperty("payer_name")
        String payerName,
        @Null States state

) implements Serializable {
    static long serialVersionUid = 1L;
}

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
interface PaymentMapper {
    Payment dtoToEntity(PaymentDto paymentDto);
    PaymentDto entityToDto(Payment payment);
}

interface PaymentService {
    PaymentDto getPaymentById(UUID id);
    PaymentDto getPaymentByPaymentNumber(Integer paymentNumber);
    Set<PaymentDto> getAllPayments(Pageable pageable);
    Set<PaymentDto> getAllPaymentByPayer(Pageable pageable, String payerName);
    UUID createPayment(PaymentDto paymentDto);
    void initPaymentProcessor(Payment payment);
}

@Service
@RequiredArgsConstructor
class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final StateMachineFactory<States, Events> stateMachineFactory;

    @Override
    public PaymentDto getPaymentById(UUID id) {
        return paymentMapper.entityToDto(handleGetById(id));
    }

    @Override
    public PaymentDto getPaymentByPaymentNumber(Integer paymentNumber) {
        return paymentMapper.entityToDto(handleGetById(paymentNumber));
    }

    @Override
    public Set<PaymentDto> getAllPayments(Pageable pageable) {
        return paymentRepository
                .findAll(pageable)
                .stream()
                .map(paymentMapper::entityToDto)
                .collect(Collectors.toSet());
    }



    @Override
    @Cacheable(key = "#payerName + '_' + #pageable", cacheNames = "payments")
    public Set<PaymentDto> getAllPaymentByPayer(Pageable pageable, String payerName) {

        return paymentRepository
                .findAllByPayerName(payerName, pageable)
                .stream()
                .map(paymentMapper::entityToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public UUID createPayment(PaymentDto paymentDto) {
        Integer paymentNumber = AppUtils.generatePaymentNumber();
        Payment paymentToPersist = paymentMapper.dtoToEntity(paymentDto);
        paymentToPersist.setPaymentNumber(paymentNumber);
        paymentToPersist.setPaymentState(States.NEW);
        Payment persisted = paymentRepository.save(paymentToPersist);
        initPaymentProcessor(persisted);
        return persisted.getId();
    }

    @Override
    public void initPaymentProcessor(Payment payment) {
        StateMachine<States, Events> sm = getFirstStateMachine(payment);
        sm
            .startReactively()
            .publishOn(Schedulers.boundedElastic())
            .doFirst(() -> {
                sm.sendEvent(getMonoMessage(Events.PRE_AUTHORIZE, payment.getPaymentNumber())).subscribe();
            }).subscribe();
    }


    private Mono<Message<Events>> getMonoMessage(Events event, Integer paymentNumber){
        Message<Events> msg = MessageBuilder
                .withPayload(event)
                .setHeader(StateMachineConfig.PAYMENT_HEADER, paymentNumber)
                .build();
        return Mono.just(msg);

    }

    private StateMachine<States, Events> getFirstStateMachine(Payment payment){

        StateMachine<States, Events> sm = stateMachineFactory
                .getStateMachine(payment.getId());

        DefaultStateMachineContext<States, Events> dsmc =
                new DefaultStateMachineContext<>(States.NEW, null, null, null);

        sm.getStateMachineAccessor().doWithAllRegions(sma -> {
            sma.resetStateMachineReactively(dsmc).subscribe();
        });

        return sm;
    }

    private StateMachine<States, Events> getStateMachine(PaymentDto paymentDto){

        StateMachine<States, Events> sm = stateMachineFactory
                .getStateMachine(paymentDto.id());

        sm.stopReactively().subscribe();

        DefaultStateMachineContext<States, Events> dsmc =
                new DefaultStateMachineContext<>(paymentDto.state(), null, null, null);

        sm.getStateMachineAccessor().doWithAllRegions(sma -> {
            sma.resetStateMachineReactively(dsmc).subscribe();
        });

        return sm;
    }

    private Payment handleGetById(Object key){
        Optional<Payment> paymentOptional = Optional.empty();
        if (key instanceof UUID){
            paymentOptional = paymentRepository.findById((UUID) key);
        }
        if (key instanceof Integer){
            paymentOptional = paymentRepository.findByPaymentNumber((Integer) key);
        }
        if (paymentOptional.isEmpty()){
            throw new ResourceNotFoundException(key.toString());
        }
        return paymentOptional.get();
    }
}

class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String id) {
        super(String.format("Resource with id %s was not found", id));
    }
}

class AppUtils {
    public static Integer generatePaymentNumber(){
        LocalDateTime now = LocalDateTime.now();
        String formattedDate = now.format(DateTimeFormatter.ofPattern("MMddHHmm"));
        int randomNumber = new Random().nextInt(0, 9);
        String generatedStr = formattedDate + randomNumber;
        return Integer.parseInt(generatedStr);
    }

}

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
class PaymentController {
    private final PaymentService paymentService;
    private final RedisTemplate<String, PaymentDto> redisTemplate;
    @GetMapping("/find/id/{id}")
    public ResponseEntity<PaymentDto> getById(@PathVariable UUID id){
        PaymentDto cached = getFromRedis(id.toString());
        if (cached != null){
            return new ResponseEntity<>(cached, HttpStatus.OK);
        }
        PaymentDto fromDb = paymentService
                .getPaymentById(id);
        savePaymentOnRedis(id.toString(), fromDb);
        return new ResponseEntity<>(fromDb, HttpStatus.OK);
    }
    @GetMapping("/find")
    public ResponseEntity<PaymentDto> getByPaymentNumber(
            @RequestParam("number") Integer paymentNumber){
        PaymentDto cached = getFromRedis(paymentNumber.toString());
        if (cached != null){
            return new ResponseEntity<>(cached, HttpStatus.OK);
        }
        PaymentDto fromDb = paymentService
                .getPaymentByPaymentNumber(paymentNumber);
        savePaymentOnRedis(paymentNumber.toString(), fromDb);
        return new ResponseEntity<>(fromDb, HttpStatus.OK);
    }
    @GetMapping
    public ResponseEntity<Set<PaymentDto>> getAllPayments(Pageable pageable){
        return new ResponseEntity<>(
                paymentService
                        .getAllPayments(
                                PageRequest.of(pageable.getPageNumber(),
                                pageable.getPageSize())), HttpStatus.OK);
    }
    @GetMapping("/find/name/{payer_name}")
    public ResponseEntity<Set<PaymentDto>> getAllByPayerName(
            @PathVariable("payer_name") String payerName,
            Pageable pageable){
        return new ResponseEntity<>(
                paymentService
                        .getAllPaymentByPayer(
                                PageRequest.of(pageable.getPageNumber(),
                                pageable.getPageSize()), payerName),
                                    HttpStatus.OK);
    }
    @PostMapping
    public ResponseEntity<Void> createPayment(
            @RequestBody @Valid PaymentDto paymentDto,
            UriComponentsBuilder ucb){

        UUID persistedId = paymentService.createPayment(paymentDto);

        URI resourcePath = ucb
                .path("/api/v1/payment/find/id/{id}")
                .buildAndExpand(persistedId)
                .toUri();

        return ResponseEntity.created(resourcePath).build();
    }

    private void savePaymentOnRedis(String key, PaymentDto dto){
        redisTemplate.opsForValue().set(key, dto, Duration.ofMinutes(3));
    }
    private PaymentDto getFromRedis(String key){
        return redisTemplate.opsForValue().get(key);
    }
}

@ControllerAdvice
@Slf4j
class GlobalExceptionHandler{
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorStdMessage> handleGenericException(Exception ex, HttpServletRequest hsr){
        log.error("Error message " + ex.getMessage() + " Class " + ex.getClass());
        return ResponseEntity.internalServerError().body(getMessageErr(ex, hsr, HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Void> handleResourceNotFound(ResourceNotFoundException ex){
        log.warn("Not successfully query from client " + ex.getMessage()); //intern
        return ResponseEntity.notFound().build();
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorStdMessage> handleResourceNotFound(MethodArgumentNotValidException ex, HttpServletRequest hsr){
        return ResponseEntity.unprocessableEntity().body(getMessageErr(ex, hsr, HttpStatus.UNPROCESSABLE_ENTITY.value()));
    }

    private ErrorStdMessage getMessageErr(Exception ex, HttpServletRequest hsr, Integer code){

        return ErrorStdMessage
                .builder()
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .statusCode(code)
                .path(hsr.getRequestURI())
                .build();
    }

}

@Builder
record ErrorStdMessage(
        String message,
        String path,
        @JsonProperty("code")
        Integer statusCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime timestamp
){ }

@Configuration
@EnableCaching
@RequiredArgsConstructor
class RedisConfig {

    private final ObjectMapper objectMapper;
    private final RedisConnectionFactory connectionFactory;
    @Bean
    public RedisCacheManager cacheManager() {
        RedisCacheConfiguration config = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(3))
                .disableCachingNullValues();

        return RedisCacheManager
                .builder(connectionFactory)
                .withCacheConfiguration("payments", config)
                .build();
    }

    @Bean
    public RedisTemplate<String, PaymentDto> redisTemplate() {

        RedisTemplate<String, PaymentDto> template = new RedisTemplate<String, PaymentDto>();

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonRedisSerializer());

        return template;
    }

    private Jackson2JsonRedisSerializer jsonRedisSerializer(){
        return new Jackson2JsonRedisSerializer(objectMapper, PaymentDto.class);
    }

}

enum States {
    NEW, PRE_AUTH, PRE_AUTH_ERROR, AUTH, AUTH_ERROR, AUTH_AUTHORIZED
}

enum Events{
    PRE_AUTHORIZE, PRE_AUTH_APPROVED, PRE_AUTH_DECLINED, AUTH_APPROVED, AUTH_DECLINED
}


@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
class StateMachineConfig extends EnumStateMachineConfigurerAdapter<States, Events> {
    public static final String PAYMENT_HEADER = "paymentNumber";
    private final PreAuthAction preAuthAction;
    private final PaymentGuard paymentGuard;
    @Override
    public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
        config
                .withConfiguration()
                .autoStartup(false)
                .listener(listener());
    }

    @Override
    public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
        states
                .withStates()
                .initial(States.NEW)
                .states(EnumSet.allOf(States.class))
                .end(States.PRE_AUTH_ERROR)
                .end(States.AUTH_ERROR)
                .end(States.AUTH_AUTHORIZED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {
        transitions
                .withExternal()
                .source(States.NEW)
                .target(States.PRE_AUTH)
                .event(Events.PRE_AUTHORIZE)
                .action(preAuthAction)
                .guard(paymentGuard)
                    .and()
                    .withExternal()
                    .source(States.PRE_AUTH)
                    .target(States.AUTH)
                    .event(Events.PRE_AUTH_APPROVED)
                    .and()
                    .withExternal()
                    .source(States.PRE_AUTH)
                    .target(States.PRE_AUTH_ERROR)
                    .event(Events.PRE_AUTH_DECLINED)
                        .and()
                        .withExternal()
                        .source(States.AUTH)
                        .target(States.AUTH_AUTHORIZED)
                        .event(Events.AUTH_APPROVED)
                        .and()
                        .withExternal()
                        .source(States.AUTH)
                        .target(States.AUTH_ERROR)
                        .event(Events.AUTH_DECLINED);
    }

    @Bean
    public StateMachineListener<States, Events> listener() {
        return new StateMachineListenerAdapter<States, Events>() {
            @Override
            public void stateChanged(State<States, Events> from, State<States, Events> to) {
                System.out.println("State change from " + from.getId() + " to " + to.getId());
            }
        };
    }
}
@Component
@RequiredArgsConstructor
class PreAuthAction implements Action<States, Events> {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final PaymentMapper paymentMapper;

    @Override
    public void execute(StateContext<States, Events> stateContext) {

        Integer paymentNumber = (Integer) stateContext.getMessage().getHeaders().get(StateMachineConfig.PAYMENT_HEADER);

        Optional<Payment> paymentOpt = paymentRepository.findByPaymentNumber(paymentNumber);

        if (paymentOpt.isEmpty()){
            throw new ResourceNotFoundException(paymentNumber.toString());
        }

        Payment updatePersist =  paymentOpt.get();

        updatePersist.setPaymentState(stateContext.getTarget().getId());

        try {

            PreAuthorizeMessageRequest request = PreAuthorizeMessageRequest
                    .builder()
                            .requestId(UUID.randomUUID())
                            .paymentDto(paymentMapper.entityToDto(updatePersist))
                            .timestamp(Instant.now()).build();

            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_PRE_AUTH_TOPIC, "payment.stub.pre.auth.key", request);

            paymentRepository.save(updatePersist);

        } catch (Exception ex){
            throw new RuntimeException("Message not processed "  + updatePersist.getPaymentNumber() + " Err " + ex.getMessage());

        }

    }
}

@Component
class PaymentGuard implements Guard<States, Events> {
    @Override
    public boolean evaluate(StateContext<States, Events> stateContext) {
        return stateContext.getMessageHeader(StateMachineConfig.PAYMENT_HEADER) != null;
    }
}

@Configuration
class RabbitConfig{
    public static final String PAYMENT_PRE_AUTHORIZE = "pre_authorize";
    public static final String PAYMENT_AUTHORIZE = "authorize";
    public static final String EXCHANGE_PRE_AUTH_TOPIC = "pre_auth_exchange";
    public static final String EXCHANGE_AUTH_TOPIC = "auth_exchange";

    //sender
    @Bean
    public Queue queuePreAuth(){
        return new Queue(PAYMENT_PRE_AUTHORIZE, false);
    }
    @Bean
    public TopicExchange exchangePreAuth(){
        return new TopicExchange(EXCHANGE_PRE_AUTH_TOPIC);
    }
    @Bean
    public Binding bindingPreAuth(){
        return BindingBuilder
                .bind(queuePreAuth())
                .to(exchangePreAuth())
                .with("payment.stub.pre.auth.#");
    }
    @Bean
    public Queue queueAuth(){
        return new Queue(PAYMENT_AUTHORIZE, false);
    }
    @Bean
    public TopicExchange exchangeAuth(){
        return new TopicExchange(EXCHANGE_AUTH_TOPIC);
    }
    @Bean
    public Binding bindingAuth(){
        return BindingBuilder
                .bind(queueAuth())
                .to(exchangeAuth())
                .with("payment.stub.auth.#");
    }

}

@Configuration
@RequiredArgsConstructor
class JsonConverterForMessageQueue implements MessageConverter {

    private final ObjectMapper objectMapper;
    @Override
    public org.springframework.amqp.core.Message toMessage(Object o, MessageProperties messageProperties) throws MessageConversionException {
        try {
            String json = objectMapper.writeValueAsString(o);
            messageProperties.setContentType("application/json");
            return new org.springframework.amqp.core.Message(json.getBytes(), messageProperties);
        } catch (JsonProcessingException e) {
            throw new MessageConversionException("Error converting object to JSON", e);
        }

    }
    @Override
    public Object fromMessage(org.springframework.amqp.core.Message message) throws MessageConversionException {
        return null;
    }
}

@Builder
record PreAuthorizeMessageRequest(
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("payment") PaymentDto paymentDto,
        Instant timestamp
) { }


