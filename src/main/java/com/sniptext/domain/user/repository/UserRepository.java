package com.sniptext.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sniptext.domain.user.entity.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

	/** 탈퇴하지 않은 회원을 찾는다. */
	Optional<User> findByIdAndDeletedAtIsNull(Long id);

	/** 탈퇴하지 않은 회원 행을 비관적 쓰기 잠금으로 찾는다 (SELECT ... FOR UPDATE). 잠금은 호출자 트랜잭션이 끝날 때 풀린다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id and u.deletedAt is null")
	Optional<User> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

}
