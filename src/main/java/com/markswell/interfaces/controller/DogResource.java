package com.markswell.interfaces.controller;

import com.markswell.infraestructure.config.RedisConfig;
import com.markswell.infraestructure.persistence.CacheRepository;
import com.markswell.interfaces.service.DogAssistant;
import com.markswell.interfaces.service.UserProfileService;
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Path("/assistant")
public class DogResource {

    private final Logger LOG = LoggerFactory.getLogger(DogResource.class);

    @ConfigProperty(name = "token.limit")
    long tokenLimit;

    @Inject
    private DogAssistant dogAssistant;

    @Inject
    private UserProfileService profileService;

    @Inject
    private CacheRepository cacheRepository;

    @Inject
    private RedisConfig redisConfig;

    @Inject
    SecurityIdentity identity;

    @POST
    @RolesAllowed("user")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String message) {
        LOG.info("Mensagem recebida: ".concat(message));
        String user = identity.getPrincipal().getName();
        profileService.updateProfile(user, message);

        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            String subId = jwt.getSubject();

            long tokens = cacheRepository.get(redisConfig.totalTokens().formatted(subId));

            if (tokens > tokenLimit) {
                return "Usuário exedeu cota de tokens!";
            }


            ContextLocals.put("userId", subId);
        }
        return dogAssistant.chat(user, message);
    }

}
