package com.markswell.interfaces.service;

import dev.langchain4j.service.SystemMessage;

public interface PreferenceExtractor{

    @SystemMessage("""
        Extraia preferências do usuário em JSON.

        Campos possíveis:
        - porte: pequeno, medio, grande
        - ambiente: apartamento, casa
        - temperamento: calmo, ativo

        Retorne JSON puro.

        Exemplo:
        {"porte":"pequeno","ambiente":"apartamento"}
    """)
    String extract(String message);
}
