package com.elatusdev.pokedex.pokedex.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pokemon_tag")
public class PokemonTagDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false, length = 30)
    private String label;

    protected PokemonTagDataModel() {
    }

    public PokemonTagDataModel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
