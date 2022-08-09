package com.CollegeAdmission.controller;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.CollegeAdmission.model.Admission;
import com.CollegeAdmission.service.CollegeAdmission;


@RestController
@RequestMapping("/stdadm")
public class AdmissionController 
{
	@Autowired
	private CollegeAdmission adm;
	
	//Get(read),Post(create/add),Put(update),Delete
	@GetMapping("/get")
	public ResponseEntity<List<Admission>> getAll()
	{
		return ResponseEntity.ok(adm.findAll());
	}
	@GetMapping("/{id}")
	public ResponseEntity<Admission> getAllById(@PathVariable Long id)
	{
		return ResponseEntity.ok(adm.findById(id).orElse(null));
	}
	
	@PostMapping("/add")
	public ResponseEntity<Admission> addAll(@RequestBody Admission Admission)
	{
		return ResponseEntity.ok(adm.save(Admission));
		
	}
	@PutMapping("/update")
	public ResponseEntity<Admission> updateAll(@RequestBody Admission Admission)
	{
		return ResponseEntity.ok(adm.save(Admission));
		
	}
	

	@DeleteMapping("/{id}")
	public ResponseEntity<Admission> delete(@PathVariable Long id)
	{
		adm.findById(id).ifPresent(adm::delete);
		return ResponseEntity.ok().build();
	}
}
