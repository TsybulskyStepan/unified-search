package com.example.searchapp.onboarding.client;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients")
public class ClientController {
  private final ClientRepository clients;

  public ClientController(ClientRepository clients) {
    this.clients = clients;
  }

  @PostMapping
  ResponseEntity<Client> create(@Valid @RequestBody CreateClientRequest request) {
    if (!request.validationErrors().isEmpty()) {
      throw new ClientValidationException(request.validationErrors());
    }
    Client client = clients.insert(request);
    URI location = URI.create("/clients/" + client.id());
    return ResponseEntity.created(location).body(client);
  }

  @GetMapping("/{id}")
  Client get(@PathVariable UUID id) {
    return clients.findById(id).orElseThrow(ClientNotFoundException::new);
  }

  @ExceptionHandler(ClientNotFoundException.class)
  ProblemDetail handleNotFound(ClientNotFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND, "Not found", "The requested client was not found", request);
  }

  @ExceptionHandler(DuplicateClientEmailException.class)
  ProblemDetail handleDuplicateEmail(
      DuplicateClientEmailException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT, "Conflict", "A client with this email already exists", request);
  }

  @ExceptionHandler(ClientValidationException.class)
  ProblemDetail handleValidation(ClientValidationException exception, HttpServletRequest request) {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid", request);
    problem.setProperty("errors", exception.errors());
    return problem;
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
