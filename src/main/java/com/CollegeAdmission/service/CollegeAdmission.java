package com.CollegeAdmission.service;

import org.springframework.data.jpa.repository.JpaRepository;

import com.CollegeAdmission.model.Admission;

public interface CollegeAdmission extends JpaRepository<Admission, Long> {

}
