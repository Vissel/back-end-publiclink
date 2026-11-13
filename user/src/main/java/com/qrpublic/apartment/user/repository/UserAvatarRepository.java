package com.qrpublic.apartment.user.repository;

import com.qrpublic.apartment.user.entity.UserAvatarEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Avatar storage keyed by username. The username always comes from the
 * gateway-forwarded X-User-ID header, never from the request body, so a caller
 * can only ever read or write their own picture.
 */
public interface UserAvatarRepository extends JpaRepository<UserAvatarEntity, String> {
}
