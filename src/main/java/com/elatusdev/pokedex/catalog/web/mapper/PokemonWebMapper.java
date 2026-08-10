package com.elatusdev.pokedex.catalog.web.mapper;

import com.elatusdev.pokedex.catalog.application.result.PokemonDetailResult;
import com.elatusdev.pokedex.catalog.application.result.PokemonPageResult;
import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.web.dto.AbilityDTO;
import com.elatusdev.pokedex.web.dto.EvolutionEdgeDTO;
import com.elatusdev.pokedex.web.dto.LocalizedNameDTO;
import com.elatusdev.pokedex.web.dto.NameSourceDTO;
import com.elatusdev.pokedex.web.dto.PageMetadataDTO;
import com.elatusdev.pokedex.web.dto.PokemonDetailDTO;
import com.elatusdev.pokedex.web.dto.StatDTO;
import com.elatusdev.pokedex.web.dto.TypeSlotDTO;
import com.elatusdev.pokedex.web.dto.PokemonPageDTO;
import com.elatusdev.pokedex.web.dto.PokemonSummaryDTO;
import com.elatusdev.pokedex.web.dto.SpriteDTO;
import org.springframework.stereotype.Component;

// The kilogram conversion happens in the Mass value object, never here — a second
// divide-by-ten at a call site is how "Bulbasaur weighs 69 kg" gets shipped.
@Component
public class PokemonWebMapper {

    public PokemonPageDTO toPage(PokemonPageResult result) {
        return new PokemonPageDTO(
                result.rows().stream().map(row -> toSummary(row, result.stale())).toList(),
                new PageMetadataDTO(result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    public PokemonSummaryDTO toSummary(Pokemon pokemon, boolean stale) {
        return new PokemonSummaryDTO(
                        pokemon.pokeApiId().map(id -> id.value()).orElse(null),
                        pokemon.replicated().name().value(),
                        pokemon.replicated().mass().toKilograms(),
                        pokemon.replicated().abilities().stream()
                                .map(ability -> new AbilityDTO(ability.name(), ability.slot(), ability.hidden()))
                                .toList(),
                        stale)
                .category(pokemon.replicated().category().map(category -> category.value()).orElse(null))
                .sprite(toSprite(pokemon));
    }

    public PokemonDetailDTO toDetail(PokemonDetailResult result) {
        Pokemon pokemon = result.pokemon();
        return new PokemonDetailDTO(
                        pokemon.pokeApiId().map(id -> id.value()).orElse(null),
                        pokemon.replicated().name().value(),
                        pokemon.replicated().mass().toKilograms(),
                        pokemon.replicated().height().toMetres(),
                        pokemon.replicated().abilities().stream()
                                .map(ability -> new AbilityDTO(ability.name(), ability.slot(), ability.hidden()))
                                .toList(),
                        pokemon.replicated().stats().stream()
                                .map(stat -> new StatDTO(stat.name(), stat.baseValue(), stat.effort()))
                                .toList(),
                        pokemon.replicated().types().stream()
                                .map(type -> new TypeSlotDTO(type.name(), type.slot()))
                                .toList(),
                        result.stale())
                .category(pokemon.replicated().category().map(category -> category.value()).orElse(null))
                .description(pokemon.replicated().description().map(text -> text.value()).orElse(null))
                .baseExperience(pokemon.replicated().baseExperience())
                .sprite(toSprite(pokemon))
                .localizedNames(pokemon.replicated().upstreamNames().stream()
                        .map(name -> new LocalizedNameDTO(
                                name.locale(), name.value(), NameSourceDTO.fromValue(name.source().name())))
                        .toList())
                .evolution(pokemon.replicated().evolutionLinks().stream()
                        .map(link -> new EvolutionEdgeDTO(link.from().value(), link.to().value(), link.trigger())
                                .minLevel(link.minLevel().orElse(null)))
                        .toList());
    }

    private SpriteDTO toSprite(Pokemon pokemon) {
        SpriteDTO sprite = new SpriteDTO();
        pokemon.replicated().sprite().preferred().ifPresent(sprite::setOfficialArtwork);
        return sprite;
    }
}
