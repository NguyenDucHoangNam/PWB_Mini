package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.model.VoiceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoiceTagRepository extends JpaRepository<VoiceTag, String> {

  @Query("SELECT vt FROM VoiceTag vt WHERE vt.id = :id AND vt.isDeleted = false")
  Optional<VoiceTag> findActiveById(@Param("id") String id);

  @Query("SELECT vt FROM VoiceTag vt WHERE vt.ownerId = :ownerId AND vt.isDeleted = false "
      + "ORDER BY vt.isDefault DESC, vt.createdAt DESC")
  List<VoiceTag> findActiveByOwner(@Param("ownerId") String ownerId);

  @Query("SELECT COUNT(vt) FROM VoiceTag vt WHERE vt.ownerId = :ownerId AND vt.isDeleted = false")
  long countActiveByOwner(@Param("ownerId") String ownerId);

  @Query("SELECT vt FROM VoiceTag vt WHERE vt.ownerId = :ownerId AND vt.isDefault = true "
      + "AND vt.isDeleted = false")
  Optional<VoiceTag> findDefaultByOwner(@Param("ownerId") String ownerId);

  @Modifying
  @Query("UPDATE VoiceTag vt SET vt.isDefault = false WHERE vt.ownerId = :ownerId "
      + "AND vt.isDefault = true AND vt.isDeleted = false")
  int clearDefaultForOwner(@Param("ownerId") String ownerId);

  @Query("SELECT vt FROM VoiceTag vt WHERE vt.id = :id AND vt.ownerId = :ownerId "
      + "AND vt.isDeleted = false")
  Optional<VoiceTag> findActiveByIdAndOwner(@Param("id") String id, @Param("ownerId") String ownerId);
}
