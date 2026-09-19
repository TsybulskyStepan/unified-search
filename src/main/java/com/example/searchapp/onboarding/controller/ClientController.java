package com.example.searchapp.onboarding.controller;

import com.example.searchapp.onboarding.dto.CreateClientRequest;
import com.example.searchapp.onboarding.entity.Client;
import com.example.searchapp.onboarding.exception.ClientNotFoundException;
import com.example.searchapp.onboarding.repository.ClientRepository;
import com.example.searchapp.shared.web.RequestValidationException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
}
