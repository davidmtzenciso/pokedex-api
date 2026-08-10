package com.elatusdev.pokedex.infrastructure.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A flattened edge of the upstream evolution tree (IA5). The column is evolution_trigger
// because TRIGGER is reserved in Postgres.
@Entity
@Table(name = "evolution_link")
public class EvolutionLinkDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_poke_api_id", nullable = false)
    private int fromPokeApiId;

    @Column(name = "to_poke_api_id", nullable = false)
    private int toPokeApiId;

    @Column(name = "evolution_trigger", nullable = false, length = 60)
    private String evolutionTrigger;

    @Column(name = "min_level")
    private Integer minLevel;

    protected EvolutionLinkDataModel() {
    }

    public EvolutionLinkDataModel(int fromPokeApiId, int toPokeApiId, String evolutionTrigger, Integer minLevel) {
        this.fromPokeApiId = fromPokeApiId;
        this.toPokeApiId = toPokeApiId;
        this.evolutionTrigger = evolutionTrigger;
        this.minLevel = minLevel;
    }

    public int getFromPokeApiId() {
        return fromPokeApiId;
    }

    public int getToPokeApiId() {
        return toPokeApiId;
    }

    public String getEvolutionTrigger() {
        return evolutionTrigger;
    }

    public Integer getMinLevel() {
        return minLevel;
    }
}
