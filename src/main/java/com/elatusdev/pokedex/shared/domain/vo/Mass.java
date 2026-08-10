package com.elatusdev.pokedex.shared.domain.vo;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record Mass(int hectograms) {

    public Mass {
        if (hectograms <= 0) {
            throw new InvalidPokemonDataException("mass must be positive, was " + hectograms);
        }
    }

    public static Mass ofHectograms(int hectograms) {
        return new Mass(hectograms);
    }

    public BigDecimal toKilograms() {
        return BigDecimal.valueOf(hectograms).divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP);
    }
}
