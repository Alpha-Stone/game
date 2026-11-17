package com.drdo.game.cms;

public class CMSRepo {
}

public interface MyTableRepository extends JpaRepository<MyEntity, Long> {

    @Query(value =
            "SELECT n FROM generate_series(CAST(:from AS integer), CAST(:to AS integer)) AS n " +
                    "WHERE NOT EXISTS (SELECT 1 FROM my_table t WHERE t.col7 @> ARRAY[n]) " +
                    "ORDER BY n LIMIT :limit",
            nativeQuery = true)
    List<Integer> findAvailableNumbers(@Param("from") int from,
                                       @Param("to") int to,
                                       @Param("limit") int limit);
}


@Service
@RequiredArgsConstructor
public class AvailableNumberService {

    private static final int MAX_LIMIT = 1000;
    private final MyTableRepository repo;

    @Transactional(readOnly = true)
    public List<Integer> getAvailable(int from, int to, int limit) {
        if (from > to) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return repo.findAvailableNumbers(from, to, safeLimit);
    }
}


@GetMapping("/available-numbers")
public ApiResponse<List<Integer>> availableNumbers(
        @RequestParam(defaultValue = "1") int from,
        @RequestParam(defaultValue = "100000") int to,
        @RequestParam(defaultValue = "100") int limit) {
    return ApiResponse.success(availableNumberService.getAvailable(from, to, limit));
}



@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ElementAlreadyAssignedException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ElementAlreadyAssignedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("ELEMENT_ALREADY_ASSIGNED", ex.getMessage(), null));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> fieldErrors.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_FAILED", "Invalid request", fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // log ex here, but don't leak details to the client
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Something went wrong", null));
    }
}


@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class MyTableController {

    private final MyTableService service;

    @PutMapping("/{id}")
    public ApiResponse<MyDto> update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateRequest req) {
        return ApiResponse.success(service.update(id, req));
    }
}


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> {
    private T data;
    private String status;        // "SUCCESS" / "ERROR"
    private String errorCode;     // e.g. "ELEMENT_ALREADY_ASSIGNED"
    private String message;
    private Map<String, Object> errors;  // optional details

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().data(data).status("SUCCESS").build();
    }

    public static <T> ApiResponse<T> error(String code, String message, Map<String, Object> errors) {
        return ApiResponse.<T>builder().status("ERROR").errorCode(code).message(message).errors(errors).build();
    }
}
