package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.shared.domain.EvolutionLink;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.shared.domain.PokemonAbility;
import com.elatusdev.pokedex.shared.domain.PokemonStat;
import com.elatusdev.pokedex.shared.domain.PokemonType;
import com.elatusdev.pokedex.pokedex.domain.ProprietaryFields;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.shared.domain.Sprite;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.identity.domain.UserId;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// The boundary between the two shapes. Both directions split on the same partition the
// domain does — Replicated and Proprietary — because localized_name rows land on opposite
// sides of it depending on one discriminator column, and that is the whole of F7.
@Component
public class PokemonPersistenceMapper {

    public Pokemon toDomain(PokemonDataModel model) {
        return Pokemon.rehydrate(
                PokemonId.of(model.getId()),
                Optional.ofNullable(model.getPokeApiId()).map(PokeApiId::of),
                replicatedOf(model),
                proprietaryOf(model),
                model.getReplicationState(),
                Optional.ofNullable(model.getSyncedAt()),
                model.getVersion());
    }

    public PokemonDataModel toDataModel(Pokemon pokemon) {
        PokemonDataModel model = rowOf(pokemon);
        copyReplicatedChildren(model, pokemon.replicated());
        copyProprietaryChildren(model, pokemon.proprietary());
        return model;
    }

    private PokemonDataModel rowOf(Pokemon pokemon) {
        ReplicatedFields replicated = pokemon.replicated();
        ProprietaryFields proprietary = pokemon.proprietary();
        return new PokemonDataModel(
                pokemon.id().map(PokemonId::value).orElse(null),
                pokemon.pokeApiId().map(PokeApiId::value).orElse(null),
                replicated.name().value(),
                replicated.category().map(Category::value).orElse(null),
                replicated.mass().hectograms(),
                replicated.height().decimetres(),
                replicated.baseExperience(),
                textOf(replicated.sprite().frontDefault()),
                textOf(replicated.sprite().officialArtwork()),
                replicated.description().map(Description::value).orElse(null),
                proprietary.region().orElse(null),
                proprietary.notes().map(Notes::value).orElse(null),
                pokemon.replicationState(),
                proprietary.curatedBy().map(UserId::value).orElse(null),
                pokemon.syncedAt().orElse(null),
                pokemon.version());
    }

    private void copyReplicatedChildren(PokemonDataModel model, ReplicatedFields replicated) {
        replicated.abilities().stream()
                .map(ability -> new PokemonAbilityDataModel(ability.name(), ability.slot(), ability.hidden()))
                .forEach(model.getAbilities()::add);
        replicated.stats().stream()
                .map(stat -> new PokemonStatDataModel(stat.name(), stat.baseValue(), stat.effort()))
                .forEach(model.getStats()::add);
        replicated.types().stream()
                .map(type -> new PokemonTypeDataModel(type.name(), type.slot()))
                .forEach(model.getTypes()::add);
        replicated.evolutionLinks().stream().map(this::evolutionLinkRowOf).forEach(model.getEvolutionLinks()::add);
        replicated.upstreamNames().stream().map(this::localizedNameRowOf).forEach(model.getLocalizedNames()::add);
    }

    private void copyProprietaryChildren(PokemonDataModel model, ProprietaryFields proprietary) {
        proprietary.tags().stream()
                .map(tag -> new PokemonTagDataModel(tag.label()))
                .forEach(model.getTags()::add);
        proprietary.curatorNames().stream().map(this::localizedNameRowOf).forEach(model.getLocalizedNames()::add);
    }

    private EvolutionLinkDataModel evolutionLinkRowOf(EvolutionLink link) {
        return new EvolutionLinkDataModel(
                link.from().value(), link.to().value(), link.trigger(), link.minLevel().orElse(null));
    }

    private LocalizedNameDataModel localizedNameRowOf(LocalizedName name) {
        return new LocalizedNameDataModel(name.locale(), name.value(), name.source());
    }

    private ReplicatedFields replicatedOf(PokemonDataModel model) {
        return new ReplicatedFields(
                new PokemonName(model.getName()),
                Optional.ofNullable(model.getCategory()).map(Category::new),
                Mass.ofHectograms(model.getMassHectograms()),
                Height.ofDecimetres(model.getHeightDecimetres()),
                model.getBaseExperience(),
                spriteOf(model),
                Optional.ofNullable(model.getDescription()).map(Description::new),
                abilitiesOf(model),
                statsOf(model),
                typesOf(model),
                evolutionLinksOf(model),
                localizedNamesOf(model, NameSource.UPSTREAM));
    }

    private ProprietaryFields proprietaryOf(PokemonDataModel model) {
        return new ProprietaryFields(
                Optional.ofNullable(model.getRegion()),
                Optional.ofNullable(model.getNotes()).map(Notes::new),
                Optional.ofNullable(model.getCuratedBy()).map(UserId::of),
                model.getTags().stream().map(tag -> new Tag(tag.getLabel())).toList(),
                localizedNamesOf(model, NameSource.CURATOR));
    }

    private Sprite spriteOf(PokemonDataModel model) {
        return new Sprite(uriOf(model.getSpriteFrontDefault()), uriOf(model.getSpriteOfficialArtwork()));
    }

    private List<PokemonAbility> abilitiesOf(PokemonDataModel model) {
        return model.getAbilities().stream()
                .map(row -> new PokemonAbility(row.getName(), row.getSlot(), row.isHidden()))
                .toList();
    }

    private List<PokemonStat> statsOf(PokemonDataModel model) {
        return model.getStats().stream()
                .map(row -> new PokemonStat(row.getName(), row.getBaseValue(), row.getEffort()))
                .toList();
    }

    private List<PokemonType> typesOf(PokemonDataModel model) {
        return model.getTypes().stream()
                .map(row -> new PokemonType(row.getName(), row.getSlot()))
                .toList();
    }

    private List<EvolutionLink> evolutionLinksOf(PokemonDataModel model) {
        return model.getEvolutionLinks().stream()
                .map(row -> new EvolutionLink(
                        PokeApiId.of(row.getFromPokeApiId()),
                        PokeApiId.of(row.getToPokeApiId()),
                        row.getEvolutionTrigger(),
                        Optional.ofNullable(row.getMinLevel())))
                .toList();
    }

    // the one query that decides which half of the partition a row belongs to
    private List<LocalizedName> localizedNamesOf(PokemonDataModel model, NameSource source) {
        return model.getLocalizedNames().stream()
                .filter(row -> row.getSource() == source)
                .map(row -> new LocalizedName(row.getLocale(), row.getValue(), row.getSource()))
                .toList();
    }

    private URI uriOf(String text) {
        return text == null ? null : URI.create(text);
    }

    private String textOf(URI uri) {
        return uri == null ? null : uri.toString();
    }
}
