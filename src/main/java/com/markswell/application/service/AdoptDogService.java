package com.markswell.application.service;

import com.markswell.domain.model.DogShelter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Random;

@ApplicationScoped
public class AdoptDogService {

    private final List<DogShelter> dogShelters = List.of(
            new DogShelter("Seu doguinho", "São Paulo"),
            new DogShelter("Novo Amigo", "Recife"),
            new DogShelter("Pegue o seu", "Salvador"),
            new DogShelter("Abrigo dos anjos", "Fortaleza")
    );

    public String adopt(String name) {
        Random random = new Random();
        int index = random.nextInt((dogShelters.size()));
        DogShelter dogShelter = dogShelters.get(index);
        return "Seu doguinho %s pode ser encontrado no abrigo %s localizado em %s".formatted(name, dogShelter.name(), dogShelter.address());
    }
}
