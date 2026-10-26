package com.sniptext.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sniptext.domain.template.entity.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {

}
