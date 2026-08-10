// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record Height(int decimetres) {

    public Height {
        if (decimetres <= 0) {
            throw new InvalidPokemonDataException("height must be positive, was " + decimetres);
        }
    }

    public static Height ofDecimetres(int decimetres) {
        return new Height(decimetres);
    }

    public BigDecimal toMetres() {
        return BigDecimal.valueOf(decimetres).divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP);
    }
}
