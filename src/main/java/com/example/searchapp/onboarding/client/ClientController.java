package com.example.searchapp.onboarding.client;

import com.example.searchapp.shared.web.ProblemDetails;
import com.example.searchapp.shared.web.RequestValidationException;
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
      throw new RequestValidationException(request.validationErrors());
    }
    Client client = clients.insert(request);
    URI location = URI.create("/clients/" + client.id());
    return ResponseEntity.created(location).body(client);
  }

  @GetMapping("/{id}")
  Client get(@PathVariable UUID id) {
    return clients.findById(id).orElseThrow(ClientNotFoundException::new);
  }

  @ExceptionHandler(DuplicateClientEmailException.class)
  ProblemDetail handleDuplicateEmail(
      DuplicateClientEmailException exception, HttpServletRequest request) {
    return ProblemDetails.of(
        HttpStatus.CONFLICT,
        "Conflict",
        "A client with this email already exists",
        URI.create(request.getRequestURI()));
  }
}
