package com.markswell.domain.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class UserProfile {

    private String userId;

    private Set<String> portes = new HashSet<>();
    private Set<String> ambientes = new HashSet<>();
    private Set<String> temperamentos = new HashSet<>();

    public UserProfile() {}

    public UserProfile(String userId, Set<String> portes, Set<String> ambientes, Set<String> temperamentos) {
        this.userId = userId;
        this.portes = portes;
        this.ambientes = ambientes;
        this.temperamentos = temperamentos;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Set<String> getPortes() {
        return portes;
    }

    public void setPortes(Set<String> portes) {
        this.portes = portes;
    }

    public Set<String> getAmbientes() {
        return ambientes;
    }

    public void setAmbientes(Set<String> ambientes) {
        this.ambientes = ambientes;
    }

    public Set<String> getTemperamentos() {
        return temperamentos;
    }

    public void setTemperamentos(Set<String> temperamentos) {
        this.temperamentos = temperamentos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserProfile that = (UserProfile) o;
        return Objects.equals(userId, that.userId) && Objects.equals(portes, that.portes) && Objects.equals(ambientes, that.ambientes) && Objects.equals(temperamentos, that.temperamentos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, portes, ambientes, temperamentos);
    }
}
