package com.markswell.interfaces.controller;

import com.markswell.interfaces.service.DogAssistant;
import com.markswell.interfaces.service.UserProfileService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/assistant")
public class DogResource {

    @Inject
    private DogAssistant dogAssistant;

    @Inject
    private UserProfileService profileService;

    @Inject
    SecurityIdentity identity;

    @POST
    @RolesAllowed("user")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String message) {
        System.out.println(message);
        String user = identity.getPrincipal().getName();
        profileService.updateProfile(user, message);
        return dogAssistant.chat(user, message);
    }

}
