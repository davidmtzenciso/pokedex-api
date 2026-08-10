package com.elatusdev.pokedex.identity.interfaces;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenDataModel, Long> {

    Optional<RefreshTokenDataModel> findByJti(String jti);

    List<RefreshTokenDataModel> findByFamilyId(String familyId);
}
