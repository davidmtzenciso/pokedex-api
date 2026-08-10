package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.Region;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UpdateTimestamp;

// Data only — the behaviour lives on the domain aggregate (persistence-patterns.md).
//
// The child associations are unidirectional with @JoinColumn rather than mappedBy: the
// domain children carry no parent reference and no surrogate key, so a bidirectional
// mapping would need the entity to wire both sides, which is behaviour this class is not
// allowed to have.
//
// created_at is deliberately unmapped. It is audit-only, the column defaults to now(), and
// mapping it would mean a detached merge writing the null the mapper could not supply.
@Entity
@Table(name = "pokemon")
public class PokemonDataModel {

    // one round trip per collection for a whole page, instead of one per row per collection
    private static final int COLLECTION_BATCH_SIZE = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poke_api_id")
    private Integer pokeApiId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "category", length = 60)
    private String category;

    @Column(name = "mass_hectograms", nullable = false)
    private int massHectograms;

    @Column(name = "height_decimetres", nullable = false)
    private int heightDecimetres;

    @Column(name = "base_experience", nullable = false)
    private int baseExperience;

    @Column(name = "sprite_front_default", length = 500)
    private String spriteFrontDefault;

    @Column(name = "sprite_official_artwork", length = 500)
    private String spriteOfficialArtwork;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", length = 20)
    private Region region;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "replication_state", nullable = false, length = 20)
    private ReplicationState replicationState;

    @Column(name = "curated_by")
    private Long curatedBy;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @OrderBy("slot")
    @BatchSize(size = COLLECTION_BATCH_SIZE)
    private List<PokemonAbilityDataModel> abilities = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @OrderBy("id")
    @BatchSize(size = COLLECTION_BATCH_SIZE)
    private List<PokemonStatDataModel> stats = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @OrderBy("slot")
    @BatchSize(size = COLLECTION_BATCH_SIZE)
    private List<PokemonTypeDataModel> types = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @OrderBy("id")
    @BatchSize(size = COLLECTION_BATCH_SIZE)
    private List<PokemonTagDataModel> tags = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @OrderBy("id")
    @BatchSize(size = COLLECTION_BATCH_SIZE)
    private List<LocalizedNameDataModel> localizedNames = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @OrderBy("id")
    @BatchSize(size = COLLECTION_BATCH_SIZE)
    private List<EvolutionLinkDataModel> evolutionLinks = new ArrayList<>();

    protected PokemonDataModel() {
    }

    @SuppressWarnings("java:S107") // a row is 16 columns wide; the alternative is a mutable builder
    public PokemonDataModel(
            Long id,
            Integer pokeApiId,
            String name,
            String category,
            int massHectograms,
            int heightDecimetres,
            int baseExperience,
            String spriteFrontDefault,
            String spriteOfficialArtwork,
            String description,
            Region region,
            String notes,
            ReplicationState replicationState,
            Long curatedBy,
            Instant syncedAt,
            long version) {
        this.id = id;
        this.pokeApiId = pokeApiId;
        this.name = name;
        this.category = category;
        this.massHectograms = massHectograms;
        this.heightDecimetres = heightDecimetres;
        this.baseExperience = baseExperience;
        this.spriteFrontDefault = spriteFrontDefault;
        this.spriteOfficialArtwork = spriteOfficialArtwork;
        this.description = description;
        this.region = region;
        this.notes = notes;
        this.replicationState = replicationState;
        this.curatedBy = curatedBy;
        this.syncedAt = syncedAt;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public Integer getPokeApiId() {
        return pokeApiId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getMassHectograms() {
        return massHectograms;
    }

    public int getHeightDecimetres() {
        return heightDecimetres;
    }

    public int getBaseExperience() {
        return baseExperience;
    }

    public String getSpriteFrontDefault() {
        return spriteFrontDefault;
    }

    public String getSpriteOfficialArtwork() {
        return spriteOfficialArtwork;
    }

    public String getDescription() {
        return description;
    }

    public Region getRegion() {
        return region;
    }

    public String getNotes() {
        return notes;
    }

    public ReplicationState getReplicationState() {
        return replicationState;
    }

    public Long getCuratedBy() {
        return curatedBy;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<PokemonAbilityDataModel> getAbilities() {
        return abilities;
    }

    public List<PokemonStatDataModel> getStats() {
        return stats;
    }

    public List<PokemonTypeDataModel> getTypes() {
        return types;
    }

    public List<PokemonTagDataModel> getTags() {
        return tags;
    }

    public List<LocalizedNameDataModel> getLocalizedNames() {
        return localizedNames;
    }

    public List<EvolutionLinkDataModel> getEvolutionLinks() {
        return evolutionLinks;
    }
}
