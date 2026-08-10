package com.elatusdev.pokedex.pokedex.infrastructure.persistence.model;

import com.elatusdev.pokedex.pokedex.domain.model.NameSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "localized_name")
public class LocalizedNameDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locale", nullable = false, length = 20)
    private String locale;

    @Column(name = "value", nullable = false, length = 120)
    private String value;

    // the discriminator the merge policy turns on: re-sync replaces UPSTREAM and preserves
    // CURATOR (F7). Storing it as the enum keeps the two sets nameable in a query.
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private NameSource source;

    protected LocalizedNameDataModel() {
    }

    public LocalizedNameDataModel(String locale, String value, NameSource source) {
        this.locale = locale;
        this.value = value;
        this.source = source;
    }

    public String getLocale() {
        return locale;
    }

    public String getValue() {
        return value;
    }

    public NameSource getSource() {
        return source;
    }
}
