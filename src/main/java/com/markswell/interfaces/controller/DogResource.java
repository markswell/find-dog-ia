package com.markswell.interfaces.controller;

import com.markswell.interfaces.service.DogAssistant;
import com.markswell.interfaces.service.PreferenceExtractor;
import com.markswell.interfaces.service.UserProfileService;
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
    private PreferenceExtractor extractor;

    @Inject
    private UserProfileService profileService;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String message) {
        System.out.println(message);
        String extracted = extractor.extract(message);

        profileService.updateProfile("vsde", extracted);
        return dogAssistant.chat("vsde", message);
    }

}
