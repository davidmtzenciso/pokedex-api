package com.elatusdev.pokedex.infrastructure.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pokemon_stat")
public class PokemonStatDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "base_value", nullable = false)
    private int baseValue;

    @Column(name = "effort", nullable = false)
    private int effort;

    protected PokemonStatDataModel() {
    }

    public PokemonStatDataModel(String name, int baseValue, int effort) {
        this.name = name;
        this.baseValue = baseValue;
        this.effort = effort;
    }

    public String getName() {
        return name;
    }

    public int getBaseValue() {
        return baseValue;
    }

    public int getEffort() {
        return effort;
    }
}
