package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.contract.dto.AbilityDTO;
import com.elatusdev.pokedex.contract.dto.CreateLocalPokemonRequestDTO;
import com.elatusdev.pokedex.contract.dto.CuratorLocalizedNameDTO;
import com.elatusdev.pokedex.contract.dto.EvolutionEdgeDTO;
import com.elatusdev.pokedex.contract.dto.LocalPokemonDTO;
import com.elatusdev.pokedex.contract.dto.LocalPokemonPageDTO;
import com.elatusdev.pokedex.contract.dto.LocalizedNameDTO;
import com.elatusdev.pokedex.contract.dto.NameSourceDTO;
import com.elatusdev.pokedex.contract.dto.PageMetadataDTO;
import com.elatusdev.pokedex.contract.dto.PatchLocalPokemonRequestDTO;
import com.elatusdev.pokedex.contract.dto.RegionDTO;
import com.elatusdev.pokedex.contract.dto.ReplaceLocalPokemonRequestDTO;
import com.elatusdev.pokedex.contract.dto.ReplicationStateDTO;
import com.elatusdev.pokedex.contract.dto.SpriteDTO;
import com.elatusdev.pokedex.contract.dto.StatDTO;
import com.elatusdev.pokedex.contract.dto.TypeSlotDTO;
import com.elatusdev.pokedex.pokedex.application.CreateLocalPokemonCommand;
import com.elatusdev.pokedex.pokedex.application.LocalPokemonPageResult;
import com.elatusdev.pokedex.pokedex.application.UpdateLocalPokemonCommand;
import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// The unit conversions live in Mass and Height, never here: a second divide-by-ten at a
// call site is how "Bulbasaur weighs 69 kg" gets shipped.
@Component
public class LocalPokemonWebMapper {

    public LocalPokemonPageDTO toPage(LocalPokemonPageResult result) {
        int totalPages = result.size() == 0 ? 0 : (int) Math.ceil((double) result.totalCount() / result.size());
        return new LocalPokemonPageDTO(
                result.rows().stream().map(this::toDto).toList(),
                new PageMetadataDTO(result.page(), result.size(), result.totalCount(), totalPages));
    }

    public LocalPokemonDTO toDto(Pokemon pokemon) {
        return new LocalPokemonDTO(
                        pokemon.id().orElseThrow().value(),
                        pokemon.replicated().name().value(),
                        ReplicationStateDTO.fromValue(pokemon.replicationState().name()),
                        pokemon.version())
                .pokeApiId(pokemon.pokeApiId().map(PokeApiId::value).orElse(null))
                .category(pokemon.replicated().category().map(Category::value).orElse(null))
                .description(pokemon.replicated().description().map(Description::value).orElse(null))
                .massKilograms(pokemon.replicated().mass().toKilograms())
                .heightMetres(pokemon.replicated().height().toMetres())
                .baseExperience(pokemon.replicated().baseExperience())
                .sprite(toSprite(pokemon))
                .abilities(pokemon.replicated().abilities().stream()
                        .map(ability -> new AbilityDTO(ability.name(), ability.slot(), ability.hidden()))
                        .toList())
                .stats(pokemon.replicated().stats().stream()
                        .map(stat -> new StatDTO(stat.name(), stat.baseValue(), stat.effort()))
                        .toList())
                .types(pokemon.replicated().types().stream()
                        .map(type -> new TypeSlotDTO(type.name(), type.slot()))
                        .toList())
                .evolution(pokemon.replicated().evolutionLinks().stream()
                        .map(link -> new EvolutionEdgeDTO(link.from().value(), link.to().value(), link.trigger())
                                .minLevel(link.minLevel().orElse(null)))
                        .toList())
                .region(pokemon.proprietary()
                        .region()
                        .map(region -> RegionDTO.fromValue(region.name()))
                        .orElse(null))
                .notes(pokemon.proprietary().notes().map(Notes::value).orElse(null))
                .tags(pokemon.tags().stream().map(Tag::label).toList())
                .curatedBy(pokemon.curatedBy().map(curator -> curator.value()).orElse(null))
                .localizedNames(allNames(pokemon))
                .syncedAt(pokemon.syncedAt().map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null));
    }

    public CreateLocalPokemonCommand toCommand(CreateLocalPokemonRequestDTO request) {
        return new CreateLocalPokemonCommand(
                new PokemonName(request.getName()),
                Optional.ofNullable(request.getPokeApiId()).map(PokeApiId::of),
                Mass.ofHectograms(request.getMassHectograms()),
                Height.ofDecimetres(request.getHeightDecimetres()),
                Optional.ofNullable(request.getCategory()).map(Category::new),
                Optional.ofNullable(request.getDescription()).map(Description::new),
                Optional.ofNullable(request.getRegion()).map(region -> Region.fromString(region.getValue())),
                Optional.ofNullable(request.getNotes()).map(Notes::new),
                request.getTags().stream().map(Tag::new).toList());
    }

    public UpdateLocalPokemonCommand toCommand(ReplaceLocalPokemonRequestDTO request) {
        return command(
                request.getVersion(),
                request.getRegion(),
                request.getNotes(),
                request.getTags(),
                request.getLocalizedNames());
    }

    public UpdateLocalPokemonCommand toCommand(PatchLocalPokemonRequestDTO request) {
        return command(
                request.getVersion(),
                request.getRegion(),
                request.getNotes(),
                request.getTags(),
                request.getLocalizedNames());
    }

    // PUT and PATCH carry the same proprietary payload, so they map identically. The
    // difference between them is the contract's, not this mapper's.
    private static UpdateLocalPokemonCommand command(
            Long version,
            RegionDTO region,
            String notes,
            List<String> tags,
            List<CuratorLocalizedNameDTO> localizedNames) {
        return new UpdateLocalPokemonCommand(
                version,
                Optional.ofNullable(region).map(value -> Region.fromString(value.getValue())),
                Optional.ofNullable(notes).map(Notes::new),
                tags.stream().map(Tag::new).toList(),
                localizedNames.stream()
                        .map(name -> new LocalizedName(name.getLocale(), name.getValue(), NameSource.CURATOR))
                        .toList());
    }

    // upstream and curator names go out together, each labelled with its source, so a
    // client can tell a translation we replicated from one a curator wrote
    private static List<LocalizedNameDTO> allNames(Pokemon pokemon) {
        return java.util.stream.Stream.concat(
                        pokemon.replicated().upstreamNames().stream(),
                        pokemon.proprietary().curatorNames().stream())
                .map(name -> new LocalizedNameDTO(
                        name.locale(), name.value(), NameSourceDTO.fromValue(name.source().name())))
                .toList();
    }

    private static SpriteDTO toSprite(Pokemon pokemon) {
        SpriteDTO sprite = new SpriteDTO();
        pokemon.replicated().sprite().preferred().ifPresent(sprite::setOfficialArtwork);
        return sprite;
    }
}
