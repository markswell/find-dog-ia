package com.markswell.infrastructure.tool;

import com.markswell.application.service.AdoptDogService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdoptDog {

    @Inject
    AdoptDogService dogService;

    @Tool("""
            - informa onde pode adotar uma cachorro
            - recebe uma {name} que é a raça do cachorro
            - retorna uma String com o informações de onde adotar um cachorro""")
    public String adopt(String name) {
        return dogService.adopt(name);
    }

}
