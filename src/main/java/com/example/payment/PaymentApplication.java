package com.example.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
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
        @Null(message = "Id must be null") UUID id,
        @Null(message = "Payment number must be null")
        @JsonProperty(value = "payment_number")
        Integer paymentNumber,
        @Positive(message = "Amount must be more than zero")
        @NotNull(message = "Amount must no be null")
        BigDecimal amount,
        @Null(message = "Timestamp must be null") Instant timestamp,
        @NotBlank(message = "Payer name must be not null and not blank")
        @Pattern(regexp = "^[a-zA-ZÀ-ÖØ-öø-ÿ\s]+$", message = "Payer name must contian only letters")
        @JsonProperty("payer_name")
        String payerName

) { }

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
    @GetMapping("/find/id/{id}")
    public ResponseEntity<PaymentDto> getById(@PathVariable UUID id){
        return new ResponseEntity<>(paymentService
                .getPaymentById(id), HttpStatus.OK);
    }
    @GetMapping("/find")
    public ResponseEntity<PaymentDto> getByPaymentNumber(
            @RequestParam("number") Integer paymentNumber){
        return new ResponseEntity<>(paymentService
                .getPaymentByPaymentNumber(paymentNumber), HttpStatus.OK);
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
    public ResponseEntity<Void> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest hsr){
        log.warn("Not successfully query from client " + ex.getMessage()); //intern
        return ResponseEntity.notFound().build();
    }

    private ErrorStdMessage getMessageErr(Exception ex, HttpServletRequest hsr, Integer code){

        return ErrorStdMessage
                .builder()
                .message(ex.getMessage())
                .timestamp(Instant.now())
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
        Instant timestamp
){ }





