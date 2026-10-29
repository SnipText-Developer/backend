package com.sniptext.domain.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sniptext.domain.user.entity.UserTermsAgreement;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

	List<UserTermsAgreement> findAllByUserId(Long userId);

}
