package com.example.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

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
        String payerName

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
}

@Service
@RequiredArgsConstructor
class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

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
        Payment paymentToPersist = paymentMapper.dtoToEntity(paymentDto);
        paymentToPersist.setPaymentNumber(AppUtils.generatePaymentNumber());
        return paymentRepository.save(paymentToPersist).getId();
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




