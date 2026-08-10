package com.elatusdev.pokedex.pokedex.infrastructure.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// The surrogate id is introduced here and nowhere else. WF-000 §3.1: no invariant references
// a child's key, and re-sync replaces every replicated child wholesale, so a domain-side key
// would have to be invented or null on every pass.
@Entity
@Table(name = "pokemon_ability")
public class PokemonAbilityDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "slot", nullable = false)
    private int slot;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    protected PokemonAbilityDataModel() {
    }

    public PokemonAbilityDataModel(String name, int slot, boolean hidden) {
        this.name = name;
        this.slot = slot;
        this.hidden = hidden;
    }

    public String getName() {
        return name;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isHidden() {
        return hidden;
    }
}
