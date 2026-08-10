package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.contract.dto.AbilityDTO;
import com.elatusdev.pokedex.contract.dto.EvolutionEdgeDTO;
import com.elatusdev.pokedex.contract.dto.LocalPokemonDTO;
import com.elatusdev.pokedex.contract.dto.LocalizedNameDTO;
import com.elatusdev.pokedex.contract.dto.NameSourceDTO;
import com.elatusdev.pokedex.contract.dto.RegionDTO;
import com.elatusdev.pokedex.contract.dto.ReplicationStateDTO;
import com.elatusdev.pokedex.contract.dto.SpriteDTO;
import com.elatusdev.pokedex.contract.dto.StatDTO;
import com.elatusdev.pokedex.contract.dto.TypeSlotDTO;
import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.ProprietaryFields;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

// The third shape. Pokemon is the domain object, PokemonDataModel is the row, and this is
// the wire — and the boundary is where the units finally convert: the aggregate stores
// hectograms and decimetres because that is what upstream sends (IA3), and only here does
// it become the kilograms and metres a reader expects (F8).
//
// Both halves of the partition flatten into one DTO, which is correct: the client sees one
// Pokemon. The partition is a rule about who may write each field, not about who may read it.
@Component
public class LocalPokemonDtoMapper {

    public LocalPokemonDTO toDto(Pokemon pokemon) {
        ReplicatedFields replicated = pokemon.replicated();
        ProprietaryFields proprietary = pokemon.proprietary();
        return new LocalPokemonDTO(
                        pokemon.id().map(PokemonId::value).orElse(null),
                        replicated.name().value(),
                        ReplicationStateDTO.fromValue(pokemon.replicationState().name()),
                        pokemon.version())
                .pokeApiId(pokemon.pokeApiId().map(PokeApiId::value).orElse(null))
                .category(replicated.category().map(Category::value).orElse(null))
                .description(replicated.description().map(Description::value).orElse(null))
                .massKilograms(replicated.mass().toKilograms())
                .heightMetres(replicated.height().toMetres())
                .baseExperience(replicated.baseExperience())
                .sprite(sprite(pokemon))
                .abilities(abilities(replicated))
                .stats(stats(replicated))
                .types(types(replicated))
                .evolution(evolution(replicated))
                .region(proprietary.region().map(Region::name).map(RegionDTO::fromValue).orElse(null))
                .notes(proprietary.notes().map(Notes::value).orElse(null))
                .tags(proprietary.tags().stream().map(Tag::label).toList())
                .localizedNames(localizedNames(pokemon))
                .curatedBy(proprietary.curatedBy().map(UserId::value).orElse(null))
                .syncedAt(pokemon.syncedAt().map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null));
    }

    // the contract types both sprite fields as URI, so nothing is stringified on the way
    // out; Sprite.NONE carries two nulls and they pass straight through as absent
    private static SpriteDTO sprite(Pokemon pokemon) {
        return new SpriteDTO()
                .frontDefault(pokemon.replicated().sprite().frontDefault())
                .officialArtwork(pokemon.replicated().sprite().officialArtwork());
    }

    // both halves, in one list, each carrying the discriminator that says which it is — the
    // client resolves displayName by preferring CURATOR, and cannot do that without it
    private static List<LocalizedNameDTO> localizedNames(Pokemon pokemon) {
        return Stream.concat(
                        pokemon.replicated().upstreamNames().stream(),
                        pokemon.proprietary().curatorNames().stream())
                .map(LocalPokemonDtoMapper::localizedName)
                .toList();
    }

    private static LocalizedNameDTO localizedName(LocalizedName name) {
        return new LocalizedNameDTO(name.locale(), name.value(), NameSourceDTO.fromValue(name.source().name()));
    }

    private static List<AbilityDTO> abilities(ReplicatedFields replicated) {
        return replicated.abilities().stream()
                .map(ability -> new AbilityDTO(ability.name(), ability.slot(), ability.hidden()))
                .toList();
    }

    private static List<StatDTO> stats(ReplicatedFields replicated) {
        return replicated.stats().stream()
                .map(stat -> new StatDTO(stat.name(), stat.baseValue(), stat.effort()))
                .toList();
    }

    private static List<TypeSlotDTO> types(ReplicatedFields replicated) {
        return replicated.types().stream()
                .map(type -> new TypeSlotDTO(type.name(), type.slot()))
                .toList();
    }

    private static List<EvolutionEdgeDTO> evolution(ReplicatedFields replicated) {
        return replicated.evolutionLinks().stream()
                .map(link -> new EvolutionEdgeDTO(link.from().value(), link.to().value(), link.trigger())
                        .minLevel(link.minLevel().orElse(null)))
                .toList();
    }
}
