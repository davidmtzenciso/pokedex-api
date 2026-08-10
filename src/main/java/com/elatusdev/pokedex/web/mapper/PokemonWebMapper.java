package com.elatusdev.pokedex.web.mapper;

import com.elatusdev.pokedex.application.result.PokemonPageResult;
import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.web.dto.AbilityDTO;
import com.elatusdev.pokedex.web.dto.PageMetadataDTO;
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

    private SpriteDTO toSprite(Pokemon pokemon) {
        SpriteDTO sprite = new SpriteDTO();
        pokemon.replicated().sprite().preferred().ifPresent(sprite::setOfficialArtwork);
        return sprite;
    }
}
